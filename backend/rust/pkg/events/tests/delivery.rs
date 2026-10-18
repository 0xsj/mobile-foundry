use events::{
    Builder, Consumer, Envelope, Publisher, Subscription,
    dispatcher::{Control, Dispatcher, Outbox, Worker},
    memory::Records,
};
use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
    id::Id,
};
use std::sync::{
    Arc, Mutex,
    atomic::{AtomicBool, AtomicUsize, Ordering},
};
fn message() -> Envelope {
    let id = Id::parse("00000000-0000-4000-8000-000000000001").unwrap();
    Envelope {
        id: id.clone(),
        name: "sample.created.v1".into(),
        producer: "sample".into(),
        account_id: Some(id.clone()),
        actor_id: id,
        version: 1,
        occurred_at: 1,
        metadata: Metadata::default(),
        data: b"{}".to_vec(),
    }
}
#[derive(Default)]
struct Store {
    rows: Mutex<Records>,
    failures: AtomicUsize,
}
impl Store {
    fn one() -> Self {
        let mut rows = Records::default();
        rows.append_admitted(message());
        Self {
            rows: Mutex::new(rows),
            failures: AtomicUsize::new(0),
        }
    }
}
impl Outbox for Store {
    fn pending<'a>(&'a self, s: &'a dyn Scope, n: usize) -> PortFuture<'a, Vec<Envelope>> {
        Box::pin(async move {
            s.check()?;
            self.rows.lock().unwrap().pending(n)
        })
    }
    fn delivered<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            self.rows.lock().unwrap().delivered(id)
        })
    }
    fn failed<'a>(&'a self, s: &'a dyn Scope, id: &'a Id, code: &'a str) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            self.failures.fetch_add(1, Ordering::Relaxed);
            self.rows.lock().unwrap().failed(id, code)
        })
    }
    fn ready<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            if self.rows.lock().unwrap().has_dead() {
                Err(Failure::new(Kind::Unavailable, "fixture.dead", "exhausted"))
            } else {
                Ok(())
            }
        })
    }
}
struct Receiver {
    calls: Arc<AtomicUsize>,
    fail: bool,
}
impl Consumer for Receiver {
    fn handle<'a>(&'a self, s: &'a dyn Scope, mut m: Envelope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            assert_eq!(m.data, b"{}");
            m.data.clear();
            self.calls.fetch_add(1, Ordering::Relaxed);
            if self.fail {
                Err(Failure::new(Kind::Unavailable, "fixture.offline", "SECRET"))
            } else {
                Ok(())
            }
        })
    }
}
#[tokio::test]
async fn independent_consumers_receive_owned_copies_after_another_fails() {
    let calls = Arc::new(AtomicUsize::new(0));
    let mut bus = Builder::default();
    for (consumer, fail) in [("a.first", true), ("b.second", false)] {
        bus.register(Subscription {
            consumer: consumer.into(),
            names: vec!["sample.created.v1".into()],
            handler: Arc::new(Receiver {
                calls: calls.clone(),
                fail,
            }),
        })
        .unwrap();
    }
    assert!(
        bus.freeze()
            .publish(&Control::default(), message())
            .await
            .is_err()
    );
    assert_eq!(calls.load(Ordering::Relaxed), 2);
}
#[tokio::test]
async fn retry_exhaustion_retains_records_and_fails_readiness() {
    let store = Arc::new(Store::one());
    let dispatch = Dispatcher::new(store.clone(), Builder::default().freeze());
    let scope = Control::default();
    for index in 0..5 {
        assert!(dispatch.dispatch(&scope).await.is_err());
        assert_eq!(
            store.pending(&scope, 32).await.unwrap().len(),
            if index == 4 { 0 } else { 1 }
        );
    }
    assert_eq!(store.failures.load(Ordering::Relaxed), 5);
    assert_eq!(
        store.ready(&scope).await.unwrap_err().kind(),
        Kind::Unavailable
    );
    assert!(dispatch.dispatch(&scope).await.is_ok());
    assert!(store.rows.lock().unwrap().has_dead());
}
struct Waiting {
    entered: Arc<tokio::sync::Notify>,
    dropped: Arc<AtomicBool>,
}
struct Guard(Arc<AtomicBool>);
impl Drop for Guard {
    fn drop(&mut self) {
        self.0.store(true, Ordering::Release);
    }
}
impl Publisher for Waiting {
    fn publish<'a>(&'a self, _: &'a dyn Scope, _: Envelope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let _guard = Guard(self.dropped.clone());
            self.entered.notify_one();
            std::future::pending().await
        })
    }
}
#[tokio::test(start_paused = true)]
async fn delivery_timeout_consumes_an_attempt_and_drops_the_owned_future() {
    let store = Arc::new(Store::one());
    let dropped = Arc::new(AtomicBool::new(false));
    let dispatcher = Dispatcher::new(
        store.clone(),
        Arc::new(Waiting {
            entered: Arc::new(tokio::sync::Notify::new()),
            dropped: dropped.clone(),
        }),
    );
    assert_eq!(
        dispatcher
            .dispatch(&Control::default())
            .await
            .unwrap_err()
            .kind(),
        Kind::Timeout
    );
    assert!(dropped.load(Ordering::Acquire));
    assert_eq!(store.failures.load(Ordering::Relaxed), 1);
    assert_eq!(
        store.pending(&Control::default(), 32).await.unwrap().len(),
        1
    );
}
#[tokio::test(start_paused = true)]
async fn worker_close_cancels_inflight_delivery_and_joins_without_consuming_an_attempt() {
    let store = Arc::new(Store::one());
    let entered = Arc::new(tokio::sync::Notify::new());
    let dropped = Arc::new(AtomicBool::new(false));
    let mut worker = Worker::start(Arc::new(Dispatcher::new(
        store.clone(),
        Arc::new(Waiting {
            entered: entered.clone(),
            dropped: dropped.clone(),
        }),
    )))
    .unwrap();
    let health = worker.health();
    entered.notified().await;
    assert!(health.check().is_ok());
    worker.close().await.unwrap();
    assert!(dropped.load(Ordering::Acquire));
    assert!(health.check().is_err());
    assert_eq!(store.failures.load(Ordering::Relaxed), 0);
    assert_eq!(
        store.pending(&Control::default(), 32).await.unwrap().len(),
        1
    );
    worker.close().await.unwrap();
}
