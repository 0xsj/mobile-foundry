#[path = "support/push.rs"]
mod support;
use std::sync::Arc;
#[tokio::test]
async fn memory_dispatch_uses_identity_capability_before_eventual_cleanup() {
    support::conformance(
        Arc::new(notifications::infra::memory::MemoryStore::default()),
        Arc::new(identity::infra::memory::MemoryStore::new(Arc::new(
            kernel::id::RandomIds,
        ))),
    )
    .await;
}
#[tokio::test]
#[ignore = "requires the disposable six-database owner: make backend-postgres-parity-test"]
async fn separate_database_dispatch() {
    assert_eq!(
        std::env::var("TEST_POSTGRES_DISPOSABLE").as_deref(),
        Ok("1")
    );
    let identity_url =
        std::env::var("TEST_IDENTITY_DATABASE_URL").expect("owned Identity database URL required");
    let notifications_url = std::env::var("TEST_NOTIFICATIONS_DATABASE_URL")
        .expect("owned Notifications database URL required");
    assert!(
        identity_url != notifications_url,
        "separate owners must use different databases"
    );
    let identity_pool = postgres_storage::open(&identity_url, 8).await.unwrap();
    let notifications_pool = postgres_storage::open(&notifications_url, 8).await.unwrap();
    support::conformance(
        Arc::new(notifications::infra::postgres::PostgresStore::new(
            notifications_pool.clone(),
        )),
        Arc::new(
            identity::infra::postgres::PostgresStore::new(
                identity_pool.clone(),
                Arc::new(kernel::id::RandomIds),
            )
            .unwrap(),
        ),
    )
    .await;
    identity_pool.close().await;
    notifications_pool.close().await;
    println!(
        "Push: separate Identity/Notifications databases, access expiry, revocation before cleanup, races and guarded retirement passed"
    );
}
