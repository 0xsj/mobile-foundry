use axum::{
    body::Body,
    http::{HeaderValue, StatusCode},
    response::Response,
};
use kernel::{Failure, Kind};
use serde::Serialize;
use std::collections::BTreeMap;

#[derive(Clone, Debug, Serialize)]
pub struct Problem {
    pub r#type: String,
    pub title: String,
    pub status: u16,
    pub detail: String,
    pub kind: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    #[serde(skip_serializing_if = "BTreeMap::is_empty")]
    pub fields: BTreeMap<String, String>,
    #[serde(skip_serializing_if = "str::is_empty")]
    pub request_id: String,
}

pub fn status(kind: Kind) -> StatusCode {
    match kind {
        Kind::Invalid => StatusCode::BAD_REQUEST,
        Kind::Unauthenticated => StatusCode::UNAUTHORIZED,
        Kind::Forbidden => StatusCode::FORBIDDEN,
        Kind::NotFound => StatusCode::NOT_FOUND,
        Kind::Conflict => StatusCode::CONFLICT,
        Kind::RateLimited => StatusCode::TOO_MANY_REQUESTS,
        Kind::Unavailable => StatusCode::SERVICE_UNAVAILABLE,
        Kind::Timeout => StatusCode::GATEWAY_TIMEOUT,
        Kind::Canceled => StatusCode::REQUEST_TIMEOUT,
        Kind::Internal => StatusCode::INTERNAL_SERVER_ERROR,
    }
}

pub fn response(error: &Failure, request_id: &str) -> Response {
    project(error, request_id, status(error.kind()))
}

// Owned admission override; application callers use the kind mapping above.
pub(crate) fn project(error: &Failure, request_id: &str, status: StatusCode) -> Response {
    let public = error.public();
    let view = Problem {
        r#type: format!("urn:problem:{}", public.kind.as_str()),
        title: status.canonical_reason().unwrap_or("Unknown Status").into(),
        status: status.as_u16(),
        detail: public.message,
        kind: public.kind.as_str().into(),
        code: public.code,
        fields: public.fields,
        request_id: request_id.into(),
    };
    let mut bytes = serde_json::to_vec(&view).expect("problem contains JSON-safe values");
    bytes.push(b'\n');
    let length = bytes.len();
    let mut response = Response::new(Body::from(bytes));
    *response.status_mut() = status;
    response.headers_mut().insert(
        "content-type",
        HeaderValue::from_static("application/problem+json; charset=utf-8"),
    );
    response
        .headers_mut()
        .insert("content-length", HeaderValue::from(length));
    if status == StatusCode::UNAUTHORIZED {
        response.headers_mut().insert(
            "www-authenticate",
            HeaderValue::from_static("Bearer realm=\"api\""),
        );
    }
    response.extensions_mut().insert(view);
    response
}

pub fn method_not_allowed(request_id: &str, allow: &'static str) -> Response {
    let mut response = project(
        &Failure::new(
            Kind::Invalid,
            "http.method_not_allowed",
            "unsupported method",
        )
        .with_public("This method is not supported for this resource."),
        request_id,
        StatusCode::METHOD_NOT_ALLOWED,
    );
    response
        .headers_mut()
        .insert("allow", HeaderValue::from_static(allow));
    response
}
