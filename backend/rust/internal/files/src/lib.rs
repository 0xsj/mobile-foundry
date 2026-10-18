//! Account-scoped metadata and immutable object bytes behind independent ports.
pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;
use kernel::{Failure, clock::Clock, id::Generator};
use std::sync::Arc;
pub struct Module {
    dependencies: app::Dependencies,
    outbox: Arc<dyn events::dispatcher::Outbox>,
}
impl Module {
    pub fn memory(
        access: Arc<dyn app::ports::AccountAccess>,
        objects: Arc<dyn app::ports::Objects>,
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
    ) -> Self {
        Self::with_store(
            Arc::new(infra::memory::MemoryStore::new(ids.clone())),
            access,
            objects,
            clock,
            ids,
        )
    }
    pub fn postgres(
        pool: sqlx::PgPool,
        access: Arc<dyn app::ports::AccountAccess>,
        objects: Arc<dyn app::ports::Objects>,
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
    ) -> Result<Self, Failure> {
        Ok(Self::with_store(
            Arc::new(infra::postgres::PostgresStore::new(pool, ids.clone())?),
            access,
            objects,
            clock,
            ids,
        ))
    }
    fn with_store<T: app::ports::Store + events::dispatcher::Outbox + 'static>(
        store: Arc<T>,
        access: Arc<dyn app::ports::AccountAccess>,
        objects: Arc<dyn app::ports::Objects>,
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
    ) -> Self {
        Self {
            dependencies: app::Dependencies {
                store: store.clone(),
                access,
                objects,
                clock,
                ids,
            },
            outbox: store,
        }
    }
    pub fn routes(&self, principals: Arc<dyn principal::Resolver>) -> axum::Router {
        transport::http::v1::Handlers::new(self.dependencies.clone(), principals).routes()
    }
    pub fn outbox(&self) -> Arc<dyn events::dispatcher::Outbox> {
        self.outbox.clone()
    }
}
