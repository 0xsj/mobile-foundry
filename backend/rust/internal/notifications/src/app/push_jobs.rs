use super::{
    ports::{PortFuture, Scope},
    push::PushLease,
};
use crate::domain::push_job::{Code, Intent, PushJob, Status};
use kernel::{Failure, Kind, id::Id};
use std::fmt;
pub fn replay() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.push_job_conflict",
        "push job ID reused for different input",
    )
}
pub fn replaced() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.push_job_replacement_exists",
        "source job already has a replacement; read it before another action",
    )
}
#[derive(Clone)]
pub struct Owner {
    pub id: Id,
    pub installation_id: Id,
    pub principal_id: Id,
    pub session_id: Id,
}
impl Owner {
    pub fn owns(&self, j: &PushJob) -> bool {
        let i = &j.snapshot().intent;
        self.installation_id == i.installation_id
            && self.principal_id == i.principal_id
            && self.session_id == i.session_id
    }
    pub fn matches(&self, j: &PushJob) -> bool {
        self.id == j.snapshot().intent.id && self.owns(j)
    }
}
impl fmt::Debug for Owner {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushJobOwner { redacted }")
    }
}
#[derive(Clone, Copy)]
pub struct JobResult {
    pub started: bool,
    pub status: Status,
    pub code: Code,
    pub retired: bool,
}
pub trait PushJobs: Send + Sync {
    fn find<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<PushJob>>;
    fn queue<'a>(&'a self, s: &'a dyn Scope, job: PushJob) -> PortFuture<'a, PushJob>;
    /// Some with claimed=false means terminal recovery committed; no send is allowed.
    fn claim<'a>(
        &'a self,
        s: &'a dyn Scope,
        token: Id,
        now: u64,
    ) -> PortFuture<'a, Option<(PushJob, bool)>>;
    fn begin<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        token: &'a Id,
        now: u64,
    ) -> PortFuture<'a, ()>;
    fn finish<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        token: &'a Id,
        result: JobResult,
        now: u64,
    ) -> PortFuture<'a, ()>;
    fn cancel<'a>(
        &'a self,
        s: &'a dyn Scope,
        owner: &'a Owner,
        now: u64,
    ) -> PortFuture<'a, PushJob>;
    fn replace<'a>(&'a self, s: &'a dyn Scope, job: PushJob) -> PortFuture<'a, PushJob>;
    fn replacement<'a>(
        &'a self,
        s: &'a dyn Scope,
        owner: &'a Owner,
    ) -> PortFuture<'a, Option<PushJob>>;
}
pub fn intent(
    lease: PushLease,
    id: Id,
    retry_of: Option<Id>,
    duplicate_risk_accepted: bool,
) -> Intent {
    Intent {
        id,
        installation_id: lease.installation_id,
        principal_id: lease.principal_id,
        session_id: lease.session_id,
        installation_version: lease.version,
        retry_of,
        duplicate_risk_accepted,
    }
}
pub fn lease(job: &PushJob) -> PushLease {
    let i = &job.snapshot().intent;
    PushLease {
        installation_id: i.installation_id.clone(),
        principal_id: i.principal_id.clone(),
        session_id: i.session_id.clone(),
        version: i.installation_version,
    }
}
