use super::*;
use crate::app::{
    command::send_push_preview::SendPushPreview,
    push::{PushAttempt, PushLease},
};
use serde::Deserialize;
#[derive(Clone)]
struct Preview {
    installations: Handlers,
    send: SendPushPreview,
}
impl Handlers {
    /// Not present in the default router. Identity capability is checked at composition.
    pub fn preview_routes(&self, send: SendPushPreview) -> Router {
        Router::new()
            .route(
                "/v1/installations/{installation}/push-preview",
                any(dispatch),
            )
            .with_state(Preview {
                installations: self.clone(),
                send,
            })
    }
}
struct Input(String);
impl<'de> Deserialize<'de> for Input {
    fn deserialize<D: de::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Input;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one push preview object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Input, A::Error> {
                let mut id = String::new();
                while let Some(key) = map.next_key::<String>()? {
                    if !key.eq_ignore_ascii_case("preview_id") {
                        return Err(de::Error::custom("unknown push preview field"));
                    }
                    if let Some(value) = map.next_value::<Option<String>>()? {
                        id = value;
                    }
                }
                Ok(Input(id))
            }
        }
        d.deserialize_map(Fields)
    }
}
async fn dispatch(
    State(h): State<Preview>,
    Extension(c): Extension<RequestContext>,
    r: Request,
) -> Response {
    if r.method() != Method::POST {
        return problem::method_not_allowed(&c.request_id, "POST");
    }
    match httpx::deadline(&c, async { Ok(handle(&h, &c, r).await) }).await {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&c),
        Err(e) => problem::response(&e, &c.request_id),
    }
}
async fn handle(h: &Preview, c: &RequestContext, r: Request) -> Result<Response, AdmissionError> {
    let (actor, proof) = actor(&h.installations, c, r.headers(), r.uri().query().is_some()).await?;
    let expected = admission::required_version(r.headers())?.value();
    let raw = r.uri().path().rsplit('/').nth(1).unwrap_or("");
    let id = super::super::input::id(raw)?;
    let (parts, body) = r.into_parts();
    let bytes = admission::read_json_body(c, &parts.headers, body).await?;
    let mut decoder = serde_json::Deserializer::from_slice(&bytes);
    let Input(preview) = Input::deserialize(&mut decoder).map_err(admission::invalid_json_input)?;
    decoder.end().map_err(admission::invalid_json_input)?;
    let preview = Id::parse(&preview)?;
    let installation = GetInstallation(h.installations.registry.clone())
        .handle(c, &actor, &id, proof)
        .await?;
    if !actor.owns(installation.snapshot()) {
        return Err(app::installations::binding().into());
    }
    if installation.snapshot().version != expected {
        return Err(app::installations::conflict().into());
    }
    let lease = PushLease {
        installation_id: id,
        principal_id: actor.principal,
        session_id: actor.session,
        version: expected,
    };
    let (disposition, retired) = match h.send.handle(c, lease, preview.clone()).await? {
        PushAttempt::Accepted => ("accepted", false),
        PushAttempt::InvalidTarget { retirement } => ("invalid_target", retirement?),
    };
    Ok(admission::json_response(
        c,
        StatusCode::OK,
        &serde_json::json!({"preview_id":preview.as_str(),"disposition":disposition,"retired":retired}),
    )?)
}
