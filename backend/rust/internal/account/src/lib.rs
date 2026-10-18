//! Account owns aggregates, personal mapping and atomically committed outbox records.
pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;
use std::sync::Arc;
pub struct Module {
    dependencies: app::Dependencies,
    outbox: Arc<dyn events::dispatcher::Outbox>,
}
impl Module {
    pub fn memory(
        clock: Arc<dyn kernel::clock::Clock>,
        ids: Arc<dyn kernel::id::Generator>,
    ) -> Self {
        let store = Arc::new(infra::memory::MemoryStore::new(ids.clone()));
        Self::with_store(clock, ids, store)
    }
    pub fn postgres(
        pool: sqlx::PgPool,
        clock: Arc<dyn kernel::clock::Clock>,
        ids: Arc<dyn kernel::id::Generator>,
    ) -> Result<Self, kernel::Failure> {
        let store = Arc::new(infra::postgres::PostgresStore::new(pool, ids.clone())?);
        Ok(Self::with_store(clock, ids, store))
    }
    fn with_store<
        T: app::ports::Store + app::ports::Directory + events::dispatcher::Outbox + 'static,
    >(
        clock: Arc<dyn kernel::clock::Clock>,
        ids: Arc<dyn kernel::id::Generator>,
        store: Arc<T>,
    ) -> Self {
        Self {
            dependencies: app::Dependencies {
                store: store.clone(),
                directory: store.clone(),
                clock,
                ids,
            },
            outbox: store,
        }
    }
    pub fn routes(&self, principals: Arc<dyn principal::Resolver>) -> axum::Router {
        transport::http::v1::Handlers::new(self.dependencies.clone(), principals).routes()
    }
    pub fn register(&self, registry: &mut capability::Builder) -> Result<(), kernel::Failure> {
        use transport::capability::v1;
        registry.register(
            v1::NAME,
            &["audit", "notes", "files", "notifications", "realtime"],
            Arc::new(v1::Provider(app::query::Access(
                self.dependencies.store.clone(),
            ))),
        )
    }
    pub fn outbox(&self) -> Arc<dyn events::dispatcher::Outbox> {
        self.outbox.clone()
    }
}
