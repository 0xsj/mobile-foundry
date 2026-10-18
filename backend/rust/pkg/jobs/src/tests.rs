use super::*;
use tokio::sync::Notify;
struct Probe {
    entered: Notify,
    canceled: Notify,
    release: Notify,
    finished: AtomicBool,
}
impl Runner for Probe {
    fn run<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            self.entered.notify_one();
            while s.check().is_ok() {
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
            self.canceled.notify_one();
            self.release.notified().await;
            self.finished.store(true, Ordering::Release);
            Ok(())
        })
    }
}
#[tokio::test]
async fn stop_settles_and_canceled_close_keeps_its_join_handle() {
    let probe = Arc::new(Probe {
        entered: Notify::new(),
        canceled: Notify::new(),
        release: Notify::new(),
        finished: AtomicBool::new(false),
    });
    let mut worker =
        Worker::start(probe.clone(), Duration::from_millis(10), Arc::new(|_| {})).unwrap();
    probe.entered.notified().await;
    let health = worker.health();
    let mut closing = Box::pin(worker.close());
    tokio::select! {r=&mut closing=>panic!("closed before settlement: {r:?}"),_=probe.canceled.notified()=>{}}
    drop(closing);
    probe.release.notify_one();
    worker.close().await.unwrap();
    assert!(probe.finished.load(Ordering::Acquire));
    assert!(health.check().is_err());
    worker.close().await.unwrap();
}
