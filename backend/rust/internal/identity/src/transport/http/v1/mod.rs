mod input;
pub mod recovery;
use crate::{
    app::{
        Grant,
        command::{Dependencies, Register, Renew, SignIn, SignOut},
        ports::{AttemptGate, Metadata, Scope},
        query::Me,
    },
    domain::{self, Principal, Secret},
    transport::time,
};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{Method, StatusCode},
    response::Response,
    routing::any,
};
use httpx::{
    PeerAddress, RequestContext,
    admission::{self, json_response},
    problem,
};
use kernel::Failure;
use serde::Serialize;
use std::sync::Arc;

#[derive(Clone)]
pub struct Handlers {
    register: Register,
    sign_in: SignIn,
    renew: Renew,
    sign_out: SignOut,
    me: Me,
    gate: Arc<dyn AttemptGate>,
}
impl Handlers {
    pub fn new(dependencies: Dependencies, gate: Arc<dyn AttemptGate>) -> Self {
        Self {
            register: Register(dependencies.clone()),
            sign_in: SignIn(dependencies.clone()),
            renew: Renew(dependencies.clone()),
            sign_out: SignOut(dependencies.clone()),
            me: Me(dependencies),
            gate,
        }
    }
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/identity/register", any(dispatch))
            .route("/v1/identity/sign-in", any(dispatch))
            .route("/v1/identity/renew", any(dispatch))
            .route("/v1/identity/sign-out", any(dispatch))
            .route("/v1/identity/me", any(dispatch))
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
struct Profile<'a> {
    id: &'a str,
    login: &'a str,
    display_name: &'a str,
}
fn profile(principal: &Principal) -> Profile<'_> {
    Profile {
        id: principal.id().as_str(),
        login: principal.login(),
        display_name: principal.display_name(),
    }
}
fn grant(context: &RequestContext, grant: Grant) -> Result<Response, Failure> {
    #[derive(Serialize)]
    struct WireGrant<'a> {
        authority_id: &'static str,
        principal: Profile<'a>,
        session_id: &'a str,
        session_version: u64,
        access_token: &'a str,
        refresh_token: &'a str,
        access_expires_at: String,
        refresh_expires_at: String,
    }
    let s = grant.session.state();
    json_response(
        context,
        StatusCode::OK,
        &WireGrant {
            authority_id: "go.identity.v1",
            principal: profile(&grant.principal),
            session_id: s.id.as_str(),
            session_version: s.generation,
            access_token: grant.access.expose(),
            refresh_token: grant.refresh.expose(),
            access_expires_at: time(s.access_expires_at),
            refresh_expires_at: time(s.expires_at),
        },
    )
}

async fn dispatch(
    State(handlers): State<Handlers>,
    Extension(context): Extension<RequestContext>,
    request: Request,
) -> Response {
    let me = request.uri().path() == "/v1/identity/me";
    if (me && request.method() != Method::GET && request.method() != Method::HEAD)
        || (!me && request.method() != Method::POST)
    {
        return problem::method_not_allowed(
            &context.request_id,
            if me { "GET, HEAD" } else { "POST" },
        );
    }
    let refusal = |error: Failure| problem::response(&error, &context.request_id);
    if request.uri().query().is_some_and(|q| !q.is_empty()) {
        return refusal(domain::invalid_input());
    }
    match admission::idempotency_key(request.headers()) {
        Err(error) => return error.response(&context),
        Ok(Some(_)) => {
            return refusal(
                Failure::new(
                    kernel::Kind::Invalid,
                    "http.unsupported_idempotency",
                    "operation has no receipt",
                )
                .with_public("This operation does not support Idempotency-Key yet."),
            );
        }
        Ok(None) => {}
    }
    let scope = HttpScope(context.clone());
    if me {
        let mut values = request.headers().get_all("authorization").iter();
        let token = match (values.next(), values.next()) {
            (Some(value), None) => value
                .to_str()
                .ok()
                .and_then(|value| value.split_once(' '))
                .filter(|(scheme, token)| {
                    scheme.eq_ignore_ascii_case("Bearer")
                        && !token.is_empty()
                        && !token.contains([' ', '\t', '\r', '\n'])
                })
                .map(|(_, token)| Secret::new(token.into())),
            _ => None,
        };
        let Some(token) = token else {
            return refusal(
                Failure::new(
                    kernel::Kind::Unauthenticated,
                    "principal.authentication_required",
                    "principal required",
                )
                .with_public("Sign in to continue."),
            );
        };
        return match httpx::deadline(&context, handlers.me.handle(&scope, token)).await {
            Ok(principal) => json_response(&context, StatusCode::OK, &profile(&principal))
                .unwrap_or_else(refusal),
            Err(error) => refusal(error),
        };
    }
    let Some(peer) = request.extensions().get::<PeerAddress>() else {
        return refusal(domain::invalid_state());
    };
    let (endpoint, mode) = match request.uri().path() {
        "/v1/identity/register" => ("register", input::Mode::Register),
        "/v1/identity/sign-in" => ("sign-in", input::Mode::SignIn),
        "/v1/identity/renew" => ("renew", input::Mode::Refresh),
        _ => ("sign-out", input::Mode::Refresh),
    };
    if let Err(error) = handlers.gate.allow(peer.0, endpoint) {
        return refusal(error);
    }
    let (parts, body) = request.into_parts();
    let bytes = match admission::read_json_body(&context, &parts.headers, body).await {
        Ok(bytes) => bytes,
        Err(error) => return error.response(&context),
    };
    let input = match input::decode(&bytes, mode) {
        Ok(input) => input,
        Err(error) => return admission::invalid_json_input(error).response(&context),
    };
    let result = match endpoint {
        "register" => match httpx::deadline(
            &context,
            handlers
                .register
                .handle(&scope, &input.login, &input.name, input.password),
        )
        .await
        {
            Ok(principal) => json_response(&context, StatusCode::CREATED, &profile(&principal)),
            Err(error) => Err(error),
        },
        "sign-in" => match httpx::deadline(
            &context,
            handlers
                .sign_in
                .handle(&scope, &input.login, input.password),
        )
        .await
        {
            Ok(value) => grant(&context, value),
            Err(error) => Err(error),
        },
        "renew" => {
            match httpx::deadline(&context, handlers.renew.handle(&scope, input.refresh)).await {
                Ok(value) => grant(&context, value),
                Err(error) => Err(error),
            }
        }
        _ => match httpx::deadline(&context, handlers.sign_out.handle(&scope, input.refresh)).await
        {
            Ok(()) => Ok(StatusCode::NO_CONTENT.into_response()),
            Err(error) => Err(error),
        },
    };
    result.unwrap_or_else(refusal)
}
use axum::response::IntoResponse;
