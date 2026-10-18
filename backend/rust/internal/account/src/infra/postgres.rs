//! Account SQL owns its aggregate, personal directory and required outbox writes.
use crate::{
    app::{
        self,
        ports::{BootstrapResult, CommittedFact, Directory, PortFuture, Scope, Store},
    },
    domain::{self, Account, Action, Fact, Snapshot},
};
use kernel::{
    Failure,
    id::{Generator, Id},
};
use postgres_storage as pg;
use sqlx::{PgConnection, PgPool, Row, postgres::PgRow};
use std::sync::Arc;

pub struct PostgresStore {
    pool: PgPool,
    ids: Arc<dyn Generator>,
    outbox: pg::outbox::Leased,
}
const COLUMNS: &str = "id::text, owner_id::text, name, version, created_at, updated_at";
fn restore(row: PgRow) -> Result<Account, Failure> {
    Account::restore(Snapshot {
        id: pg::id(&row, "id")?,
        owner_id: pg::id(&row, "owner_id")?,
        name: row.try_get("name").map_err(pg::failure)?,
        version: pg::version(&row, "version")?,
        created_at: pg::micros(&row, "created_at")?,
        updated_at: pg::micros(&row, "updated_at")?,
    })
}
impl PostgresStore {
    pub fn new(pool: PgPool, ids: Arc<dyn Generator>) -> Result<Self, Failure> {
        let outbox = pg::outbox::Leased::new(
            pool.clone(),
            ids.new_id()?,
            super::postgres_outbox::queries(),
            |m, stream| {
                m.producer == "account"
                    && m.account_id
                        .as_ref()
                        .is_some_and(|id| id.as_str() == stream)
            },
            super::postgres_outbox::exhausted,
        );
        Ok(Self { pool, ids, outbox })
    }
    fn prepare(
        &self,
        scope: &dyn Scope,
        account: &Account,
        fact: Fact,
        expected: u64,
    ) -> Result<events::Envelope, Failure> {
        scope.check()?;
        let s = account.snapshot();
        Account::restore(s.clone())?;
        if expected.checked_add(1) != Some(s.version)
            || account.fact(
                if expected == 0 {
                    Action::Created
                } else {
                    Action::Renamed
                },
                &s.owner_id,
            )? != fact
        {
            return Err(domain::invalid_state());
        }
        super::events::message(&CommittedFact {
            id: self.ids.new_id()?,
            fact,
            metadata: scope.metadata(),
        })
    }
    async fn insert(conn: &mut PgConnection, account: &Account) -> Result<(), Failure> {
        let s = account.snapshot();
        sqlx::query(
            r#"INSERT INTO account.accounts(id,
            owner_id,
            name,
            version,
            created_at,
            updated_at) VALUES($1::uuid,
            $2::uuid,
            $3,
            $4,
            $5,
            $6)"#,
        )
        .bind(s.id.as_str())
        .bind(s.owner_id.as_str())
        .bind(&s.name)
        .bind(s.version as i64)
        .bind(pg::time(s.created_at)?)
        .bind(pg::time(s.updated_at)?)
        .execute(conn)
        .await
        .map_err(|e| {
            if pg::constraint(&e, "accounts_pkey") {
                app::conflict()
            } else {
                pg::failure(e)
            }
        })?;
        Ok(())
    }
    async fn append(conn: &mut PgConnection, message: &events::Envelope) -> Result<(), Failure> {
        sqlx::query(
            r#"INSERT INTO account.outbox(event_id,
            account_id,
            aggregate_version,
            envelope) VALUES($1::uuid,
            $2::uuid,
            $3,
            $4)"#,
        )
        .bind(message.id.as_str())
        .bind(
            message
                .account_id
                .as_ref()
                .ok_or_else(domain::invalid_state)?
                .as_str(),
        )
        .bind(message.version as i64)
        .bind(pg::outbox::encode(message)?)
        .execute(conn)
        .await
        .map_err(pg::failure)?;
        Ok(())
    }
}
impl Store for PostgresStore {
    fn get<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<Account>> {
        Box::pin(async move {
            pg::read(
                scope,
                sqlx::query(&format!(
                    "SELECT {COLUMNS} FROM account.accounts WHERE id=$1::uuid"
                ))
                .bind(id.as_str())
                .fetch_optional(&self.pool),
            )
            .await?
            .map(restore)
            .transpose()
        })
    }
    fn create<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        fact: Fact,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let message = self.prepare(scope, &account, fact, 0)?;
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                Self::insert(&mut tx, &account).await?;
                Self::append(&mut tx, &message).await
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
    fn rename<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        expected: u64,
        fact: Fact,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let message = self.prepare(scope, &account, fact, expected)?;
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                let s = account.snapshot();
                let query =
                    format!("SELECT {COLUMNS} FROM account.accounts WHERE id=$1::uuid FOR UPDATE");
                let row = sqlx::query(&query)
                    .bind(s.id.as_str())
                    .fetch_optional(&mut *tx)
                    .await
                    .map_err(pg::failure)?
                    .ok_or_else(app::not_found)?;
                let prior = restore(row)?;
                let old = prior.snapshot();
                if old.version != expected {
                    return Err(app::conflict());
                }
                if old.owner_id != s.owner_id
                    || old.created_at != s.created_at
                    || old.updated_at > s.updated_at
                {
                    return Err(domain::invalid_state());
                }
                let changed = sqlx::query(
                    "UPDATE account.accounts SET name=$2,version=$3,updated_at=$4
                     WHERE id=$1::uuid AND version=$5",
                )
                .bind(s.id.as_str())
                .bind(&s.name)
                .bind(s.version as i64)
                .bind(pg::time(s.updated_at)?)
                .bind(expected as i64)
                .execute(&mut *tx)
                .await
                .map_err(pg::failure)?;
                if changed.rows_affected() != 1 {
                    return Err(app::conflict());
                }
                Self::append(&mut tx, &message).await
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
}
impl Directory for PostgresStore {
    fn bootstrap<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        fact: Fact,
    ) -> PortFuture<'a, BootstrapResult> {
        Box::pin(async move {
            let message = self.prepare(scope, &account, fact, 0)?;
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                let s = account.snapshot();
                let won = sqlx::query(
                    r#"INSERT INTO account.personal_accounts(owner_id,
            account_id) VALUES($1::uuid,
            $2::uuid) ON CONFLICT(owner_id) DO NOTHING
            RETURNING account_id::text"#,
                )
                .bind(s.owner_id.as_str())
                .bind(s.id.as_str())
                .fetch_optional(&mut *tx)
                .await
                .map_err(pg::failure)?
                .is_some();
                if won {
                    Self::insert(&mut tx, &account).await?;
                    Self::append(&mut tx, &message).await?;
                    return Ok(BootstrapResult {
                        account,
                        created: true,
                    });
                }
                // Separate Read Committed statement sees the winner after its unique-key wait.
                let row = sqlx::query(
                    r#"SELECT a.id::text,
            a.owner_id::text,
            a.name,
            a.version,
            a.created_at,
            a.updated_at
            FROM account.personal_accounts p JOIN account.accounts a ON a.id=p.account_id
            WHERE p.owner_id=$1::uuid"#,
                )
                .bind(s.owner_id.as_str())
                .fetch_optional(&mut *tx)
                .await
                .map_err(pg::failure)?
                .ok_or_else(domain::invalid_state)?;
                let existing = restore(row)?;
                if !existing.owned_by(&s.owner_id) {
                    return Err(domain::invalid_state());
                }
                Ok(BootstrapResult {
                    account: existing,
                    created: false,
                })
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
    fn list<'a>(
        &'a self,
        scope: &'a dyn Scope,
        owner: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<Account>> {
        Box::pin(async move {
            if !(1..=101).contains(&limit) {
                return Err(app::invalid_page());
            }
            pg::read(
                scope,
                sqlx::query(&format!(
                    r#"SELECT {COLUMNS}
            FROM account.accounts
            WHERE owner_id=$1::uuid
            AND id>$2::uuid
            ORDER BY id
            LIMIT $3"#
                ))
                .bind(owner.as_str())
                .bind(after.map_or("00000000-0000-0000-0000-000000000000", Id::as_str))
                .bind(limit as i64)
                .fetch_all(&self.pool),
            )
            .await?
            .into_iter()
            .map(restore)
            .collect()
        })
    }
}
impl events::dispatcher::Outbox for PostgresStore {
    fn pending<'a>(&'a self, s: &'a dyn Scope, n: usize) -> PortFuture<'a, Vec<events::Envelope>> {
        Box::pin(self.outbox.pending(s, n))
    }
    fn delivered<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, ()> {
        Box::pin(self.outbox.delivered(s, id))
    }
    fn failed<'a>(&'a self, s: &'a dyn Scope, id: &'a Id, c: &'a str) -> PortFuture<'a, ()> {
        Box::pin(self.outbox.failed(s, id, c))
    }
    fn ready<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(self.outbox.ready(s))
    }
}
