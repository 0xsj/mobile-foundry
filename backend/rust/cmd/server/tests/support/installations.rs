//! Runs only under the disposable PostgreSQL acceptance owner.
#[path = "../../../../internal/notifications/tests/support/installations.rs"]
mod conformance;
use events::dispatcher::Control;
use notifications::{app::installations::Installations, infra::postgres::PostgresStore};
use sqlx::PgPool;
use std::sync::Arc;
pub async fn check(pool: &PgPool) {
    let store = Arc::new(PostgresStore::new(pool.clone()));
    conformance::conformance(store.clone()).await;
    let s = Control::default();
    let a = conformance::actor();
    let m = conformance::mutation(&a);
    let observer = conformance::actor();
    let original = store
        .register(&s, m.clone(), conformance::AT)
        .await
        .unwrap();
    let r = conformance::revocation(&a);
    for (table, condition) in [
        (
            "installations",
            format!("principal_id<>'{}'::uuid OR enabled", a.principal),
        ),
        (
            "revoked_installation_sessions",
            format!("session_id<>'{}'::uuid", a.session),
        ),
        (
            "installation_inbox",
            format!("event_id<>'{}'::uuid", r.event_id),
        ),
    ] {
        super::sql(pool,&format!("ALTER TABLE notifications.{table} ADD CONSTRAINT reject_installation_commit CHECK({condition}) NOT VALID")).await;
        assert!(store.retire_session(&s, r.clone()).await.is_err());
        assert_eq!(
            store
                .get(&s, &observer, &m.id, &m.proof_digest, conformance::AT + 60)
                .await
                .unwrap(),
            original
        );
        for query in [
            format!(
                "SELECT count(*) FROM notifications.revoked_installation_sessions WHERE principal_id='{}' AND session_id='{}'",
                a.principal, a.session
            ),
            format!(
                "SELECT count(*) FROM notifications.installation_inbox WHERE event_id='{}'",
                r.event_id
            ),
        ] {
            assert_eq!(super::count(pool, &query).await, 0);
        }
        super::sql(
            pool,
            &format!(
                "ALTER TABLE notifications.{table} DROP CONSTRAINT reject_installation_commit"
            ),
        )
        .await;
    }
    store.retire_session(&s, r.clone()).await.unwrap();
    let retired = store
        .get(&s, &observer, &m.id, &m.proof_digest, conformance::AT + 60)
        .await
        .unwrap();
    assert!(!retired.snapshot().enabled);
    assert!(retired.snapshot().spec.token.expose().is_empty());
    assert_eq!(super::count(pool,&format!("SELECT count(*) FROM notifications.installations WHERE id='{}' AND token='' AND token_key IS NULL",m.id)).await,1);
    store.retire_session(&s, r).await.unwrap();
    // A failed second write must roll back the first target's expiry cleanup.
    let mut short = conformance::actor();
    short.expires_at = conformance::AT + 10;
    let prior = conformance::mutation(&short);
    let original = store
        .register(&s, prior.clone(), conformance::AT)
        .await
        .unwrap();
    let mut claim = conformance::mutation(&observer);
    claim.spec = prior.spec.clone();
    super::sql(pool,&format!("ALTER TABLE notifications.installations ADD CONSTRAINT reject_installation_claim CHECK(id<>'{}'::uuid) NOT VALID",claim.id)).await;
    assert!(
        store
            .register(&s, claim.clone(), conformance::AT + 10)
            .await
            .is_err()
    );
    assert_eq!(
        store
            .get(
                &s,
                &observer,
                &prior.id,
                &prior.proof_digest,
                conformance::AT + 11
            )
            .await
            .unwrap(),
        original
    );
    assert_eq!(
        super::count(
            pool,
            &format!(
                "SELECT count(*) FROM notifications.installations WHERE id='{}'",
                claim.id
            )
        )
        .await,
        0
    );
    super::sql(
        pool,
        "ALTER TABLE notifications.installations DROP CONSTRAINT reject_installation_claim",
    )
    .await;
    store
        .register(&s, claim, conformance::AT + 11)
        .await
        .unwrap();
    assert!(
        !store
            .get(
                &s,
                &observer,
                &prior.id,
                &prior.proof_digest,
                conformance::AT + 12
            )
            .await
            .unwrap()
            .snapshot()
            .enabled
    );
    println!(
        "Installations PostgreSQL: concurrent checkpoint/target/revocation conformance, cleanup+tombstone+inbox rollback and expired-target reclamation rollback passed"
    );
}
