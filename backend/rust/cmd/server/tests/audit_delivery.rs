//! Composition acceptance may wire several modules; business crates never import each other.
use account::{
    app::{Dependencies, command::Create},
    infra::memory::MemoryStore,
};
use events::{
    Envelope,
    dispatcher::{Control, Dispatcher, Outbox},
};
use kernel::{
    Failure, Kind,
    context::{PortFuture, Scope},
    id::{Generator, Id, RandomIds},
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
struct Clock(AtomicU64);
impl kernel::clock::Clock for Clock {
    fn now(&self) -> SystemTime {
        UNIX_EPOCH + Duration::from_secs(self.0.load(Ordering::Relaxed))
    }
}
struct LostAck {
    inner: Arc<dyn Outbox>,
    lost: AtomicBool,
}
impl Outbox for LostAck {
    fn pending<'a>(&'a self, s: &'a dyn Scope, n: usize) -> PortFuture<'a, Vec<Envelope>> {
        self.inner.pending(s, n)
    }
    fn delivered<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if !self.lost.swap(true, Ordering::AcqRel) {
                return Err(Failure::new(
                    Kind::Unavailable,
                    "fixture.ack_lost",
                    "ack lost",
                ));
            }
            self.inner.delivered(s, id).await
        })
    }
    fn failed<'a>(&'a self, s: &'a dyn Scope, id: &'a Id, c: &'a str) -> PortFuture<'a, ()> {
        self.inner.failed(s, id, c)
    }
    fn ready<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        self.inner.ready(s)
    }
}
#[tokio::test]
async fn lost_ack_replays_the_same_committed_event_and_audit_keeps_one_original_entry() {
    use audit::app::ports::Store;
    let source = Arc::new(MemoryStore::new(Arc::new(RandomIds)));
    let scope = Control::default();
    let owner = RandomIds.new_id().unwrap();
    let clock = Arc::new(Clock(AtomicU64::new(1_800_000_000)));
    let deps = Dependencies {
        store: source.clone(),
        directory: source.clone(),
        clock: clock.clone(),
        ids: Arc::new(RandomIds),
    };
    let account = Create(deps)
        .handle(&scope, &owner, "SECRET workspace")
        .await
        .unwrap();
    let pending = source.pending(&scope, 32).await.unwrap();
    assert_eq!(pending.len(), 1);
    assert!(!String::from_utf8_lossy(&pending[0].data).contains("SECRET"));
    let evidence = Arc::new(audit::infra::memory::MemoryStore::default());
    let mut builder = events::Builder::default();
    builder
        .register(
            audit::infra::events::Receiver {
                record: audit::app::command::Record {
                    store: evidence.clone(),
                    clock: clock.clone(),
                },
                source: audit::infra::events::Source::Account,
            }
            .subscription(),
        )
        .unwrap();
    let dispatcher = Dispatcher::new(
        Arc::new(LostAck {
            inner: source.clone(),
            lost: AtomicBool::new(false),
        }),
        builder.freeze(),
    );
    assert!(dispatcher.dispatch(&scope).await.is_err());
    let first = evidence
        .list(&scope, &account.snapshot().id, 0, 100)
        .await
        .unwrap();
    assert_eq!(first.records.len(), 1);
    assert_eq!(
        source.pending(&scope, 32).await.unwrap()[0].id,
        pending[0].id
    );
    clock.0.fetch_add(50, Ordering::Relaxed);
    dispatcher.dispatch(&scope).await.unwrap();
    let next = evidence
        .list(&scope, &account.snapshot().id, 0, 100)
        .await
        .unwrap();
    assert_eq!(next.records.len(), 1);
    assert_eq!(
        next.records[0].entry.recorded_at(),
        first.records[0].entry.recorded_at()
    );
    assert!(source.pending(&scope, 32).await.unwrap().is_empty());
}
