use account::{
    app::ports::{Metadata, PortFuture, PrincipalResolver, Scope},
    infra::capabilities::{CREDENTIAL, Resolver},
};
use kernel::{Failure, Kind};
use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

struct TestScope;
impl Scope for TestScope {
    fn check(&self) -> Result<(), Failure> {
        Ok(())
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
struct Provider {
    response: Vec<u8>,
    failed: bool,
    calls: AtomicUsize,
}
impl capability::Invoker for Provider {
    fn invoke<'a>(
        &'a self,
        _: &'a dyn Scope,
        name: &'a str,
        request: &'a [u8],
    ) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            self.calls.fetch_add(1, Ordering::Relaxed);
            assert_eq!(name, CREDENTIAL);
            assert_eq!(
                serde_json::from_slice::<serde_json::Value>(request).unwrap(),
                serde_json::json!({"access_token":format!("a1.{}", "A".repeat(43))})
            );
            if self.failed {
                Err(Failure::new(
                    Kind::Unavailable,
                    "fixture.offline",
                    "provider offline",
                ))
            } else {
                Ok(self.response.clone())
            }
        })
    }
}
fn provider(value: serde_json::Value) -> Arc<Provider> {
    Arc::new(Provider {
        response: serde_json::to_vec(&value).unwrap(),
        failed: false,
        calls: AtomicUsize::new(0),
    })
}
#[tokio::test]
async fn malformed_evidence_or_dependency_failure_never_becomes_anonymous_success() {
    let token = format!("a1.{}", "A".repeat(43));
    let evidence = serde_json::json!({"principal_id":"00000000-0000-4000-8000-000000000001", "session_id":"00000000-0000-4000-8000-000000000002", "session_expires_at":"2030-01-01T00:00:00Z"});
    let valid = provider(evidence.clone());
    let principal = Resolver(valid.clone())
        .resolve(&TestScope, &token)
        .await
        .unwrap();
    assert_eq!(
        principal.session_id.as_str(),
        "00000000-0000-4000-8000-000000000002"
    );
    assert_eq!(
        principal
            .session_expires_at
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs(),
        1_893_456_000
    );
    let short = provider(evidence.clone());
    assert_eq!(
        Resolver(short.clone())
            .resolve(&TestScope, "short")
            .await
            .err()
            .unwrap()
            .public()
            .code
            .as_deref(),
        Some("principal.authentication_required")
    );
    assert_eq!(short.calls.load(Ordering::Relaxed), 0);
    for (key, value) in [
        ("principal_id", serde_json::json!("invalid")),
        ("session_id", serde_json::Value::Null),
        (
            "session_expires_at",
            serde_json::json!("1969-12-31T00:00:00Z"),
        ),
        ("extra", serde_json::json!(true)),
    ] {
        let mut malformed = evidence.clone();
        malformed[key] = value;
        assert_eq!(
            Resolver(provider(malformed))
                .resolve(&TestScope, &token)
                .await
                .err()
                .unwrap()
                .kind(),
            Kind::Internal
        );
    }
    let offline = Arc::new(Provider {
        response: Vec::new(),
        failed: true,
        calls: AtomicUsize::new(0),
    });
    assert_eq!(
        Resolver(offline.clone())
            .resolve(&TestScope, &token)
            .await
            .err()
            .unwrap()
            .kind(),
        Kind::Unavailable
    );
    assert_eq!(offline.calls.load(Ordering::Relaxed), 1);
}
