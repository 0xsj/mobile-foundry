use super::{MemoryStore, State};
use crate::{
    app::{
        self,
        installations::{self, Actor, Installations, Mutation, Revocation},
        ports::{PortFuture, Scope},
    },
    domain::installation::{Installation, Secret},
    infra::installation_records as records,
};
use kernel::{Failure, id::Id};
fn actor(state: &State, a: &Actor, now: u64) -> Result<(), Failure> {
    a.validate(now)?;
    if state
        .revoked
        .contains(&(a.principal.clone(), a.session.clone()))
    {
        return Err(installations::session_required());
    }
    Ok(())
}
fn find(state: &State, id: &Id, proof: &Secret) -> Result<Installation, Failure> {
    state
        .installations
        .get(id)
        .filter(|i| records::proof_matches(&i.snapshot().proof_digest, proof))
        .cloned()
        .ok_or_else(installations::missing)
}
impl Installations for MemoryStore {
    fn register<'a>(
        &'a self,
        s: &'a dyn Scope,
        m: Mutation,
        now: u64,
    ) -> PortFuture<'a, Installation> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            m.validate(now)?;
            actor(&state, &m.actor, now)?;
            let next = if let Some(prior) = state.installations.get(&m.id) {
                let v = prior.snapshot();
                if !records::proof_matches(&v.proof_digest, &m.proof_digest) {
                    return Err(installations::missing());
                }
                if m.expected == 0
                    || m.expected != v.version
                    || m.registration_id == v.registration_id
                {
                    return Err(installations::conflict());
                }
                prior.bind(
                    m.actor.principal.clone(),
                    m.actor.session.clone(),
                    m.registration_id.clone(),
                    m.spec.clone(),
                    now,
                    m.actor.expires_at,
                )?
            } else {
                if m.expected != 0 {
                    return Err(installations::missing());
                }
                if state.installations.len() >= 10000 {
                    return Err(app::capacity());
                }
                m.create(now)?
            };
            let target = records::target(&m.spec);
            let mut retired = None;
            for (id, prior) in &state.installations {
                let v = prior.snapshot();
                if id == &m.id || !v.enabled || records::target(&v.spec) != target {
                    continue;
                }
                if now < v.expires_at {
                    return Err(installations::target_conflict());
                }
                retired = Some(prior.disable(now)?);
                break;
            }
            s.check()?;
            if let Some(retired) = retired {
                state
                    .installations
                    .insert(retired.snapshot().id.clone(), retired);
            }
            state.installations.insert(m.id, next.clone());
            Ok(next)
        })
    }
    fn get<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Actor,
        id: &'a Id,
        proof: &'a Secret,
        now: u64,
    ) -> PortFuture<'a, Installation> {
        Box::pin(async move {
            let state = self.lock(s)?;
            actor(&state, a, now)?;
            find(&state, id, proof)
        })
    }
    fn unregister<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Actor,
        id: &'a Id,
        proof: &'a Secret,
        expected: u64,
        now: u64,
    ) -> PortFuture<'a, Installation> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            actor(&state, a, now)?;
            let prior = find(&state, id, proof)?;
            let v = prior.snapshot();
            if !a.owns(v) {
                return Err(installations::binding());
            }
            if !v.enabled {
                return Ok(prior);
            }
            if expected != v.version {
                return Err(installations::conflict());
            }
            let next = prior.disable(now)?;
            s.check()?;
            state.installations.insert(id.clone(), next.clone());
            Ok(next)
        })
    }
    fn retire_session<'a>(&'a self, s: &'a dyn Scope, r: Revocation) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            r.validate()?;
            let hash = records::revocation_fingerprint(&r)?;
            if let Some(prior) = state.installation_inbox.get(&r.event_id) {
                return if prior == &hash {
                    Ok(())
                } else {
                    Err(app::replay_conflict())
                };
            }
            if state.installation_inbox.len() >= 10000 || state.revoked.len() >= 10000 {
                return Err(app::capacity());
            }
            let mut updates = vec![];
            for prior in state.installations.values() {
                let v = prior.snapshot();
                if v.principal_id == r.principal_id && v.session_id == r.session_id {
                    updates.push(prior.disable(r.occurred_at)?);
                }
            }
            s.check()?;
            for next in updates {
                state.installations.insert(next.snapshot().id.clone(), next);
            }
            state.revoked.insert((r.principal_id, r.session_id));
            state.installation_inbox.insert(r.event_id, hash);
            Ok(())
        })
    }
}
