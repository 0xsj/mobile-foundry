use crate::app::query::CheckReady;
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{Method, StatusCode},
    response::Response,
};
use httpx::{RequestContext, admission::json_response, problem};
use kernel::{Failure, Kind};

pub fn routes(query: CheckReady) -> Router {
    Router::new().fallback(dispatch).with_state(query)
}

async fn dispatch(
    State(query): State<CheckReady>,
    Extension(context): Extension<RequestContext>,
    request: Request,
) -> Response {
    let health = matches!(request.uri().path(), "/v1/health/live" | "/v1/health/ready");
    let method = request.method();
    if health && method != Method::GET && method != Method::HEAD {
        return problem::method_not_allowed(&context.request_id, "GET, HEAD");
    }
    if !health {
        if ![
            Method::GET,
            Method::HEAD,
            Method::POST,
            Method::PATCH,
            Method::PUT,
            Method::DELETE,
        ]
        .contains(method)
        {
            return problem::method_not_allowed(
                &context.request_id,
                "GET, HEAD, POST, PATCH, PUT, DELETE",
            );
        }
        return problem::response(
            &Failure::new(Kind::NotFound, "http.route_not_found", "no matching route")
                .with_public("The requested resource was not found."),
            &context.request_id,
        );
    }
    let result = if request.uri().path() == "/v1/health/ready" {
        // Cooperative query work is dropped on deadline. No writes exist in this slice.
        httpx::deadline(&context, query.handle()).await
    } else {
        context.check()
    };
    match result {
        Err(error) => problem::response(&error, &context.request_id),
        Ok(()) => {
            #[derive(serde::Serialize)]
            struct Status {
                status: &'static str,
            }
            json_response(&context, StatusCode::OK, &Status { status: "ok" })
                .unwrap_or_else(|error| problem::response(&error, &context.request_id))
        }
    }
}
