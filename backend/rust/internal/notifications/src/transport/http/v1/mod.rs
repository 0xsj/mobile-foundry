mod input;
pub mod installations;
use crate::{
    app::{
        self, Dependencies, Target,
        command::{MarkRead, SetMediaPreference},
        query::{GetPreferences, ListInbox},
    },
    domain::Item,
};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{Method, StatusCode},
    response::Response,
    routing::any,
};
use httpx::{
    RequestContext,
    admission::{self, AdmissionError},
    problem,
};
use kernel::Failure;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
#[derive(Clone)]
pub struct Handlers {
    d: Dependencies,
    principals: Arc<dyn principal::Resolver>,
}
impl Handlers {
    pub fn new(d: Dependencies, principals: Arc<dyn principal::Resolver>) -> Self {
        Self { d, principals }
    }
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/accounts/{account}/notifications", any(dispatch))
            .route(
                "/v1/accounts/{account}/notifications/{item}/read",
                any(dispatch),
            )
            .route(
                "/v1/accounts/{account}/notification-preferences",
                any(dispatch),
            )
            .with_state(self.clone())
    }
}
#[derive(Serialize)]
struct ItemWire<'a> {
    id: &'a str,
    file_id: &'a str,
    #[serde(rename = "type")]
    kind: &'a str,
    category: &'a str,
    occurred_at: String,
    created_at: String,
    read_at: Option<String>,
}
fn project(i: &Item) -> Result<ItemWire<'_>, Failure> {
    let v = i.snapshot();
    Ok(ItemWire {
        id: v.id.as_str(),
        file_id: v.file_id.as_str(),
        kind: &v.kind,
        category: &v.category,
        occurred_at: events::time(v.occurred_at)?,
        created_at: events::time(v.created_at)?,
        read_at: v.read_at.map(events::time).transpose()?,
    })
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
fn plain(r: &Request, body: bool) -> Result<(), Failure> {
    if r.uri().query().is_some()
        || r.headers().contains_key("idempotency-key")
        || r.headers().contains_key("if-match")
    {
        return Err(app::invalid());
    }
    if !body {
        no_body(r)?;
    }
    Ok(())
}
#[derive(Serialize)]
struct PreferenceWire<'a> {
    account_id: &'a str,
    media_in_app: bool,
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(c): Extension<RequestContext>,
    r: Request,
) -> Response {
    let prefs = r.uri().path().ends_with("/notification-preferences");
    let read = r.uri().path().ends_with("/read");
    let (allowed, allow) = if prefs {
        (
            matches!(*r.method(), Method::GET | Method::HEAD | Method::PUT),
            "GET, HEAD, PUT",
        )
    } else if read {
        (r.method() == Method::POST, "POST")
    } else {
        (
            matches!(*r.method(), Method::GET | Method::HEAD),
            "GET, HEAD",
        )
    };
    if !allowed {
        return problem::method_not_allowed(&c.request_id, allow);
    }
    match httpx::deadline(&c, async { Ok(handle(&h, &c, r, prefs, read).await) }).await {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&c),
        Err(e) => problem::response(&e, &c.request_id),
    }
}
async fn handle(
    h: &Handlers,
    c: &RequestContext,
    r: Request,
    prefs: bool,
    read: bool,
) -> Result<Response, AdmissionError> {
    let actor = h
        .principals
        .resolve(c, httpx::auth::bearer(r.headers())?)
        .await?;
    let parts: Vec<_> = r.uri().path().split('/').collect();
    let t = Target {
        actor: actor.id,
        account: input::id(parts[3])?,
    };
    if !prefs && !read {
        no_body(&r)?;
        let input = input::page(r.uri().query(), t)?;
        let unread = input.unread_only;
        let target = input.target.clone();
        let page = ListInbox(h.d.clone()).handle(c, input).await?;
        #[derive(Serialize)]
        struct PageWire<'a> {
            account_id: &'a str,
            items: Vec<ItemWire<'a>>,
            unread_count: u64,
            next_cursor: Option<String>,
        }
        return Ok(admission::json_response(
            c,
            StatusCode::OK,
            &PageWire {
                account_id: target.account.as_str(),
                items: page
                    .items
                    .iter()
                    .map(project)
                    .collect::<Result<Vec<_>, _>>()?,
                unread_count: page.unread_count,
                next_cursor: page
                    .next
                    .map(|v| input::cursor(&target, v, unread))
                    .transpose()?,
            },
        )?);
    }
    let setting = prefs && r.method() == Method::PUT;
    plain(&r, setting)?;
    if read {
        let item = MarkRead(h.d.clone())
            .handle(c, &t, &input::id(parts[5])?)
            .await?;
        return Ok(admission::json_response(
            c,
            StatusCode::OK,
            &project(&item)?,
        )?);
    }
    let enabled = if setting {
        let (parts, body) = r.into_parts();
        let bytes = admission::read_json_body(c, &parts.headers, body).await?;
        let mut decoder = serde_json::Deserializer::from_slice(&bytes);
        let pref =
            input::Preference::deserialize(&mut decoder).map_err(admission::invalid_json_input)?;
        decoder.end().map_err(admission::invalid_json_input)?;
        let enabled = pref.0.ok_or_else(app::invalid)?;
        SetMediaPreference(h.d.clone())
            .handle(c, &t, enabled)
            .await?;
        enabled
    } else {
        GetPreferences(h.d.clone()).handle(c, &t).await?
    };
    Ok(admission::json_response(
        c,
        StatusCode::OK,
        &PreferenceWire {
            account_id: t.account.as_str(),
            media_in_app: enabled,
        },
    )?)
}
