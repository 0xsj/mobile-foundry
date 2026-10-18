use crate::app::query::session_eligibility::SessionEligibility;
use kernel::{
    context::{PortFuture, Scope},
    id::Id,
};
use serde::{
    Deserialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
pub const NAME: &str = "identity.session.eligible.v1";
pub struct Provider(pub SessionEligibility);
#[derive(Default)]
struct Request {
    principal_id: String,
    session_id: String,
}
impl<'de> Deserialize<'de> for Request {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Request;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one session status request")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Request, A::Error> {
                let mut input = Request::default();
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
                        "principal_id" => &mut input.principal_id,
                        "session_id" => &mut input.session_id,
                        _ => return Err(de::Error::custom("unknown session status field")),
                    };
                    if let Some(value) = map.next_value::<Option<String>>()? {
                        *target = value;
                    }
                }
                Ok(input)
            }
        }
        decoder.deserialize_map(Fields)
    }
}
impl capability::Handler for Provider {
    fn call<'a>(&'a self, s: &'a dyn Scope, wire: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let input: Request = capability::decode(wire)?;
            let principal =
                Id::parse(&input.principal_id).map_err(|_| crate::domain::invalid_input())?;
            let session =
                Id::parse(&input.session_id).map_err(|_| crate::domain::invalid_input())?;
            let eligible = self.0.handle(s, &principal, &session).await?;
            serde_json::to_vec(&serde_json::json!({"eligible":eligible}))
                .map_err(|_| capability::invalid_response())
        })
    }
}
