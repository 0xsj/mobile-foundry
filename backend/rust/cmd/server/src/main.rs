use httpx::logger::Logger;
use kernel::{Failure, Kind};
use runtime_config::{Authentication, Runtime, Storage};
use runtime_server::Server;
use std::{process::ExitCode, sync::Arc};

#[tokio::main]
async fn main() -> ExitCode {
    // catch_unwind runs the global hook first. Never print panic payload/location.
    std::panic::set_hook(Box::new(|_| eprintln!("runtime panic")));
    match run().await {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("server failed: {}", error.kind().as_str());
            ExitCode::FAILURE
        }
    }
}

async fn run() -> Result<(), Failure> {
    let config = Runtime::load(|key| match std::env::var(key) {
        Ok(value) => Some(value),
        Err(std::env::VarError::NotPresent) => None,
        // A present non-Unicode value must fail validation too.
        Err(std::env::VarError::NotUnicode(_)) => Some("\0".into()),
    })?;
    let log = Logger::new(std::io::stderr(), config.log_format, config.log_level);
    let server = Server::new(log, config.shutdown_timeout)?;
    let pools = Pools::open(&config).await?;
    let directory = if let Storage::Postgres(db) = &config.storage {
        match files::infra::objects::Directory::new(&db.files_directory) {
            Ok(directory) => Some(Arc::new(directory)),
            Err(error) => {
                pools.close().await;
                return Err(error);
            }
        }
    } else {
        None
    };
    let objects: Arc<dyn files::app::ports::Objects> = match &directory {
        Some(directory) => directory.clone(),
        None => Arc::new(files::infra::objects::Memory::default()),
    };
    let result = run_selected(config, server, &pools, objects).await;
    let objects_closed = if let Some(directory) = directory {
        directory.close().await
    } else {
        Ok(())
    };
    // HTTP and all workers/KDF jobs have finished before any owned pool closes.
    pools.close().await;
    result.and(objects_closed)
}
async fn run_selected(
    config: Runtime,
    server: Server,
    pools: &Pools,
    objects: Arc<dyn files::app::ports::Objects>,
) -> Result<(), Failure> {
    // Install signal handlers before serving, so an unavailable signal source is startup failure.
    let mut terminate = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        .map_err(|cause| {
        Failure::new(Kind::Internal, "server.signal", "signal setup failed").with_cause(cause)
    })?;
    let mut interrupt = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::interrupt())
        .map_err(|cause| {
        Failure::new(Kind::Internal, "server.signal", "signal setup failed").with_cause(cause)
    })?;
    let clock: Arc<dyn kernel::clock::Clock> = Arc::new(kernel::clock::SystemClock);
    let ids: Arc<dyn kernel::id::Generator> = Arc::new(kernel::id::RandomIds);
    let accounts = match pools.get("account") {
        Some(pool) => account::Module::postgres(pool, clock.clone(), ids.clone())?,
        None => account::Module::memory(clock.clone(), ids.clone()),
    };
    let module = if config.authentication == Authentication::Identity {
        let mail = identity::infra::email::load(
            config.mail_config.as_deref(),
            config.environment != "production",
        )?;
        Some(match pools.get("identity") {
            Some(pool) => {
                identity::Module::postgres_with_mail(pool, clock.clone(), ids.clone(), mail).await?
            }
            None => identity::Module::memory_with_mail(clock.clone(), ids.clone(), mail).await?,
        })
    } else {
        None
    };
    // Assembly is complete and frozen before workers or listener admission.
    let assembled = (|| -> Result<_, Failure> {
        let mut builder = capability::Builder::default();
        accounts.register(&mut builder)?;
        if let Some(module) = &module {
            module.register(&mut builder)?;
        }
        let registry = builder.freeze();
        registry.require(audit::infra::capabilities::ACCOUNT_ACCESS, "audit")?;
        registry.require(notes::infra::capabilities::ACCOUNT_ACCESS, "notes")?;
        registry.require(files::infra::capabilities::ACCOUNT_ACCESS, "files")?;
        registry.require(
            notifications::infra::capabilities::ACCOUNT_ACCESS,
            "notifications",
        )?;
        let principals: Arc<dyn principal::Resolver> = if module.is_some() {
            registry.require(principal::capabilities::CREDENTIAL, "api")?;
            Arc::new(principal::capabilities::Resolver(Arc::new(
                registry.client("api")?,
            )))
        } else {
            Arc::new(principal::capabilities::Deny)
        };
        let access: Arc<dyn audit::app::ports::AccountAccess> = Arc::new(
            audit::infra::capabilities::Client(Arc::new(registry.client("audit")?)),
        );
        let audit = match pools.get("audit") {
            Some(pool) => audit::Module::postgres(pool, clock.clone(), access, principals.clone()),
            None => audit::Module::memory(clock.clone(), access, principals.clone()),
        };
        let access: Arc<dyn notes::app::ports::AccountAccess> = Arc::new(
            notes::infra::capabilities::Client(Arc::new(registry.client("notes")?)),
        );
        let notes = match pools.get("notes") {
            Some(pool) => notes::Module::postgres(pool, access, principals.clone(), ids.clone()),
            None => notes::Module::memory(access, principals.clone(), ids.clone()),
        };
        let mut subscriptions = events::Builder::default();
        let access: Arc<dyn files::app::ports::AccountAccess> = Arc::new(
            files::infra::capabilities::Client(Arc::new(registry.client("files")?)),
        );
        let files = match pools.get("files") {
            Some(pool) => {
                files::Module::postgres(pool, access, objects, clock.clone(), ids.clone())?
            }
            None => files::Module::memory(access, objects, clock.clone(), ids.clone()),
        };
        audit.subscribe(&mut subscriptions)?;
        let access: Arc<dyn notifications::app::ports::AccountAccess> = Arc::new(
            notifications::infra::capabilities::Client(Arc::new(registry.client("notifications")?)),
        );
        let notifications = match pools.get("notifications") {
            Some(pool) => {
                notifications::Module::postgres(pool, clock.clone(), access, principals.clone())
            }
            None => notifications::Module::memory(clock.clone(), access, principals.clone()),
        };
        notifications.subscribe(&mut subscriptions)?;
        Ok((
            principals,
            audit,
            notes,
            files,
            notifications,
            subscriptions.freeze(),
        ))
    })();
    let (principals, audit, notes, files, notifications, bus) = match assembled {
        Ok(value) => value,
        Err(error) => {
            if let Some(module) = &module {
                module.close().await?;
            }
            return Err(error);
        }
    };
    let listener = match tokio::net::TcpListener::bind(config.address)
        .await
        .map_err(|cause| {
            Failure::new(Kind::Unavailable, "server.bind", "listener bind failed").with_cause(cause)
        }) {
        Ok(listener) => listener,
        Err(error) => {
            if let Some(module) = &module {
                module.close().await?;
            }
            return Err(error);
        }
    };
    let mut outboxes = vec![accounts.outbox(), files.outbox()];
    if let Some(module) = &module {
        outboxes.push(module.outbox());
    }
    let mut workers = Vec::new();
    let mut start_failure = None;
    for outbox in &outboxes {
        match events::dispatcher::Worker::start(Arc::new(events::dispatcher::Dispatcher::new(
            outbox.clone(),
            bus.clone(),
        ))) {
            Ok(worker) => workers.push(worker),
            Err(error) => {
                start_failure = Some(error);
                break;
            }
        }
    }
    if let Some(error) = start_failure {
        if let Some(module) = &module {
            module.stop();
        }
        for worker in &workers {
            worker.stop();
        }
        for worker in &mut workers {
            let _ = worker.close().await;
        }
        if let Some(module) = &module {
            module.close().await?;
        }
        return Err(error);
    }
    let probe = DeliveryReady {
        recovery: module.as_ref().map(|m| m.recovery_readiness()),
        pools: pools.0.iter().map(|(_, pool)| pool.clone()).collect(),
        outboxes,
        workers: workers.iter().map(|w| w.health()).collect(),
    };
    let mut server = server
        .with_probe(Arc::new(probe))
        .with_routes(accounts.routes(principals.clone()))
        .with_routes(files.routes(principals))
        .with_routes(audit.routes())
        .with_routes(notes.routes());
    server = server.with_routes(notifications.routes());
    if let Some(module) = &module {
        server = server.with_routes(module.routes());
    }
    let result = server
        .serve(listener, async {
            tokio::select! { _ = terminate.recv() => {}, _ = interrupt.recv() => {} }
        })
        .await;
    // Fence every worker before joining any; no module resources close first.
    if let Some(module) = &module {
        module.stop();
    }
    for worker in &workers {
        worker.stop();
    }
    let mut worker_failure = None;
    for worker in &mut workers {
        if let Err(error) = worker.close().await {
            if worker_failure.is_none() {
                worker_failure = Some(error);
            }
        }
    }
    if let Some(module) = &module {
        module.close().await?;
    }
    result?;
    worker_failure.map_or(Ok(()), Err)
}

struct DeliveryReady {
    recovery: Option<Arc<identity::infra::reset_queue::ResetQueue>>,
    pools: Vec<sqlx::PgPool>,
    outboxes: Vec<Arc<dyn events::dispatcher::Outbox>>,
    workers: Vec<events::dispatcher::WorkerHealth>,
}
impl health::app::query::Readiness for DeliveryReady {
    fn check(&self) -> health::app::query::ProbeFuture<'_> {
        Box::pin(async {
            if let Some(queue) = &self.recovery {
                queue.ready()?;
            }
            for worker in &self.workers {
                worker.check()?;
            }
            let scope = events::dispatcher::Control::default();
            for pool in &self.pools {
                postgres_storage::read(&scope, sqlx::query("SELECT 1").execute(pool)).await?;
            }
            for outbox in &self.outboxes {
                outbox.ready(&scope).await?;
            }
            Ok(())
        })
    }
}

struct Pools(Vec<(&'static str, sqlx::PgPool)>);
impl Pools {
    async fn open(config: &Runtime) -> Result<Self, Failure> {
        let mut pools = Self(Vec::new());
        let result = async {
            if let Storage::Postgres(db) = &config.storage {
                let scope = events::dispatcher::Control::default();
                for schema in
                    migration_catalog::selected(config.authentication == Authentication::Identity)
                {
                    let url = match schema.name {
                        "account" => &db.account_url,
                        "audit" => &db.audit_url,
                        "notes" => &db.notes_url,
                        "files" => &db.files_url,
                        "notifications" => &db.notifications_url,
                        _ => db
                            .identity_url
                            .as_ref()
                            .ok_or_else(postgres_storage::configuration)?,
                    };
                    let pool = postgres_storage::open(url, db.maximum).await?;
                    pools.0.push((schema.name, pool.clone()));
                    schema.check(&scope, &pool).await?;
                }
            }
            Ok(())
        }
        .await;
        if let Err(error) = result {
            pools.close().await;
            return Err(error);
        }
        Ok(pools)
    }
    fn get(&self, name: &str) -> Option<sqlx::PgPool> {
        self.0
            .iter()
            .find(|(owner, _)| *owner == name)
            .map(|(_, pool)| pool.clone())
    }
    async fn close(&self) {
        for (_, pool) in &self.0 {
            pool.close().await;
        }
    }
}
