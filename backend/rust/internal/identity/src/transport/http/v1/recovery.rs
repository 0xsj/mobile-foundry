use crate::{
    app::{command::recovery::Recovery, ports::AttemptGate, query::Me, recovery::ResetRequests},
    domain::{self, Secret},
    transport::time,
};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{Method, StatusCode},
    response::{IntoResponse, Response},
    routing::any,
};
use httpx::{
    PeerAddress, RequestContext,
    admission::{self, AdmissionError},
    problem,
};
use kernel::{Failure, Kind};
use serde::de::{self, DeserializeSeed, MapAccess, Visitor};
use std::{collections::BTreeMap, fmt, sync::Arc};
#[derive(Clone)]
pub struct Handlers {
    commands: Recovery,
    queue: Arc<dyn ResetRequests>,
    gate: Arc<dyn AttemptGate>,
}
impl Handlers {
    pub fn new(
        commands: Recovery,
        queue: Arc<dyn ResetRequests>,
        gate: Arc<dyn AttemptGate>,
    ) -> Self {
        Self {
            commands,
            queue,
            gate,
        }
    }
    pub fn routes(&self) -> Router {
        let mut router = Router::new();
        for path in [
            "/v1/identity/email",
            "/v1/identity/email/request",
            "/v1/identity/email/verify",
            "/v1/identity/password/request-reset",
            "/v1/identity/password/reset",
            "/v1/identity/password/change",
        ] {
            router = router.route(path, any(dispatch));
        }
        router.with_state(self.clone())
    }
}
struct Seed(&'static [&'static str]);
impl<'de> DeserializeSeed<'de> for Seed {
    type Value = BTreeMap<String, Secret>;
    fn deserialize<D: de::Deserializer<'de>>(self, d: D) -> Result<Self::Value, D::Error> {
        d.deserialize_map(self)
    }
}
impl<'de> Visitor<'de> for Seed {
    type Value = BTreeMap<String, Secret>;
    fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("one recovery object")
    }
    fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Self::Value, A::Error> {
        let mut fields = BTreeMap::new();
        while let Some(key) = map.next_key::<String>()? {
            let key: String = key
                .chars()
                .map(|c| match c {
                    '\u{17f}' => 's',
                    '\u{212a}' => 'k',
                    c => c.to_ascii_lowercase(),
                })
                .collect();
            if !self.0.contains(&key.as_str()) {
                return Err(de::Error::custom("unknown recovery field"));
            }
            if let Some(value) = map.next_value::<Option<String>>()? {
                fields.insert(key, Secret::new(value));
            }
        }
        Ok(fields)
    }
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(context): Extension<RequestContext>,
    request: Request,
) -> Response {
    let read = request.uri().path() == "/v1/identity/email";
    if (read && !matches!(*request.method(), Method::GET | Method::HEAD))
        || (!read && request.method() != Method::POST)
    {
        return problem::method_not_allowed(
            &context.request_id,
            if read { "GET, HEAD" } else { "POST" },
        );
    }
    match httpx::deadline(&context, async {
        Ok(handle(&h, &context, request, read).await)
    })
    .await
    {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&context),
        Err(e) => problem::response(&e, &context.request_id),
    }
}
async fn handle(
    h: &Handlers,
    c: &RequestContext,
    request: Request,
    read: bool,
) -> Result<Response, AdmissionError> {
    if request.uri().query().is_some_and(|q| !q.is_empty()) {
        return Err(domain::invalid_input().into());
    }
    if admission::idempotency_key(request.headers())?.is_some() {
        return Err(Failure::new(
            Kind::Invalid,
            "http.unsupported_idempotency",
            "operation has no receipt",
        )
        .with_public("This operation does not support Idempotency-Key yet.")
        .into());
    }
    let path = request.uri().path().to_string();
    if !read {
        let peer = request
            .extensions()
            .get::<PeerAddress>()
            .ok_or_else(domain::invalid_state)?;
        let endpoint = match path.as_str() {
            "/v1/identity/email/request" => "email/request",
            "/v1/identity/email/verify" => "email/verify",
            "/v1/identity/password/request-reset" => "password/request-reset",
            "/v1/identity/password/reset" => "password/reset",
            _ => "password/change",
        };
        h.gate.allow(peer.0, endpoint)?;
    }
    let actor = if read || path.ends_with("/change") || path.ends_with("/email/request") {
        Some(
            Me(h.commands.core.clone())
                .handle(
                    c,
                    Secret::new(httpx::auth::bearer(request.headers())?.into()),
                )
                .await?
                .id()
                .clone(),
        )
    } else {
        None
    };
    if read {
        let contact = h
            .commands
            .store
            .contact(c, actor.as_ref().ok_or_else(domain::invalid_state)?)
            .await?;
        #[derive(serde::Serialize)]
        struct View {
            verified: bool,
            #[serde(skip_serializing_if = "Option::is_none")]
            email: Option<String>,
            #[serde(skip_serializing_if = "Option::is_none")]
            verified_at: Option<String>,
        }
        let view = match contact {
            Some(v) => View {
                verified: true,
                email: Some(v.email),
                verified_at: Some(time(v.verified_at)),
            },
            None => View {
                verified: false,
                email: None,
                verified_at: None,
            },
        };
        return Ok(admission::json_response(c, StatusCode::OK, &view)?);
    }
    let allowed: &'static [&'static str] = match path.as_str() {
        "/v1/identity/email/request" => &["email", "password"],
        "/v1/identity/email/verify" => &["token"],
        "/v1/identity/password/request-reset" => &["email"],
        "/v1/identity/password/reset" => &["token", "password"],
        _ => &["current_password", "new_password"],
    };
    let (parts, body) = request.into_parts();
    let bytes = admission::read_json_body(c, &parts.headers, body).await?;
    let mut decoder = serde_json::Deserializer::from_slice(&bytes);
    let mut fields = Seed(allowed)
        .deserialize(&mut decoder)
        .map_err(admission::invalid_json_input)?;
    decoder.end().map_err(admission::invalid_json_input)?;
    let mut take = |key: &str| {
        fields
            .remove(key)
            .unwrap_or_else(|| Secret::new(String::new()))
    };
    match path.as_str() {
        "/v1/identity/email/request" => {
            h.commands
                .request_verification(
                    c,
                    actor.as_ref().ok_or_else(domain::invalid_state)?,
                    take("email").expose(),
                    take("password"),
                )
                .await?
        }
        "/v1/identity/email/verify" => h.commands.verify(c, take("token")).await?,
        "/v1/identity/password/request-reset" => {
            h.queue.enqueue(c, take("email").expose())?;
            return Ok(admission::json_response(
                c,
                StatusCode::ACCEPTED,
                &serde_json::json!({"message":"If an eligible address exists, recovery instructions may arrive."}),
            )?);
        }
        "/v1/identity/password/reset" => {
            h.commands.reset(c, take("token"), take("password")).await?
        }
        _ => {
            h.commands
                .change(
                    c,
                    actor.as_ref().ok_or_else(domain::invalid_state)?,
                    take("current_password"),
                    take("new_password"),
                )
                .await?
        }
    }
    Ok(StatusCode::NO_CONTENT.into_response())
}
