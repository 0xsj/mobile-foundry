use identity::{
    app::{
        command::{Dependencies, Register, Renew, SignIn, SignOut},
        ports::{Metadata, Passwords, PortFuture, Scope, Store},
        query::Me,
    },
    domain::{self, Action, Password, Secret},
    infra::{memory::MemoryStore, security::TokenAdapter},
};
use kernel::{
    Failure, Kind,
    clock::Clock,
    id::{Generator, Id, RandomIds},
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, AtomicUsize, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};

#[derive(Default)]
struct TestScope {
    canceled: Arc<AtomicBool>,
}
impl Scope for TestScope {
    fn check(&self) -> Result<(), Failure> {
        if self.canceled.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Canceled, "", "test canceled"))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata {
            request_id: "request-test".into(),
            correlation_id: Some("flow-test".into()),
        }
    }
}
struct FixedClock;
impl Clock for FixedClock {
    fn now(&self) -> SystemTime {
        UNIX_EPOCH + Duration::from_secs(1_700_000_000)
    }
}
// Test-only adapter: intentionally substitutes policy mechanics without running a KDF.
#[derive(Default)]
struct TestPasswords {
    dummy: AtomicUsize,
}
impl Passwords for TestPasswords {
    fn hash<'a>(&'a self, scope: &'a dyn Scope, password: Password) -> PortFuture<'a, Secret> {
        Box::pin(async move {
            scope.check()?;
            Ok(Secret::new(format!("test-only:{}", password.expose())))
        })
    }
    fn verify<'a>(
        &'a self,
        scope: &'a dyn Scope,
        hash: Secret,
        password: Password,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            scope.check()?;
            Ok(hash.expose() == format!("test-only:{}", password.expose()))
        })
    }
    fn dummy<'a>(&'a self, scope: &'a dyn Scope, _: Password) -> PortFuture<'a, ()> {
        Box::pin(async move {
            scope.check()?;
            self.dummy.fetch_add(1, Ordering::Relaxed);
            Ok(())
        })
    }
}
struct FactIds {
    fail: AtomicBool,
}
impl Generator for FactIds {
    fn new_id(&self) -> Result<Id, Failure> {
        if self.fail.load(Ordering::Acquire) {
            Err(domain::invalid_state())
        } else {
            RandomIds.new_id()
        }
    }
}
fn setup() -> (
    Dependencies,
    Arc<MemoryStore>,
    Arc<FactIds>,
    Arc<TestPasswords>,
) {
    let ids = Arc::new(FactIds {
        fail: AtomicBool::new(false),
    });
    let store = Arc::new(MemoryStore::new(ids.clone()));
    let passwords = Arc::new(TestPasswords::default());
    let deps = Dependencies {
        store: store.clone(),
        passwords: passwords.clone(),
        tokens: Arc::new(TokenAdapter),
        ids: Arc::new(RandomIds),
        clock: Arc::new(FixedClock),
    };
    (deps, store, ids, passwords)
}
fn secret(value: &str) -> Secret {
    Secret::new(value.into())
}
#[tokio::test]
async fn invalid_integration_metadata_does_not_register_a_principal_without_its_outbox_record() {
    use events::dispatcher::Outbox;
    struct InvalidMetadata;
    impl Scope for InvalidMetadata {
        fn check(&self) -> Result<(), Failure> {
            Ok(())
        }
        fn metadata(&self) -> Metadata {
            Metadata {
                request_id: "invalid token".into(),
                correlation_id: None,
            }
        }
    }
    let (deps, store, _, _) = setup();
    let scope = TestScope::default();
    assert!(
        Register(deps.clone())
            .handle(
                &InvalidMetadata,
                "alice",
                "Ada",
                secret("valid-password-123")
            )
            .await
            .is_err()
    );
    assert!(store.by_login(&scope, "alice").await.unwrap().is_none());
    assert!(store.pending(&scope, 32).await.unwrap().is_empty());
    Register(deps)
        .handle(&scope, "alice", "Ada", secret("valid-password-123"))
        .await
        .unwrap();
    let messages = store.pending(&scope, 32).await.unwrap();
    assert_eq!(messages.len(), 1);
    assert_eq!(messages[0].id, store.facts().unwrap()[0].id);
}

#[tokio::test]
async fn registration_and_issue_failure_do_not_commit_partial_state_or_facts() {
    let (deps, store, ids, _) = setup();
    let scope = TestScope::default();
    ids.fail.store(true, Ordering::Release);
    assert!(
        Register(deps.clone())
            .handle(&scope, "alice", "Ada", secret("valid-password-123"))
            .await
            .is_err()
    );
    assert!(store.by_login(&scope, "alice").await.unwrap().is_none());
    assert!(store.facts().unwrap().is_empty());
    ids.fail.store(false, Ordering::Release);
    Register(deps.clone())
        .handle(&scope, "alice", "Ada", secret("valid-password-123"))
        .await
        .unwrap();
    ids.fail.store(true, Ordering::Release);
    assert!(
        SignIn(deps.clone())
            .handle(&scope, "alice", secret("valid-password-123"))
            .await
            .is_err()
    );
    assert_eq!(store.facts().unwrap().len(), 1);
    ids.fail.store(false, Ordering::Release);
    let grant = SignIn(deps.clone())
        .handle(&scope, "alice", secret("valid-password-123"))
        .await
        .unwrap();
    assert!(Me(deps).handle(&scope, grant.access).await.is_ok());
    let facts = store.facts().unwrap();
    assert_eq!(facts.len(), 2);
    assert_eq!(facts[0].metadata.request_id, "request-test");
    assert_eq!(facts[1].fact.action, Action::Issued);
}

#[tokio::test]
async fn duplicate_registration_concurrency_and_dummy_refusal() {
    let (deps, store, _, passwords) = setup();
    let scope = TestScope::default();
    let a = Register(deps.clone());
    let b = Register(deps.clone());
    let (a, b) = tokio::join!(
        a.handle(&scope, "alice", "One", secret("valid-password-123")),
        b.handle(&scope, "ALICE", "Two", secret("valid-password-123"))
    );
    assert_ne!(a.is_ok(), b.is_ok());
    assert_eq!(store.facts().unwrap().len(), 1);
    let error = SignIn(deps.clone())
        .handle(&scope, "unknown", secret("valid-password-123"))
        .await
        .unwrap_err();
    assert_eq!(error.kind(), Kind::Unauthenticated);
    assert_eq!(passwords.dummy.load(Ordering::Relaxed), 1);
    scope.canceled.store(true, Ordering::Release);
    assert_eq!(
        SignIn(deps)
            .handle(&scope, "alice", secret("valid-password-123"))
            .await
            .unwrap_err()
            .kind(),
        Kind::Canceled
    );
}

#[tokio::test]
async fn concurrent_refresh_reuse_commits_revocation_and_sign_out_is_idempotent() {
    let (deps, store, ids, _) = setup();
    let scope = TestScope::default();
    Register(deps.clone())
        .handle(&scope, "alice", "Ada", secret("valid-password-123"))
        .await
        .unwrap();
    let grant = SignIn(deps.clone())
        .handle(&scope, "alice", secret("valid-password-123"))
        .await
        .unwrap();
    ids.fail.store(true, Ordering::Release);
    assert!(
        Renew(deps.clone())
            .handle(&scope, grant.refresh.clone())
            .await
            .is_err()
    );
    assert!(
        Me(deps.clone())
            .handle(&scope, grant.access.clone())
            .await
            .is_ok()
    );
    assert_eq!(store.facts().unwrap().len(), 2);
    ids.fail.store(false, Ordering::Release);
    let a = Renew(deps.clone());
    let b = Renew(deps.clone());
    let (a, b) = tokio::join!(
        a.handle(&scope, grant.refresh.clone()),
        b.handle(&scope, grant.refresh.clone())
    );
    assert_ne!(a.is_ok(), b.is_ok());
    let next = a.or(b).unwrap();
    assert_eq!(
        Me(deps.clone())
            .handle(&scope, next.access)
            .await
            .unwrap_err()
            .kind(),
        Kind::Unauthenticated
    );
    let facts = store.facts().unwrap();
    assert_eq!(facts.len(), 4);
    assert_eq!(facts[2].fact.action, Action::Renewed);
    assert_eq!(facts[3].fact.action, Action::RefreshReused);
    assert_eq!(facts[3].fact.version, 3);
    for _ in 0..2 {
        SignOut(deps.clone())
            .handle(&scope, grant.refresh.clone())
            .await
            .unwrap();
    }
    assert_eq!(store.facts().unwrap().len(), 4);
}

#[tokio::test]
async fn versioned_credentials_preserve_v1_shape_and_trusted_v2_evidence() {
    use capability::Invoker;
    use identity::{
        app::query::ResolveSession,
        transport::capability::{v1, v2},
    };
    let (deps, _, _, _) = setup();
    let scope = TestScope::default();
    let principal = Register(deps.clone())
        .handle(&scope, "alice", "Ada", secret("valid-password-123"))
        .await
        .unwrap();
    let grant = SignIn(deps.clone())
        .handle(&scope, "alice", secret("valid-password-123"))
        .await
        .unwrap();
    let mut builder = capability::Builder::default();
    builder
        .register(
            v1::NAME,
            &["api"],
            Arc::new(v1::Credential(ResolveSession(deps.clone()))),
        )
        .unwrap();
    builder
        .register(
            v2::NAME,
            &["api"],
            Arc::new(v2::Credential(ResolveSession(deps.clone()))),
        )
        .unwrap();
    let client = builder.freeze().client("api").unwrap();
    let wire =
        serde_json::to_vec(&serde_json::json!({"access_token": grant.access.expose()})).unwrap();
    let response: serde_json::Value =
        serde_json::from_slice(&client.invoke(&scope, v1::NAME, &wire).await.unwrap()).unwrap();
    assert_eq!(
        response,
        serde_json::json!({"principal_id":principal.id().as_str()})
    );
    let response: serde_json::Value =
        serde_json::from_slice(&client.invoke(&scope, v2::NAME, &wire).await.unwrap()).unwrap();
    assert_eq!(response.as_object().unwrap().len(), 3);
    assert_eq!(response["principal_id"], principal.id().as_str());
    assert_eq!(response["session_id"], grant.session.state().id.as_str());
    let expiry =
        chrono::DateTime::parse_from_rfc3339(response["session_expires_at"].as_str().unwrap())
            .unwrap();
    assert_eq!(
        expiry.timestamp_micros() as u64,
        grant.session.state().expires_at.micros()
    );
    assert_ne!(
        expiry.timestamp_micros() as u64,
        grant.session.state().access_expires_at.micros()
    );
    let ordered = format!(
        r#"{{"ACCESS_TOKEN":"{}","access_token":null}}"#,
        grant.access.expose()
    );
    assert!(
        client
            .invoke(&scope, v2::NAME, ordered.as_bytes())
            .await
            .is_ok()
    );
    for wire in [
        br#"{"access_token":1,"access_token":"later"}"#.as_slice(),
        br#"{"extra":true}"#,
        b"[]",
    ] {
        assert_eq!(
            client
                .invoke(&scope, v2::NAME, wire)
                .await
                .unwrap_err()
                .public()
                .code
                .as_deref(),
            Some("capability.invalid_request")
        );
    }
    SignOut(deps).handle(&scope, grant.refresh).await.unwrap();
    for name in [v1::NAME, v2::NAME] {
        assert_eq!(
            client.invoke(&scope, name, &wire).await.unwrap_err().kind(),
            Kind::Unauthenticated
        );
    }
}
