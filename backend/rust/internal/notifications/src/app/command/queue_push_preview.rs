use crate::{
    app::{
        self,
        ports::Scope,
        push::{PushLease, PushTargets},
        push_jobs::{self, PushJobs},
    },
    domain::push_job::PushJob,
};
use kernel::{Failure, clock::Clock, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct QueuePushPreview {
    pub store: Arc<dyn PushJobs>,
    pub targets: Arc<dyn PushTargets>,
    pub clock: Arc<dyn Clock>,
}
impl QueuePushPreview {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        lease: PushLease,
        id: Id,
    ) -> Result<PushJob, Failure> {
        lease.validate()?;
        let job = PushJob::new(
            push_jobs::intent(lease.clone(), id.clone(), None, false),
            app::now(self.clock.as_ref())?,
        )?;
        if let Some(prior) = self.store.find(s, &id).await? {
            return if prior.same_intent(&job) {
                Ok(prior)
            } else {
                Err(push_jobs::replay())
            };
        }
        self.targets
            .target(s, &lease, app::now(self.clock.as_ref())?)
            .await?;
        self.store.queue(s, job).await
    }
}
