use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
    id::{Generator, Id, RandomIds},
};
use realtime::{
    app::{AccountAccess, Feed, Subscription, query::Subscribe},
    infra::capabilities::{ACCOUNT_ACCESS, Client},
};
use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

struct Control;
impl Scope for Control {
    fn check(&self) -> Result<(), Failure> {
        Ok(())
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
struct Access(usize);
impl AccountAccess for Access {
    fn allowed<'a>(&'a self, _: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async move {
            match self.0 {
                0 => Ok(true),
                1 => Ok(false),
                _ => Err(Failure::new(
                    Kind::Unavailable,
                    "fixture.offline",
                    "PRIVATE",
                )),
            }
        })
    }
}
struct NeverFeed(AtomicUsize);
impl Feed for NeverFeed {
    fn watch(&self, _: &Id, _: &Id) -> Result<Box<dyn Subscription>, Failure> {
        self.0.fetch_add(1, Ordering::AcqRel);
        Err(Failure::new(
            Kind::RateLimited,
            "broadcast.capacity",
            "fixture",
        ))
    }
}
#[tokio::test]
async fn refusal_and_dependency_failure_do_not_allocate_subscriptions() {
    let id = RandomIds.new_id().unwrap();
    let feed = Arc::new(NeverFeed(AtomicUsize::new(0)));
    for (mode, kind) in [
        (1, Kind::Forbidden),
        (2, Kind::Unavailable),
        (0, Kind::RateLimited),
    ] {
        let query = Subscribe {
            access: Arc::new(Access(mode)),
            feed: feed.clone(),
        };
        assert_eq!(
            query.handle(&Control, &id, &id).await.err().unwrap().kind(),
            kind
        );
    }
    assert_eq!(feed.0.load(Ordering::Acquire), 1);
}
struct Answer(&'static [u8]);
impl capability::Handler for Answer {
    fn call<'a>(&'a self, _: &'a dyn Scope, request: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let value: serde_json::Value = serde_json::from_slice(request).unwrap();
            assert_eq!(value["action"], "account.read");
            assert_eq!(value.as_object().unwrap().len(), 3);
            Ok(self.0.to_vec())
        })
    }
}
#[tokio::test]
async fn capability_admission_requires_explicit_boolean_and_registered_caller() {
    let id = RandomIds.new_id().unwrap();
    for wire in [
        b"{}".as_slice(),
        b"{\"allowed\":null}",
        b"{\"allowed\":1}",
        b"{\"allowed\":true,\"extra\":1}",
        b"{\"allowed\":true,\"allowed\":false}",
        b"[]",
    ] {
        let mut builder = capability::Builder::default();
        builder
            .register(ACCOUNT_ACCESS, &["realtime"], Arc::new(Answer(wire)))
            .unwrap();
        let registry = builder.freeze();
        let client = Client(Arc::new(registry.client("realtime").unwrap()));
        let failure = client.allowed(&Control, &id, &id).await.unwrap_err();
        assert_eq!(failure.kind(), Kind::Internal);
        assert_eq!(failure.public().code, None);
    }
    for (wire, expected) in [
        (b"{\"allowed\":false}".as_slice(), false),
        (b"{\"allowed\":true}".as_slice(), true),
    ] {
        let mut builder = capability::Builder::default();
        builder
            .register(ACCOUNT_ACCESS, &["realtime"], Arc::new(Answer(wire)))
            .unwrap();
        let registry = builder.freeze();
        assert!(registry.require(ACCOUNT_ACCESS, "notes").is_err());
        let client = Client(Arc::new(registry.client("realtime").unwrap()));
        assert_eq!(client.allowed(&Control, &id, &id).await.unwrap(), expected);
    }
}
