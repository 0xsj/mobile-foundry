//! Owned HTTP boundary: public problems, safe metadata/logs, and cooperative deadlines.
pub mod admission;
pub mod auth;
pub mod logger;
pub mod problem;
pub mod proxy;
pub mod query;

use axum::{
    body::Body,
    extract::{Request, State},
    http::{HeaderValue, Method},
    middleware::Next,
    response::Response,
};
use futures_util::FutureExt;
use kernel::{
    Failure, Kind,
    id::{Generator, RandomIds},
};
use logger::Logger;
use std::{panic::AssertUnwindSafe, sync::Arc, time::Duration};
use tokio::time::Instant;

/// Effective client address, initially accepted socket and optionally an admitted proxy value.
#[derive(Clone, Copy)]
pub struct PeerAddress(pub std::net::IpAddr);

#[derive(Clone, Debug)]
pub struct RequestContext {
    pub request_id: String,
    pub correlation_id: Option<String>,
    deadline: Instant,
}
impl RequestContext {
    pub fn new(request_id: String, correlation_id: Option<String>) -> Self {
        Self {
            request_id,
            correlation_id,
            deadline: Instant::now() + Duration::from_secs(15),
        }
    }
    pub fn check(&self) -> Result<(), Failure> {
        if Instant::now() >= self.deadline {
            Err(Failure::new(Kind::Timeout, "", "request deadline exceeded"))
        } else {
            Ok(())
        }
    }
    pub async fn expired(&self) {
        tokio::time::sleep_until(self.deadline).await;
    }
}
impl kernel::context::Scope for RequestContext {
    fn check(&self) -> Result<(), Failure> {
        RequestContext::check(self)
    }
    fn metadata(&self) -> kernel::context::Metadata {
        kernel::context::Metadata {
            request_id: self.request_id.clone(),
            correlation_id: self.correlation_id.clone(),
        }
    }
}

pub fn valid_token(value: &str) -> bool {
    (1..=128).contains(&value.len())
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._:-".contains(&b))
}

/// Cooperative async work stops at its owned request deadline; no implicit retry.
pub async fn deadline<T>(
    context: &RequestContext,
    future: impl std::future::Future<Output = Result<T, Failure>>,
) -> Result<T, Failure> {
    context.check()?;
    tokio::select! {
        biased;
        _ = context.expired() => Err(Failure::new(Kind::Timeout, "", "request deadline exceeded")),
        result = future => { context.check()?; result }
    }
}

#[derive(Clone)]
pub struct BoundaryState {
    log: Logger,
    ids: Arc<dyn Generator>,
    trusted_proxies: runtime_config::TrustedProxies,
}
impl BoundaryState {
    pub fn new(log: Logger, ids: Arc<dyn Generator>) -> Self {
        Self {
            log,
            ids,
            trusted_proxies: Default::default(),
        }
    }
    pub fn with_trusted_proxies(mut self, trusted: runtime_config::TrustedProxies) -> Self {
        self.trusted_proxies = trusted;
        self
    }
}
impl From<Logger> for BoundaryState {
    fn from(log: Logger) -> Self {
        Self::new(log, Arc::new(RandomIds))
    }
}

pub async fn boundary(
    State(state): State<BoundaryState>,
    mut request: Request,
    next: Next,
) -> Response {
    let log = &state.log;
    let started = Instant::now();
    let method = request.method().clone();
    let request_id = match state.ids.new_id() {
        Ok(id) => id.to_string(),
        Err(cause) => {
            let error = Failure::new(
                Kind::Internal,
                "http.request_id_failed",
                "request ID generation failed",
            )
            .with_cause(cause);
            log.failure("", "internal", None);
            let mut response = problem::response(&error, "");
            secure_headers(&mut response);
            if method == Method::HEAD {
                *response.body_mut() = Body::empty();
            }
            return response;
        }
    };
    let values: Vec<_> = request
        .headers()
        .get_all("x-correlation-id")
        .iter()
        .collect();
    let correlation = match values.as_slice() {
        [] => Ok(None),
        [value] => value
            .to_str()
            .ok()
            .filter(|s| valid_token(s))
            .map(|s| Some(s.to_owned()))
            .ok_or(()),
        _ => Err(()),
    };
    let mut response = match correlation.as_ref() {
        Err(()) => problem::response(
            &Failure::new(
                Kind::Invalid,
                "http.invalid_correlation_id",
                "invalid correlation header",
            )
            .with_public("Send one valid X-Correlation-ID of at most 128 characters."),
            &request_id,
        ),
        Ok(correlation_id) => {
            request.extensions_mut().insert(RequestContext::new(
                request_id.clone(),
                correlation_id.clone(),
            ));
            match proxy::admit(&mut request, &state.trusted_proxies) {
                Err(error) => problem::response(&error, &request_id),
                Ok(()) => match AssertUnwindSafe(next.run(request)).catch_unwind().await {
                    Ok(response) => response,
                    Err(_) => {
                        log.panic(&request_id);
                        problem::response(
                            &Failure::new(Kind::Internal, "http.panic", "request panic"),
                            &request_id,
                        )
                    }
                },
            }
        }
    };
    let headers = response.headers_mut();
    headers.insert(
        "x-request-id",
        HeaderValue::from_str(&request_id).expect("generated UUID is a header value"),
    );
    if let Ok(Some(correlation_id)) = &correlation {
        headers.insert(
            "x-correlation-id",
            HeaderValue::from_str(correlation_id).expect("admitted ASCII token"),
        );
    }
    if let Some(view) = response.extensions().get::<problem::Problem>() {
        log.failure(&request_id, &view.kind, view.code.as_deref());
    }
    if correlation.is_ok() {
        log.completed(
            &request_id,
            method.as_str(),
            response.status().as_u16(),
            started.elapsed().as_millis(),
        );
    }
    // Applies to every error too, including middleware admission failures.
    if method == Method::HEAD {
        *response.body_mut() = Body::empty();
    }
    secure_headers(&mut response);
    response
}

fn secure_headers(response: &mut Response) {
    response
        .headers_mut()
        .insert("cache-control", HeaderValue::from_static("no-store"));
    response.headers_mut().insert(
        "x-content-type-options",
        HeaderValue::from_static("nosniff"),
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test(start_paused = true)]
    async fn cooperative_deadline_is_observed_without_retry() {
        let context = RequestContext::new("test".into(), None);
        let error = deadline(&context, std::future::pending::<Result<(), Failure>>())
            .await
            .unwrap_err();
        assert_eq!(error.kind(), Kind::Timeout);
        assert!(context.check().is_err());
        let calls = std::sync::atomic::AtomicUsize::new(0);
        let result = deadline(&context, async {
            calls.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
            Ok(())
        })
        .await;
        assert!(result.is_err());
        assert_eq!(calls.load(std::sync::atomic::Ordering::Relaxed), 0);
    }
}
