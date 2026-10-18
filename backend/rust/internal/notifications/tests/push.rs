#[path = "support/push.rs"]
mod support;
use kernel::{
    Failure, Kind,
    context::{PortFuture, Scope},
    id::Id,
};
use notifications::app::{
    command::send_push_preview::SendPushPreview,
    push::{
        PushAddress, PushAttempt, PushDisposition, PushGateway, PushTargets, SessionEligibility,
    },
};
use std::sync::Arc;
use std::{
    sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering},
    time::Duration,
};
#[tokio::test]
async fn memory_push_conformance() {
    support::conformance(Arc::new(
        notifications::infra::memory::MemoryStore::default(),
    ))
    .await;
}
#[test]
fn private_addresses_and_strong_lease_versions() {
    use notifications::app::push::{PushAddress, PushDisposition};
    let m = support::mutation();
    let mut l = support::lease(&m);
    let a = PushAddress {
        lease: l.clone(),
        spec: m.spec.clone(),
        registered_at: support::AT,
    };
    for output in [format!("{l:?}"), format!("{a:?}")] {
        for secret in [
            m.id.as_str(),
            m.actor.principal.as_str(),
            m.actor.session.as_str(),
            m.spec.token.expose(),
        ] {
            assert!(!output.contains(secret));
        }
    }
    l.version = 0;
    assert!(l.validate().is_err());
    l.version = 9_007_199_254_740_992;
    assert!(l.validate().is_err());
    assert!(PushDisposition::parse("unknown").is_err());
}

struct Dropped(Arc<AtomicBool>);
impl Drop for Dropped {
    fn drop(&mut self) {
        self.0.store(true, Ordering::Release);
    }
}
struct Paced {
    address: PushAddress,
    target_seconds: u64,
    session_seconds: u64,
    gateway_seconds: u64,
    cleanup_seconds: u64,
    invalid: bool,
    calls: AtomicUsize,
    finished: AtomicBool,
    dropped: Arc<AtomicBool>,
    cancel: Option<Arc<events::dispatcher::Control>>,
}
impl Paced {
    fn new() -> Self {
        let m = support::mutation();
        Self {
            address: PushAddress {
                lease: support::lease(&m),
                spec: m.spec,
                registered_at: support::AT,
            },
            target_seconds: 0,
            session_seconds: 0,
            gateway_seconds: 0,
            cleanup_seconds: 0,
            invalid: false,
            calls: AtomicUsize::new(0),
            finished: AtomicBool::new(false),
            dropped: Arc::new(AtomicBool::new(false)),
            cancel: None,
        }
    }
}
impl PushTargets for Paced {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        _: &'a notifications::app::push::PushLease,
        _: u64,
    ) -> PortFuture<'a, PushAddress> {
        Box::pin(async move {
            s.check()?;
            tokio::time::sleep(Duration::from_secs(self.target_seconds)).await;
            s.check()?;
            Ok(self.address.clone())
        })
    }
    fn retire<'a>(
        &'a self,
        s: &'a dyn Scope,
        _: &'a notifications::app::push::PushLease,
        _: u64,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            s.check()?;
            let _guard = Dropped(self.dropped.clone());
            tokio::time::sleep(Duration::from_secs(self.cleanup_seconds)).await;
            s.check()?;
            self.finished.store(true, Ordering::Release);
            Ok(true)
        })
    }
}
impl SessionEligibility for Paced {
    fn eligible<'a>(&'a self, s: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async move {
            s.check()?;
            tokio::time::sleep(Duration::from_secs(self.session_seconds)).await;
            s.check()?;
            Ok(true)
        })
    }
}
impl PushGateway for Paced {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        _: PushAddress,
        _: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            s.check()?;
            self.calls.fetch_add(1, Ordering::Relaxed);
            let _guard = Dropped(self.dropped.clone());
            tokio::time::sleep(Duration::from_secs(self.gateway_seconds)).await;
            s.check()?;
            if let Some(c) = &self.cancel {
                c.cancel();
            }
            if self.invalid {
                Ok(PushDisposition::InvalidTarget)
            } else {
                self.finished.store(true, Ordering::Release);
                Ok(PushDisposition::Accepted)
            }
        })
    }
}
fn command(p: Arc<Paced>) -> SendPushPreview {
    SendPushPreview {
        targets: p.clone(),
        sessions: p.clone(),
        gateway: p,
        clock: Arc::new(support::TestClock(AtomicU64::new(support::AT))),
    }
}
#[tokio::test(start_paused = true)]
async fn one_budget_covers_all_phases_and_drops_uncertain_send() {
    let mut p = Paced::new();
    p.target_seconds = 1;
    p.session_seconds = 1;
    p.gateway_seconds = 3;
    let p = Arc::new(p);
    let h = command(p.clone());
    let start = tokio::time::Instant::now();
    assert_eq!(
        h.handle(
            &events::dispatcher::Control::default(),
            p.address.lease.clone(),
            support::id()
        )
        .await
        .unwrap_err()
        .kind(),
        Kind::Timeout
    );
    assert_eq!(start.elapsed(), Duration::from_secs(5));
    assert_eq!(p.calls.load(Ordering::Relaxed), 1);
    assert!(p.dropped.load(Ordering::Acquire));
    tokio::time::advance(Duration::from_secs(30)).await;
    assert!(!p.finished.load(Ordering::Acquire));
}
#[tokio::test(start_paused = true)]
async fn definitive_rejection_survives_cleanup_timeout() {
    let mut p = Paced::new();
    p.invalid = true;
    p.cleanup_seconds = 10;
    let p = Arc::new(p);
    let h = command(p.clone());
    let start = tokio::time::Instant::now();
    let result = h
        .handle(
            &events::dispatcher::Control::default(),
            p.address.lease.clone(),
            support::id(),
        )
        .await
        .unwrap();
    assert!(
        matches!(result,PushAttempt::InvalidTarget{retirement:Err(e)} if e.kind()==Kind::Timeout)
    );
    assert_eq!(start.elapsed(), Duration::from_secs(5));
    assert_eq!(p.calls.load(Ordering::Relaxed), 1);
    tokio::time::advance(Duration::from_secs(30)).await;
    assert!(!p.finished.load(Ordering::Acquire));
}
#[tokio::test(start_paused = true)]
async fn definitive_rejection_survives_parent_cancellation() {
    let s = Arc::new(events::dispatcher::Control::default());
    let mut p = Paced::new();
    p.invalid = true;
    p.cancel = Some(s.clone());
    let p = Arc::new(p);
    assert!(
        matches!(command(p.clone()).handle(s.as_ref(),p.address.lease.clone(),support::id()).await.unwrap(),PushAttempt::InvalidTarget{retirement:Err(e)} if e.kind()==Kind::Canceled)
    );
    assert!(!p.finished.load(Ordering::Acquire));
    assert_eq!(p.calls.load(Ordering::Relaxed), 1);
}
struct PendingProvider(Arc<AtomicBool>);
impl capability::Handler for PendingProvider {
    fn call<'a>(&'a self, _: &'a dyn Scope, _: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let _guard = Dropped(self.0.clone());
            std::future::pending().await
        })
    }
}
#[tokio::test(start_paused = true)]
async fn identity_capability_keeps_its_two_second_budget() {
    let p = Arc::new(Paced::new());
    let dropped = Arc::new(AtomicBool::new(false));
    let mut b = capability::Builder::default();
    b.register(
        notifications::infra::capabilities::sessions::NAME,
        &["notifications"],
        Arc::new(PendingProvider(dropped.clone())),
    )
    .unwrap();
    let h = notifications::new_push_preview(
        p.clone(),
        Arc::new(support::TestClock(AtomicU64::new(support::AT))),
        &b.freeze(),
        p.clone(),
    )
    .unwrap();
    let start = tokio::time::Instant::now();
    assert_eq!(
        h.handle(
            &events::dispatcher::Control::default(),
            p.address.lease.clone(),
            support::id()
        )
        .await
        .unwrap_err()
        .kind(),
        Kind::Timeout
    );
    assert_eq!(start.elapsed(), Duration::from_secs(2));
    assert_eq!(p.calls.load(Ordering::Relaxed), 0);
    assert!(dropped.load(Ordering::Acquire));
}
struct LostCleanupAck(Arc<notifications::infra::memory::MemoryStore>);
impl PushTargets for LostCleanupAck {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        l: &'a notifications::app::push::PushLease,
        now: u64,
    ) -> PortFuture<'a, PushAddress> {
        self.0.target(s, l, now)
    }
    fn retire<'a>(
        &'a self,
        s: &'a dyn Scope,
        l: &'a notifications::app::push::PushLease,
        now: u64,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            self.0.retire(s, l, now).await?;
            Err(Failure::new(
                Kind::Timeout,
                "fixture.ack_lost",
                "PRIVATE lost cleanup acknowledgement",
            ))
        })
    }
}
#[tokio::test]
async fn lost_cleanup_ack_preserves_rejection_without_resending() {
    use notifications::app::installations::Installations;
    let store = Arc::new(notifications::infra::memory::MemoryStore::default());
    let m = support::mutation();
    let s = events::dispatcher::Control::default();
    store.register(&s, m.clone(), support::AT).await.unwrap();
    let mut p = Paced::new();
    p.invalid = true;
    let p = Arc::new(p);
    let mut h = command(p.clone());
    h.targets = Arc::new(LostCleanupAck(store.clone()));
    assert!(
        matches!(h.handle(&s,support::lease(&m),support::id()).await.unwrap(),PushAttempt::InvalidTarget{retirement:Err(e)} if e.kind()==Kind::Timeout)
    );
    assert!(
        !store
            .get(&s, &m.actor, &m.id, &m.proof_digest, support::AT)
            .await
            .unwrap()
            .snapshot()
            .enabled
    );
    assert!(
        !store
            .retire(&s, &support::lease(&m), support::AT)
            .await
            .unwrap()
    );
    assert_eq!(p.calls.load(Ordering::Relaxed), 1);
}
