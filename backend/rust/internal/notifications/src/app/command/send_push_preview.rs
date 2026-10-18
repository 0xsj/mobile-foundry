use crate::app::{
    self,
    ports::{PortFuture, Scope},
    push::{
        self, PushAttempt, PushDisposition, PushGateway, PushLease, PushTargets, SessionEligibility,
    },
};
use kernel::{Failure, Kind, clock::Clock, context::Metadata, id::Id};
use std::{sync::Arc, time::Duration};
use tokio::time::Instant;
pub const BUDGET: Duration = Duration::from_secs(5);
fn timeout() -> Failure {
    Failure::new(Kind::Timeout, "", "push attempt budget elapsed")
}
struct AttemptScope<'a> {
    parent: &'a dyn Scope,
    deadline: Instant,
}
impl Scope for AttemptScope<'_> {
    fn check(&self) -> Result<(), Failure> {
        self.parent.check()?;
        if Instant::now() >= self.deadline {
            return Err(timeout());
        }
        Ok(())
    }
    fn metadata(&self) -> Metadata {
        self.parent.metadata()
    }
}
impl AttemptScope<'_> {
    async fn within<T>(&self, future: PortFuture<'_, T>) -> Result<T, Failure> {
        self.check()?;
        tokio::time::timeout_at(self.deadline, future)
            .await
            .map_err(|_| timeout())?
    }
}
#[derive(Clone)]
pub struct SendPushPreview {
    pub targets: Arc<dyn PushTargets>,
    pub sessions: Arc<dyn SessionEligibility>,
    pub gateway: Arc<dyn PushGateway>,
    pub clock: Arc<dyn Clock>,
}
impl SendPushPreview {
    /// Trusted internal intent, one attempt. Repeated invocation can send twice.
    pub async fn handle(
        &self,
        parent: &dyn Scope,
        lease: PushLease,
        preview_id: Id,
    ) -> Result<PushAttempt, Failure> {
        lease.validate()?;
        let s = AttemptScope {
            parent,
            deadline: Instant::now() + BUDGET,
        };
        let address = s
            .within(
                self.targets
                    .target(&s, &lease, app::now(self.clock.as_ref())?),
            )
            .await?;
        if !s
            .within(
                self.sessions
                    .eligible(&s, &lease.principal_id, &lease.session_id),
            )
            .await?
        {
            return Err(push::session_ineligible());
        }
        s.within(
            self.targets
                .target(&s, &lease, app::now(self.clock.as_ref())?),
        )
        .await?;
        match s
            .within(self.gateway.send_preview(&s, address, preview_id))
            .await?
        {
            PushDisposition::Accepted => Ok(PushAttempt::Accepted),
            PushDisposition::InvalidTarget => {
                // Phase-local timeout retains definitive rejection during cleanup.
                let retirement = match app::now(self.clock.as_ref()) {
                    Ok(now) => s.within(self.targets.retire(&s, &lease, now)).await,
                    Err(error) => Err(error),
                };
                Ok(PushAttempt::InvalidTarget { retirement })
            }
        }
    }
}
