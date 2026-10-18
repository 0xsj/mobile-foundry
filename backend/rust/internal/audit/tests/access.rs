use audit::{
    app::{
        ports::{AccountAccess, PortFuture, Scope},
        query::List,
    },
    infra::{capabilities::Client, memory::MemoryStore},
};
use events::dispatcher::Control;
use kernel::{Failure, Kind, id::Id};
use std::sync::Arc;
struct Provider(Vec<u8>);
impl capability::Invoker for Provider {
    fn invoke<'a>(
        &'a self,
        _: &'a dyn Scope,
        name: &'a str,
        _: &'a [u8],
    ) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            assert_eq!(name, "account.access.v1");
            Ok(self.0.clone())
        })
    }
}
#[tokio::test]
async fn shared_access_responses_require_an_explicit_boolean() {
    #[derive(serde::Deserialize)]
    struct Case {
        name: String,
        wire: String,
        valid: bool,
        #[serde(default)]
        allowed: bool,
    }
    let cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-audit/access-responses.json"
    ))
    .unwrap();
    let id = Id::parse("00000000-0000-4000-8000-000000000001").unwrap();
    for case in cases {
        let result = Client(Arc::new(Provider(case.wire.into_bytes())))
            .allowed(&Control::default(), &id, &id)
            .await;
        assert_eq!(result.is_ok(), case.valid, "{}", case.name);
        if case.valid {
            assert_eq!(result.unwrap(), case.allowed);
        }
    }
}
struct Access(bool);
impl AccountAccess for Access {
    fn allowed<'a>(&'a self, _: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async move {
            if self.0 {
                Err(Failure::new(Kind::Unavailable, "fixture.offline", "SECRET"))
            } else {
                Ok(false)
            }
        })
    }
}
#[tokio::test]
async fn unavailable_access_is_a_failure_and_an_admitted_negative_is_denial() {
    let id = Id::parse("00000000-0000-4000-8000-000000000001").unwrap();
    for (failed, kind) in [(true, Kind::Unavailable), (false, Kind::Forbidden)] {
        let query = List {
            store: Arc::new(MemoryStore::default()),
            access: Arc::new(Access(failed)),
        };
        let error = query
            .handle(&Control::default(), &id, &id, 0, 50)
            .await
            .err()
            .unwrap();
        assert_eq!(error.kind(), kind);
    }
}
