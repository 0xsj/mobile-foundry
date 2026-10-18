mod input;
use crate::app::{
    ports::Record,
    query::{List, ListPrincipal},
};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{Method, StatusCode},
    response::Response,
    routing::any,
};
use httpx::{RequestContext, admission::json_response, problem};
use kernel::{Failure, id::Id};
use serde::Serialize;
use std::sync::Arc;
#[derive(Clone)]
pub struct Handlers {
    pub list: List,
    pub personal: ListPrincipal,
    pub principals: Arc<dyn principal::Resolver>,
}
impl Handlers {
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/accounts/{account_id}/audit", any(dispatch))
            .route("/v1/principals/me/audit", any(dispatch))
            .with_state(self.clone())
    }
}
#[derive(Serialize)]
struct Row<'a> {
    sequence: u64,
    event_id: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    account_id: Option<&'a str>,
    actor_id: &'a str,
    action: &'a str,
    aggregate_version: u64,
    occurred_at: String,
    recorded_at: String,
    #[serde(skip_serializing_if = "str::is_empty")]
    request_id: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    correlation_id: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    principal_id: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    session_id: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    reason: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    file_id: Option<&'a str>,
}
fn project(record: &Record) -> Result<Row<'_>, Failure> {
    let e = record.entry.evidence();
    Ok(Row {
        sequence: record.sequence,
        event_id: e.event_id.as_str(),
        account_id: e.account_id.as_ref().map(Id::as_str),
        actor_id: e.actor_id.as_str(),
        action: &e.action,
        aggregate_version: e.version,
        occurred_at: events::time(e.occurred_at)?,
        recorded_at: events::time(record.entry.recorded_at())?,
        request_id: &e.request_id,
        correlation_id: e.correlation_id.as_deref(),
        principal_id: e.principal_id.as_ref().map(Id::as_str),
        session_id: e.session_id.as_ref().map(Id::as_str),
        reason: e.reason.as_deref(),
        file_id: e.file_id.as_ref().map(Id::as_str),
    })
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(context): Extension<RequestContext>,
    request: Request,
) -> Response {
    if request.method() != Method::GET && request.method() != Method::HEAD {
        return problem::method_not_allowed(&context.request_id, "GET, HEAD");
    }
    match httpx::deadline(&context, read(&h, &context, request)).await {
        Ok(response) => response,
        Err(error) => problem::response(&error, &context.request_id),
    }
}
async fn read(
    h: &Handlers,
    context: &RequestContext,
    request: Request,
) -> Result<Response, Failure> {
    let actor = h
        .principals
        .resolve(context, httpx::auth::bearer(request.headers())?)
        .await?;
    let personal = request.uri().path() == "/v1/principals/me/audit";
    let id = if personal {
        None
    } else {
        let raw = request
            .uri()
            .path()
            .strip_prefix("/v1/accounts/")
            .and_then(|v| v.strip_suffix("/audit"))
            .unwrap_or_default();
        let value = httpx::query::component(raw, false).map_err(|_| kernel::id::invalid())?;
        Some(Id::parse(&value)?)
    };
    let principal = personal.then_some(&actor.id);
    let (after, limit) = input::parse(
        request.uri().query().unwrap_or_default(),
        id.as_ref(),
        principal,
    )?;
    let page = if let Some(id) = &id {
        h.list.handle(context, &actor.id, id, after, limit).await?
    } else {
        h.personal.handle(context, &actor.id, after, limit).await?
    };
    let next = page
        .next
        .map(|after| input::cursor(id.as_ref(), principal, after))
        .transpose()?;
    #[derive(Serialize)]
    struct Page<'a> {
        entries: Vec<Row<'a>>,
        next_cursor: Option<String>,
    }
    json_response(
        context,
        StatusCode::OK,
        &Page {
            entries: page.records.iter().map(project).collect::<Result<_, _>>()?,
            next_cursor: next,
        },
    )
}
