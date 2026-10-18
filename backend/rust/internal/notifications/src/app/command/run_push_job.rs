use super::send_push_preview::SendPushPreview;
use crate::{
    app::{
        self,
        ports::{PortFuture, Scope},
        push::{PushAddress, PushAttempt, PushDisposition, PushGateway},
        push_jobs::{self, JobResult, PushJobs},
    },
    domain::push_job::{Code, PushJob, Status},
};
use kernel::{
    Failure, Kind,
    context::Metadata,
    id::{Generator, Id},
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::time::Instant;
#[derive(Clone)]
pub struct RunPushJob {
    pub store: Arc<dyn PushJobs>,
    pub send: SendPushPreview,
    pub ids: Arc<dyn Generator>,
}
struct Marked {
    store: Arc<dyn PushJobs>,
    job: PushJob,
    send: SendPushPreview,
    started: AtomicBool,
}
impl PushGateway for Marked {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        address: PushAddress,
        preview: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            let v = self.job.snapshot();
            self.store
                .begin(
                    s,
                    &v.intent.id,
                    v.claim
                        .as_ref()
                        .ok_or_else(crate::domain::push_job::claim_lost)?,
                    app::now(self.send.clock.as_ref())?,
                )
                .await?;
            // An unconfirmed marker may have committed, but never authorizes a send.
            self.started.store(true, Ordering::Release);
            self.send.gateway.send_preview(s, address, preview).await
        })
    }
}
struct Settlement {
    deadline: Instant,
    metadata: Metadata,
}
impl Scope for Settlement {
    fn check(&self) -> Result<(), Failure> {
        if Instant::now() >= self.deadline {
            Err(Failure::new(
                Kind::Timeout,
                "",
                "push settlement budget elapsed",
            ))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        self.metadata.clone()
    }
}
impl RunPushJob {
    pub async fn handle(&self, s: &dyn Scope) -> Result<(), Failure> {
        s.check()?;
        let claim = self.ids.new_id()?;
        let selected = tokio::time::timeout(
            Duration::from_secs(5),
            self.store
                .claim(s, claim.clone(), app::now(self.send.clock.as_ref())?),
        )
        .await
        .map_err(|_| Failure::new(Kind::Timeout, "", "push claim budget elapsed"))??;
        let Some((job, true)) = selected else {
            return Ok(());
        };
        let gateway = Arc::new(Marked {
            store: self.store.clone(),
            job: job.clone(),
            send: self.send.clone(),
            started: AtomicBool::new(false),
        });
        let mut send = self.send.clone();
        send.gateway = gateway.clone();
        let outcome = send
            .handle(s, push_jobs::lease(&job), job.snapshot().intent.id.clone())
            .await;
        let started = gateway.started.load(Ordering::Acquire);
        let (status, code, retired) = if !started {
            let blocked = outcome.as_ref().err().is_some_and(|e| {
                matches!(
                    e.public().code.as_deref(),
                    Some(
                        "notifications.push_target_changed"
                            | "notifications.push_session_ineligible"
                    )
                )
            });
            if blocked {
                (Status::Blocked, Code::TargetIneligible, false)
            } else {
                (Status::Queued, Code::PreflightUnavailable, false)
            }
        } else {
            match outcome {
                Ok(PushAttempt::Accepted) => (Status::Accepted, Code::None, false),
                Ok(PushAttempt::InvalidTarget { retirement }) => match retirement {
                    Ok(retired) => (Status::InvalidTarget, Code::None, retired),
                    Err(_) => (Status::InvalidTarget, Code::CleanupUnconfirmed, false),
                },
                Err(_) => (Status::Uncertain, Code::ProviderUnconfirmed, false),
            }
        };
        let settle = Settlement {
            deadline: Instant::now() + Duration::from_secs(2),
            metadata: s.metadata(),
        };
        tokio::time::timeout_at(
            settle.deadline,
            self.store.finish(
                &settle,
                &job.snapshot().intent.id,
                &claim,
                JobResult {
                    started,
                    status,
                    code,
                    retired,
                },
                app::now(self.send.clock.as_ref())?,
            ),
        )
        .await
        .map_err(|_| Failure::new(Kind::Timeout, "", "push settlement budget elapsed"))?
    }
}
impl jobs::Runner for RunPushJob {
    fn run<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(self.handle(s))
    }
}
