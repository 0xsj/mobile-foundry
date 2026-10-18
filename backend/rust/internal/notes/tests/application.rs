mod support;
use kernel::{
    Failure, Kind,
    id::{Generator, Id, RandomIds},
};
use notes::{
    app::{
        Dependencies,
        command::Write,
        ports::{AccountAccess, Changes, PortFuture, Scope},
        query::Read,
    },
    infra::memory::MemoryStore,
};
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicUsize, Ordering},
};
struct Access(AtomicUsize);
impl AccountAccess for Access {
    fn allowed<'a>(&'a self, _: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async move {
            match self.0.load(Ordering::Acquire) {
                0 => Ok(true),
                1 => Ok(false),
                _ => Err(Failure::new(Kind::Unavailable, "fixture.offline", "SECRET")),
            }
        })
    }
}
struct IDs(AtomicBool);
impl Generator for IDs {
    fn new_id(&self) -> Result<Id, Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Internal, "fixture.ids", "SECRET"))
        } else {
            RandomIds.new_id()
        }
    }
}
#[derive(Default)]
struct Signals(AtomicUsize);
impl Changes for Signals {
    fn changed(&self, _: &Id) {
        self.0.fetch_add(1, Ordering::AcqRel);
    }
}
#[tokio::test]
async fn memory_receipts_replay_before_cas_and_existence() {
    support::conformance(Arc::new(MemoryStore::default())).await;
}
#[tokio::test]
async fn replay_needs_authorization_but_no_new_id_and_hints_follow_confirmed_success() {
    let access = Arc::new(Access(AtomicUsize::new(0)));
    let ids = Arc::new(IDs(AtomicBool::new(false)));
    let signals = Arc::new(Signals::default());
    let d = Dependencies {
        store: Arc::new(MemoryStore::default()),
        access: access.clone(),
        ids: ids.clone(),
        changes: Some(signals.clone()),
    };
    let write = Write(d.clone());
    let read = Read(d);
    let s = support::Control::default();
    let (a, p) = (support::id(), support::id());
    let input = support::create("SECRET");
    let r = write.handle(&s, &p, &a, input.clone()).await.unwrap();
    assert_eq!(signals.0.load(Ordering::Acquire), 1);
    ids.0.store(true, Ordering::Release);
    assert_eq!(write.handle(&s, &p, &a, input.clone()).await.unwrap(), r);
    assert!(
        write
            .handle(&s, &p, &a, support::create("New allocation fails"))
            .await
            .is_err()
    );
    assert_eq!(signals.0.load(Ordering::Acquire), 2);
    for (mode, kind) in [(1, Kind::Forbidden), (2, Kind::Unavailable)] {
        access.0.store(mode, Ordering::Release);
        assert_eq!(
            write
                .handle(&s, &p, &a, input.clone())
                .await
                .unwrap_err()
                .kind(),
            kind
        );
        assert_eq!(
            read.receipt(&s, &p, &a, &input.command_id)
                .await
                .unwrap_err()
                .kind(),
            kind
        );
        assert_eq!(signals.0.load(Ordering::Acquire), 2);
    }
}
