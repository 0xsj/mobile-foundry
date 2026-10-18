#[path = "support/push.rs"]
#[allow(dead_code)]
mod targets;
use events::dispatcher::Control;
use kernel::{
    Failure, Kind,
    context::{PortFuture, Scope},
    id::Id,
};
use notifications::{
    app::{
        command::{
            queue_push_preview::QueuePushPreview, run_push_job::RunPushJob,
            send_push_preview::SendPushPreview,
        },
        installations::Installations,
        push::{PushAddress, PushDisposition, PushGateway, PushTargets, SessionEligibility},
        push_jobs::{JobResult, Owner, PushJobs},
    },
    domain::push_job::{LEASE, PushJob, Status},
};
use std::sync::{
    Arc,
    atomic::{AtomicU64, AtomicUsize, Ordering},
};
use targets::{AT, TestClock, id};
fn failure() -> Failure {
    Failure::new(Kind::Unavailable, "fixture.unavailable", "PRIVATE failure")
}
struct Sessions(usize);
impl SessionEligibility for Sessions {
    fn eligible<'a>(&'a self, _: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async move {
            match self.0 {
                1 => Ok(false),
                2 => Err(failure()),
                _ => Ok(true),
            }
        })
    }
}
struct Gateway {
    mode: usize,
    calls: AtomicUsize,
    cancel: Control,
}
impl PushGateway for Gateway {
    fn send_preview<'a>(
        &'a self,
        _: &'a dyn Scope,
        _: PushAddress,
        _: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            self.calls.fetch_add(1, Ordering::SeqCst);
            match self.mode {
                1 => Ok(PushDisposition::InvalidTarget),
                2 => Err(failure()),
                3 => {
                    self.cancel.cancel();
                    Ok(PushDisposition::Accepted)
                }
                4 => std::future::pending().await,
                _ => Ok(PushDisposition::Accepted),
            }
        })
    }
}
struct Store {
    inner: Arc<notifications::infra::memory::MemoryStore>,
    begin: usize,
    finish: usize,
    cleanup: bool,
}
impl PushJobs for Store {
    fn find<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<PushJob>> {
        self.inner.find(s, id)
    }
    fn queue<'a>(&'a self, s: &'a dyn Scope, j: PushJob) -> PortFuture<'a, PushJob> {
        self.inner.queue(s, j)
    }
    fn claim<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: Id,
        now: u64,
    ) -> PortFuture<'a, Option<(PushJob, bool)>> {
        self.inner.claim(s, t, now)
    }
    fn begin<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        t: &'a Id,
        now: u64,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if self.begin == 1 {
                return Err(failure());
            }
            self.inner.begin(s, id, t, now).await?;
            if self.begin == 2 {
                return Err(failure());
            }
            Ok(())
        })
    }
    fn finish<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        t: &'a Id,
        r: JobResult,
        now: u64,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if self.finish == 1 {
                return Err(failure());
            }
            self.inner.finish(s, id, t, r, now).await?;
            if self.finish == 2 {
                return Err(failure());
            }
            Ok(())
        })
    }
    fn cancel<'a>(&'a self, s: &'a dyn Scope, o: &'a Owner, now: u64) -> PortFuture<'a, PushJob> {
        self.inner.cancel(s, o, now)
    }
    fn replace<'a>(&'a self, s: &'a dyn Scope, j: PushJob) -> PortFuture<'a, PushJob> {
        self.inner.replace(s, j)
    }
    fn replacement<'a>(
        &'a self,
        s: &'a dyn Scope,
        o: &'a Owner,
    ) -> PortFuture<'a, Option<PushJob>> {
        self.inner.replacement(s, o)
    }
}
impl PushTargets for Store {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        l: &'a notifications::app::push::PushLease,
        now: u64,
    ) -> PortFuture<'a, PushAddress> {
        self.inner.target(s, l, now)
    }
    fn retire<'a>(
        &'a self,
        s: &'a dyn Scope,
        l: &'a notifications::app::push::PushLease,
        now: u64,
    ) -> PortFuture<'a, bool> {
        if self.cleanup {
            Box::pin(async { Err(failure()) })
        } else {
            self.inner.retire(s, l, now)
        }
    }
}
#[tokio::test(start_paused = true)]
async fn fenced_markers_partial_results_and_uncertainty_never_resend() {
    for (name, begin, finish, gateway_mode, sessions, cleanup, expected, calls) in [
        ("accepted", 0, 0, 0, 0, false, Status::Accepted, 1),
        ("rejected", 0, 0, 1, 0, false, Status::InvalidTarget, 1),
        (
            "cleanup_unconfirmed",
            0,
            0,
            1,
            0,
            true,
            Status::InvalidTarget,
            1,
        ),
        (
            "provider_unconfirmed",
            0,
            0,
            2,
            0,
            false,
            Status::Uncertain,
            1,
        ),
        ("authority_blocked", 0, 0, 0, 1, false, Status::Blocked, 0),
        (
            "preflight_unavailable",
            0,
            0,
            0,
            2,
            false,
            Status::Queued,
            0,
        ),
        ("marker_failed", 1, 0, 0, 0, false, Status::Queued, 0),
        ("marker_ack_lost", 2, 0, 0, 0, false, Status::Sending, 0),
        ("settlement_failed", 0, 1, 0, 0, false, Status::Sending, 1),
        (
            "settlement_ack_lost",
            0,
            2,
            0,
            0,
            false,
            Status::Accepted,
            1,
        ),
        (
            "canceled_after_effect",
            0,
            0,
            3,
            0,
            false,
            Status::Accepted,
            1,
        ),
        ("send_timeout", 0, 0, 4, 0, false, Status::Uncertain, 1),
    ] {
        let inner = Arc::new(notifications::infra::memory::MemoryStore::default());
        let store = Arc::new(Store {
            inner: inner.clone(),
            begin,
            finish,
            cleanup,
        });
        let clock = Arc::new(TestClock(AtomicU64::new(AT)));
        let control = Control::default();
        let gateway = Arc::new(Gateway {
            mode: gateway_mode,
            calls: AtomicUsize::new(0),
            cancel: control.clone(),
        });
        let mutation = targets::mutation();
        inner
            .register(&control, mutation.clone(), AT)
            .await
            .unwrap();
        let lease = targets::lease(&mutation);
        let job = id();
        QueuePushPreview {
            store: store.clone(),
            targets: store.clone(),
            clock: clock.clone(),
        }
        .handle(&control, lease.clone(), job.clone())
        .await
        .unwrap();
        let run = RunPushJob {
            store: store.clone(),
            send: SendPushPreview {
                targets: store.clone(),
                sessions: Arc::new(Sessions(sessions)),
                gateway: gateway.clone(),
                clock: clock.clone(),
            },
            ids: Arc::new(kernel::id::RandomIds),
        };
        let result = run.handle(&control).await;
        if begin == 2 || finish > 0 {
            assert!(result.is_err(), "{name}");
        } else {
            result.unwrap();
        }
        let state = inner
            .find(&Control::default(), &job)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(state.snapshot().status, expected, "{name}");
        assert_eq!(gateway.calls.load(Ordering::SeqCst), calls, "{name}");
        if cleanup {
            assert_eq!(state.snapshot().code.as_str(), "cleanup_unconfirmed");
            assert!(inner.target(&Control::default(), &lease, AT).await.is_ok());
        }
        if matches!(expected, Status::Queued) {
            continue;
        }
        clock.0.store(AT + LEASE, Ordering::SeqCst);
        run.handle(&Control::default()).await.unwrap();
        assert_eq!(
            gateway.calls.load(Ordering::SeqCst),
            calls,
            "{name}: replayed provider after an uncertain outcome"
        );
        if expected == Status::Sending {
            let state = inner
                .find(&Control::default(), &job)
                .await
                .unwrap()
                .unwrap();
            assert_eq!(state.snapshot().status, Status::Uncertain);
            assert_eq!(state.snapshot().code.as_str(), "worker_lost");
        }
    }
}
