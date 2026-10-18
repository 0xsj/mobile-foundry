//! Explicit disposable-owner checks; no supplied production configuration.
#[path = "../../../../internal/notifications/tests/support/mod.rs"]
mod conformance;
use events::dispatcher::Control;
use notifications::{app::ports::Store, infra::postgres::PostgresStore};
use sqlx::PgPool;
use std::sync::Arc;
pub async fn check(pool: &PgPool) {
    let store = Arc::new(PostgresStore::new(pool.clone()));
    conformance::conformance(store.clone()).await;
    let s = Control::default();
    let t = conformance::target();
    let d = conformance::delivery(&t);
    let at = d.occurred_at;
    super::sql(pool,&format!("ALTER TABLE notifications.scope_state ADD CONSTRAINT reject_notice_sequence CHECK(account_id<>'{}'::uuid OR next_sequence=1) NOT VALID",t.account)).await;
    assert!(store.deliver(&s, d.clone(), at).await.is_err());
    assert_eq!(
        super::count(
            pool,
            &format!(
                "SELECT count(*) FROM notifications.inbox WHERE event_id='{}'",
                d.event_id
            )
        )
        .await,
        0
    );
    assert_eq!(
        super::count(
            pool,
            &format!(
                "SELECT count(*) FROM notifications.items WHERE id='{}'",
                d.event_id
            )
        )
        .await,
        0
    );
    assert_eq!(
        super::count(
            pool,
            &format!(
                "SELECT count(*) FROM notifications.scope_state WHERE account_id='{}'",
                t.account
            )
        )
        .await,
        0
    );
    super::sql(
        pool,
        "ALTER TABLE notifications.scope_state DROP CONSTRAINT reject_notice_sequence",
    )
    .await;
    store.deliver(&s, d.clone(), at).await.unwrap();
    assert_eq!(
        store.list(&s, conformance::input(&t)).await.unwrap().items[0]
            .snapshot()
            .sequence,
        1
    );
    super::sql(pool,"ALTER TABLE notifications.items ADD CONSTRAINT reject_notice_read CHECK(read_at IS NULL) NOT VALID").await;
    assert!(store.mark_read(&s, &t, &d.event_id, at + 1).await.is_err());
    assert!(
        store.list(&s, conformance::input(&t)).await.unwrap().items[0]
            .snapshot()
            .read_at
            .is_none()
    );
    super::sql(
        pool,
        "ALTER TABLE notifications.items DROP CONSTRAINT reject_notice_read",
    )
    .await;
    let read = store.mark_read(&s, &t, &d.event_id, at + 1).await.unwrap();
    assert_eq!(read.snapshot().read_at, Some(at + 1));
    super::sql(pool,"ALTER TABLE notifications.scope_state ADD CONSTRAINT reject_notice_preference CHECK(media_enabled) NOT VALID").await;
    assert!(store.set_media_enabled(&s, &t, false).await.is_err());
    assert!(store.media_enabled(&s, &t).await.unwrap());
    super::sql(
        pool,
        "ALTER TABLE notifications.scope_state DROP CONSTRAINT reject_notice_preference",
    )
    .await;
    store.set_media_enabled(&s, &t, false).await.unwrap();
    let suppressed = conformance::delivery(&t);
    super::sql(pool,&format!("ALTER TABLE notifications.inbox ADD CONSTRAINT reject_notice_source CHECK(event_id<>'{}'::uuid) NOT VALID",suppressed.event_id)).await;
    assert!(store.deliver(&s, suppressed.clone(), at + 2).await.is_err());
    assert_eq!(
        super::count(
            pool,
            &format!(
                "SELECT count(*) FROM notifications.inbox WHERE event_id='{}'",
                suppressed.event_id
            )
        )
        .await,
        0
    );
    super::sql(
        pool,
        "ALTER TABLE notifications.inbox DROP CONSTRAINT reject_notice_source",
    )
    .await;
    store.set_media_enabled(&s, &t, true).await.unwrap();
    store.deliver(&s, suppressed, at + 3).await.unwrap();
    let page = store.list(&s, conformance::input(&t)).await.unwrap();
    assert_eq!(page.items.len(), 2);
    assert_eq!(page.unread_count, 1);
    println!(
        "Notifications PostgreSQL: decision/item/sequence/inbox rollback, first-read/settings rollback and concurrent snapshot conformance passed"
    );
}
