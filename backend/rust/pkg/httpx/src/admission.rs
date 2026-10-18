//! Bounded wire admission. Parsing a version/key never establishes CAS or replay.
use crate::{RequestContext, deadline, problem, valid_token};
use axum::{
    body::{Body, Bytes, to_bytes},
    http::{HeaderMap, HeaderValue, StatusCode},
    response::Response,
};
use kernel::{Failure, Kind};
use serde::{Serialize, de::DeserializeOwned};
use std::{error::Error, fmt};

pub const MAX_JSON_BYTES: usize = 16 * 1024;
pub const MAX_VERSION: u64 = 9_007_199_254_740_991;

/// Transport-owned status override; arbitrary domain codes cannot select an HTTP status.
#[derive(Debug)]
pub struct AdmissionError {
    failure: Failure,
    status: StatusCode,
}
impl AdmissionError {
    fn new(status: StatusCode, code: &str, detail: &str) -> Self {
        Self {
            status,
            failure: Failure::new(Kind::Invalid, code, "HTTP admission refused")
                .with_public(detail),
        }
    }
    fn with_cause(mut self, cause: impl Error + Send + Sync + 'static) -> Self {
        self.failure = self.failure.with_cause(cause);
        self
    }
    pub fn failure(&self) -> &Failure {
        &self.failure
    }
    pub fn status(&self) -> StatusCode {
        self.status
    }
    pub fn response(&self, context: &RequestContext) -> Response {
        problem::project(&self.failure, &context.request_id, self.status)
    }
}
impl fmt::Display for AdmissionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.failure.fmt(f)
    }
}
impl Error for AdmissionError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        Some(&self.failure)
    }
}
impl From<Failure> for AdmissionError {
    fn from(failure: Failure) -> Self {
        Self {
            status: problem::status(failure.kind()),
            failure,
        }
    }
}

fn invalid_json() -> AdmissionError {
    AdmissionError::new(
        StatusCode::BAD_REQUEST,
        "http.invalid_json",
        "Send one valid JSON object with the documented fields.",
    )
}
fn media_type() -> AdmissionError {
    AdmissionError::new(
        StatusCode::UNSUPPORTED_MEDIA_TYPE,
        "http.invalid_content_type",
        "Use Content-Type application/json.",
    )
}
pub fn too_large() -> AdmissionError {
    AdmissionError::new(
        StatusCode::PAYLOAD_TOO_LARGE,
        "http.body_too_large",
        "The request body is too large.",
    )
}

/// Admit one bounded object and reject ignored fields, including nested DTO fields.
/// Object members normalize last-key-wins before typed decoding. Endpoint DTOs
/// must separately establish Go-compatible null, casing, numeric and duplicate-type policy.
pub async fn decode_json<T: DeserializeOwned>(
    context: &RequestContext,
    headers: &HeaderMap,
    body: Body,
) -> Result<T, AdmissionError> {
    let bytes = read_json_body(context, headers, body).await?;
    let value: serde_json::Value =
        serde_json::from_slice(&bytes).map_err(|cause| invalid_json().with_cause(cause))?;
    if !value.is_object() {
        return Err(invalid_json());
    }
    let mut unknown = false;
    let admitted = serde_ignored::deserialize(value, |_| unknown = true)
        .map_err(|cause| invalid_json().with_cause(cause))?;
    if unknown {
        return Err(invalid_json());
    }
    context.check()?;
    Ok(admitted)
}

/// Endpoint-owned DTO decoders may retain duplicate member order without bypassing admission.
pub async fn read_json_body(
    context: &RequestContext,
    headers: &HeaderMap,
    body: Body,
) -> Result<Bytes, AdmissionError> {
    let content_type = single(headers, "content-type")
        .map_err(|_| media_type())?
        .ok_or_else(media_type)?;
    let parsed: mime::Mime = content_type.parse().map_err(|_| media_type())?;
    if parsed.essence_str() != "application/json" {
        return Err(media_type());
    }
    let mut parameters = std::collections::BTreeMap::new();
    for (name, value) in parsed.params() {
        if parameters
            .insert(name.to_string(), value.to_string())
            .is_some_and(|old| old != value.as_str())
        {
            return Err(media_type());
        }
    }
    let bytes = deadline(context, async { Ok(to_bytes(body, MAX_JSON_BYTES).await) })
        .await?
        .map_err(|cause| {
            // Axum preserves the limit error as a source; transport read failure is JSON admission failure.
            if cause
                .source()
                .is_some_and(|source| source.is::<http_body_util::LengthLimitError>())
            {
                too_large()
            } else {
                invalid_json().with_cause(cause)
            }
        })?;
    context.check()?;
    Ok(bytes)
}

pub fn invalid_json_input(cause: serde_json::Error) -> AdmissionError {
    invalid_json().with_cause(cause)
}

fn single<'a>(headers: &'a HeaderMap, name: &str) -> Result<Option<&'a str>, ()> {
    let mut values = headers.get_all(name).iter();
    match (values.next(), values.next()) {
        (None, _) => Ok(None),
        (Some(value), None) => value.to_str().map(Some).map_err(|_| ()),
        _ => Err(()),
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Version(u64);
impl Version {
    pub fn new(value: u64) -> Result<Self, Failure> {
        if (1..=MAX_VERSION).contains(&value) {
            Ok(Self(value))
        } else {
            Err(Failure::new(
                Kind::Invalid,
                "http.invalid_version",
                "version is outside the wire-safe range",
            ))
        }
    }
    pub fn value(self) -> u64 {
        self.0
    }
    pub fn etag(self) -> HeaderValue {
        HeaderValue::from_str(&format!("\"{}\"", self.0)).expect("quoted safe integer")
    }
}

pub fn required_version(headers: &HeaderMap) -> Result<Version, AdmissionError> {
    let invalid = || {
        AdmissionError::new(
            StatusCode::BAD_REQUEST,
            "http.invalid_precondition",
            "Send one strong ETag returned by this API in If-Match.",
        )
    };
    let value = single(headers, "if-match")
        .map_err(|_| invalid())?
        .ok_or_else(|| {
            AdmissionError::new(
                StatusCode::PRECONDITION_REQUIRED,
                "http.precondition_required",
                "Read the current resource and send its ETag in If-Match.",
            )
        })?;
    let digits = value
        .strip_prefix('"')
        .and_then(|v| v.strip_suffix('"'))
        .ok_or_else(invalid)?;
    let number = digits.parse::<u64>().map_err(|_| invalid())?;
    if number.to_string() != digits {
        return Err(invalid());
    }
    Version::new(number).map_err(|_| invalid())
}

pub fn idempotency_key(headers: &HeaderMap) -> Result<Option<String>, AdmissionError> {
    let invalid = || {
        AdmissionError::new(
            StatusCode::BAD_REQUEST,
            "http.invalid_idempotency_key",
            "Send one Idempotency-Key of 1 to 128 letters, digits, or ._:- characters.",
        )
    };
    let value = single(headers, "idempotency-key").map_err(|_| invalid())?;
    match value {
        None => Ok(None),
        Some(value) if valid_token(value) => Ok(Some(value.into())),
        _ => Err(invalid()),
    }
}

/// Fully encode before returning a response; the outer boundary owns HEAD suppression.
pub fn json_response<T: Serialize>(
    context: &RequestContext,
    status: StatusCode,
    value: &T,
) -> Result<Response, Failure> {
    context.check()?;
    if status.as_u16() < 200 || status.as_u16() > 599 || matches!(status.as_u16(), 204 | 205 | 304)
    {
        return Err(Failure::new(
            Kind::Internal,
            "http.invalid_status",
            "JSON response needs a body-capable status",
        ));
    }
    let mut bytes = serde_json::to_vec(value).map_err(|cause| {
        Failure::new(Kind::Internal, "http.encode_failed", "JSON encoding failed").with_cause(cause)
    })?;
    bytes.push(b'\n');
    let length = bytes.len();
    let mut response = Response::new(Body::from(bytes));
    *response.status_mut() = status;
    response.headers_mut().insert(
        "content-type",
        HeaderValue::from_static("application/json; charset=utf-8"),
    );
    response
        .headers_mut()
        .insert("content-length", HeaderValue::from(length));
    Ok(response)
}
