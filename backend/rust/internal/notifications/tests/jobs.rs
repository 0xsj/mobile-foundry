#[path = "support/jobs.rs"]
mod support;
#[tokio::test]
async fn memory_job_conformance() {
    support::conformance(std::sync::Arc::new(
        notifications::infra::memory::MemoryStore::default(),
    ))
    .await;
}
#[tokio::test]
async fn retained_capacity_does_not_refuse_existing_replay() {
    use notifications::app::push_jobs::PushJobs;
    let store = notifications::infra::memory::MemoryStore::default();
    let s = events::dispatcher::Control::default();
    let first = support::job();
    store.queue(&s, first.clone()).await.unwrap();
    for _ in 1..10000 {
        store.queue(&s, support::job()).await.unwrap();
    }
    assert_eq!(
        store
            .queue(&s, support::job())
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("notifications.capacity")
    );
    assert_eq!(store.queue(&s, first.clone()).await.unwrap(), first);
}
