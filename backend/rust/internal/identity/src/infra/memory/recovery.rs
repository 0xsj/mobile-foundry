use super::{MemoryStore, State};
use crate::infra::Mutation;
use crate::{
    app::{
        self,
        ports::{PortFuture, Scope},
        recovery::{RecoveryStore, challenge_refused, contact_conflict},
    },
    domain::{
        self, Action, Digest, Fact, Principal, Secret, Timestamp,
        recovery::{Challenge, Contact, Purpose},
    },
};
use kernel::{Failure, id::Id};
use std::collections::HashSet;
fn challenge(
    state: &State,
    digest: &Digest,
    purpose: Purpose,
    now: Timestamp,
) -> Result<Challenge, Failure> {
    state
        .challenges
        .values()
        .find(|c| {
            c.purpose == purpose
                && &c.digest == digest
                && c.active(now)
                && state
                    .principals
                    .get(&c.principal_id)
                    .is_some_and(|p| p.password_hash().expose() == c.credential_hash.expose())
        })
        .cloned()
        .ok_or_else(challenge_refused)
}
impl MemoryStore {
    fn change(
        &self,
        s: &dyn Scope,
        state: &mut State,
        actor: &Id,
        mutation: Mutation<'_>,
    ) -> Result<(), Failure> {
        let Mutation {
            expected,
            hash,
            now,
            reset,
        } = mutation;
        let p = state
            .principals
            .get(actor)
            .filter(|p| p.password_hash().expose() == expected.expose() && now >= p.created_at())
            .ok_or_else(domain::credential_refused)?;
        let next = p.with_password(hash)?;
        let revision = state
            .revisions
            .get(actor)
            .and_then(|v| v.checked_add(1))
            .ok_or_else(domain::invalid_state)?;
        let mut messages = vec![self.prepare(
            s,
            state,
            Fact {
                principal_id: actor.clone(),
                session_id: None,
                action: if reset {
                    Action::PasswordRecovered
                } else {
                    Action::PasswordChanged
                },
                version: revision,
                occurred_at: now,
            },
        )?];
        let mut seen = HashSet::from([messages[0].record.id.clone()]);
        let mut updates = Vec::new();
        let mut sessions: Vec<_> = state
            .sessions
            .values()
            .filter(|current| {
                &current.state().principal_id == actor && current.state().revoked_at.is_none()
            })
            .collect();
        sessions.sort_by_key(|current| &current.state().id);
        for current in sessions {
            let revoked = current.revoke(now)?;
            let message = self.prepare(
                s,
                state,
                revoked.fact(if reset {
                    Action::PasswordResetRevoked
                } else {
                    Action::PasswordChangeRevoked
                }),
            )?;
            if !seen.insert(message.record.id.clone()) {
                return Err(domain::invalid_state());
            }
            messages.push(message);
            updates.push(revoked);
        }
        if state.facts.len() + messages.len() > 10000 {
            return Err(app::capacity());
        }
        s.check()?;
        state.principals.insert(actor.clone(), next);
        state.revisions.insert(actor.clone(), revision);
        for revoked in updates {
            state.sessions.insert(revoked.state().id.clone(), revoked);
        }
        state.challenges.retain(|(id, _), _| id != actor);
        for message in messages {
            Self::append(state, message);
        }
        Ok(())
    }
}
impl RecoveryStore for MemoryStore {
    fn contact<'a>(&'a self, s: &'a dyn Scope, actor: &'a Id) -> PortFuture<'a, Option<Contact>> {
        Box::pin(async move { Ok(self.lock(s)?.contacts.get(actor).cloned()) })
    }
    fn by_email<'a>(
        &'a self,
        s: &'a dyn Scope,
        address: &'a str,
    ) -> PortFuture<'a, Option<Principal>> {
        Box::pin(async move {
            let state = self.lock(s)?;
            Ok(state
                .contacts
                .values()
                .find(|c| c.email == address)
                .and_then(|c| state.principals.get(&c.principal_id))
                .cloned())
        })
    }
    fn begin_challenge<'a>(&'a self, s: &'a dyn Scope, c: Challenge) -> PortFuture<'a, ()> {
        Box::pin(async move {
            c.validate()?;
            let mut state = self.lock(s)?;
            let p = state
                .principals
                .get(&c.principal_id)
                .ok_or_else(domain::credential_refused)?;
            if p.password_hash().expose() != c.credential_hash.expose()
                || c.created_at < p.created_at()
            {
                return Err(domain::credential_refused());
            }
            let contact = state.contacts.get(&c.principal_id);
            if contact.is_some_and(|v| v.email != c.email) {
                return Err(contact_conflict());
            }
            if c.purpose == Purpose::ResetPassword && contact.is_none_or(|v| v.email != c.email) {
                return Err(challenge_refused());
            }
            state
                .challenges
                .insert((c.principal_id.clone(), c.purpose), c);
            Ok(())
        })
    }
    fn verify_email<'a>(
        &'a self,
        s: &'a dyn Scope,
        digest: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let c = challenge(&state, &digest, Purpose::VerifyEmail, now)?;
            if state
                .contacts
                .get(&c.principal_id)
                .is_some_and(|v| v.email != c.email)
                || state
                    .contacts
                    .values()
                    .any(|v| v.email == c.email && v.principal_id != c.principal_id)
            {
                return Err(contact_conflict());
            }
            let contact = Contact {
                principal_id: c.principal_id.clone(),
                email: c.email.clone(),
                verified_at: now,
            };
            contact.validate()?;
            let revision = state
                .revisions
                .get(&c.principal_id)
                .and_then(|v| v.checked_add(1))
                .ok_or_else(domain::invalid_state)?;
            let fact = self.prepare(
                s,
                &state,
                Fact {
                    principal_id: c.principal_id.clone(),
                    session_id: None,
                    action: Action::EmailVerified,
                    version: revision,
                    occurred_at: now,
                },
            )?;
            state.contacts.insert(c.principal_id.clone(), contact);
            state.revisions.insert(c.principal_id.clone(), revision);
            state.challenges.remove(&(c.principal_id, c.purpose));
            Self::append(&mut state, fact);
            Ok(())
        })
    }
    fn reset_password<'a>(
        &'a self,
        s: &'a dyn Scope,
        digest: Digest,
        hash: Secret,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let c = challenge(&state, &digest, Purpose::ResetPassword, now)?;
            self.change(
                s,
                &mut state,
                &c.principal_id,
                Mutation {
                    expected: &c.credential_hash,
                    hash,
                    now,
                    reset: true,
                },
            )
        })
    }
    fn change_password<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        expected: Secret,
        hash: Secret,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            self.change(
                s,
                &mut state,
                actor,
                Mutation {
                    expected: &expected,
                    hash,
                    now,
                    reset: false,
                },
            )
        })
    }
    fn prune_challenges<'a>(
        &'a self,
        s: &'a dyn Scope,
        now: Timestamp,
        limit: usize,
    ) -> PortFuture<'a, usize> {
        Box::pin(async move {
            if !(1..=1000).contains(&limit) {
                return Err(domain::invalid_input());
            }
            let mut state = self.lock(s)?;
            let mut expired: Vec<_> = state
                .challenges
                .iter()
                .filter(|(_, c)| c.expires_at <= now)
                .map(|(key, _)| key.clone())
                .take(limit)
                .collect();
            let n = expired.len();
            for key in expired.drain(..) {
                state.challenges.remove(&key);
            }
            Ok(n)
        })
    }
}
