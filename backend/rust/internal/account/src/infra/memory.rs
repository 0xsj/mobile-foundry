use crate::{
    app::{
        self,
        ports::{BootstrapResult, CommittedFact, Directory, PortFuture, Scope, Store},
    },
    domain::{self, Account, Action, Fact},
};
use kernel::{
    Failure,
    id::{Generator, Id},
};
use std::{
    collections::{BTreeMap, HashSet},
    sync::{Arc, Mutex, MutexGuard},
};

#[derive(Default)]
struct State {
    accounts: BTreeMap<Id, Account>,
    personal: BTreeMap<Id, Id>,
    facts: Vec<CommittedFact>,
    fact_ids: HashSet<Id>,
    outbox: events::memory::Records,
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
        let state = self.state.lock().map_err(|_| domain::invalid_state())?;
        scope.check()?;
        Ok(state)
    }
    fn prepare(
        &self,
        scope: &dyn Scope,
        account: &Account,
        fact: Fact,
        expected: u64,
    ) -> Result<Prepared, Failure> {
        scope.check()?;
        let s = account.snapshot();
        Account::restore(s.clone())?;
        let required = account.fact(
            if expected == 0 {
                Action::Created
            } else {
                Action::Renamed
            },
            &s.owner_id,
        )?;
        if fact != required || expected.checked_add(1) != Some(s.version) {
            return Err(domain::invalid_state());
        }
        let id = self
            .ids
            .new_id()
            .map_err(|cause| domain::invalid_state().with_cause(cause))?;
        let record = CommittedFact {
            id,
            fact,
            metadata: scope.metadata(),
        };
        let message = super::events::message(&record)?;
        Ok(Prepared { record, message })
    }
    fn admit(state: &State, fact: &Prepared, is_new: bool) -> Result<(), Failure> {
        if state.fact_ids.contains(&fact.record.id) {
            return Err(domain::invalid_state());
        }
        if state.facts.len() >= 10_000 || (is_new && state.accounts.len() >= 1000) {
            return Err(app::capacity());
        }
        Ok(())
    }
    fn append(state: &mut State, account: Account, fact: Prepared) {
        state
            .accounts
            .insert(account.snapshot().id.clone(), account);
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
    fn get<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<Account>> {
        Box::pin(async move { Ok(self.lock(scope)?.accounts.get(id).cloned()) })
    }
    fn create<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        fact: Fact,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let fact = self.prepare(scope, &account, fact, 0)?;
            let mut state = self.lock(scope)?;
            if state.accounts.contains_key(&account.snapshot().id) {
                return Err(app::conflict());
            }
            Self::admit(&state, &fact, true)?;
            Self::append(&mut state, account, fact);
            Ok(())
        })
    }
    fn rename<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        expected: u64,
        fact: Fact,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let fact = self.prepare(scope, &account, fact, expected)?;
            let mut state = self.lock(scope)?;
            let prior = state
                .accounts
                .get(&account.snapshot().id)
                .ok_or_else(app::not_found)?
                .snapshot();
            let next = account.snapshot();
            if prior.version != expected {
                return Err(app::conflict());
            }
            if prior.owner_id != next.owner_id
                || prior.created_at != next.created_at
                || next.updated_at < prior.updated_at
            {
                return Err(domain::invalid_state());
            }
            Self::admit(&state, &fact, false)?;
            Self::append(&mut state, account, fact);
            Ok(())
        })
    }
}
impl Directory for MemoryStore {
    fn bootstrap<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        fact: Fact,
    ) -> PortFuture<'a, BootstrapResult> {
        Box::pin(async move {
            let fact = self.prepare(scope, &account, fact, 0)?;
            let mut state = self.lock(scope)?;
            let s = account.snapshot();
            if let Some(id) = state.personal.get(&s.owner_id) {
                let existing = state
                    .accounts
                    .get(id)
                    .filter(|a| a.owned_by(&s.owner_id))
                    .ok_or_else(domain::invalid_state)?;
                return Ok(BootstrapResult {
                    account: existing.clone(),
                    created: false,
                });
            }
            if state.accounts.contains_key(&s.id) {
                return Err(app::conflict());
            }
            Self::admit(&state, &fact, true)?;
            state.personal.insert(s.owner_id.clone(), s.id.clone());
            Self::append(&mut state, account.clone(), fact);
            Ok(BootstrapResult {
                account,
                created: true,
            })
        })
    }
    fn list<'a>(
        &'a self,
        scope: &'a dyn Scope,
        owner: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<Account>> {
        Box::pin(async move {
            if !(1..=101).contains(&limit) {
                return Err(app::invalid_page());
            }
            Ok(self
                .lock(scope)?
                .accounts
                .iter()
                .filter(|(id, row)| row.owned_by(owner) && after.is_none_or(|after| *id > after))
                .take(limit)
                .map(|(_, row)| row.clone())
                .collect())
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
                    "account.delivery_exhausted",
                    "event delivery exhausted retries",
                )
                .with_public("Account event delivery needs attention."));
            }
            Ok(())
        })
    }
}
