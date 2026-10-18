#[path = "../../../internal/notifications/tests/support/jobs.rs"]
mod conformance;
#[path = "support/jobs_postgres.rs"]
mod durable;
#[tokio::test]
#[ignore = "requires disposable PostgreSQL: make backend-postgres-parity-test"]
async fn postgres_jobs() {
    assert_eq!(
        std::env::var("TEST_POSTGRES_DISPOSABLE").as_deref(),
        Ok("1")
    );
    let url = std::env::var("TEST_POSTGRES_URL").expect("owned URL required");
    let pool = postgres_storage::open(&url, 8).await.unwrap();
    conformance::conformance(std::sync::Arc::new(
        notifications::infra::postgres::PostgresStore::new(pool.clone()),
    ))
    .await;
    durable::faults(&pool).await;
    durable::crash(&pool, &url).await;
    pool.close().await;
}
#[tokio::test]
#[ignore = "private child of the disposable crash acceptance owner"]
async fn crash_child() {
    durable::child().await;
}
