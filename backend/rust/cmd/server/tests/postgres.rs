//! Runs only inside the disposable PostgreSQL acceptance owner; never reads app DSNs.
#[path = "../../../internal/identity/tests/support/eligibility.rs"]
mod eligibility_checks;
#[path = "../../../internal/files/tests/support/mod.rs"]
mod files_support;
#[path = "support/installations.rs"]
mod installations_checks;
#[path = "../../../internal/notes/tests/support/mod.rs"]
mod notes_support;
#[path = "support/notifications.rs"]
mod notifications_checks;
#[path = "support/push_postgres.rs"]
mod push_checks;
#[path = "../../../internal/identity/tests/support/recovery.rs"]
mod recovery_support;
use account::{
    app::ports::{Directory, Store as AccountStore},
    domain::{Account, Action as AccountAction},
};
use audit::{
    app::ports::Store as AuditStore,
    domain::{Entry, Evidence},
};
use events::dispatcher::{Control, Outbox};
use identity::{
    app::ports::{Rotation, Store as IdentityStore},
    domain::{Digest, DisplayName, Login, Principal, Secret, Session, Timestamp},
};
use kernel::{
    Kind,
    id::{Generator, Id, RandomIds},
};
use postgres_storage as pg;
use sqlx::PgPool;
use std::{
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
fn id() -> Id {
    RandomIds.new_id().unwrap()
}
fn candidate(owner: &Id) -> Account {
    Account::new(id(), owner.clone(), "Owned account", SystemTime::now()).unwrap()
}
async fn sql(pool: &PgPool, sql: &str) {
    sqlx::raw_sql(sql).execute(pool).await.unwrap();
}
async fn count(pool: &PgPool, sql: &str) -> i64 {
    sqlx::query_scalar(sql).fetch_one(pool).await.unwrap()
}
fn evidence(account: &Id, actor: &Id) -> Entry {
    Entry::new(
        Evidence {
            event_id: id(),
            account_id: Some(account.clone()),
            actor_id: actor.clone(),
            action: "account.created".into(),
            version: 1,
            occurred_at: 1_700_000_000_123_000,
            request_id: "request-1".into(),
            correlation_id: None,
            principal_id: None,
            session_id: None,
            reason: None,
            file_id: None,
        },
        SystemTime::now(),
    )
    .unwrap()
}

#[tokio::test]
#[ignore = "requires the disposable database owner: make backend-postgres-parity-test"]
async fn durable_transaction_boundaries() {
    let url = std::env::var("TEST_POSTGRES_URL").expect("requires disposable test database URL");
    assert_eq!(
        std::env::var("TEST_POSTGRES_DISPOSABLE").as_deref(),
        Ok("1"),
        "refusing unowned database"
    );
    let pool = pg::open(&url, 8).await.unwrap();
    let scope = Control::default();
    for schema in migration_catalog::selected(true) {
        let (a, b) = tokio::join!(schema.apply(&scope, &pool), schema.apply(&scope, &pool));
        a.unwrap();
        b.unwrap();
        schema.check(&scope, &pool).await.unwrap();
    }
    migrations(&pool).await;
    accounts(&pool).await;
    identities(&pool).await;
    audit(&pool).await;
    cancellation(&url, &pool).await;
    notes(&pool).await;
    recovery_support::conformance(Arc::new(
        identity::infra::postgres::PostgresStore::new(pool.clone(), Arc::new(RandomIds)).unwrap(),
    ))
    .await;
    recovery_rollback(&pool).await;
    files_support::conformance(
        Arc::new(
            files::infra::postgres::PostgresStore::new(pool.clone(), Arc::new(RandomIds)).unwrap(),
        ),
        Arc::new(files::infra::objects::Memory::default()),
    )
    .await;
    files_rollback(&pool).await;
    notifications_checks::check(&pool).await;
    installations_checks::check(&pool).await;
    eligibility_checks::conformance(Arc::new(
        identity::infra::postgres::PostgresStore::new(pool.clone(), Arc::new(RandomIds)).unwrap(),
    ))
    .await;
    push_checks::check(&pool).await;
    pool.close().await;
    println!(
        "PostgreSQL: migration history/atomicity, concurrent bootstrap/CAS, rollback, refresh reuse, leases/quarantine, Audit inbox/cursor and cancellation passed"
    );
}
async fn files_rollback(pool: &PgPool) {
    use files::app::{Action, Command, Target, ports::Store};
    let store =
        files::infra::postgres::PostgresStore::new(pool.clone(), Arc::new(RandomIds)).unwrap();
    let scope = Control::default();
    let target = Target {
        actor: id(),
        account: id(),
    };
    let file_id = id();
    let request = Command {
        action: Action::Request,
        key: "rollback".into(),
        file_id: None,
        spec: Some(files_support::spec()),
        expected: 0,
    };
    sql(
        pool,
        "ALTER TABLE files.outbox ADD CONSTRAINT reject_files_test CHECK(false) NOT VALID",
    )
    .await;
    assert!(
        store
            .apply(&scope, &target, request.clone(), Some(file_id.clone()), 1)
            .await
            .is_err()
    );
    assert!(
        store
            .get(&scope, &target.account, &file_id)
            .await
            .unwrap()
            .is_none()
    );
    assert!(
        store
            .receipt(&scope, &target, "rollback")
            .await
            .unwrap()
            .is_none()
    );
    sql(
        pool,
        "ALTER TABLE files.outbox DROP CONSTRAINT reject_files_test",
    )
    .await;
    let original = store
        .apply(&scope, &target, request, Some(file_id.clone()), 1)
        .await
        .unwrap();
    let complete = Command {
        action: Action::Complete,
        key: "complete-fault".into(),
        file_id: Some(file_id.clone()),
        spec: None,
        expected: 1,
    };
    sql(pool,"ALTER TABLE files.receipts ADD CONSTRAINT reject_files_finalization CHECK(result IS NULL) NOT VALID").await;
    assert!(
        store
            .apply(&scope, &target, complete.clone(), None, 2)
            .await
            .is_err()
    );
    assert_eq!(
        store
            .get(&scope, &target.account, &file_id)
            .await
            .unwrap()
            .unwrap()
            .snapshot(),
        &original.file
    );
    assert!(
        store
            .receipt(&scope, &target, "complete-fault")
            .await
            .unwrap()
            .is_none()
    );
    sql(
        pool,
        "ALTER TABLE files.receipts DROP CONSTRAINT reject_files_finalization",
    )
    .await;
    assert_eq!(
        store
            .apply(&scope, &target, complete, None, 2)
            .await
            .unwrap()
            .file
            .version,
        2
    );
    let before = count(pool, "SELECT count(*) FROM files.receipts").await;
    let incomplete=sqlx::query("INSERT INTO files.receipts(account_id,principal_id,key,fingerprint) VALUES($1::uuid,$2::uuid,'incomplete',$3)")
        .bind(target.account.as_str()).bind(target.actor.as_str()).bind(vec![0u8;32]).execute(pool).await;
    assert!(incomplete.is_err());
    assert_eq!(
        count(pool, "SELECT count(*) FROM files.receipts").await,
        before
    );
    for version in 1..=2 {
        let event = store
            .pending(&scope, 32)
            .await
            .unwrap()
            .into_iter()
            .find(|m| m.account_id.as_ref() == Some(&target.account))
            .unwrap();
        assert_eq!(event.version, version);
        store.delivered(&scope, &event.id).await.unwrap();
    }
    println!(
        "Files PostgreSQL: concurrent replay, lifecycle/cleanup/ordering, outbox/finalization rollback and deferred receipt guard passed"
    );
}
async fn recovery_rollback(pool: &PgPool) {
    use identity::app::recovery::RecoveryStore;
    use identity::domain::recovery::Purpose;
    let store =
        identity::infra::postgres::PostgresStore::new(pool.clone(), Arc::new(RandomIds)).unwrap();
    let s = Control::default();
    let actor = id();
    let now = Timestamp::from_micros(1_800_000_000_000_000).unwrap();
    store
        .register(
            &s,
            Principal::new(
                actor.clone(),
                Login::new(actor.as_str()).unwrap(),
                DisplayName::new("SECRET").unwrap(),
                Secret::new("atomic-checkpoint".into()),
                now,
            )
            .unwrap(),
        )
        .await
        .unwrap();
    let c = recovery_support::challenge(&actor, Purpose::VerifyEmail, "atomic-checkpoint", now);
    store.begin_challenge(&s, c.clone()).await.unwrap();
    sql(
        pool,
        "ALTER TABLE identity.outbox ADD CONSTRAINT reject_recovery_test CHECK(false) NOT VALID",
    )
    .await;
    assert!(store.verify_email(&s, c.digest.clone(), now).await.is_err());
    assert!(store.contact(&s, &actor).await.unwrap().is_none());
    sql(
        pool,
        "ALTER TABLE identity.outbox DROP CONSTRAINT reject_recovery_test",
    )
    .await;
    store.verify_email(&s, c.digest, now).await.unwrap();
    let current = recovery_support::session(&actor, now);
    store
        .issue_checked(
            &s,
            current.clone(),
            &Secret::new("atomic-checkpoint".into()),
        )
        .await
        .unwrap();
    let c = recovery_support::challenge(&actor, Purpose::ResetPassword, "atomic-checkpoint", now);
    store.begin_challenge(&s, c.clone()).await.unwrap();
    sql(
        pool,
        "ALTER TABLE identity.outbox ADD CONSTRAINT reject_recovery_test CHECK(false) NOT VALID",
    )
    .await;
    assert!(
        store
            .reset_password(
                &s,
                c.digest.clone(),
                Secret::new("new-checkpoint".into()),
                now
            )
            .await
            .is_err()
    );
    assert_eq!(
        store
            .by_id(&s, &actor)
            .await
            .unwrap()
            .unwrap()
            .password_hash()
            .expose(),
        "atomic-checkpoint"
    );
    assert!(
        store
            .access_session(&s, current.state().access.clone(), now)
            .await
            .is_ok()
    );
    sql(
        pool,
        "ALTER TABLE identity.outbox DROP CONSTRAINT reject_recovery_test",
    )
    .await;
    store
        .reset_password(&s, c.digest, Secret::new("new-checkpoint".into()), now)
        .await
        .unwrap();
    assert!(
        store
            .access_session(&s, current.state().access.clone(), now)
            .await
            .is_err()
    );
    println!(
        "Recovery PostgreSQL: contact/hash/session/challenge and required-event rollback passed"
    );
}
async fn notes(pool: &PgPool) {
    use notes::{app::ports::Store, infra::postgres::PostgresStore};
    let store = Arc::new(PostgresStore::new(pool.clone()));
    notes_support::conformance(store.clone()).await;
    let s = Control::default();
    let (account, actor) = (id(), id());
    let input = notes_support::create("SECRET atomic receipt");
    sql(pool,"ALTER TABLE notes.receipts ADD CONSTRAINT reject_notes_test CHECK(result IS NULL) NOT VALID").await;
    assert!(
        store
            .apply(&s, &account, &actor, input.clone(), Some(id()))
            .await
            .is_err()
    );
    assert!(
        store
            .receipt(&s, &account, &actor, &input.command_id)
            .await
            .unwrap()
            .is_none()
    );
    assert!(store.list(&s, &account, None, 10).await.unwrap().is_empty());
    sql(
        pool,
        "ALTER TABLE notes.receipts DROP CONSTRAINT reject_notes_test",
    )
    .await;
    let receipt = store
        .apply(&s, &account, &actor, input.clone(), Some(id()))
        .await
        .unwrap();
    assert_eq!(
        store
            .apply(&s, &account, &actor, input, None)
            .await
            .unwrap(),
        receipt
    );
    let incomplete = id();
    let failed=sqlx::query("INSERT INTO notes.receipts(account_id,principal_id,command_id,fingerprint) VALUES($1::uuid,$2::uuid,$3::uuid,$4)")
        .bind(account.as_str()).bind(actor.as_str()).bind(incomplete.as_str()).bind(vec![0u8;32]).execute(pool).await;
    assert!(
        failed.is_err(),
        "deferred completeness guard must reject a committed claim"
    );
    assert!(
        store
            .receipt(&s, &account, &actor, &incomplete)
            .await
            .unwrap()
            .is_none()
    );
    println!(
        "Notes PostgreSQL: twelve-way replay, CAS, historical deletion replay, receipt scope/pages/cancellation, finalization rollback and deferred guard passed"
    );
}
async fn migrations(pool: &PgPool) {
    use pg::migrations::{Migration, Schema};
    let s = Control::default();
    let checksum: String = sqlx::query_scalar(
        "SELECT checksum FROM account.schema_migrations WHERE name='0001_accounts_outbox.sql'",
    )
    .fetch_one(pool)
    .await
    .unwrap();
    sql(pool,"UPDATE account.schema_migrations SET checksum='drift' WHERE name='0001_accounts_outbox.sql'").await;
    assert_eq!(
        migration_catalog::ACCOUNT
            .check(&s, pool)
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("postgres.schema")
    );
    sqlx::query(
        "UPDATE account.schema_migrations SET checksum=$1 WHERE name='0001_accounts_outbox.sql'",
    )
    .bind(checksum)
    .execute(pool)
    .await
    .unwrap();
    sql(
        pool,
        "INSERT INTO account.schema_migrations(name,checksum) VALUES('9999_future.sql','future')",
    )
    .await;
    assert!(migration_catalog::ACCOUNT.apply(&s, pool).await.is_err());
    sql(
        pool,
        "DELETE FROM account.schema_migrations WHERE name='9999_future.sql'",
    )
    .await;
    static BAD: Schema = Schema {
        name: "migration_probe",
        migrations: &[
            Migration {
                name: "0001_good.sql",
                sql: "CREATE TABLE migration_probe.good(id integer);",
            },
            Migration {
                name: "0002_bad.sql",
                sql: "CREATE TABLE migration_probe.rolled_back(id integer); SELECT missing_column;",
            },
        ],
    };
    assert!(BAD.apply(&s, pool).await.is_err());
    assert_eq!(
        count(
            pool,
            "SELECT count(*) FROM migration_probe.schema_migrations"
        )
        .await,
        1
    );
    let exists: Option<String> =
        sqlx::query_scalar("SELECT to_regclass('migration_probe.rolled_back')::text")
            .fetch_one(pool)
            .await
            .unwrap();
    assert!(exists.is_none());
}
async fn accounts(pool: &PgPool) {
    let s = Control::default();
    let ids = Arc::new(RandomIds);
    let store = account::infra::postgres::PostgresStore::new(pool.clone(), ids.clone()).unwrap();
    let actor = id();
    sql(
        pool,
        "ALTER TABLE account.outbox ADD CONSTRAINT reject_test CHECK (false) NOT VALID",
    )
    .await;
    let failed = candidate(&actor);
    assert!(
        store
            .create(
                &s,
                failed.clone(),
                failed.fact(AccountAction::Created, &actor).unwrap()
            )
            .await
            .is_err()
    );
    assert!(
        store
            .get(&s, &failed.snapshot().id)
            .await
            .unwrap()
            .is_none()
    );
    sql(
        pool,
        "ALTER TABLE account.outbox DROP CONSTRAINT reject_test",
    )
    .await;
    let a = candidate(&actor);
    let b = candidate(&actor);
    let (a, b) = tokio::join!(
        store.bootstrap(
            &s,
            a.clone(),
            a.fact(AccountAction::Created, &actor).unwrap()
        ),
        store.bootstrap(
            &s,
            b.clone(),
            b.fact(AccountAction::Created, &actor).unwrap()
        )
    );
    let (a, b) = (a.unwrap(), b.unwrap());
    assert_ne!(a.created, b.created);
    assert_eq!(a.account.snapshot().id, b.account.snapshot().id);
    let old = a.account;
    let first = old.rename("First", SystemTime::now()).unwrap();
    let second = old.rename("Second", SystemTime::now()).unwrap();
    let (a, b) = tokio::join!(
        store.rename(
            &s,
            first.clone(),
            1,
            first.fact(AccountAction::Renamed, &actor).unwrap()
        ),
        store.rename(
            &s,
            second.clone(),
            1,
            second.fact(AccountAction::Renamed, &actor).unwrap()
        )
    );
    assert_ne!(a.is_ok(), b.is_ok());
    assert_eq!(a.err().or(b.err()).unwrap().kind(), Kind::Conflict);
    assert_eq!(store.list(&s, &actor, None, 10).await.unwrap().len(), 1);
    let next = old.rename("Rollback", SystemTime::now()).unwrap();
    assert!(
        store
            .rename(
                &s,
                next.clone(),
                1,
                next.fact(AccountAction::Renamed, &actor).unwrap()
            )
            .await
            .is_err()
    );
    let successor = account::infra::postgres::PostgresStore::new(pool.clone(), ids).unwrap();
    let claimed = store.pending(&s, 32).await.unwrap();
    assert_eq!(claimed.len(), 1);
    let event = &claimed[0].id;
    assert!(successor.pending(&s, 32).await.unwrap().is_empty());
    sql(pool,"UPDATE account.outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE lease_owner IS NOT NULL").await;
    let replay = successor.pending(&s, 32).await.unwrap();
    assert_eq!(replay[0].id, *event);
    assert!(store.delivered(&s, event).await.is_err());
    successor.delivered(&s, event).await.unwrap();
    let renamed = store.pending(&s, 32).await.unwrap();
    assert_eq!(renamed[0].version, 2);
    store.delivered(&s, &renamed[0].id).await.unwrap();
    let poison = candidate(&id());
    store
        .create(
            &s,
            poison.clone(),
            poison
                .fact(AccountAction::Created, &poison.snapshot().owner_id)
                .unwrap(),
        )
        .await
        .unwrap();
    sql(
        pool,
        "UPDATE account.outbox SET envelope='{}'::jsonb WHERE delivered_at IS NULL",
    )
    .await;
    assert!(store.pending(&s, 32).await.is_err());
    assert_eq!(
        store.ready(&s).await.unwrap_err().public().code.as_deref(),
        Some("account.delivery_exhausted")
    );
    sql(pool, "DELETE FROM account.outbox WHERE dead").await;
    let retry = candidate(&id());
    let actor = &retry.snapshot().owner_id;
    store
        .create(
            &s,
            retry.clone(),
            retry.fact(AccountAction::Created, actor).unwrap(),
        )
        .await
        .unwrap();
    let changed = retry
        .rename("Blocked successor", SystemTime::now())
        .unwrap();
    store
        .rename(
            &s,
            changed.clone(),
            1,
            changed.fact(AccountAction::Renamed, actor).unwrap(),
        )
        .await
        .unwrap();
    for _ in 0..5 {
        let pending = store.pending(&s, 32).await.unwrap();
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].version, 1);
        store
            .failed(&s, &pending[0].id, "consumer.unavailable")
            .await
            .unwrap();
    }
    assert!(store.ready(&s).await.is_err());
    assert!(
        store.pending(&s, 32).await.unwrap().is_empty(),
        "dead earlier event must block its successor"
    );
    // Leave no intentionally blocked test streams for subsequent process acceptance.
    sql(
        pool,
        "DELETE FROM account.outbox WHERE delivered_at IS NULL",
    )
    .await;
}
async fn identities(pool: &PgPool) {
    let s = Control::default();
    let store =
        identity::infra::postgres::PostgresStore::new(pool.clone(), Arc::new(RandomIds)).unwrap();
    let now = Timestamp::from_system_time(SystemTime::now()).unwrap();
    let p = Principal::new(
        id(),
        Login::new("database.owner").unwrap(),
        DisplayName::new("Owner").unwrap(),
        Secret::new("private-hash-checkpoint".into()),
        now,
    )
    .unwrap();
    store.register(&s, p.clone()).await.unwrap();
    assert!(store.register(&s, p.clone()).await.is_err());
    let digest = |n: usize| Digest::parse(format!("{n:064x}")).unwrap();
    let session = Session::new(id(), p.id().clone(), digest(1), digest(2), now).unwrap();
    assert!(
        store
            .issue_checked(&s, session.clone(), &Secret::new("stale".into()))
            .await
            .is_err()
    );
    store
        .issue_checked(&s, session.clone(), p.password_hash())
        .await
        .unwrap();
    sql(
        pool,
        "ALTER TABLE identity.outbox ADD CONSTRAINT reject_test CHECK (false) NOT VALID",
    )
    .await;
    assert!(
        store
            .rotate(&s, digest(2), digest(3), digest(4), now)
            .await
            .is_err()
    );
    assert!(store.access_session(&s, digest(1), now).await.is_ok());
    sql(
        pool,
        "ALTER TABLE identity.outbox DROP CONSTRAINT reject_test",
    )
    .await;
    assert!(matches!(
        store
            .rotate(&s, digest(2), digest(3), digest(4), now)
            .await
            .unwrap(),
        Rotation::Rotated(_)
    ));
    assert!(matches!(
        store
            .rotate(&s, digest(2), digest(5), digest(6), now)
            .await
            .unwrap(),
        Rotation::Reused
    ));
    assert!(store.access_session(&s, digest(3), now).await.is_err());
    assert!(
        store
            .rotate(&s, digest(4), digest(7), digest(8), now)
            .await
            .is_err()
    );
    store.revoke(&s, digest(2), now).await.unwrap();
    assert_eq!(count(pool, "SELECT count(*) FROM identity.outbox").await, 4);
}
async fn audit(pool: &PgPool) {
    let s = Control::default();
    let store = Arc::new(audit::infra::postgres::PostgresStore::new(pool.clone()));
    let actor = id();
    let account = id();
    let entry = evidence(&account, &actor);
    sql(pool,"ALTER TABLE audit.inbox ADD CONSTRAINT reject_test CHECK (consumer <> 'blocked') NOT VALID").await;
    assert!(store.record(&s, "blocked", entry.clone()).await.is_err());
    assert_eq!(count(pool, "SELECT count(*) FROM audit.entries").await, 0);
    assert_eq!(
        count(pool, "SELECT next_sequence FROM audit.append_state").await,
        1
    );
    sql(pool, "ALTER TABLE audit.inbox DROP CONSTRAINT reject_test").await;
    store.record(&s, "accepted", entry.clone()).await.unwrap();
    let replay = Entry::new(
        entry.evidence().clone(),
        UNIX_EPOCH + Duration::from_secs(1),
    )
    .unwrap();
    store.record(&s, "accepted", replay).await.unwrap();
    let mut changed = entry.evidence().clone();
    changed.request_id = "different".into();
    assert_eq!(
        store
            .record(
                &s,
                "accepted",
                Entry::new(changed, SystemTime::now()).unwrap()
            )
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("audit.event_conflict")
    );
    let page = store.list(&s, &account, 0, 1).await.unwrap();
    assert_eq!(page.records.len(), 1);
    assert!(page.next.is_none());
    assert_eq!(page.records[0].entry.recorded_at(), entry.recorded_at());
    let mut blocker = pool.begin().await.unwrap();
    sqlx::query("SELECT next_sequence FROM audit.append_state FOR UPDATE")
        .execute(&mut *blocker)
        .await
        .unwrap();
    let a = store.clone();
    let e = evidence(&account, &actor);
    let task = tokio::spawn(async move { a.record(&Control::default(), "other", e).await });
    tokio::time::sleep(Duration::from_millis(40)).await;
    assert!(!task.is_finished());
    assert_eq!(
        store.list(&s, &account, 0, 10).await.unwrap().records.len(),
        1
    );
    blocker.commit().await.unwrap();
    task.await.unwrap().unwrap();
    let page = store.list(&s, &account, 0, 1).await.unwrap();
    assert_eq!(page.next, Some(1));
    assert_eq!(
        store.list(&s, &account, 1, 1).await.unwrap().records[0].sequence,
        2
    );
}
async fn cancellation(url: &str, pool: &PgPool) {
    let owned = pg::open(url, 1).await.unwrap();
    let s = Control::default();
    let mut tx = pg::begin(&s, &owned).await.unwrap();
    sqlx::query("INSERT INTO migration_probe.good VALUES(7)")
        .execute(&mut *tx)
        .await
        .unwrap();
    let cancelled = tokio::time::timeout(Duration::from_millis(20), async {
        sqlx::query("SELECT pg_sleep(0.1)")
            .execute(&mut *tx)
            .await
            .unwrap();
        pg::finish(&s, tx, Ok(())).await
    })
    .await;
    assert!(cancelled.is_err());
    assert_eq!(
        tokio::time::timeout(
            Duration::from_secs(2),
            count(&owned, "SELECT count(*) FROM migration_probe.good")
        )
        .await
        .unwrap(),
        0
    );
    let mut tx = pg::begin(&s, pool).await.unwrap();
    sqlx::query("INSERT INTO migration_probe.good VALUES(8)")
        .execute(&mut *tx)
        .await
        .unwrap();
    s.cancel();
    assert!(pg::finish(&s, tx, Ok(())).await.is_err());
    assert_eq!(
        count(pool, "SELECT count(*) FROM migration_probe.good").await,
        0
    );
    owned.close().await;
}
