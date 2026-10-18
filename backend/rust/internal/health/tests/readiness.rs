use axum::{
    body::{Body, to_bytes},
    extract::Request,
};
use health::app::query::{CheckReady, ProbeFuture, Readiness};
use httpx::logger::Logger;
use kernel::{Failure, Kind};
use std::sync::Arc;
use tower::ServiceExt;

struct Unavailable;
impl Readiness for Unavailable {
    fn check(&self) -> ProbeFuture<'_> {
        Box::pin(async {
            Err(Failure::new(
                Kind::Internal,
                "dependency.secret",
                "SECRET dependency",
            ))
        })
    }
}

#[tokio::test]
async fn failed_probe_is_unavailable_but_liveness_survives() {
    let query = CheckReady::new(Arc::new(Unavailable));
    let error = query.handle().await.unwrap_err();
    assert!(std::error::Error::source(&error).is_some());
    assert_eq!(error.public().code.as_deref(), Some("health.not_ready"));
    let router = health::build(
        Arc::new(Unavailable),
        Logger::new(
            std::io::sink(),
            runtime_config::LogFormat::Json,
            runtime_config::LogLevel::Info,
        ),
    );
    for (path, expected) in [("ready", 503), ("live", 200)] {
        let response = router
            .clone()
            .oneshot(
                Request::builder()
                    .uri(format!("/v1/health/{path}"))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), expected);
        let body = to_bytes(response.into_body(), 16384).await.unwrap();
        assert!(!String::from_utf8_lossy(&body).contains("SECRET"));
        if expected == 503 {
            let value: serde_json::Value = serde_json::from_slice(&body).unwrap();
            assert_eq!(value["code"], "health.not_ready");
            assert_eq!(value["detail"], "The service is not ready.");
        }
    }
}
