use crate::{
    app::{
        self,
        installations::Actor,
        ports::Scope,
        push_jobs::{Owner, PushJobs},
    },
    domain::push_job::PushJob,
};
use kernel::{Failure, clock::Clock, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct GetPushJob {
    pub store: Arc<dyn PushJobs>,
    pub clock: Arc<dyn Clock>,
}
impl GetPushJob {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Actor,
        installation: Id,
        id: Id,
    ) -> Result<PushJob, Failure> {
        actor.validate(app::now(self.clock.as_ref())?)?;
        let owner = Owner {
            id: id.clone(),
            installation_id: installation,
            principal_id: actor.principal.clone(),
            session_id: actor.session.clone(),
        };
        self.store
            .find(s, &id)
            .await?
            .filter(|j| owner.matches(j))
            .ok_or_else(app::missing)
    }
}
#[derive(Clone)]
pub struct GetPushJobReplacement {
    pub store: Arc<dyn PushJobs>,
    pub clock: Arc<dyn Clock>,
}
impl GetPushJobReplacement {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Actor,
        installation: Id,
        id: Id,
    ) -> Result<PushJob, Failure> {
        actor.validate(app::now(self.clock.as_ref())?)?;
        let owner = Owner {
            id,
            installation_id: installation,
            principal_id: actor.principal.clone(),
            session_id: actor.session.clone(),
        };
        self.store
            .replacement(s, &owner)
            .await?
            .ok_or_else(app::missing)
    }
}
