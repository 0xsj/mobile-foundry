mod recovery;
use crate::{
    app::{
        self,
        ports::{CommittedFact, PortFuture, Rotation, Scope, SessionEvidence, Store},
    },
    domain::{self, Action, Digest, Fact, Principal, Secret, Session, Timestamp},
};
use kernel::{
    Failure,
    id::{Generator, Id},
};
use std::{
    collections::{HashMap, HashSet},
    sync::{Arc, Mutex, MutexGuard},
};

#[derive(Default)]
struct State {
    principals: HashMap<Id, Principal>,
    logins: HashMap<String, Id>,
    sessions: HashMap<Id, Session>,
    access: HashMap<Digest, Id>,
    refresh: HashMap<Digest, (Id, u64)>,
    facts: Vec<CommittedFact>,
    fact_ids: HashSet<Id>,
    outbox: events::memory::Records,
    revisions: HashMap<Id, u64>,
    contacts: HashMap<Id, crate::domain::recovery::Contact>,
    challenges: HashMap<(Id, crate::domain::recovery::Purpose), crate::domain::recovery::Challenge>,
}
struct Prepared {
    record: CommittedFact,
    message: events::Envelope,
}
pub struct MemoryStore {
    state: Mutex<State>,
    ids: Arc<dyn Generator>,
}
impl MemoryStore {
    pub fn new(ids: Arc<dyn Generator>) -> Self {
        Self {
            state: Mutex::new(State::default()),
            ids,
        }
    }
    fn lock(&self, scope: &dyn Scope) -> Result<MutexGuard<'_, State>, Failure> {
        let guard = self.state.lock().map_err(|_| domain::invalid_state())?;
        scope.check()?;
        Ok(guard)
    }
    fn prepare(&self, scope: &dyn Scope, state: &State, fact: Fact) -> Result<Prepared, Failure> {
        if state.facts.len() >= 10000 {
            return Err(app::capacity());
        }
        let id = self
            .ids
            .new_id()
            .map_err(|cause| domain::invalid_state().with_cause(cause))?;
        if state.fact_ids.contains(&id) {
            return Err(domain::invalid_state());
        }
        scope.check()?;
        let record = CommittedFact {
            id,
            fact,
            metadata: scope.metadata(),
        };
        let message = super::events::message(&record)?;
        Ok(Prepared { record, message })
    }
    fn append(state: &mut State, fact: Prepared) {
        state.fact_ids.insert(fact.record.id.clone());
        state.outbox.append_admitted(fact.message);
        state.facts.push(fact.record);
    }
    pub fn facts(&self) -> Result<Vec<CommittedFact>, Failure> {
        Ok(self
            .state
            .lock()
            .map_err(|_| domain::invalid_state())?
            .facts
            .clone())
    }
}
impl Store for MemoryStore {
    fn by_login<'a>(
        &'a self,
        scope: &'a dyn Scope,
        login: &'a str,
    ) -> PortFuture<'a, Option<Principal>> {
        Box::pin(async move {
            let state = self.lock(scope)?;
            Ok(state
                .logins
                .get(login)
                .and_then(|id| state.principals.get(id))
                .cloned())
        })
    }
    fn by_id<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<Principal>> {
        Box::pin(async move { Ok(self.lock(scope)?.principals.get(id).cloned()) })
    }
    fn register<'a>(&'a self, scope: &'a dyn Scope, principal: Principal) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(scope)?;
            if state.logins.contains_key(principal.login()) {
                return Err(app::login_exists());
            }
            if state.principals.contains_key(principal.id()) {
                return Err(domain::invalid_state());
            }
            if state.principals.len() >= 1000 {
                return Err(app::capacity());
            }
            let fact = self.prepare(scope, &state, principal.fact())?;
            state
                .logins
                .insert(principal.login().into(), principal.id().clone());
            state.revisions.insert(principal.id().clone(), 1);
            state.principals.insert(principal.id().clone(), principal);
            Self::append(&mut state, fact);
            Ok(())
        })
    }
    fn issue_checked<'a>(
        &'a self,
        scope: &'a dyn Scope,
        session: Session,
        expected: &'a Secret,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(scope)?;
            let s = session.state();
            let principal = state
                .principals
                .get(&s.principal_id)
                .ok_or_else(domain::invalid_state)?;
            if principal.password_hash().expose() != expected.expose() {
                return Err(domain::credential_refused());
            }
            if s.generation != 1
                || s.revoked_at.is_some()
                || state.sessions.contains_key(&s.id)
                || state.access.contains_key(&s.access)
                || state.refresh.contains_key(&s.refresh)
            {
                return Err(domain::invalid_state());
            }
            if state.sessions.len() >= 10000 || state.refresh.len() >= 100000 {
                return Err(app::capacity());
            }
            let fact = self.prepare(scope, &state, session.fact(Action::Issued))?;
            state.access.insert(s.access.clone(), s.id.clone());
            state
                .refresh
                .insert(s.refresh.clone(), (s.id.clone(), s.generation));
            state.sessions.insert(s.id.clone(), session);
            Self::append(&mut state, fact);
            Ok(())
        })
    }
    fn rotate<'a>(
        &'a self,
        scope: &'a dyn Scope,
        previous: Digest,
        access: Digest,
        refresh: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, Rotation> {
        Box::pin(async move {
            let mut state = self.lock(scope)?;
            let (id, generation) = state
                .refresh
                .get(&previous)
                .cloned()
                .ok_or_else(domain::credential_refused)?;
            let current = state.sessions.get(&id).ok_or_else(domain::invalid_state)?;
            if !current.renewable(now) {
                return Err(domain::credential_refused());
            }
            let reused =
                generation != current.state().generation || previous != current.state().refresh;
            let old_access = current.state().access.clone();
            let next = if reused {
                current.revoke(now)?
            } else {
                if state.refresh.len() >= 100000 {
                    return Err(app::capacity());
                }
                if state.refresh.contains_key(&refresh) || state.access.contains_key(&access) {
                    return Err(domain::invalid_state());
                }
                current.rotate(access, refresh, now)?
            };
            let fact = self.prepare(
                scope,
                &state,
                next.fact(if reused {
                    Action::RefreshReused
                } else {
                    Action::Renewed
                }),
            )?;
            state.access.remove(&old_access);
            if !reused {
                state.access.insert(next.state().access.clone(), id.clone());
                state.refresh.insert(
                    next.state().refresh.clone(),
                    (id.clone(), next.state().generation),
                );
            }
            state.sessions.insert(id, next.clone());
            Self::append(&mut state, fact);
            Ok(if reused {
                Rotation::Reused
            } else {
                Rotation::Rotated(next)
            })
        })
    }
    fn revoke<'a>(
        &'a self,
        scope: &'a dyn Scope,
        refresh: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(scope)?;
            let (id, _) = state
                .refresh
                .get(&refresh)
                .cloned()
                .ok_or_else(domain::credential_refused)?;
            let current = state.sessions.get(&id).ok_or_else(domain::invalid_state)?;
            if current.state().revoked_at.is_some() {
                return Ok(());
            }
            let old_access = current.state().access.clone();
            let next = current.revoke(now)?;
            let fact = self.prepare(scope, &state, next.fact(Action::SignedOut))?;
            state.access.remove(&old_access);
            state.sessions.insert(id, next);
            Self::append(&mut state, fact);
            Ok(())
        })
    }
    fn access_session<'a>(
        &'a self,
        scope: &'a dyn Scope,
        access: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, SessionEvidence> {
        Box::pin(async move {
            let state = self.lock(scope)?;
            let session = state
                .access
                .get(&access)
                .and_then(|id| state.sessions.get(id))
                .ok_or_else(domain::credential_refused)?;
            if !session.active(now) {
                return Err(domain::credential_refused());
            }
            Ok(SessionEvidence {
                principal_id: session.state().principal_id.clone(),
                session_id: session.state().id.clone(),
                expires_at: session.state().expires_at,
            })
        })
    }
}

impl events::dispatcher::Outbox for MemoryStore {
    fn pending<'a>(
        &'a self,
        scope: &'a dyn Scope,
        limit: usize,
    ) -> PortFuture<'a, Vec<events::Envelope>> {
        Box::pin(async move { self.lock(scope)?.outbox.pending(limit) })
    }
    fn delivered<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, ()> {
        Box::pin(async move { self.lock(scope)?.outbox.delivered(id) })
    }
    fn failed<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id, code: &'a str) -> PortFuture<'a, ()> {
        Box::pin(async move { self.lock(scope)?.outbox.failed(id, code) })
    }
    fn ready<'a>(&'a self, scope: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if self.lock(scope)?.outbox.has_dead() {
                return Err(kernel::Failure::new(
                    kernel::Kind::Unavailable,
                    "identity.delivery_exhausted",
                    "event delivery exhausted retries",
                )
                .with_public("Identity event delivery needs attention."));
            }
            Ok(())
        })
    }
}
