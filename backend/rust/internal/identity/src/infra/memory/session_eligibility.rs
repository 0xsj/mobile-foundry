use super::MemoryStore;
use crate::{
    app::{
        ports::{PortFuture, Scope},
        query::session_eligibility::SessionStatus,
    },
    domain::Timestamp,
};
use kernel::id::Id;
impl SessionStatus for MemoryStore {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        principal: &'a Id,
        session: &'a Id,
        now: Timestamp,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            let state = self.lock(s)?;
            Ok(state
                .sessions
                .get(session)
                .is_some_and(|v| &v.state().principal_id == principal && v.renewable(now)))
        })
    }
}
