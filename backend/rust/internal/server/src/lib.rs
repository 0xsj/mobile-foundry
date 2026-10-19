//! Owned listener/connections; readiness closes before graceful drain begins.
use health::app::query::{ProbeFuture, Readiness};
use httpx::logger::{Lifecycle, Logger};
use hyper::server::conn::http1;
use hyper_util::{
    rt::{TokioIo, TokioTimer},
    service::TowerToHyperService,
};
use kernel::{
    Failure, Kind,
    id::{Generator, RandomIds},
};
use std::{
    future::Future,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::{net::TcpListener, sync::watch, task::JoinSet};

#[derive(Clone, Default)]
pub struct Serving {
    ready: Arc<AtomicBool>,
}
impl Serving {
    pub fn is_ready(&self) -> bool {
        self.ready.load(Ordering::Acquire)
    }
}
impl Readiness for Serving {
    fn check(&self) -> ProbeFuture<'_> {
        Box::pin(async {
            if self.is_ready() {
                Ok(())
            } else {
                Err(Failure::new(
                    Kind::Unavailable,
                    "server.not_serving",
                    "server is not serving",
                ))
            }
        })
    }
}
struct Probes {
    serving: Serving,
    probes: Vec<Arc<dyn Readiness>>,
}
impl Readiness for Probes {
    fn check(&self) -> ProbeFuture<'_> {
        Box::pin(async {
            self.serving.check().await?;
            for probe in &self.probes {
                probe.check().await?;
            }
            Ok(())
        })
    }
}

pub struct Server {
    serving: Serving,
    log: Logger,
    shutdown_timeout: Duration,
    ids: Arc<dyn Generator>,
    routes: Option<axum::Router>,
    probes: Vec<Arc<dyn Readiness>>,
    trusted_proxies: runtime_config::TrustedProxies,
    metrics: Option<TcpListener>,
    header_timeout: Duration,
    buffer_limit: usize,
    connection_lifetime: Option<Duration>,
}
impl Server {
    pub fn new(log: Logger, shutdown_timeout: Duration) -> Result<Self, Failure> {
        if shutdown_timeout.is_zero() {
            return Err(Failure::new(
                Kind::Invalid,
                "server.invalid_config",
                "shutdown timeout must be positive",
            ));
        }
        Ok(Self {
            serving: Serving::default(),
            log,
            shutdown_timeout,
            ids: Arc::new(RandomIds),
            routes: None,
            probes: Vec::new(),
            trusted_proxies: Default::default(),
            metrics: None,
            header_timeout: Duration::from_secs(5),
            buffer_limit: 16 * 1024,
            connection_lifetime: None,
        })
    }
    pub fn serving(&self) -> Serving {
        self.serving.clone()
    }

    pub fn with_ids(mut self, ids: Arc<dyn Generator>) -> Self {
        self.ids = ids;
        self
    }
    pub fn with_trusted_proxies(mut self, trusted: runtime_config::TrustedProxies) -> Self {
        self.trusted_proxies = trusted;
        self
    }
    /// The process binds this private listener before serving; no API metrics route.
    pub fn with_metrics(mut self, listener: TcpListener) -> Self {
        self.metrics = Some(listener);
        self
    }
    pub fn with_probe(mut self, probe: Arc<dyn Readiness>) -> Self {
        self.probes.push(probe);
        self
    }

    pub fn with_routes(mut self, routes: axum::Router) -> Self {
        self.routes = Some(match self.routes.take() {
            Some(current) => current.merge(routes),
            None => routes,
        });
        self
    }

    /// Consuming self prevents a second serve. Shutdown never cancels active request work.
    pub async fn serve(
        mut self,
        listener: TcpListener,
        shutdown: impl Future<Output = ()>,
    ) -> Result<(), Failure> {
        let mut router = health::routes(Arc::new(Probes {
            serving: self.serving.clone(),
            probes: std::mem::take(&mut self.probes),
        }));
        if let Some(routes) = self.routes.take() {
            router = router.merge(routes);
        }
        let mut router = router.layer(axum::middleware::from_fn_with_state(
            httpx::BoundaryState::new(self.log.clone(), self.ids.clone())
                .with_trusted_proxies(self.trusted_proxies.clone()),
            httpx::boundary,
        ));
        let Some(metrics) = self.metrics.take() else {
            return self.serve_router(listener, shutdown, router).await;
        };
        let recorder = Arc::new(telemetry::Recorder::default());
        router = router.layer(axum::middleware::from_fn_with_state(
            recorder.clone(),
            telemetry::observe,
        ));
        let mut monitor = Server::new(self.log.clone(), Duration::from_secs(3))?;
        monitor.header_timeout = Duration::from_secs(2);
        monitor.buffer_limit = 8192; // Hyper's minimum read buffer, not a header-byte claim.
        monitor.connection_lifetime = Some(Duration::from_secs(3));
        let (stop, mut stopped) = watch::channel(false);
        let monitor = monitor.serve_router(
            metrics,
            async move {
                let _ = stopped.changed().await;
            },
            telemetry::routes(recorder),
        );
        self.serve_monitored(listener, shutdown, router, monitor, stop)
            .await
    }

    async fn serve_monitored(
        self,
        listener: TcpListener,
        shutdown: impl Future<Output = ()>,
        router: axum::Router,
        monitor: impl Future<Output = Result<(), Failure>> + Send + 'static,
        stop: watch::Sender<bool>,
    ) -> Result<(), Failure> {
        let mut tasks = JoinSet::new();
        tasks.spawn(monitor);
        let mut metrics_failure = None;
        let result = self.serve_router(listener, async {
            tokio::select! {
                _ = shutdown => {},
                outcome = tasks.join_next() => {
                    metrics_failure = Some(match outcome {
                        Some(Ok(Err(error))) => error,
                        _ => Failure::new(Kind::Unavailable, "server.metrics_stopped", "metrics listener stopped unexpectedly"),
                    });
                }
            }
        }, router).await;
        let _ = stop.send(true);
        // Retain ownership while selecting/joining; dropping this future aborts, never detaches.
        while let Some(outcome) = tasks.join_next().await {
            match outcome {
                Ok(Ok(())) => {}
                Ok(Err(error)) => metrics_failure = Some(error),
                Err(_) => {
                    metrics_failure = Some(Failure::new(
                        Kind::Internal,
                        "server.metrics_task",
                        "metrics task failed",
                    ))
                }
            }
        }
        result.and(metrics_failure.map_or(Ok(()), Err))
    }

    async fn serve_router(
        self,
        listener: TcpListener,
        shutdown: impl Future<Output = ()>,
        router: axum::Router,
    ) -> Result<(), Failure> {
        let (drain, receiver) = watch::channel(false);
        let mut connections = JoinSet::new();
        tokio::pin!(shutdown);
        // Scope guard also closes readiness on unexpected future drop.
        let _reset = ResetReady(self.serving.clone());
        self.serving.ready.store(true, Ordering::Release);
        self.log.lifecycle(Lifecycle::Serving);
        let mut failure = None;
        loop {
            tokio::select! {
                biased;
                _ = &mut shutdown => break,
                Some(result) = connections.join_next(), if !connections.is_empty() => {
                    if result.is_err() {
                        failure = Some(Failure::new(Kind::Internal, "server.connection_task", "connection task failed"));
                        break;
                    }
                }
                accepted = listener.accept() => {
                    let (stream, peer) = match accepted {
                        Ok(value) => value,
                        Err(cause) => {
                            failure = Some(Failure::new(Kind::Unavailable, "server.accept", "listener failed").with_cause(cause));
                            break;
                        }
                    };
                    let mut receiver = receiver.clone();
                    let service = TowerToHyperService::new(router.clone().layer(axum::Extension(httpx::PeerAddress(peer.ip()))));
                    connections.spawn(async move {
                        let mut builder = http1::Builder::new();
                        builder.timer(TokioTimer::new()).header_read_timeout(self.header_timeout)
                            .max_buf_size(self.buffer_limit);
                        let connection = builder.serve_connection(TokioIo::new(stream), service).with_upgrades();
                        tokio::pin!(connection);
                        tokio::select! {
                            _ = receiver.changed() => {
                                connection.as_mut().graceful_shutdown();
                                let _ = connection.await;
                            }
                            _ = &mut connection => {}
                            _ = async {
                                if let Some(duration) = self.connection_lifetime { tokio::time::sleep(duration).await; }
                                else { std::future::pending().await }
                            } => {}
                        }
                    });
                }
            }
        }
        self.serving.ready.store(false, Ordering::Release);
        self.log.lifecycle(Lifecycle::Draining);
        drop(listener);
        let _ = drain.send(true);
        let drained = tokio::time::timeout(self.shutdown_timeout, async {
            let mut task_failed = false;
            while let Some(result) = connections.join_next().await {
                task_failed |= result.is_err();
            }
            if task_failed {
                return Err(Failure::new(
                    Kind::Internal,
                    "server.connection_task",
                    "connection task failed",
                ));
            }
            Ok(())
        })
        .await;
        let result = match drained {
            Ok(result) => result,
            Err(_) => {
                connections.abort_all();
                while connections.join_next().await.is_some() {}
                Err(Failure::new(
                    Kind::Timeout,
                    "server.shutdown_timeout",
                    "forced connection shutdown",
                ))
            }
        };
        self.log.lifecycle(Lifecycle::Stopped);
        result?;
        if let Some(error) = failure {
            return Err(error);
        }
        Ok(())
    }
}

struct ResetReady(Serving);
impl Drop for ResetReady {
    fn drop(&mut self) {
        self.0.ready.store(false, Ordering::Release);
    }
}

#[cfg(test)]
mod tests;
