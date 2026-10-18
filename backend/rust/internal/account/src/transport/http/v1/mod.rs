mod input;
use crate::{
    app::{
        self, Dependencies,
        command::{Bootstrap, Create, Rename},
        ports::{Metadata, PrincipalResolver, Scope},
        query::{Get, List},
    },
    domain::Account,
};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{HeaderMap, HeaderValue, Method, StatusCode},
    response::Response,
    routing::any,
};
use httpx::{
    RequestContext,
    admission::{self, AdmissionError},
    problem,
};
use kernel::{Failure, Kind, id::Id};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Clone)]
pub struct Handlers {
    create: Create,
    bootstrap: Bootstrap,
    rename: Rename,
    get: Get,
    list: List,
    principals: Arc<dyn PrincipalResolver>,
}
impl Handlers {
    pub fn new(deps: Dependencies, principals: Arc<dyn PrincipalResolver>) -> Self {
        Self {
            create: Create(deps.clone()),
            bootstrap: Bootstrap(deps.clone()),
            rename: Rename(deps.clone()),
            get: Get(deps.clone()),
            list: List(deps),
            principals,
        }
    }
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/accounts", any(dispatch))
            .route("/v1/accounts/bootstrap", any(dispatch))
            .route("/v1/accounts/{account_id}", any(dispatch))
            .with_state(self.clone())
    }
}
struct HttpScope(RequestContext);
impl Scope for HttpScope {
    fn check(&self) -> Result<(), Failure> {
        self.0.check()
    }
    fn metadata(&self) -> Metadata {
        Metadata {
            request_id: self.0.request_id.clone(),
            correlation_id: self.0.correlation_id.clone(),
        }
    }
}
#[derive(Serialize)]
struct ResponseAccount<'a> {
    id: &'a str,
    owner_id: &'a str,
    name: &'a str,
    version: u64,
    created_at: String,
    updated_at: String,
}
fn time(micros: u64) -> String {
    let time =
        chrono::DateTime::from_timestamp_micros(micros as i64).expect("admitted account timestamp");
    let base = time.format("%Y-%m-%dT%H:%M:%S").to_string();
    let fraction = time.timestamp_subsec_micros();
    if fraction == 0 {
        format!("{base}Z")
    } else {
        format!("{base}.{}Z", format!("{fraction:06}").trim_end_matches('0'))
    }
}
fn project(account: &Account) -> ResponseAccount<'_> {
    let s = account.snapshot();
    ResponseAccount {
        id: s.id.as_str(),
        owner_id: s.owner_id.as_str(),
        name: &s.name,
        version: s.version,
        created_at: time(s.created_at),
        updated_at: time(s.updated_at),
    }
}
fn account_response(
    context: &RequestContext,
    account: &Account,
    status: StatusCode,
    location: bool,
) -> Result<Response, Failure> {
    let mut response = admission::json_response(context, status, &project(account))?;
    response.headers_mut().insert(
        "etag",
        admission::Version::new(account.snapshot().version)?.etag(),
    );
    if location {
        response.headers_mut().insert(
            "location",
            HeaderValue::from_str(&format!("/v1/accounts/{}", account.snapshot().id))
                .expect("canonical ID"),
        );
    }
    Ok(response)
}
fn no_replay(headers: &HeaderMap) -> Result<(), AdmissionError> {
    if admission::idempotency_key(headers)?.is_some() {
        return Err(Failure::new(
            Kind::Invalid,
            "http.unsupported_idempotency",
            "account operation has no receipt",
        )
        .with_public("This operation does not support Idempotency-Key yet.")
        .into());
    }
    Ok(())
}
async fn name(context: &RequestContext, request: Request) -> Result<String, AdmissionError> {
    let (parts, body) = request.into_parts();
    let bytes = admission::read_json_body(context, &parts.headers, body).await?;
    let mut decoder = serde_json::Deserializer::from_slice(&bytes);
    let name = input::Name::deserialize(&mut decoder).map_err(admission::invalid_json_input)?;
    decoder.end().map_err(admission::invalid_json_input)?;
    Ok(name.0)
}
async fn dispatch(
    State(handlers): State<Handlers>,
    Extension(context): Extension<RequestContext>,
    request: Request,
) -> Response {
    let path = request.uri().path();
    let allow = if path == "/v1/accounts" {
        "GET, HEAD, POST"
    } else if path == "/v1/accounts/bootstrap" {
        "POST"
    } else {
        "GET, HEAD, PATCH"
    };
    let allowed = match request.method() {
        &Method::GET | &Method::HEAD => path != "/v1/accounts/bootstrap",
        &Method::POST => ["/v1/accounts", "/v1/accounts/bootstrap"].contains(&path),
        &Method::PATCH => path != "/v1/accounts" && path != "/v1/accounts/bootstrap",
        _ => false,
    };
    if !allowed {
        return problem::method_not_allowed(&context.request_id, allow);
    }
    match httpx::deadline(&context, async {
        Ok(handle(&handlers, &context, request).await)
    })
    .await
    {
        Ok(Ok(response)) => response,
        Ok(Err(error)) => error.response(&context),
        Err(error) => problem::response(&error, &context.request_id),
    }
}
async fn handle(
    h: &Handlers,
    context: &RequestContext,
    request: Request,
) -> Result<Response, AdmissionError> {
    let scope = HttpScope(context.clone());
    let actor = h
        .principals
        .resolve(&scope, httpx::auth::bearer(request.headers())?)
        .await?;
    let path = request.uri().path();
    if path == "/v1/accounts"
        && (request.method() == Method::GET || request.method() == Method::HEAD)
    {
        let (limit, after) = input::page(request.uri().query().unwrap_or_default(), &actor.id)?;
        let page = h
            .list
            .handle(&scope, &actor.id, after.as_ref(), limit)
            .await?;
        let next = page
            .next
            .as_ref()
            .map(|id| input::cursor(&actor.id, id))
            .transpose()?;
        #[derive(Serialize)]
        struct Page<'a> {
            accounts: Vec<ResponseAccount<'a>>,
            next_cursor: Option<String>,
        }
        return Ok(admission::json_response(
            context,
            StatusCode::OK,
            &Page {
                accounts: page.accounts.iter().map(project).collect(),
                next_cursor: next,
            },
        )?);
    }
    if path == "/v1/accounts" || path == "/v1/accounts/bootstrap" {
        let bootstrap = path.ends_with("/bootstrap");
        if bootstrap && request.uri().query().is_some_and(|q| !q.is_empty()) {
            return Err(app::invalid_page().into());
        }
        no_replay(request.headers())?;
        let name = name(context, request).await?;
        if bootstrap {
            let result = h.bootstrap.handle(&scope, &actor.id, &name).await?;
            return Ok(account_response(
                context,
                &result.account,
                if result.created {
                    StatusCode::CREATED
                } else {
                    StatusCode::OK
                },
                true,
            )?);
        }
        let account = h.create.handle(&scope, &actor.id, &name).await?;
        return Ok(account_response(
            context,
            &account,
            StatusCode::CREATED,
            true,
        )?);
    }
    let path_id = input::component(
        path.strip_prefix("/v1/accounts/").unwrap_or_default(),
        false,
    )
    .map_err(|_| kernel::id::invalid())?;
    let id = Id::parse(&path_id)?;
    if request.method() == Method::PATCH {
        no_replay(request.headers())?;
        let expected = admission::required_version(request.headers())?.value();
        let name = name(context, request).await?;
        let account = h
            .rename
            .handle(&scope, &actor.id, &id, &name, expected)
            .await?;
        return Ok(account_response(context, &account, StatusCode::OK, false)?);
    }
    let account = h.get.handle(&scope, &actor.id, &id).await?;
    Ok(account_response(context, &account, StatusCode::OK, false)?)
}
