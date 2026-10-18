use kernel::{Failure, Kind, id::Id};
use notifications::{
    app::{
        ports::{PortFuture, Scope},
        push::SessionEligibility,
    },
    infra::capabilities::{Client, sessions::NAME},
};
use std::sync::Arc;
struct Invoker(Vec<u8>);
impl capability::Invoker for Invoker {
    fn invoke<'a>(
        &'a self,
        s: &'a dyn Scope,
        name: &'a str,
        request: &'a [u8],
    ) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            s.check()?;
            assert_eq!(name, NAME);
            let input: serde_json::Value = serde_json::from_slice(request).unwrap();
            assert_eq!(input.as_object().unwrap().len(), 2);
            assert!(input["principal_id"].is_string() && input["session_id"].is_string());
            Ok(self.0.clone())
        })
    }
}
#[tokio::test]
async fn shared_response_corpus_and_missing_provider() {
    let s = events::dispatcher::Control::default();
    let id = Id::parse("10000000-0000-4000-8000-000000000001").unwrap();
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-push/responses.json"
    ))
    .unwrap();
    for c in cases {
        let client = Client(Arc::new(Invoker(
            c["wire"].as_str().unwrap().as_bytes().to_vec(),
        )));
        let result = client.eligible(&s, &id, &id).await;
        assert_eq!(
            result.is_ok(),
            c["valid"].as_bool().unwrap(),
            "{}",
            c["name"]
        );
        if let Ok(eligible) = result {
            assert_eq!(eligible, c["eligible"].as_bool().unwrap());
        }
    }
    let registry = capability::Builder::default().freeze();
    let client = Client(Arc::new(registry.client("notifications").unwrap()));
    assert_eq!(
        client.eligible(&s, &id, &id).await.unwrap_err().kind(),
        Kind::Unavailable
    );
    let canceled = events::dispatcher::Control::default();
    canceled.cancel();
    let client = Client(Arc::new(Invoker(br#"{"eligible":true}"#.to_vec())));
    assert_eq!(
        client
            .eligible(&canceled, &id, &id)
            .await
            .unwrap_err()
            .kind(),
        Kind::Canceled
    );
}
struct Failed;
impl capability::Invoker for Failed {
    fn invoke<'a>(&'a self, _: &'a dyn Scope, _: &'a str, _: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async {
            Err(Failure::new(
                Kind::Unavailable,
                "fixture.outage",
                "PRIVATE provider cause",
            ))
        })
    }
}
#[tokio::test]
async fn outage_is_not_a_negative_answer() {
    let id = Id::parse("10000000-0000-4000-8000-000000000001").unwrap();
    assert_eq!(
        Client(Arc::new(Failed))
            .eligible(&events::dispatcher::Control::default(), &id, &id)
            .await
            .unwrap_err()
            .kind(),
        Kind::Unavailable
    );
}
