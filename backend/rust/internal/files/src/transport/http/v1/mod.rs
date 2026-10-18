mod input;
use crate::{
    app::{
        self, Dependencies, Receipt, Target,
        command::{CompleteUpload, DeleteFile, RequestUpload, UploadContent},
        query::{DownloadFile, GetFile, GetReceipt, ListFiles},
    },
    domain::{self, Snapshot},
};
use axum::{
    Extension, Router,
    body::{Body, to_bytes},
    extract::{Request, State},
    http::{HeaderValue, Method, StatusCode},
    response::{IntoResponse, Response},
    routing::any,
};
use httpx::{
    RequestContext,
    admission::{self, AdmissionError},
    problem,
};
use kernel::{Failure, Kind};
use serde::{Deserialize, Serialize};
use std::{error::Error, sync::Arc};
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
            .route("/v1/accounts/{account}/files/uploads", any(dispatch))
            .route("/v1/accounts/{account}/files", any(dispatch))
            .route("/v1/accounts/{account}/files/{file}", any(dispatch))
            .route("/v1/accounts/{account}/files/{file}/content", any(dispatch))
            .route(
                "/v1/accounts/{account}/files/{file}/complete",
                any(dispatch),
            )
            .route("/v1/receipts/files/{key}", any(dispatch))
            .with_state(self.clone())
    }
}
#[derive(Serialize)]
struct FileWire<'a> {
    id: &'a str,
    account_id: &'a str,
    owner_id: &'a str,
    name: &'a str,
    content_type: &'a str,
    byte_size: u64,
    sha256: &'a str,
    status: &'a str,
    version: u64,
    created_at: String,
    updated_at: String,
}
fn project(s: &Snapshot) -> Result<FileWire<'_>, Failure> {
    Ok(FileWire {
        id: s.id.as_str(),
        account_id: s.account_id.as_str(),
        owner_id: s.owner_id.as_str(),
        name: &s.spec.name,
        content_type: &s.spec.content_type,
        byte_size: s.spec.byte_size,
        sha256: &s.spec.sha256,
        status: s.status.as_str(),
        version: s.version,
        created_at: events::time(s.created_at)?,
        updated_at: events::time(s.updated_at)?,
    })
}
fn etag(r: &mut Response, v: u64) -> Result<(), Failure> {
    r.headers_mut()
        .insert("etag", admission::Version::new(v)?.etag());
    Ok(())
}
fn receipt(
    c: &RequestContext,
    r: &Receipt,
    status: StatusCode,
    location: bool,
) -> Result<Response, Failure> {
    #[derive(Serialize)]
    struct Wire<'a> {
        key: &'a str,
        action: &'a str,
        principal_id: &'a str,
        file: FileWire<'a>,
    }
    let mut response = admission::json_response(
        c,
        status,
        &Wire {
            key: &r.key,
            action: r.action.as_str(),
            principal_id: r.actor.as_str(),
            file: project(&r.file)?,
        },
    )?;
    etag(&mut response, r.file.version)?;
    if location {
        response.headers_mut().insert(
            "location",
            HeaderValue::from_str(&format!(
                "/v1/accounts/{}/files/{}",
                r.file.account_id, r.file.id
            ))
            .map_err(|_| domain::invalid_state())?,
        );
    }
    Ok(response)
}
fn no_body(r: &Request) -> Result<(), Failure> {
    if r.headers().contains_key("transfer-encoding")
        || r.headers()
            .get("content-length")
            .is_some_and(|v| v.to_str().ok().and_then(|v| v.parse::<u64>().ok()) != Some(0))
    {
        return Err(Failure::new(
            Kind::Invalid,
            "http.invalid_json",
            "file mutation body refused",
        )
        .with_public("Send one valid JSON object with the documented fields."));
    }
    Ok(())
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(c): Extension<RequestContext>,
    r: Request,
) -> Response {
    let p = r.uri().path();
    let receipt_route = p.starts_with("/v1/receipts/");
    let upload = p.ends_with("/files/uploads");
    let collection = p.ends_with("/files");
    let content = p.ends_with("/content");
    let complete = p.ends_with("/complete");
    let (valid, allow) = if receipt_route || collection {
        (
            matches!(*r.method(), Method::GET | Method::HEAD),
            "GET, HEAD",
        )
    } else if upload || complete {
        (r.method() == Method::POST, "POST")
    } else if content {
        (
            matches!(*r.method(), Method::GET | Method::HEAD | Method::PUT),
            "GET, HEAD, PUT",
        )
    } else {
        (
            matches!(*r.method(), Method::GET | Method::HEAD | Method::DELETE),
            "DELETE, GET, HEAD",
        )
    };
    if !valid {
        return problem::method_not_allowed(&c.request_id, allow);
    }
    match httpx::deadline(&c, async { Ok(handle(&h, &c, r).await) }).await {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&c),
        Err(e) => problem::response(&e, &c.request_id),
    }
}
async fn handle(h: &Handlers, c: &RequestContext, r: Request) -> Result<Response, AdmissionError> {
    let actor = h
        .principals
        .resolve(c, httpx::auth::bearer(r.headers())?)
        .await?;
    let parts: Vec<_> = r.uri().path().split('/').collect();
    if parts[2] == "receipts" {
        let values =
            httpx::query::unique(r.uri().query().unwrap_or("")).map_err(|_| app::invalid())?;
        if values.len() != 1 {
            return Err(app::invalid().into());
        }
        let account = kernel::id::Id::parse(values.get("account_id").ok_or_else(app::invalid)?)?;
        let t = Target {
            actor: actor.id,
            account,
        };
        return Ok(receipt(
            c,
            &GetReceipt(h.d.clone())
                .handle(c, &t, &input::path(parts[4])?)
                .await?,
            StatusCode::OK,
            false,
        )?);
    }
    let t = Target {
        actor: actor.id,
        account: input::id(parts[3])?,
    };
    if parts.len() == 5 {
        let (limit, after) = input::page(r.uri().query().unwrap_or(""), &t)?;
        let p = ListFiles(h.d.clone())
            .handle(c, &t, after.as_ref(), limit)
            .await?;
        #[derive(Serialize)]
        struct Page<'a> {
            account_id: &'a str,
            files: Vec<FileWire<'a>>,
            next_cursor: Option<String>,
        }
        return Ok(admission::json_response(
            c,
            StatusCode::OK,
            &Page {
                account_id: t.account.as_str(),
                files: p.files.iter().map(project).collect::<Result<Vec<_>, _>>()?,
                next_cursor: p.next.as_ref().map(|v| input::cursor(&t, v)).transpose()?,
            },
        )?);
    }
    if r.uri().query().is_some() {
        return Err(app::invalid().into());
    }
    if parts[5] == "uploads" && parts.len() == 6 {
        if r.headers().contains_key("if-match") {
            return Err(Failure::new(
                Kind::Invalid,
                "http.invalid_precondition",
                "unexpected upload precondition",
            )
            .with_public("Send one strong ETag returned by this API in If-Match.")
            .into());
        }
        let key = admission::idempotency_key(r.headers())?.unwrap_or_default();
        let (head, body) = r.into_parts();
        let bytes = admission::read_json_body(c, &head.headers, body).await?;
        let mut decoder = serde_json::Deserializer::from_slice(&bytes);
        let input =
            input::Upload::deserialize(&mut decoder).map_err(admission::invalid_json_input)?;
        decoder.end().map_err(admission::invalid_json_input)?;
        return Ok(receipt(
            c,
            &RequestUpload(h.d.clone())
                .handle(c, &t, input.0, key)
                .await?,
            StatusCode::CREATED,
            true,
        )?);
    }
    let id = input::id(parts[5])?;
    let content = parts.get(6) == Some(&"content");
    if r.method() == Method::PUT {
        app::authorize(c, h.d.access.as_ref(), &t).await?;
        let mut values = r.headers().get_all("content-type").iter();
        let media: mime::Mime = values
            .next()
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.parse().ok())
            .ok_or_else(app::content)?;
        if values.next().is_some() || media.params().next().is_some() {
            return Err(app::content().into());
        }
        let media = media.essence_str().to_owned();
        if r.headers().contains_key("idempotency-key") || r.headers().contains_key("if-match") {
            return Err(app::invalid().into());
        }
        let bytes = to_bytes(r.into_body(), domain::MAX_BYTES)
            .await
            .map_err(|e| {
                if e.source()
                    .is_some_and(|v| v.is::<http_body_util::LengthLimitError>())
                {
                    admission::too_large()
                } else {
                    app::content().with_cause(e).into()
                }
            })?;
        UploadContent(h.d.clone())
            .handle(c, &t, &id, &media, bytes.to_vec())
            .await?;
        return Ok(admission::json_response(
            c,
            StatusCode::OK,
            &serde_json::json!({"status":"stored"}),
        )?);
    }
    if matches!(*r.method(), Method::GET | Method::HEAD) {
        if content {
            let (f, bytes) = DownloadFile(h.d.clone()).handle(c, &t, &id).await?;
            let mut response = Body::from(bytes).into_response();
            for (name, value) in [
                ("content-type", f.spec.content_type.clone()),
                ("content-disposition", input::disposition(&f.spec.name)),
                ("content-length", f.spec.byte_size.to_string()),
                ("x-content-type-options", "nosniff".into()),
                ("cache-control", "no-store".into()),
            ] {
                response.headers_mut().insert(
                    name,
                    HeaderValue::from_str(&value).map_err(|_| domain::invalid_state())?,
                );
            }
            etag(&mut response, f.version)?;
            return Ok(response);
        }
        let f = GetFile(h.d.clone()).handle(c, &t, &id).await?;
        let mut response = admission::json_response(c, StatusCode::OK, &project(&f)?)?;
        etag(&mut response, f.version)?;
        return Ok(response);
    }
    no_body(&r)?;
    let expected = admission::required_version(r.headers())?.value();
    let key = admission::idempotency_key(r.headers())?.unwrap_or_default();
    let result = if r.method() == Method::DELETE {
        DeleteFile(h.d.clone())
            .handle(c, &t, id, expected, key)
            .await?
    } else {
        CompleteUpload(h.d.clone())
            .handle(c, &t, id, expected, key)
            .await?
    };
    Ok(receipt(c, &result, StatusCode::OK, false)?)
}
