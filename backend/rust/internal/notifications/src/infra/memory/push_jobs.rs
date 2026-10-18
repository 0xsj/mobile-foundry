use super::*;
use crate::{
    app::push_jobs::{self, JobResult, Owner, PushJobs},
    domain::push_job::{self, PushJob, Status},
};
impl PushJobs for MemoryStore {
    fn find<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<PushJob>> {
        Box::pin(async move { Ok(self.lock(s)?.push_jobs.get(id).cloned()) })
    }
    fn queue<'a>(&'a self, s: &'a dyn Scope, job: PushJob) -> PortFuture<'a, PushJob> {
        Box::pin(async move {
            let v = job.snapshot();
            if v.status != Status::Queued || v.attempts != 0 || v.intent.retry_of.is_some() {
                return Err(app::invalid());
            }
            let mut state = self.lock(s)?;
            if let Some(prior) = state.push_jobs.get(&v.intent.id) {
                return if prior.same_intent(&job) {
                    Ok(prior.clone())
                } else {
                    Err(push_jobs::replay())
                };
            }
            if state.push_jobs.len() >= 10000 {
                return Err(app::capacity());
            }
            state.push_jobs.insert(v.intent.id.clone(), job.clone());
            Ok(job)
        })
    }
    fn claim<'a>(
        &'a self,
        s: &'a dyn Scope,
        token: Id,
        now: u64,
    ) -> PortFuture<'a, Option<(PushJob, bool)>> {
        Box::pin(async move {
            if now > domain::MAX_TIME {
                return Err(app::invalid());
            }
            let mut state = self.lock(s)?;
            let next = state
                .push_jobs
                .values()
                .filter(|j| j.due(now))
                .min_by_key(|j| (j.snapshot().created_at, &j.snapshot().intent.id))
                .cloned();
            let Some(prior) = next else { return Ok(None) };
            let (next, claimed) = prior.claim_next(token, now)?;
            state
                .push_jobs
                .insert(next.snapshot().intent.id.clone(), next.clone());
            Ok(Some((next, claimed)))
        })
    }
    fn begin<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        token: &'a Id,
        now: u64,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let prior = state.push_jobs.get(id).ok_or_else(push_job::claim_lost)?;
            let next = prior.begin(token, now)?;
            state.push_jobs.insert(id.clone(), next);
            Ok(())
        })
    }
    fn finish<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        token: &'a Id,
        r: JobResult,
        now: u64,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let prior = state.push_jobs.get(id).ok_or_else(push_job::claim_lost)?;
            let next = prior.finish(token, r.started, r.status, r.code, r.retired, now)?;
            state.push_jobs.insert(id.clone(), next);
            Ok(())
        })
    }
    fn cancel<'a>(
        &'a self,
        s: &'a dyn Scope,
        owner: &'a Owner,
        now: u64,
    ) -> PortFuture<'a, PushJob> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let prior = state
                .push_jobs
                .get(&owner.id)
                .filter(|j| owner.matches(j))
                .ok_or_else(app::missing)?;
            let next = prior.cancel(now)?;
            state.push_jobs.insert(owner.id.clone(), next.clone());
            Ok(next)
        })
    }
    fn replace<'a>(&'a self, s: &'a dyn Scope, job: PushJob) -> PortFuture<'a, PushJob> {
        Box::pin(async move {
            let v = job.snapshot();
            let source = v.intent.retry_of.as_ref().ok_or_else(app::invalid)?;
            if v.status != Status::Queued || v.attempts != 0 {
                return Err(app::invalid());
            }
            let mut state = self.lock(s)?;
            if let Some(prior) = state.push_jobs.get(&v.intent.id) {
                return if prior.same_intent(&job) {
                    Ok(prior.clone())
                } else {
                    Err(push_jobs::replay())
                };
            }
            let owner = Owner {
                id: source.clone(),
                installation_id: v.intent.installation_id.clone(),
                principal_id: v.intent.principal_id.clone(),
                session_id: v.intent.session_id.clone(),
            };
            let parent = state
                .push_jobs
                .get(source)
                .filter(|j| owner.matches(j))
                .ok_or_else(app::missing)?;
            let next = parent.replacement(&job)?;
            if state
                .push_jobs
                .values()
                .any(|j| j.snapshot().intent.retry_of.as_ref() == Some(source))
            {
                return Err(push_jobs::replaced());
            }
            if state.push_jobs.len() >= 10000 {
                return Err(app::capacity());
            }
            state.push_jobs.insert(v.intent.id.clone(), next.clone());
            Ok(next)
        })
    }
    fn replacement<'a>(
        &'a self,
        s: &'a dyn Scope,
        owner: &'a Owner,
    ) -> PortFuture<'a, Option<PushJob>> {
        Box::pin(async move {
            Ok(self
                .lock(s)?
                .push_jobs
                .values()
                .find(|j| j.snapshot().intent.retry_of.as_ref() == Some(&owner.id) && owner.owns(j))
                .cloned())
        })
    }
}
