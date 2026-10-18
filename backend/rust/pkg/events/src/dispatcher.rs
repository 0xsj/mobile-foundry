use crate::{Envelope, Publisher, invalid};
use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
    id::Id,
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::{
    sync::{Mutex, watch},
    task::JoinHandle,
    time::Instant,
};

pub trait Outbox: Send + Sync {
    fn pending<'a>(&'a self, scope: &'a dyn Scope, limit: usize) -> PortFuture<'a, Vec<Envelope>>;
    fn delivered<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, ()>;
    fn failed<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id, code: &'a str) -> PortFuture<'a, ()>;
    fn ready<'a>(&'a self, scope: &'a dyn Scope) -> PortFuture<'a, ()>;
}
#[derive(Clone, Default)]
pub struct Control {
    canceled: Arc<AtomicBool>,
}
impl Control {
    pub fn cancel(&self) {
        self.canceled.store(true, Ordering::Release);
    }
}
impl Scope for Control {
    fn check(&self) -> Result<(), Failure> {
        if self.canceled.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Canceled, "", "delivery canceled"))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
struct Attempt<'a> {
    parent: &'a dyn Scope,
    deadline: Instant,
}
impl Scope for Attempt<'_> {
    fn check(&self) -> Result<(), Failure> {
        self.parent.check()?;
        if Instant::now() >= self.deadline {
            return Err(timeout());
        }
        Ok(())
    }
    fn metadata(&self) -> Metadata {
        self.parent.metadata()
    }
}
fn timeout() -> Failure {
    Failure::new(Kind::Timeout, "", "event delivery budget expired")
}
pub struct Dispatcher {
    outbox: Arc<dyn Outbox>,
    delivery: Arc<dyn Publisher>,
    serial: Mutex<()>,
}
impl Dispatcher {
    pub fn new(outbox: Arc<dyn Outbox>, delivery: Arc<dyn Publisher>) -> Self {
        Self {
            outbox,
            delivery,
            serial: Mutex::new(()),
        }
    }
    /// One owner dispatches each outbox. Lost acknowledgments replay stable event IDs.
    pub async fn dispatch(&self, scope: &dyn Scope) -> Result<(), Failure> {
        let _serial = self.serial.lock().await;
        scope.check()?;
        let batch = self.outbox.pending(scope, 32).await?;
        let mut first = None;
        for message in batch {
            scope.check()?;
            let id = message.id.clone();
            let attempt = Attempt {
                parent: scope,
                deadline: Instant::now() + Duration::from_secs(2),
            };
            let result =
                tokio::time::timeout_at(attempt.deadline, self.delivery.publish(&attempt, message))
                    .await
                    .map_err(|_| timeout())
                    .and_then(|result| {
                        attempt.check()?;
                        result
                    });
            // Parent cancellation leaves the record pending, without consuming an attempt.
            scope.check()?;
            match result {
                Ok(()) => {
                    if let Err(error) = self.outbox.delivered(scope, &id).await {
                        if first.is_none() {
                            first = Some(error);
                        }
                    }
                }
                Err(error) => {
                    let public = error.public();
                    let code = public.code.unwrap_or_else(|| public.kind.as_str().into());
                    let marked = self.outbox.failed(scope, &id, &code).await;
                    if first.is_none() {
                        first = Some(error);
                    }
                    marked?;
                }
            }
        }
        first.map_or(Ok(()), Err)
    }
}
#[derive(Clone)]
pub struct WorkerHealth(Arc<AtomicBool>);
impl WorkerHealth {
    pub fn check(&self) -> Result<(), Failure> {
        if self.0.load(Ordering::Acquire) {
            Ok(())
        } else {
            Err(Failure::new(
                Kind::Unavailable,
                "events.worker_stopped",
                "event worker stopped",
            ))
        }
    }
}
struct Running(WorkerHealth);
impl Drop for Running {
    fn drop(&mut self) {
        self.0.0.store(false, Ordering::Release);
    }
}
pub struct Worker {
    stop: watch::Sender<bool>,
    control: Control,
    task: Option<JoinHandle<()>>,
    health: WorkerHealth,
}
impl Worker {
    pub fn start(dispatcher: Arc<Dispatcher>) -> Result<Self, Failure> {
        let runtime = tokio::runtime::Handle::try_current().map_err(|_| invalid())?;
        let (stop, mut stopped) = watch::channel(false);
        let control = Control::default();
        let health = WorkerHealth(Arc::new(AtomicBool::new(true)));
        let (scope, guard) = (control.clone(), Running(health.clone()));
        let task = runtime.spawn(async move {
            let _guard = guard;
            let mut ticks = tokio::time::interval_at(Instant::now()+Duration::from_millis(100), Duration::from_millis(100));
            ticks.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            loop {
                tokio::select! { biased; _ = stopped.changed() => break, _ = ticks.tick() => {} }
                tokio::select! { biased; _ = stopped.changed() => break, _ = dispatcher.dispatch(&scope) => {} }
            }
        });
        Ok(Self {
            stop,
            control,
            task: Some(task),
            health,
        })
    }
    pub fn health(&self) -> WorkerHealth {
        self.health.clone()
    }
    pub fn stop(&self) {
        self.control.cancel();
        let _ = self.stop.send(true);
    }
    pub async fn close(&mut self) -> Result<(), Failure> {
        self.stop();
        let result = if let Some(task) = self.task.as_mut() {
            task.await.map_err(|_| {
                Failure::new(
                    Kind::Internal,
                    "events.worker_failed",
                    "event worker task failed",
                )
            })
        } else {
            Ok(())
        };
        // Keep the handle owned while awaiting; canceling close cannot detach it.
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

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn canceled_close_retains_the_join_handle_and_failed_join_is_consumed_once() {
        use std::{future::Future, task::Poll};
        let (stop, _) = watch::channel(false);
        let mut worker = Worker {
            stop,
            control: Control::default(),
            task: Some(tokio::spawn(std::future::pending())),
            health: WorkerHealth(Arc::new(AtomicBool::new(true))),
        };
        let mut closing = Box::pin(worker.close());
        std::future::poll_fn(|cx| {
            assert!(closing.as_mut().poll(cx).is_pending());
            Poll::Ready(())
        })
        .await;
        drop(closing);
        assert!(worker.task.is_some());
        worker.task.as_ref().unwrap().abort();
        assert!(worker.close().await.is_err());
        assert!(worker.close().await.is_ok());
    }
}
