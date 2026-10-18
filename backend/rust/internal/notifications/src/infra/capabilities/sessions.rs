use super::Client;
use crate::app::{
    ports::{PortFuture, Scope},
    push::SessionEligibility,
};
use kernel::id::Id;
use serde::{
    Deserialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
pub const NAME: &str = "identity.session.eligible.v1";
struct Response(Option<bool>);
impl<'de> Deserialize<'de> for Response {
    fn deserialize<D: de::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Response;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one eligibility answer")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Response, A::Error> {
                let mut value = None;
                while let Some(key) = map.next_key::<String>()? {
                    if !key.eq_ignore_ascii_case("eligible") {
                        return Err(de::Error::custom("unknown eligibility field"));
                    }
                    // Go's pointer bool resets on null, including a repeated field.
                    value = map.next_value::<Option<bool>>()?;
                }
                Ok(Response(value))
            }
        }
        d.deserialize_map(Fields)
    }
}
impl SessionEligibility for Client {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        principal: &'a Id,
        session: &'a Id,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            let request=serde_json::to_vec(&serde_json::json!({"principal_id":principal.as_str(),"session_id":session.as_str()})).map_err(|_|capability::invalid_request())?;
            let wire = self.0.invoke(s, NAME, &request).await?;
            let response: Response =
                capability::decode(&wire).map_err(|_| capability::invalid_response())?;
            response.0.ok_or_else(capability::invalid_response)
        })
    }
}
