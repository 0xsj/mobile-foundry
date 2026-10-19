//! Bounded method/status metrics. Never accept arbitrary labels from request values.
use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
};
use std::{
    fmt::Write,
    sync::{Arc, Mutex},
    time::Instant,
};
const METHODS: [&str; 8] = [
    "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "OTHER",
];
#[derive(Default)]
struct Snapshot {
    counts: [[u64; 6]; 8],
    seconds: f64,
    requests: u64,
    active: u64,
}
#[derive(Default)]
pub struct Recorder(Mutex<Snapshot>);
pub struct Observation {
    recorder: Arc<Recorder>,
    method: usize,
    started: Instant,
    status: u16,
}
impl Recorder {
    pub fn begin(self: &Arc<Self>, method: &str) -> Observation {
        self.0.lock().expect("metrics state").active += 1;
        Observation {
            recorder: self.clone(),
            method: METHODS.iter().position(|m| *m == method).unwrap_or(7),
            started: Instant::now(),
            status: 500,
        }
    }
    pub fn text(&self) -> String {
        let s = self.0.lock().expect("metrics state");
        let mut text = String::from("# TYPE api_http_requests_total counter\n");
        for (method, counts) in METHODS.iter().zip(s.counts) {
            for (class, count) in counts.iter().enumerate().filter(|(_, n)| **n > 0) {
                writeln!(
                    &mut text,
                    "api_http_requests_total{{method=\"{method}\",status=\"{class}xx\"}} {count}"
                )
                .expect("string output");
            }
        }
        write!(&mut text,"# TYPE api_http_active gauge\napi_http_active {}\n# TYPE api_http_duration_seconds summary\napi_http_duration_seconds_sum {}\napi_http_duration_seconds_count {}\n",s.active,s.seconds,s.requests).expect("string output");
        text
    }
}
impl Observation {
    pub fn status(&mut self, value: u16) {
        self.status = value;
    }
}
impl Drop for Observation {
    fn drop(&mut self) {
        // Do not introduce a second panic during an unwinding request.
        if let Ok(mut s) = self.recorder.0.lock() {
            s.active -= 1;
            s.requests += 1;
            s.seconds += self.started.elapsed().as_secs_f64();
            s.counts[self.method][usize::from(self.status / 100).min(5)] += 1;
        }
    }
}
pub async fn observe(
    State(recorder): State<Arc<Recorder>>,
    request: Request,
    next: Next,
) -> Response {
    let mut observation = recorder.begin(request.method().as_str());
    let response = next.run(request).await;
    observation.status(response.status().as_u16());
    response
}
pub fn routes(recorder: Arc<Recorder>) -> axum::Router {
    axum::Router::new()
        .fallback(
            |State(r): State<Arc<Recorder>>, request: Request| async move {
                if request.method() != axum::http::Method::GET || request.uri().path() != "/metrics"
                {
                    return StatusCode::NOT_FOUND.into_response();
                }
                (
                    [
                        ("content-type", "text/plain; version=0.0.4; charset=utf-8"),
                        ("cache-control", "no-store"),
                    ],
                    r.text(),
                )
                    .into_response()
            },
        )
        .with_state(recorder)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tower::ServiceExt;
    #[test]
    fn bounded_labels_active_ownership_and_unconfirmed_drop() {
        let r = Arc::new(Recorder::default());
        let mut active = r.begin("POST");
        assert!(r.text().contains("api_http_active 1\n"));
        active.status(201);
        drop(active);
        for _ in 0..40 {
            drop(r.begin("PRIVATE-custom-method"));
        }
        let text = r.text();
        assert!(!text.contains("PRIVATE"));
        assert!(text.contains("method=\"POST\",status=\"2xx\"} 1"));
        assert!(text.contains("method=\"OTHER\",status=\"5xx\"} 40"));
        assert!(text.contains("api_http_active 0\n"));
        assert!(text.contains("api_http_duration_seconds_count 41\n"));
    }
    #[tokio::test]
    async fn private_metrics_routes_have_no_public_labels() {
        let r = Arc::new(Recorder::default());
        let app = axum::Router::new()
            .fallback(|| async { StatusCode::CREATED })
            .layer(axum::middleware::from_fn_with_state(r.clone(), observe));
        let request = Request::builder()
            .method("POST")
            .uri("/PRIVATE-account?token=PRIVATE-secret")
            .header("authorization", "Bearer PRIVATE-token")
            .body(axum::body::Body::empty())
            .unwrap();
        assert_eq!(app.oneshot(request).await.unwrap().status(), 201);
        let text = r.text();
        assert!(!text.contains("PRIVATE"));
        assert!(text.contains("method=\"POST\",status=\"2xx\"} 1"));
        for (method, path, status) in [
            ("GET", "/metrics", 200),
            ("GET", "/unknown", 404),
            ("POST", "/metrics", 404),
            ("HEAD", "/metrics", 404),
        ] {
            let response = routes(r.clone())
                .oneshot(
                    Request::builder()
                        .method(method)
                        .uri(path)
                        .body(axum::body::Body::empty())
                        .unwrap(),
                )
                .await
                .unwrap();
            assert_eq!(response.status().as_u16(), status);
        }
    }
}
