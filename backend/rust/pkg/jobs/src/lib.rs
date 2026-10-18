//! Owned serial polling. Business policy and settlement remain in the runner.
#[cfg(test)]
mod tests;
use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::{sync::watch, task::JoinHandle, time::Instant};
pub trait Runner: Send + Sync {
    fn run<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()>;
}
#[derive(Clone, Default)]
struct Control(Arc<AtomicBool>);
impl Scope for Control {
    fn check(&self) -> Result<(), Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Canceled, "", "polling stopped"))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
#[derive(Clone)]
pub struct Health(Arc<AtomicBool>);
impl Health {
    pub fn check(&self) -> Result<(), Failure> {
        if self.0.load(Ordering::Acquire) {
            Ok(())
        } else {
            Err(Failure::new(
                Kind::Unavailable,
                "jobs.worker_stopped",
                "polling worker stopped",
            ))
        }
    }
}
struct Running(Health);
impl Drop for Running {
    fn drop(&mut self) {
        self.0.0.store(false, Ordering::Release);
    }
}
pub struct Worker {
    control: Control,
    stop: watch::Sender<bool>,
    task: Option<JoinHandle<()>>,
    health: Health,
}
impl Worker {
    pub fn start(
        runner: Arc<dyn Runner>,
        interval: Duration,
        on_error: Arc<dyn Fn(Kind) + Send + Sync>,
    ) -> Result<Self, Failure> {
        let invalid = || {
            Failure::new(
                Kind::Invalid,
                "jobs.invalid",
                "invalid polling worker configuration",
            )
        };
        if !(Duration::from_millis(10)..=Duration::from_secs(60)).contains(&interval) {
            return Err(invalid());
        }
        let runtime = tokio::runtime::Handle::try_current().map_err(|_| invalid())?;
        let (stop, mut stopped) = watch::channel(false);
        let control = Control::default();
        let health = Health(Arc::new(AtomicBool::new(true)));
        let scope = control.clone();
        let guard = Running(health.clone());
        let task = runtime.spawn(async move {
            let _guard = guard;
            let mut ticks = tokio::time::interval_at(Instant::now() + interval, interval);
            ticks.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            loop {
                tokio::select! {biased;_=stopped.changed()=>break,_=ticks.tick()=>{}}
                if scope.check().is_err() {
                    break;
                }
                // Stop cancels Scope but never drops an in-flight runner: it must settle.
                if let Err(e) = runner.run(&scope).await {
                    on_error(e.kind());
                }
                if scope.check().is_err() {
                    break;
                }
            }
        });
        Ok(Self {
            control,
            stop,
            task: Some(task),
            health,
        })
    }
    pub fn health(&self) -> Health {
        self.health.clone()
    }
    pub fn stop(&self) {
        self.control.0.store(true, Ordering::Release);
        let _ = self.stop.send(true);
    }
    pub async fn close(&mut self) -> Result<(), Failure> {
        self.stop();
        let result = if let Some(task) = self.task.as_mut() {
            task.await.map_err(|_| {
                Failure::new(Kind::Internal, "jobs.worker_failed", "polling task failed")
            })
        } else {
            Ok(())
        };
        self.task.take();
        result
    }
}
impl Drop for Worker {
    fn drop(&mut self) {
        self.stop();
        if let Some(task) = &self.task {
            task.abort();
        }
    }
}
