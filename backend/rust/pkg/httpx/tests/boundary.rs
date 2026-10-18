use axum::{
    Router,
    body::{Body, to_bytes},
    extract::Request,
    http::StatusCode,
    middleware,
    routing::get,
};
use httpx::{logger::Logger, problem};
use kernel::{Failure, Kind};
use runtime_config::{LogFormat, LogLevel};
use serde::Deserialize;
use std::{
    collections::BTreeMap,
    io::{self, Write},
    sync::{Arc, Mutex},
};
use tower::ServiceExt;

#[derive(Deserialize)]
struct Case {
    name: String,
    kind: Kind,
    code: String,
    public: Option<String>,
    fields: BTreeMap<String, String>,
    expected: serde_json::Value,
}

#[tokio::test]
async fn shared_public_failure_corpus() {
    let cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-runtime/failures.json"
    ))
    .unwrap();
    for case in cases {
        let mut error = Failure::new(case.kind, case.code, "SECRET diagnostic")
            .with_fields(case.fields)
            .with_cause(io::Error::other("SECRET dependency"));
        if let Some(public) = case.public {
            error = error.with_public(public);
        }
        assert!(std::error::Error::source(&error).is_some());
        let response = problem::response(&error, "fixture-request");
        assert_eq!(
            response.status().as_u16(),
            case.expected["status"],
            "{}",
            case.name
        );
        let body = to_bytes(response.into_body(), 16384).await.unwrap();
        assert_eq!(
            serde_json::from_slice::<serde_json::Value>(&body).unwrap(),
            case.expected,
            "{}",
            case.name
        );
        assert!(!String::from_utf8_lossy(&body).contains("SECRET"));
        assert!(!format!("{error:?} {error}").contains("SECRET"));
    }
}

#[derive(Clone, Default)]
struct Capture(Arc<Mutex<Vec<u8>>>);
impl Write for Capture {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.0.lock().unwrap().extend_from_slice(bytes);
        Ok(bytes.len())
    }
    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

#[tokio::test]
async fn panic_is_projected_and_sensitive_input_stays_out_of_logs() {
    let capture = Capture::default();
    let logger = Logger::new(capture.clone(), LogFormat::Json, LogLevel::Debug);
    let router = Router::new()
        .route(
            "/private/SECRET-path",
            get(|| async {
                panic!("SECRET panic payload");
                #[allow(unreachable_code)]
                "unreachable"
            }),
        )
        .layer(middleware::from_fn_with_state(
            httpx::BoundaryState::from(logger),
            httpx::boundary,
        ));
    let response = router
        .oneshot(
            Request::builder()
                .uri("/private/SECRET-path?password=SECRET-query")
                .header("authorization", "Bearer SECRET-credential")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::INTERNAL_SERVER_ERROR);
    let bytes = to_bytes(response.into_body(), 16384).await.unwrap();
    assert!(!String::from_utf8_lossy(&bytes).contains("SECRET"));
    let logs = String::from_utf8(capture.0.lock().unwrap().clone()).unwrap();
    assert!(logs.contains("request panic"));
    assert!(logs.contains("request completed"));
    assert!(!logs.contains("SECRET"));
}

struct RefusesIds;
impl kernel::id::Generator for RefusesIds {
    fn new_id(&self) -> Result<kernel::id::Id, Failure> {
        Err(
            Failure::new(Kind::Internal, "private.entropy", "SECRET generator")
                .with_cause(io::Error::other("SECRET source")),
        )
    }
}
struct FixedId;
impl kernel::id::Generator for FixedId {
    fn new_id(&self) -> Result<kernel::id::Id, Failure> {
        kernel::id::Id::parse("11111111-1111-4111-8111-111111111111")
    }
}

#[tokio::test]
async fn injected_ids_reach_metadata_and_fail_closed_before_handler() {
    use std::sync::atomic::{AtomicUsize, Ordering};
    let calls = Arc::new(AtomicUsize::new(0));
    let capture = Capture::default();
    for fail in [false, true] {
        let ids: Arc<dyn kernel::id::Generator> = if fail {
            Arc::new(RefusesIds)
        } else {
            Arc::new(FixedId)
        };
        let state = httpx::BoundaryState::new(
            Logger::new(capture.clone(), LogFormat::Json, LogLevel::Debug),
            ids,
        );
        let calls = calls.clone();
        let router = Router::new().route("/", get(move |axum::Extension(context): axum::Extension<httpx::RequestContext>| async move {
            calls.fetch_add(1, Ordering::Relaxed); context.request_id
        })).layer(middleware::from_fn_with_state(state, httpx::boundary));
        for method in ["GET", "HEAD"] {
            let response = router
                .clone()
                .oneshot(
                    Request::builder()
                        .method(method)
                        .uri("/")
                        .header("x-request-id", "caller-owned")
                        .body(Body::empty())
                        .unwrap(),
                )
                .await
                .unwrap();
            assert_eq!(response.status().as_u16(), if fail { 500 } else { 200 });
            assert_eq!(response.headers()["cache-control"], "no-store");
            assert_eq!(response.headers()["x-content-type-options"], "nosniff");
            if fail {
                assert!(response.headers().get("x-request-id").is_none());
            } else {
                assert_eq!(
                    response.headers()["x-request-id"],
                    "11111111-1111-4111-8111-111111111111"
                );
            }
            let body = to_bytes(response.into_body(), 16384).await.unwrap();
            if method == "HEAD" {
                assert!(body.is_empty());
            } else if fail {
                let value: serde_json::Value = serde_json::from_slice(&body).unwrap();
                assert!(value.get("request_id").is_none());
                assert!(value.get("code").is_none());
                assert_eq!(value["detail"], "An unexpected error occurred.");
            } else {
                assert_eq!(&body[..], b"11111111-1111-4111-8111-111111111111");
            }
        }
    }
    assert_eq!(calls.load(Ordering::Relaxed), 2);
    assert!(
        !String::from_utf8(capture.0.lock().unwrap().clone())
            .unwrap()
            .contains("SECRET")
    );
}
