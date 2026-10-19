//! Owned principals and sessions. Infrastructure is selected by module composition.
pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;

use app::command::Dependencies;
use infra::{
    memory::MemoryStore,
    security::{Gate, PasswordAdapter, TokenAdapter},
};
use kernel::{Failure, clock::Clock, id::Generator};
use std::sync::Arc;

/// Explicit maintenance facade; no HTTP, crypto worker or mail selection is started.
pub async fn prune_expired(
    scope: &dyn kernel::context::Scope,
    pool: sqlx::PgPool,
    now: std::time::SystemTime,
    limit: usize,
) -> Result<usize, Failure> {
    use app::recovery::RecoveryStore;
    if !(1..=1000).contains(&limit) {
        return Err(Failure::new(
            kernel::Kind::Invalid,
            "maintenance.limit",
            "invalid maintenance limit",
        ));
    }
    let store = infra::postgres::PostgresStore::new(pool, Arc::new(kernel::id::RandomIds))?;
    store
        .prune_challenges(scope, domain::Timestamp::from_system_time(now)?, limit)
        .await
}

pub struct Module {
    handlers: transport::http::v1::Handlers,
    recovery: transport::http::v1::recovery::Handlers,
    reset_queue: Arc<infra::reset_queue::ResetQueue>,
    passwords: Arc<PasswordAdapter>,
    credentials: app::query::ResolveSession,
    eligibility: app::query::session_eligibility::SessionEligibility,
    outbox: Arc<dyn events::dispatcher::Outbox>,
}
impl Module {
    pub async fn memory(clock: Arc<dyn Clock>, ids: Arc<dyn Generator>) -> Result<Self, Failure> {
        let store = Arc::new(MemoryStore::new(ids.clone()));
        Self::with_store(clock, ids, store, Arc::new(infra::email::Disabled)).await
    }
    pub async fn postgres(
        pool: sqlx::PgPool,
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
    ) -> Result<Self, Failure> {
        let store = Arc::new(infra::postgres::PostgresStore::new(pool, ids.clone())?);
        Self::with_store(clock, ids, store, Arc::new(infra::email::Disabled)).await
    }
    pub async fn memory_with_mail(
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
        mail: Arc<dyn app::recovery::Mailer>,
    ) -> Result<Self, Failure> {
        let store = Arc::new(MemoryStore::new(ids.clone()));
        Self::with_store(clock, ids, store, mail).await
    }
    pub async fn postgres_with_mail(
        pool: sqlx::PgPool,
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
        mail: Arc<dyn app::recovery::Mailer>,
    ) -> Result<Self, Failure> {
        let store = Arc::new(infra::postgres::PostgresStore::new(pool, ids.clone())?);
        Self::with_store(clock, ids, store, mail).await
    }
    async fn with_store<
        T: app::ports::Store
            + app::recovery::RecoveryStore
            + app::query::session_eligibility::SessionStatus
            + events::dispatcher::Outbox
            + 'static,
    >(
        clock: Arc<dyn Clock>,
        ids: Arc<dyn Generator>,
        store: Arc<T>,
        mail: Arc<dyn app::recovery::Mailer>,
    ) -> Result<Self, Failure> {
        let passwords = Arc::new(PasswordAdapter::new().await?);
        let dependencies = Dependencies {
            store: store.clone(),
            passwords: passwords.clone(),
            tokens: Arc::new(TokenAdapter),
            ids,
            clock: clock.clone(),
        };
        let gate = Arc::new(Gate::default());
        let commands = app::command::recovery::Recovery {
            core: dependencies.clone(),
            store: store.clone(),
            mail,
        };
        let queue = match infra::reset_queue::ResetQueue::start(commands.clone()) {
            Ok(q) => Arc::new(q),
            Err(error) => {
                passwords.close().await?;
                return Err(error);
            }
        };
        let recovery =
            transport::http::v1::recovery::Handlers::new(commands, queue.clone(), gate.clone());
        Ok(Self {
            recovery,
            reset_queue: queue,
            credentials: app::query::ResolveSession(dependencies.clone()),
            eligibility: app::query::session_eligibility::SessionEligibility {
                store: store.clone(),
                clock,
            },
            handlers: transport::http::v1::Handlers::new(dependencies, gate),
            passwords,
            outbox: store,
        })
    }
    pub fn routes(&self) -> axum::Router {
        self.handlers.routes().merge(self.recovery.routes())
    }
    pub fn outbox(&self) -> Arc<dyn events::dispatcher::Outbox> {
        self.outbox.clone()
    }
    pub fn register(&self, registry: &mut capability::Builder) -> Result<(), Failure> {
        use transport::capability::{v1, v2};
        registry.register(
            v1::NAME,
            &["api"],
            Arc::new(v1::Credential(self.credentials.clone())),
        )?;
        registry.register(
            v2::NAME,
            &["api"],
            Arc::new(v2::Credential(self.credentials.clone())),
        )?;
        registry.register(
            v1::session_eligibility::NAME,
            &["notifications"],
            Arc::new(v1::session_eligibility::Provider(self.eligibility.clone())),
        )
    }
    pub fn stop(&self) {
        self.reset_queue.stop();
    }
    pub fn recovery_readiness(&self) -> Arc<infra::reset_queue::ResetQueue> {
        self.reset_queue.clone()
    }
    pub async fn close(&self) -> Result<(), Failure> {
        let queue = self.reset_queue.close().await;
        let crypto = self.passwords.close().await;
        queue?;
        crypto
    }
}
