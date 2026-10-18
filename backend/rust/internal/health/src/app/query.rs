use kernel::{Failure, Kind};
use std::{future::Future, pin::Pin, sync::Arc};

pub type ProbeFuture<'a> = Pin<Box<dyn Future<Output = Result<(), Failure>> + Send + 'a>>;

/// Consumer-owned dependency check, independent of HTTP and database frameworks.
pub trait Readiness: Send + Sync {
    fn check(&self) -> ProbeFuture<'_>;
}

#[derive(Clone)]
pub struct CheckReady {
    probe: Arc<dyn Readiness>,
}
impl CheckReady {
    pub fn new(probe: Arc<dyn Readiness>) -> Self {
        Self { probe }
    }
    pub async fn handle(&self) -> Result<(), Failure> {
        self.probe.check().await.map_err(|cause| {
            Failure::new(
                Kind::Unavailable,
                "health.not_ready",
                "readiness probe failed",
            )
            .with_public("The service is not ready.")
            .with_cause(cause)
        })
    }
}
