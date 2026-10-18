#[path = "support/push_http.rs"]
mod support;
#[tokio::test]
async fn memory_preview_http() {
    support::conformance(None, None).await;
}
#[tokio::test]
#[ignore = "requires the disposable six-database owner: make backend-postgres-parity-test"]
async fn separate_database_preview_http() {
    assert_eq!(
        std::env::var("TEST_POSTGRES_DISPOSABLE").as_deref(),
        Ok("1")
    );
    let identity = std::env::var("TEST_IDENTITY_DATABASE_URL").expect("owned Identity URL");
    let notifications =
        std::env::var("TEST_NOTIFICATIONS_DATABASE_URL").expect("owned Notifications URL");
    assert_ne!(identity, notifications);
    let i = postgres_storage::open(&identity, 8).await.unwrap();
    let n = postgres_storage::open(&notifications, 8).await.unwrap();
    support::conformance(Some(i.clone()), Some(n.clone())).await;
    i.close().await;
    n.close().await;
}
