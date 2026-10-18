use capability::{Builder, Handler, Invoker};
use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
};
use std::sync::{
    Arc,
    atomic::{AtomicBool, Ordering},
};

#[derive(Default)]
struct TestScope(AtomicBool);
impl Scope for TestScope {
    fn check(&self) -> Result<(), Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Canceled, "", "canceled"))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
struct Provider(u8);
impl Handler for Provider {
    fn call<'a>(&'a self, scope: &'a dyn Scope, _: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            scope.check()?;
            match self.0 {
                0 => Ok(br#"{"answer":true}"#.to_vec()),
                1 => Err(
                    Failure::new(Kind::Internal, "SECRET.code", "SECRET diagnostic")
                        .with_cause(std::io::Error::other("SECRET cause")),
                ),
                2 => Ok(b"[]".to_vec()),
                _ => {
                    std::future::pending::<()>().await;
                    unreachable!()
                }
            }
        })
    }
}
#[tokio::test]
async fn caller_registration_validation_and_failures_cross_the_boundary() {
    let mut builder = Builder::default();
    assert!(
        builder
            .register("bad", &["api"], Arc::new(Provider(0)))
            .is_err()
    );
    builder
        .register("sample.answer.v1", &["api"], Arc::new(Provider(0)))
        .unwrap();
    assert!(
        builder
            .register("sample.answer.v1", &["api"], Arc::new(Provider(1)))
            .is_err()
    );
    builder
        .register("sample.failure.v1", &["api"], Arc::new(Provider(1)))
        .unwrap();
    builder
        .register("sample.bad_response.v1", &["api"], Arc::new(Provider(2)))
        .unwrap();
    let registry = builder.freeze();
    let client = registry.client("api").unwrap();
    let scope = TestScope::default();
    assert!(registry.require("sample.answer.v1", "api").is_ok());
    let denied = registry
        .client("account")
        .unwrap()
        .invoke(&scope, "sample.answer.v1", b"{}")
        .await
        .unwrap_err();
    assert_eq!(
        denied.public().code.as_deref(),
        Some("capability.caller_not_allowed")
    );
    assert_eq!(
        client
            .invoke(&scope, "sample.missing.v1", b"{}")
            .await
            .unwrap_err()
            .kind(),
        Kind::Unavailable
    );
    for input in [
        b"[]".as_slice(),
        b"{}{}",
        &vec![b' '; capability::MAX_BYTES + 1],
    ] {
        assert_eq!(
            client
                .invoke(&scope, "sample.answer.v1", input)
                .await
                .unwrap_err()
                .kind(),
            Kind::Invalid
        );
    }
    assert_eq!(
        client
            .invoke(&scope, "sample.answer.v1", b"{}")
            .await
            .unwrap(),
        br#"{"answer":true}"#
    );
    let error = client
        .invoke(&scope, "sample.failure.v1", b"{}")
        .await
        .unwrap_err();
    assert_eq!(error.diagnostic(), "capability provider failed");
    assert!(std::error::Error::source(&error).is_none());
    assert_eq!(error.public().code, None);
    assert_eq!(
        client
            .invoke(&scope, "sample.bad_response.v1", b"{}")
            .await
            .unwrap_err()
            .kind(),
        Kind::Internal
    );
    scope.0.store(true, Ordering::Release);
    assert_eq!(
        client
            .invoke(&scope, "sample.answer.v1", b"{}")
            .await
            .unwrap_err()
            .kind(),
        Kind::Canceled
    );
}
#[tokio::test(start_paused = true)]
async fn budget_drops_owned_provider_future_without_detaching_work() {
    struct Waiting(Arc<AtomicBool>);
    struct Guard(Arc<AtomicBool>);
    impl Drop for Guard {
        fn drop(&mut self) {
            self.0.store(true, Ordering::Release);
        }
    }
    impl Handler for Waiting {
        fn call<'a>(&'a self, _: &'a dyn Scope, _: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
            Box::pin(async move {
                let _guard = Guard(self.0.clone());
                std::future::pending().await
            })
        }
    }
    let dropped = Arc::new(AtomicBool::new(false));
    let mut builder = Builder::default();
    builder
        .register(
            "sample.wait.v1",
            &["api"],
            Arc::new(Waiting(dropped.clone())),
        )
        .unwrap();
    let client = builder.freeze().client("api").unwrap();
    let error = client
        .invoke(&TestScope::default(), "sample.wait.v1", b"{}")
        .await
        .unwrap_err();
    assert_eq!(error.kind(), Kind::Timeout);
    assert!(dropped.load(Ordering::Acquire));
}
