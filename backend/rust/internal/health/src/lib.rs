pub mod app;
pub mod transport;

use app::query::{CheckReady, Readiness};
use axum::Router;
use httpx::logger::Logger;
use std::sync::Arc;

/// Module composition selects an application-owned probe and its HTTP adapter.
pub fn build(probe: Arc<dyn Readiness>, log: Logger) -> Router {
    build_with_boundary(probe, log.into())
}

pub fn build_with_boundary(probe: Arc<dyn Readiness>, boundary: httpx::BoundaryState) -> Router {
    routes(probe).layer(axum::middleware::from_fn_with_state(
        boundary,
        httpx::boundary,
    ))
}

pub fn routes(probe: Arc<dyn Readiness>) -> Router {
    transport::http::v1::routes(CheckReady::new(probe))
}
