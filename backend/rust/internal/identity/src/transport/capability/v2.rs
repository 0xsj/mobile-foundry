use crate::{
    app::{ports::SessionEvidence, query::ResolveSession},
    domain::Secret,
};
use kernel::context::{PortFuture, Scope};
use serde::{
    Deserialize, Serialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;

pub const NAME: &str = "identity.credential.resolve.v2";
#[derive(Clone)]
pub struct Credential(pub ResolveSession);
pub(super) struct Request {
    pub access_token: String,
}
impl<'de> Deserialize<'de> for Request {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Request;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one access credential object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Request, A::Error> {
                let mut value = Request {
                    access_token: String::new(),
                };
                while let Some(key) = map.next_key::<String>()? {
                    let key: String = key
                        .chars()
                        .map(|c| match c {
                            '\u{17f}' => 's',
                            '\u{212a}' => 'k',
                            c => c.to_ascii_lowercase(),
                        })
                        .collect();
                    if key != "access_token" {
                        return Err(de::Error::custom("unknown credential field"));
                    }
                    if let Some(token) = map.next_value::<Option<String>>()? {
                        value.access_token = token;
                    }
                }
                Ok(value)
            }
        }
        decoder.deserialize_map(Fields)
    }
}
#[derive(Serialize)]
struct Response<'a> {
    principal_id: &'a str,
    session_id: &'a str,
    session_expires_at: String,
}
pub(super) async fn resolve(
    query: &ResolveSession,
    scope: &dyn Scope,
    wire: &[u8],
) -> Result<SessionEvidence, kernel::Failure> {
    let request: Request = capability::decode(wire)?;
    query.handle(scope, Secret::new(request.access_token)).await
}
impl capability::Handler for Credential {
    fn call<'a>(&'a self, scope: &'a dyn Scope, wire: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let evidence = resolve(&self.0, scope, wire).await?;
            let response = Response {
                principal_id: evidence.principal_id.as_str(),
                session_id: evidence.session_id.as_str(),
                session_expires_at: crate::transport::time(evidence.expires_at),
            };
            serde_json::to_vec(&response).map_err(|_| capability::invalid_response())
        })
    }
}
