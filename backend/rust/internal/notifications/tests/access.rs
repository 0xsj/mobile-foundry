use kernel::id::Id;
use notifications::{
    app::ports::{AccountAccess, PortFuture, Scope},
    infra::capabilities::Client,
};
use std::sync::Arc;
struct TestScope;
impl Scope for TestScope {
    fn check(&self) -> Result<(), kernel::Failure> {
        Ok(())
    }
    fn metadata(&self) -> kernel::context::Metadata {
        Default::default()
    }
}
struct Provider(Vec<u8>);
impl capability::Invoker for Provider {
    fn invoke<'a>(
        &'a self,
        _: &'a dyn Scope,
        name: &'a str,
        request: &'a [u8],
    ) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            assert_eq!(name, "account.access.v1");
            let input: serde_json::Value = serde_json::from_slice(request).unwrap();
            assert_eq!(input["action"], "account.read");
            Ok(self.0.clone())
        })
    }
}
#[tokio::test]
async fn account_reply_requires_an_explicit_boolean() {
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-audit/access-responses.json"
    ))
    .unwrap();
    let id = Id::parse("00000000-0000-4000-8000-000000000001").unwrap();
    for case in cases {
        let result = Client(Arc::new(Provider(
            case["wire"].as_str().unwrap().as_bytes().to_vec(),
        )))
        .allowed(&TestScope, &id, &id)
        .await;
        assert_eq!(result.is_ok(), case["valid"].as_bool().unwrap());
        if let Ok(allowed) = result {
            assert_eq!(allowed, case["allowed"].as_bool().unwrap());
        }
    }
}
