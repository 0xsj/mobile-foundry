use crate::{
    app::{
        self,
        installations::Actor,
        ports::Scope,
        push::{PushLease, PushTargets},
        push_jobs::{self, Owner, PushJobs},
    },
    domain::push_job::PushJob,
};
use kernel::{Failure, clock::Clock, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct CancelPushJob {
    pub store: Arc<dyn PushJobs>,
    pub clock: Arc<dyn Clock>,
}
impl CancelPushJob {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Actor,
        id: Id,
        installation_id: Id,
    ) -> Result<PushJob, Failure> {
        let now = app::now(self.clock.as_ref())?;
        actor.validate(now)?;
        self.store
            .cancel(
                s,
                &Owner {
                    id,
                    installation_id,
                    principal_id: actor.principal.clone(),
                    session_id: actor.session.clone(),
                },
                now,
            )
            .await
    }
}
#[derive(Clone)]
pub struct ReplacePushJob {
    pub store: Arc<dyn PushJobs>,
    pub targets: Arc<dyn PushTargets>,
    pub clock: Arc<dyn Clock>,
}
impl ReplacePushJob {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        lease: PushLease,
        source: Id,
        id: Id,
        ack: bool,
    ) -> Result<PushJob, Failure> {
        lease.validate()?;
        if source == id {
            return Err(app::invalid());
        }
        let candidate = PushJob::new(
            push_jobs::intent(lease.clone(), id.clone(), Some(source), ack),
            app::now(self.clock.as_ref())?,
        )?;
        if let Some(prior) = self.store.find(s, &id).await? {
            return if prior.same_intent(&candidate) {
                Ok(prior)
            } else {
                Err(push_jobs::replay())
            };
        }
        self.targets
            .target(s, &lease, app::now(self.clock.as_ref())?)
            .await?;
        self.store.replace(s, candidate).await
    }
}
