//! Scoped inbox, installations and explicitly selected one-attempt push previews.
pub mod app;
pub mod domain;
pub mod infra;
pub mod transport;
use std::sync::Arc;
/// A single composition owner. Shutdown after HTTP/worker drain closes idle pools;
/// retained application gateway handles then refuse new work.
pub struct PushProvider(Arc<infra::push::Gateway>);
impl PushProvider {
    pub fn gateway(&self) -> Arc<dyn app::push::PushGateway> {
        self.0.clone()
    }
}
impl Drop for PushProvider {
    fn drop(&mut self) {
        self.0.close();
    }
}
pub fn load_push_gateway(
    path: Option<&str>,
    clock: Arc<dyn kernel::clock::Clock>,
) -> Result<Option<PushProvider>, kernel::Failure> {
    path.filter(|p| !p.is_empty())
        .map(|path| {
            infra::push::Gateway::load(std::path::Path::new(path), clock)
                .map(|g| PushProvider(Arc::new(g)))
        })
        .transpose()
}
/// Optional trusted composition. No gateway, route or worker is selected by default.
pub fn new_push_preview(
    targets: Arc<dyn app::push::PushTargets>,
    clock: Arc<dyn kernel::clock::Clock>,
    registry: &Arc<capability::Registry>,
    gateway: Arc<dyn app::push::PushGateway>,
) -> Result<app::command::send_push_preview::SendPushPreview, kernel::Failure> {
    registry.require(infra::capabilities::sessions::NAME, "notifications")?;
    Ok(app::command::send_push_preview::SendPushPreview {
        targets,
        clock,
        gateway,
        sessions: Arc::new(infra::capabilities::Client(Arc::new(
            registry.client("notifications")?,
        ))),
    })
}
pub struct Module {
    handlers: transport::http::v1::Handlers,
    delivery: app::command::DeliverUpload,
    installations: transport::http::v1::installations::Handlers,
    retirement: app::command::installations::RetireSession,
    targets: Arc<dyn app::push::PushTargets>,
    clock: Arc<dyn kernel::clock::Clock>,
    preview: Option<app::command::send_push_preview::SendPushPreview>,
    jobs: Arc<dyn app::push_jobs::PushJobs>,
    job_selection: Option<(
        transport::http::v1::installations::jobs::Handlers,
        app::command::run_push_job::RunPushJob,
    )>,
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
    fn with_store<
        T: app::ports::Store
            + app::installations::Installations
            + app::push::PushTargets
            + app::push_jobs::PushJobs
            + 'static,
    >(
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
            targets: store.clone(),
            clock: clock.clone(),
            preview: None,
            jobs: store.clone(),
            job_selection: None,
            delivery: app::command::DeliverUpload { store, clock },
        }
    }
    pub fn with_push(
        mut self,
        registry: &Arc<capability::Registry>,
        gateway: Arc<dyn app::push::PushGateway>,
    ) -> Result<Self, kernel::Failure> {
        self.preview = Some(new_push_preview(
            self.targets.clone(),
            self.clock.clone(),
            registry,
            gateway,
        )?);
        Ok(self)
    }
    pub fn routes(&self) -> axum::Router {
        let routes = self.handlers.routes().merge(self.installations.routes());
        let routes = match &self.preview {
            Some(send) => routes.merge(self.installations.preview_routes(send.clone())),
            None => routes,
        };
        match &self.job_selection {
            Some((h, _)) => routes.merge(h.routes()),
            None => routes,
        }
    }
    /// Trusted composition also permits memory for conformance; runtime requires PostgreSQL.
    pub fn with_push_jobs(
        mut self,
        ids: Arc<dyn kernel::id::Generator>,
    ) -> Result<Self, kernel::Failure> {
        let send = self.preview.clone().ok_or_else(app::invalid)?;
        self.job_selection = Some((
            transport::http::v1::installations::jobs::Handlers {
                installations: self.installations.clone(),
                queue: app::command::queue_push_preview::QueuePushPreview {
                    store: self.jobs.clone(),
                    targets: self.targets.clone(),
                    clock: self.clock.clone(),
                },
                get: app::query::push_job::GetPushJob {
                    store: self.jobs.clone(),
                    clock: self.clock.clone(),
                },
                cancel: app::command::recover_push_job::CancelPushJob {
                    store: self.jobs.clone(),
                    clock: self.clock.clone(),
                },
                replace: app::command::recover_push_job::ReplacePushJob {
                    store: self.jobs.clone(),
                    targets: self.targets.clone(),
                    clock: self.clock.clone(),
                },
                replacement: app::query::push_job::GetPushJobReplacement {
                    store: self.jobs.clone(),
                    clock: self.clock.clone(),
                },
            },
            app::command::run_push_job::RunPushJob {
                store: self.jobs.clone(),
                send,
                ids,
            },
        ));
        Ok(self)
    }
    pub fn start_push_jobs(&self) -> Result<Option<jobs::Worker>, kernel::Failure> {
        self.job_selection
            .as_ref()
            .map(|(_, run)| {
                jobs::Worker::start(
                    Arc::new(run.clone()),
                    std::time::Duration::from_millis(100),
                    Arc::new(|kind| eprintln!("push job failed: {}", kind.as_str())),
                )
            })
            .transpose()
    }
    pub fn subscribe(&self, builder: &mut events::Builder) -> Result<(), kernel::Failure> {
        builder.register(infra::events::Receiver(self.delivery.clone()).subscription())?;
        builder.register(infra::events::identity::Receiver(self.retirement.clone()).subscription())
    }
}
