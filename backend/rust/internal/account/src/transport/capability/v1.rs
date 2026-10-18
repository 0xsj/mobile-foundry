use crate::app::query::Access;
use kernel::{
    context::{PortFuture, Scope},
    id::Id,
};
use serde::{
    Deserialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
pub const NAME: &str = "account.access.v1";
pub struct Provider(pub Access);
#[derive(Default)]
struct Request {
    principal_id: String,
    account_id: String,
    action: String,
}
impl<'de> Deserialize<'de> for Request {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Request;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one access request")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Request, A::Error> {
                let mut value = Request::default();
                while let Some(key) = map.next_key::<String>()? {
                    let key: String = key
                        .chars()
                        .map(|c| match c {
                            '\u{17f}' => 's',
                            '\u{212a}' => 'k',
                            c => c.to_ascii_lowercase(),
                        })
                        .collect();
                    let target = match key.as_str() {
                        "principal_id" => &mut value.principal_id,
                        "account_id" => &mut value.account_id,
                        "action" => &mut value.action,
                        _ => return Err(de::Error::custom("unknown access field")),
                    };
                    if let Some(next) = map.next_value::<Option<String>>()? {
                        *target = next;
                    }
                }
                Ok(value)
            }
        }
        decoder.deserialize_map(Fields)
    }
}
impl capability::Handler for Provider {
    fn call<'a>(&'a self, scope: &'a dyn Scope, wire: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let input: Request = capability::decode(wire)?;
            let actor = Id::parse(&input.principal_id)?;
            let account = Id::parse(&input.account_id)?;
            if !["account.read", "account.rename"].contains(&input.action.as_str()) {
                return Err(capability::invalid_request());
            }
            let allowed = self
                .0
                .handle(scope, &actor, &account, &input.action)
                .await?;
            serde_json::to_vec(&serde_json::json!({"allowed":allowed}))
                .map_err(|_| capability::invalid_response())
        })
    }
}
