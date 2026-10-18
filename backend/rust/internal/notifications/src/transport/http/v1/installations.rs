use crate::{
    app::{
        self,
        command::installations::{RegisterInstallation, UnregisterInstallation},
        installations::{Actor, Registration, Registry},
        query::installation::GetInstallation,
    },
    domain::installation::{Installation, InstallationSpec, PushRoute, Secret},
};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{HeaderValue, Method, StatusCode},
    response::Response,
    routing::any,
};
use httpx::{
    RequestContext,
    admission::{self, AdmissionError},
    problem,
};
use kernel::{Failure, id::Id};
use serde::{
    Serialize,
    de::{self, DeserializeSeed, MapAccess, Visitor},
};
use std::{fmt, sync::Arc, time::UNIX_EPOCH};
pub mod jobs;
mod push;
#[derive(Clone)]
pub struct Handlers {
    registry: Registry,
    principals: Arc<dyn principal::Resolver>,
}
impl Handlers {
    pub fn new(registry: Registry, principals: Arc<dyn principal::Resolver>) -> Self {
        Self {
            registry,
            principals,
        }
    }
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/installations", any(dispatch))
            .route("/v1/installations/{installation}", any(dispatch))
            .with_state(self.clone())
    }
}
#[derive(Default)]
struct Input {
    installation_id: String,
    registration_id: String,
    provider: String,
    application: String,
    environment: String,
    token: String,
}
struct Fields(bool);
impl<'de> DeserializeSeed<'de> for Fields {
    type Value = Input;
    fn deserialize<D: de::Deserializer<'de>>(self, d: D) -> Result<Input, D::Error> {
        d.deserialize_map(self)
    }
}
impl<'de> Visitor<'de> for Fields {
    type Value = Input;
    fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("one installation registration object")
    }
    fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Input, A::Error> {
        let mut fields = Input::default();
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
                "installation_id" if self.0 => &mut fields.installation_id,
                "registration_id" => &mut fields.registration_id,
                "provider" => &mut fields.provider,
                "application" => &mut fields.application,
                "environment" => &mut fields.environment,
                "token" => &mut fields.token,
                _ => return Err(de::Error::custom("unknown installation field")),
            };
            if let Some(v) = map.next_value::<Option<String>>()? {
                *target = v;
            }
        }
        Ok(fields)
    }
}
#[derive(Serialize)]
struct View<'a> {
    id: &'a str,
    registration_id: &'a str,
    version: u64,
    provider: &'a str,
    application: &'a str,
    environment: &'a str,
    registered: bool,
    current_binding: bool,
    created_at: String,
    updated_at: String,
    expires_at: String,
}
fn response(
    c: &RequestContext,
    i: &Installation,
    a: &Actor,
    create: bool,
) -> Result<Response, Failure> {
    let v = i.snapshot();
    let mut r = admission::json_response(
        c,
        if create {
            StatusCode::CREATED
        } else {
            StatusCode::OK
        },
        &View {
            id: v.id.as_str(),
            registration_id: v.registration_id.as_str(),
            version: v.version,
            provider: &v.spec.route.provider,
            application: &v.spec.route.application,
            environment: &v.spec.route.environment,
            registered: v.enabled,
            current_binding: a.owns(v),
            created_at: events::time(v.created_at)?,
            updated_at: events::time(v.updated_at)?,
            expires_at: events::time(v.expires_at)?,
        },
    )?;
    r.headers_mut()
        .insert("etag", admission::Version::new(v.version)?.etag());
    if create {
        r.headers_mut().insert(
            "location",
            HeaderValue::from_str(&format!("/v1/installations/{}", v.id))
                .map_err(|_| app::invalid())?,
        );
    }
    Ok(r)
}
fn no_body(r: &Request) -> Result<(), Failure> {
    if r.headers().contains_key("transfer-encoding")
        || r.headers()
            .get("content-length")
            .is_some_and(|v| v.to_str().ok().and_then(|v| v.parse::<u64>().ok()) != Some(0))
    {
        return Err(app::invalid());
    }
    Ok(())
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(c): Extension<RequestContext>,
    r: Request,
) -> Response {
    let create = r.uri().path() == "/v1/installations";
    let (valid, allow) = if create {
        (r.method() == Method::POST, "POST")
    } else {
        (
            matches!(
                *r.method(),
                Method::GET | Method::HEAD | Method::PUT | Method::DELETE
            ),
            "DELETE, GET, HEAD, PUT",
        )
    };
    if !valid {
        return problem::method_not_allowed(&c.request_id, allow);
    }
    match httpx::deadline(&c, async { Ok(handle(&h, &c, r, create).await) }).await {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&c),
        Err(e) => problem::response(&e, &c.request_id),
    }
}
async fn actor(
    h: &Handlers,
    c: &RequestContext,
    headers: &axum::http::HeaderMap,
    has_query: bool,
) -> Result<(Actor, Secret), AdmissionError> {
    let p = h
        .principals
        .resolve(c, httpx::auth::bearer(headers)?)
        .await?;
    if has_query || headers.contains_key("idempotency-key") || headers.contains_key("if-none-match")
    {
        return Err(app::invalid().into());
    }
    let mut proofs = headers.get_all("x-installation-key").iter();
    let proof = Secret::new(
        proofs
            .next()
            .and_then(|v| v.to_str().ok())
            .ok_or_else(app::invalid)?
            .to_owned(),
    );
    if proofs.next().is_some() {
        return Err(app::invalid().into());
    }
    Ok((
        Actor {
            principal: p.id,
            session: p.session_id,
            expires_at: p
                .session_expires_at
                .duration_since(UNIX_EPOCH)
                .map_err(|_| app::installations::session_required())?
                .as_micros()
                .try_into()
                .map_err(|_| app::installations::session_required())?,
        },
        proof,
    ))
}
async fn handle(
    h: &Handlers,
    c: &RequestContext,
    r: Request,
    create: bool,
) -> Result<Response, AdmissionError> {
    let (actor, proof) = actor(h, c, r.headers(), r.uri().query().is_some()).await?;
    if create || r.method() == Method::PUT {
        let expected = if create {
            if r.headers().contains_key("if-match") {
                return Err(app::invalid().into());
            }
            0
        } else {
            admission::required_version(r.headers())?.value()
        };
        let path_id = if create {
            None
        } else {
            Some(r.uri().path().rsplit('/').next().unwrap_or("").to_owned())
        };
        let (parts, body) = r.into_parts();
        let bytes = admission::read_json_body(c, &parts.headers, body).await?;
        let mut decoder = serde_json::Deserializer::from_slice(&bytes);
        let input = Fields(create)
            .deserialize(&mut decoder)
            .map_err(admission::invalid_json_input)?;
        decoder.end().map_err(admission::invalid_json_input)?;
        let id = if let Some(id) = path_id {
            super::input::id(&id)?
        } else {
            Id::parse(&input.installation_id)?
        };
        let registration_id = Id::parse(&input.registration_id)?;
        let i = RegisterInstallation(h.registry.clone())
            .handle(
                c,
                Registration {
                    actor: actor.clone(),
                    id,
                    registration_id,
                    proof,
                    expected,
                    spec: InstallationSpec {
                        route: PushRoute {
                            provider: input.provider,
                            application: input.application,
                            environment: input.environment,
                        },
                        token: Secret::new(input.token),
                    },
                },
            )
            .await?;
        return Ok(response(c, &i, &actor, create)?);
    }
    no_body(&r)?;
    if r.method() != Method::DELETE && r.headers().contains_key("if-match") {
        return Err(app::invalid().into());
    }
    let id = super::input::id(r.uri().path().rsplit('/').next().unwrap_or(""))?;
    let i = if r.method() == Method::DELETE {
        let expected = admission::required_version(r.headers())?.value();
        UnregisterInstallation(h.registry.clone())
            .handle(c, &actor, &id, proof, expected)
            .await?
    } else {
        GetInstallation(h.registry.clone())
            .handle(c, &actor, &id, proof)
            .await?
    };
    c.check()?;
    Ok(response(c, &i, &actor, false)?)
}
