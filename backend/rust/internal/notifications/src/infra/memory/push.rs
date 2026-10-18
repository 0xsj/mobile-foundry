use super::MemoryStore;
use crate::app::{
    ports::{PortFuture, Scope},
    push::{self, PushAddress, PushLease, PushTargets},
};
impl PushTargets for MemoryStore {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        lease: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, PushAddress> {
        Box::pin(async move {
            lease.validate()?;
            let state = self.lock(s)?;
            let v = state
                .installations
                .get(&lease.installation_id)
                .ok_or_else(push::target_changed)?
                .snapshot();
            if !lease.matches(v)
                || !v.enabled
                || now < v.created_at
                || now >= v.expires_at
                || state
                    .revoked
                    .contains(&(lease.principal_id.clone(), lease.session_id.clone()))
            {
                return Err(push::target_changed());
            }
            Ok(PushAddress {
                lease: lease.clone(),
                spec: v.spec.clone(),
                registered_at: v.updated_at,
            })
        })
    }
    fn retire<'a>(
        &'a self,
        s: &'a dyn Scope,
        lease: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            lease.validate()?;
            let mut state = self.lock(s)?;
            let Some(prior) = state
                .installations
                .get(&lease.installation_id)
                .filter(|i| lease.matches(i.snapshot()) && i.snapshot().enabled)
            else {
                return Ok(false);
            };
            let next = prior.disable(now)?;
            s.check()?;
            state
                .installations
                .insert(lease.installation_id.clone(), next);
            Ok(true)
        })
    }
}
