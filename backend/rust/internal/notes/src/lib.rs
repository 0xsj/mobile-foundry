//! Account-scoped Notes owns records and command receipts, independently of Account.
pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;
use std::sync::Arc;
pub struct Module {
    handlers: transport::http::v1::Handlers,
}
impl Module {
    pub fn memory(
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
        ids: Arc<dyn kernel::id::Generator>,
    ) -> Self {
        Self::with_store(
            Arc::new(infra::memory::MemoryStore::default()),
            access,
            principals,
            ids,
        )
    }
    pub fn postgres(
        pool: sqlx::PgPool,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
        ids: Arc<dyn kernel::id::Generator>,
    ) -> Self {
        Self::with_store(
            Arc::new(infra::postgres::PostgresStore::new(pool)),
            access,
            principals,
            ids,
        )
    }
    fn with_store(
        store: Arc<dyn app::ports::Store>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
        ids: Arc<dyn kernel::id::Generator>,
    ) -> Self {
        Self {
            handlers: transport::http::v1::Handlers::new(
                app::Dependencies {
                    store,
                    access,
                    ids,
                    changes: None,
                },
                principals,
            ),
        }
    }
    pub fn routes(&self) -> axum::Router {
        self.handlers.routes()
    }
    pub fn with_changes(mut self, changes: Arc<dyn app::ports::Changes>) -> Self {
        self.handlers = self.handlers.with_changes(changes);
        self
    }
    pub fn with_local_changes(self, hub: Arc<broadcast::Hub>) -> Self {
        self.with_changes(Arc::new(infra::broadcast::Changes(hub)))
    }
}
