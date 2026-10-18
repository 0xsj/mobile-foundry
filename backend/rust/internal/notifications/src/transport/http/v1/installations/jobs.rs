use super::*;
use crate::app::{
    command::{
        queue_push_preview::QueuePushPreview,
        recover_push_job::{CancelPushJob, ReplacePushJob},
    },
    push::PushLease,
    query::push_job::{GetPushJob, GetPushJobReplacement},
};
use serde::Deserialize;
#[derive(Clone)]
pub struct Handlers {
    pub installations: super::Handlers,
    pub queue: QueuePushPreview,
    pub get: GetPushJob,
    pub cancel: CancelPushJob,
    pub replace: ReplacePushJob,
    pub replacement: GetPushJobReplacement,
}
impl Handlers {
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/installations/{installation}/push-jobs", any(dispatch))
            .route(
                "/v1/installations/{installation}/push-jobs/{job}",
                any(dispatch),
            )
            .route(
                "/v1/installations/{installation}/push-jobs/{job}/cancel",
                any(dispatch),
            )
            .route(
                "/v1/installations/{installation}/push-jobs/{job}/replacement",
                any(dispatch),
            )
            .with_state(self.clone())
    }
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Replacement {
    job_id: String,
    accept_possible_duplicate: bool,
}
struct Root(String);
impl<'de> Deserialize<'de> for Root {
    fn deserialize<D: de::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Root;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one job object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Root, A::Error> {
                let mut id = String::new();
                while let Some(k) = map.next_key::<String>()? {
                    if !k.eq_ignore_ascii_case("job_id") {
                        return Err(de::Error::custom("unknown job field"));
                    }
                    if let Some(v) = map.next_value::<Option<String>>()? {
                        id = v;
                    }
                }
                Ok(Root(id))
            }
        }
        d.deserialize_map(Fields)
    }
}
#[derive(Clone, Copy)]
enum Action {
    Queue,
    Get,
    Cancel,
    Replace,
    Replacement,
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(c): Extension<RequestContext>,
    r: Request,
) -> Response {
    let parts: Vec<_> = r.uri().path().split('/').collect();
    let (action, allow) = match (parts.len(), parts.last().copied(), r.method()) {
        (5, _, &Method::POST) => (Some(Action::Queue), "POST"),
        (5, _, _) => (None, "POST"),
        (6, _, &Method::GET | &Method::HEAD) => (Some(Action::Get), "GET, HEAD"),
        (6, _, _) => (None, "GET, HEAD"),
        (7, Some("cancel"), &Method::POST) => (Some(Action::Cancel), "POST"),
        (7, Some("cancel"), _) => (None, "POST"),
        (7, _, &Method::POST) => (Some(Action::Replace), "GET, HEAD, POST"),
        (7, _, &Method::GET | &Method::HEAD) => (Some(Action::Replacement), "GET, HEAD, POST"),
        _ => (None, "GET, HEAD, POST"),
    };
    let Some(action) = action else {
        return problem::method_not_allowed(&c.request_id, allow);
    };
    match httpx::deadline(&c, async { Ok(handle(&h, &c, r, action).await) }).await {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&c),
        Err(e) => problem::response(&e, &c.request_id),
    }
}
async fn handle(
    h: &Handlers,
    c: &RequestContext,
    r: Request,
    action: Action,
) -> Result<Response, AdmissionError> {
    let (actor, proof) = actor(&h.installations, c, r.headers(), r.uri().query().is_some()).await?;
    let parts: Vec<_> = r.uri().path().split('/').collect();
    let installation = super::super::input::id(parts[3])?;
    let current = GetInstallation(h.installations.registry.clone())
        .handle(c, &actor, &installation, proof)
        .await?;
    if !actor.owns(current.snapshot()) {
        return Err(app::installations::binding().into());
    }
    let (job, status) = match action {
        Action::Queue | Action::Replace => {
            let version = admission::required_version(r.headers())?.value();
            let source = if matches!(action, Action::Replace) {
                Some(super::super::input::id(parts[5])?)
            } else {
                None
            };
            let (parts, body) = r.into_parts();
            let bytes = admission::read_json_body(c, &parts.headers, body).await?;
            let lease = PushLease {
                installation_id: installation,
                principal_id: actor.principal,
                session_id: actor.session,
                version,
            };
            let job = if let Some(source) = source {
                let input: Replacement =
                    serde_json::from_slice(&bytes).map_err(admission::invalid_json_input)?;
                h.replace
                    .handle(
                        c,
                        lease,
                        source,
                        Id::parse(&input.job_id)?,
                        input.accept_possible_duplicate,
                    )
                    .await?
            } else {
                let Root(id) =
                    serde_json::from_slice(&bytes).map_err(admission::invalid_json_input)?;
                h.queue.handle(c, lease, Id::parse(&id)?).await?
            };
            (job, StatusCode::ACCEPTED)
        }
        Action::Get | Action::Cancel | Action::Replacement => {
            no_body(&r)?;
            if r.headers().contains_key("if-match") {
                return Err(app::invalid().into());
            }
            let id = super::super::input::id(parts[5])?;
            let job = match action {
                Action::Get => h.get.handle(c, &actor, installation, id).await?,
                Action::Cancel => h.cancel.handle(c, &actor, id, installation).await?,
                _ => h.replacement.handle(c, &actor, installation, id).await?,
            };
            (job, StatusCode::OK)
        }
    };
    let v = job.snapshot();
    Ok(admission::json_response(
        c,
        status,
        &serde_json::json!({"job_id":v.intent.id.as_str(),"status":v.status.as_str(),"attempts":v.attempts,"result_code":v.code.as_str(),"retired":v.retired,"created_at":events::time(v.created_at)?,"updated_at":events::time(v.updated_at)?,"expires_at":events::time(v.expires_at)?}),
    )?)
}
