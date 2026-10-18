//! Native bearer WebSockets expose lossy account hints, never authoritative data.
pub mod app;
pub mod infra;
pub mod transport;
use std::sync::Arc;
pub use transport::websocket::v1::Health;
pub struct Module {
    handlers: transport::websocket::v1::Handlers,
}
impl Module {
    pub fn new(subscribe: app::query::Subscribe, principals: Arc<dyn principal::Resolver>) -> Self {
        Self {
            handlers: transport::websocket::v1::Handlers::new(subscribe, principals),
        }
    }
    pub fn local(
        hub: Arc<broadcast::Hub>,
        registry: &Arc<capability::Registry>,
        principals: Arc<dyn principal::Resolver>,
    ) -> Result<Self, kernel::Failure> {
        registry.require(infra::capabilities::ACCOUNT_ACCESS, "realtime")?;
        Ok(Self::new(
            app::query::Subscribe {
                access: Arc::new(infra::capabilities::Client(Arc::new(
                    registry.client("realtime")?,
                ))),
                feed: Arc::new(infra::local::Local(hub)),
            },
            principals,
        ))
    }
    pub fn routes(&self) -> axum::Router {
        self.handlers.routes()
    }
    pub fn stop(&self) {
        self.handlers.stop();
    }
    pub fn health(&self) -> Health {
        self.handlers.health()
    }
    pub async fn close(&self) -> Result<(), kernel::Failure> {
        self.handlers.close().await
    }
}
impl Drop for Module {
    fn drop(&mut self) {
        self.stop();
    }
}
