//! Scoped in-app delivery and session-bound installations; no external push sender.
pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;
use std::sync::Arc;
pub struct Module {
    handlers: transport::http::v1::Handlers,
    delivery: app::command::DeliverUpload,
    installations: transport::http::v1::installations::Handlers,
    retirement: app::command::installations::RetireSession,
}
impl Module {
    pub fn memory(
        clock: Arc<dyn kernel::clock::Clock>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
    ) -> Self {
        Self::with_store(
            Arc::new(infra::memory::MemoryStore::default()),
            clock,
            access,
            principals,
        )
    }
    pub fn postgres(
        pool: sqlx::PgPool,
        clock: Arc<dyn kernel::clock::Clock>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
    ) -> Self {
        Self::with_store(
            Arc::new(infra::postgres::PostgresStore::new(pool)),
            clock,
            access,
            principals,
        )
    }
    fn with_store<T: app::ports::Store + app::installations::Installations + 'static>(
        store: Arc<T>,
        clock: Arc<dyn kernel::clock::Clock>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
    ) -> Self {
        let registry = app::installations::Registry {
            store: store.clone(),
            proofs: Arc::new(infra::security::Proofs),
            clock: clock.clone(),
        };
        Self {
            handlers: transport::http::v1::Handlers::new(
                app::Dependencies {
                    store: store.clone(),
                    access,
                    clock: clock.clone(),
                },
                principals.clone(),
            ),
            installations: transport::http::v1::installations::Handlers::new(registry, principals),
            retirement: app::command::installations::RetireSession(store.clone()),
            delivery: app::command::DeliverUpload { store, clock },
        }
    }
    pub fn routes(&self) -> axum::Router {
        self.handlers.routes().merge(self.installations.routes())
    }
    pub fn subscribe(&self, builder: &mut events::Builder) -> Result<(), kernel::Failure> {
        builder.register(infra::events::Receiver(self.delivery.clone()).subscription())?;
        builder.register(infra::events::identity::Receiver(self.retirement.clone()).subscription())
    }
}
