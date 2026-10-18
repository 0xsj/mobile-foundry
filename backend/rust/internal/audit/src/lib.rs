pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;
use std::sync::Arc;
pub struct Module {
    handlers: transport::http::v1::Handlers,
    recorder: app::command::Record,
}
impl Module {
    pub fn memory(
        clock: Arc<dyn kernel::clock::Clock>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
    ) -> Self {
        let store = Arc::new(infra::memory::MemoryStore::default());
        Self::with_store(clock, access, principals, store)
    }
    pub fn postgres(
        pool: sqlx::PgPool,
        clock: Arc<dyn kernel::clock::Clock>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
    ) -> Self {
        Self::with_store(
            clock,
            access,
            principals,
            Arc::new(infra::postgres::PostgresStore::new(pool)),
        )
    }
    fn with_store<T: app::ports::Store + app::ports::PrincipalStore + 'static>(
        clock: Arc<dyn kernel::clock::Clock>,
        access: Arc<dyn app::ports::AccountAccess>,
        principals: Arc<dyn principal::Resolver>,
        store: Arc<T>,
    ) -> Self {
        Self {
            handlers: transport::http::v1::Handlers {
                list: app::query::List {
                    store: store.clone(),
                    access,
                },
                personal: app::query::ListPrincipal(store.clone()),
                principals,
            },
            recorder: app::command::Record { store, clock },
        }
    }
    pub fn routes(&self) -> axum::Router {
        self.handlers.routes()
    }
    pub fn subscribe(&self, bus: &mut events::Builder) -> Result<(), kernel::Failure> {
        for source in [
            infra::events::Source::Account,
            infra::events::Source::Identity,
            infra::events::Source::Files,
        ] {
            bus.register(
                infra::events::Receiver {
                    record: self.recorder.clone(),
                    source,
                }
                .subscription(),
            )?;
        }
        Ok(())
    }
}
