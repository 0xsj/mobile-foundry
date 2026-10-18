mod input;
use crate::{
    app::{Dependencies, command::Write, query::Read},
    domain::{self, Action, Note, Receipt},
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
use kernel::{Failure, Kind};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
#[derive(Clone)]
pub struct Handlers {
    write: Write,
    read: Read,
    principals: Arc<dyn principal::Resolver>,
}
impl Handlers {
    pub fn new(d: Dependencies, principals: Arc<dyn principal::Resolver>) -> Self {
        Self {
            write: Write(d.clone()),
            read: Read(d),
            principals,
        }
    }
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/accounts/{account}/notes", any(dispatch))
            .route("/v1/accounts/{account}/notes/{note}", any(dispatch))
            .route(
                "/v1/accounts/{account}/note-receipts/{command}",
                any(dispatch),
            )
            .with_state(self.clone())
    }
}
#[derive(Serialize)]
struct NoteWire<'a> {
    id: &'a str,
    title: &'a str,
    version: u64,
}
fn note_wire(n: &Note) -> NoteWire<'_> {
    NoteWire {
        id: n.id().as_str(),
        title: n.title(),
        version: n.version(),
    }
}
#[derive(Serialize)]
struct ReceiptWire<'a> {
    command_id: &'a str,
    account_id: &'a str,
    principal_id: &'a str,
    action: &'a str,
    note_id: &'a str,
    title: &'a str,
    version: u64,
}
fn receipt_response(
    context: &RequestContext,
    r: &Receipt,
    status: StatusCode,
    location: bool,
) -> Result<Response, Failure> {
    let s = r.snapshot();
    let wire = ReceiptWire {
        command_id: s.command_id.as_str(),
        account_id: s.account_id.as_str(),
        principal_id: s.principal_id.as_str(),
        action: s.action.as_str(),
        note_id: s.note_id.as_str(),
        title: &s.title,
        version: s.version,
    };
    let mut response = admission::json_response(context, status, &wire)?;
    response
        .headers_mut()
        .insert("etag", admission::Version::new(s.version)?.etag());
    if location {
        response.headers_mut().insert(
            "location",
            HeaderValue::from_str(&format!(
                "/v1/accounts/{}/note-receipts/{}",
                s.account_id, s.command_id
            ))
            .expect("canonical IDs"),
        );
    }
    Ok(response)
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(context): Extension<RequestContext>,
    request: Request,
) -> Response {
    let receipt = request.uri().path().split('/').nth(4) == Some("note-receipts");
    let collection = request.uri().path().split('/').count() == 5;
    let allow = if receipt {
        "GET, HEAD"
    } else if collection {
        "GET, HEAD, POST"
    } else {
        "GET, HEAD, PATCH, DELETE"
    };
    let allowed = match request.method() {
        &Method::GET | &Method::HEAD => true,
        &Method::POST => collection && !receipt,
        &Method::PATCH | &Method::DELETE => !receipt && !collection,
        _ => false,
    };
    if !allowed {
        return problem::method_not_allowed(&context.request_id, allow);
    }
    match httpx::deadline(&context, async {
        Ok(handle(&h, &context, request, receipt, collection).await)
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
    receipt: bool,
    collection: bool,
) -> Result<Response, AdmissionError> {
    let actor = h
        .principals
        .resolve(c, httpx::auth::bearer(request.headers())?)
        .await?;
    let segments: Vec<_> = request.uri().path().split('/').collect();
    let account = input::path_id(segments[3])?;
    let is_read = matches!(*request.method(), Method::GET | Method::HEAD);
    if collection && is_read {
        let (limit, after) = input::page(request.uri().query().unwrap_or_default())?;
        let page = h
            .read
            .list(c, &actor.id, &account, after.as_ref(), limit)
            .await?;
        #[derive(Serialize)]
        struct Page<'a> {
            account_id: &'a str,
            notes: Vec<NoteWire<'a>>,
            next_after: Option<&'a str>,
        }
        return Ok(admission::json_response(
            c,
            StatusCode::OK,
            &Page {
                account_id: account.as_str(),
                notes: page.notes.iter().map(note_wire).collect(),
                next_after: page.next.as_ref().map(|id| id.as_str()),
            },
        )?);
    }
    if request.uri().query().is_some() {
        return Err(domain::invalid_write().into());
    }
    if is_read {
        let id = input::path_id(segments[5])?;
        if receipt {
            return Ok(receipt_response(
                c,
                &h.read.receipt(c, &actor.id, &account, &id).await?,
                StatusCode::OK,
                false,
            )?);
        }
        let note = h.read.get(c, &actor.id, &account, &id).await?;
        let mut response = admission::json_response(c, StatusCode::OK, &note_wire(&note))?;
        response
            .headers_mut()
            .insert("etag", admission::Version::new(note.version())?.etag());
        return Ok(response);
    }
    let command = admission::idempotency_key(request.headers())?
        .and_then(|s| kernel::id::Id::parse(&s).ok())
        .ok_or_else(domain::invalid_write)?;
    let action = if collection {
        Action::Create
    } else if request.method() == Method::PATCH {
        Action::Update
    } else {
        Action::Delete
    };
    let (note_id, expected) = if action == Action::Create {
        if request.headers().contains_key("if-match") {
            return Err(Failure::new(
                Kind::Invalid,
                "http.invalid_precondition",
                "unexpected create precondition",
            )
            .with_public("Send one strong ETag returned by this API in If-Match.")
            .into());
        }
        (None, 0)
    } else {
        (
            Some(input::path_id(segments[5])?),
            admission::required_version(request.headers())?.value(),
        )
    };
    let title = if action != Action::Delete {
        let (parts, body) = request.into_parts();
        let bytes = admission::read_json_body(c, &parts.headers, body).await?;
        let mut decoder = serde_json::Deserializer::from_slice(&bytes);
        let title =
            input::Title::deserialize(&mut decoder).map_err(admission::invalid_json_input)?;
        decoder.end().map_err(admission::invalid_json_input)?;
        title.0.ok_or_else(domain::invalid_title)?
    } else {
        if request.headers().contains_key("transfer-encoding")
            || request
                .headers()
                .get("content-length")
                .is_some_and(|v| v.to_str().ok().and_then(|v| v.parse::<u64>().ok()) != Some(0))
        {
            return Err(
                Failure::new(Kind::Invalid, "http.invalid_json", "delete body refused")
                    .with_public("Send one valid JSON object with the documented fields.")
                    .into(),
            );
        }
        String::new()
    };
    let result = h
        .write
        .handle(
            c,
            &actor.id,
            &account,
            domain::Write {
                command_id: command,
                action,
                note_id,
                title,
                expected,
            },
        )
        .await?;
    Ok(receipt_response(
        c,
        &result,
        if action == Action::Create {
            StatusCode::CREATED
        } else {
            StatusCode::OK
        },
        true,
    )?)
}
