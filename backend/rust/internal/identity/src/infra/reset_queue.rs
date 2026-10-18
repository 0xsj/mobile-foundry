//! Owned, bounded, ephemeral anonymous admission. No account lookup in enqueue.
use crate::{
    app::{command::recovery::Recovery, ports::Scope, recovery::mail_unavailable},
    domain::recovery,
};
use kernel::{Failure, Kind};
use std::sync::{
    Arc,
    atomic::{AtomicBool, Ordering},
};
use tokio::{
    sync::{Mutex, mpsc, watch},
    task::JoinHandle,
};
pub struct ResetQueue {
    sender: mpsc::Sender<String>,
    stop: watch::Sender<bool>,
    worker: Mutex<Option<JoinHandle<()>>>,
    running: Arc<AtomicBool>,
}
impl ResetQueue {
    pub fn start(recovery: Recovery) -> Result<Self, Failure> {
        let runtime = tokio::runtime::Handle::try_current().map_err(|_| mail_unavailable())?;
        let (sender, mut receiver) = mpsc::channel::<String>(64);
        let (stop, mut stopped) = watch::channel(false);
        let running = Arc::new(AtomicBool::new(true));
        let state = running.clone();
        let worker = runtime.spawn(async move {
            struct Guard(Arc<AtomicBool>);
            impl Drop for Guard {
                fn drop(&mut self) {
                    self.0.store(false, Ordering::Release);
                }
            }
            let _guard = Guard(state);
            loop {
                tokio::select! {
                    biased;
                    _ = stopped.changed() => break,
                    address = receiver.recv() => {
                        let Some(address) = address else { break };
                        let scope = events::dispatcher::Control::default();
                        tokio::select! {
                            biased;
                            _ = stopped.changed() => {
                                scope.cancel();
                                break;
                            }
                            result = tokio::time::timeout(
                                std::time::Duration::from_secs(10),
                                recovery.request_reset(&scope, &address),
                            ) => {
                                let result = result.unwrap_or_else(|_| Err(Failure::new(
                                    Kind::Timeout, "", "reset work budget expired",
                                )));
                                if let Err(error) = result {
                                    let public = error.public();
                                    eprintln!(
                                        "identity recovery request could not complete kind={} code={}",
                                        public.kind.as_str(),
                                        public.code.as_deref().unwrap_or_default(),
                                    );
                                }
                            }
                        }
                    }
                }
            }
        });
        Ok(Self {
            sender,
            stop,
            worker: Mutex::new(Some(worker)),
            running,
        })
    }
    pub fn enqueue(&self, s: &dyn Scope, address: &str) -> Result<(), Failure> {
        let address = recovery::email(address)?;
        s.check()?;
        if *self.stop.borrow() || !self.running.load(Ordering::Acquire) {
            return Err(mail_unavailable());
        }
        self.sender.try_send(address).map_err(|e| match e {
            mpsc::error::TrySendError::Full(_) => crate::app::busy(),
            _ => mail_unavailable(),
        })
    }
    pub fn stop(&self) {
        self.stop.send_replace(true);
    }
    pub fn ready(&self) -> Result<(), Failure> {
        if self.running.load(Ordering::Acquire) && !*self.stop.borrow() {
            Ok(())
        } else {
            Err(mail_unavailable())
        }
    }
    pub async fn close(&self) -> Result<(), Failure> {
        self.stop();
        let mut slot = self.worker.lock().await;
        if let Some(handle) = slot.as_mut() {
            let result = handle.await;
            slot.take();
            result.map_err(|_| mail_unavailable())?;
        }
        Ok(())
    }
}
impl Drop for ResetQueue {
    fn drop(&mut self) {
        self.stop();
        if let Some(handle) = self.worker.get_mut().take() {
            handle.abort();
        }
    }
}

impl crate::app::recovery::ResetRequests for ResetQueue {
    fn enqueue(&self, s: &dyn Scope, address: &str) -> Result<(), Failure> {
        ResetQueue::enqueue(self, s, address)
    }
}
