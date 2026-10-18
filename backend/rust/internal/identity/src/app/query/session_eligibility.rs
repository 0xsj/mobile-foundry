//! Trusted point-in-time session status, independent of access-token authentication.
use crate::{
    app::ports::{PortFuture, Scope},
    domain::Timestamp,
};
use kernel::{Failure, id::Id};
use std::sync::Arc;
pub trait SessionStatus: Send + Sync {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        principal: &'a Id,
        session: &'a Id,
        now: Timestamp,
    ) -> PortFuture<'a, bool>;
}
#[derive(Clone)]
pub struct SessionEligibility {
    pub store: Arc<dyn SessionStatus>,
    pub clock: Arc<dyn kernel::clock::Clock>,
}
impl SessionEligibility {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        principal: &Id,
        session: &Id,
    ) -> Result<bool, Failure> {
        self.store
            .eligible(
                s,
                principal,
                session,
                Timestamp::from_system_time(self.clock.now())?,
            )
            .await
    }
}
