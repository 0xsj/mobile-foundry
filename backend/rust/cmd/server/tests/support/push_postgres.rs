use kernel::{
    context::{PortFuture, Scope},
    id::Id,
};
use notifications::{
    app::{
        command::send_push_preview::SendPushPreview,
        installations::Installations,
        push::{PushAddress, PushAttempt, PushDisposition, PushGateway, PushTargets},
    },
    infra::postgres::PostgresStore,
};
use sqlx::PgPool;
use std::sync::{Arc, atomic::AtomicU64};
#[path = "../../../../internal/notifications/tests/support/push.rs"]
mod targets;
pub async fn check(pool: &PgPool) {
    let store = Arc::new(PostgresStore::new(pool.clone()));
    // Exercise the same one-attempt scenarios against actual transactions.
    targets::conformance(store.clone()).await;
    let s = events::dispatcher::Control::default();
    let m = targets::mutation();
    store.register(&s, m.clone(), targets::AT).await.unwrap();
    let lease = targets::lease(&m);
    let h = SendPushPreview {
        targets: store.clone(),
        sessions: Arc::new(InvalidGateway),
        gateway: Arc::new(InvalidGateway),
        clock: Arc::new(targets::TestClock(AtomicU64::new(targets::AT))),
    };
    super::sql(pool,"ALTER TABLE notifications.installations ADD CONSTRAINT reject_push_cleanup CHECK(enabled) NOT VALID").await;
    let failed = h.handle(&s, lease.clone(), targets::id()).await.unwrap();
    assert!(matches!(
        failed,
        PushAttempt::InvalidTarget { retirement: Err(_) }
    ));
    let unchanged = store.target(&s, &lease, targets::AT).await.unwrap();
    assert_eq!(unchanged.spec, m.spec);
    let mut collision = targets::mutation();
    collision.spec = m.spec.clone();
    assert!(
        store
            .register(&s, collision.clone(), targets::AT)
            .await
            .is_err()
    );
    super::sql(
        pool,
        "ALTER TABLE notifications.installations DROP CONSTRAINT reject_push_cleanup",
    )
    .await;
    // Reconcile only cleanup. No second gateway call is necessary.
    assert!(store.retire(&s, &lease, targets::AT).await.unwrap());
    assert!(!store.retire(&s, &lease, targets::AT).await.unwrap());
    store.register(&s, collision, targets::AT).await.unwrap();
    let counts:i64=sqlx::query_scalar("SELECT count(*) FROM notifications.installations WHERE id=$1::uuid AND NOT enabled AND token='' AND token_key IS NULL AND version=2").bind(m.id.as_str()).fetch_one(pool).await.unwrap();
    assert_eq!(counts, 1);
    println!(
        "Push PostgreSQL: guarded retirement rollback preserves token/reservation, known rejection retained and cleanup-only reconciliation passed"
    );
    use identity::app::query::session_eligibility::SessionStatus;
    let identity = identity::infra::postgres::PostgresStore::new(
        pool.clone(),
        Arc::new(kernel::id::RandomIds),
    )
    .unwrap();
    let p = super::eligibility_checks::issued(&identity).await;
    super::sql(
        pool,
        &format!(
            "UPDATE identity.sessions SET expires_at=expires_at+interval '1 day' WHERE id='{}'",
            p.state().id
        ),
    )
    .await;
    assert!(
        identity
            .eligible(
                &s,
                &p.state().principal_id,
                &p.state().id,
                identity::domain::Timestamp::from_micros(targets::AT).unwrap()
            )
            .await
            .is_err()
    );
    super::sql(
        pool,
        &format!(
            "UPDATE identity.sessions SET expires_at=expires_at-interval '1 day' WHERE id='{}'",
            p.state().id
        ),
    )
    .await;
}
struct InvalidGateway;
impl notifications::app::push::SessionEligibility for InvalidGateway {
    fn eligible<'a>(&'a self, _: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async { Ok(true) })
    }
}
impl PushGateway for InvalidGateway {
    fn send_preview<'a>(
        &'a self,
        _: &'a dyn Scope,
        _: PushAddress,
        _: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async { Ok(PushDisposition::InvalidTarget) })
    }
}
