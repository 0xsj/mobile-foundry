#[path = "support/eligibility.rs"]
mod support;
use capability::Invoker;
use identity::{
    app::{
        ports::{PortFuture, Scope},
        query::session_eligibility::{SessionEligibility, SessionStatus},
    },
    domain::Timestamp,
    transport::capability::v1::session_eligibility::{NAME, Provider},
};
use kernel::{
    Failure, Kind,
    id::{Id, RandomIds},
};
use std::{
    sync::Arc,
    time::{Duration, UNIX_EPOCH},
};
struct Clock;
impl kernel::clock::Clock for Clock {
    fn now(&self) -> std::time::SystemTime {
        UNIX_EPOCH + Duration::from_micros(support::AT)
    }
}
struct Status(bool);
impl SessionStatus for Status {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        p: &'a Id,
        session: &'a Id,
        _: Timestamp,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            s.check()?;
            assert_eq!(p.as_str(), "10000000-0000-4000-8000-000000000001");
            assert_eq!(session.as_str(), "10000000-0000-4000-8000-000000000002");
            if self.0 {
                Ok(true)
            } else {
                Err(Failure::new(
                    Kind::Unavailable,
                    "fixture.outage",
                    "PRIVATE store failure",
                ))
            }
        })
    }
}
#[tokio::test]
async fn memory_session_status() {
    support::conformance(Arc::new(identity::infra::memory::MemoryStore::new(
        Arc::new(RandomIds),
    )))
    .await;
}
#[tokio::test]
async fn shared_request_corpus_and_provider_failures() {
    let s = events::dispatcher::Control::default();
    let mut b = capability::Builder::default();
    b.register(
        NAME,
        &["notifications"],
        Arc::new(Provider(SessionEligibility {
            store: Arc::new(Status(true)),
            clock: Arc::new(Clock),
        })),
    )
    .unwrap();
    let registry = b.freeze();
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-push/requests.json"
    ))
    .unwrap();
    for c in cases {
        let client = registry.client(c["caller"].as_str().unwrap()).unwrap();
        let result = client
            .invoke(&s, NAME, c["wire"].as_str().unwrap().as_bytes())
            .await;
        if c["valid"] == true {
            assert_eq!(
                serde_json::from_slice::<serde_json::Value>(&result.unwrap()).unwrap(),
                serde_json::json!({"eligible":true}),
                "{}",
                c["name"]
            );
        } else {
            assert_eq!(
                result.unwrap_err().kind().as_str(),
                c["error_kind"].as_str().unwrap(),
                "{}",
                c["name"]
            );
        }
    }
    let p = Id::parse("10000000-0000-4000-8000-000000000001").unwrap();
    let session = Id::parse("10000000-0000-4000-8000-000000000002").unwrap();
    assert_eq!(
        SessionEligibility {
            store: Arc::new(Status(false)),
            clock: Arc::new(Clock)
        }
        .handle(&s, &p, &session)
        .await
        .unwrap_err()
        .kind(),
        Kind::Unavailable
    );
}
#[tokio::test]
async fn module_registers_only_the_notifications_caller() {
    let module = identity::Module::memory(Arc::new(Clock), Arc::new(RandomIds))
        .await
        .unwrap();
    let mut b = capability::Builder::default();
    module.register(&mut b).unwrap();
    let registry = b.freeze();
    registry.require(NAME, "notifications").unwrap();
    assert_eq!(
        registry.require(NAME, "api").unwrap_err().kind(),
        Kind::Forbidden
    );
    let wire=serde_json::to_vec(&serde_json::json!({"principal_id":support::id().as_str(),"session_id":support::id().as_str()})).unwrap();
    assert_eq!(
        serde_json::from_slice::<serde_json::Value>(
            &registry
                .client("notifications")
                .unwrap()
                .invoke(&events::dispatcher::Control::default(), NAME, &wire)
                .await
                .unwrap()
        )
        .unwrap(),
        serde_json::json!({"eligible":false})
    );
    module.close().await.unwrap();
}
