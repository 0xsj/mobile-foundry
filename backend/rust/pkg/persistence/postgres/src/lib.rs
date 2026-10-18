//! Owned PostgreSQL connection, transaction and migration mechanics; no business SQL.
pub mod migrations;
pub mod outbox;
use kernel::{Failure, Kind, context::Scope, id::Id};
use sqlx::{
    ConnectOptions, PgPool, Postgres, Row, Transaction,
    postgres::{PgConnectOptions, PgPoolOptions, PgRow},
};
use std::{future::Future, str::FromStr, time::Duration};

pub async fn open(url: &str, maximum: u32) -> Result<PgPool, Failure> {
    if !(1..=128).contains(&maximum) {
        return Err(configuration());
    }
    let options = PgConnectOptions::from_str(url)
        .map_err(|_| configuration())?
        .options([
            ("timezone", "UTC"),
            ("statement_timeout", "5000"),
            ("lock_timeout", "2000"),
        ])
        .disable_statement_logging();
    let pool = PgPoolOptions::new()
        .max_connections(maximum)
        .acquire_timeout(Duration::from_secs(5))
        .idle_timeout(Some(Duration::from_secs(1800)))
        .connect_with(options)
        .await
        .map_err(failure)?;
    Ok(pool)
}
pub fn configuration() -> Failure {
    Failure::new(
        Kind::Invalid,
        "postgres.configuration",
        "invalid PostgreSQL configuration",
    )
    .with_public("Check the PostgreSQL connection configuration.")
}
pub fn schema_failure() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "postgres.schema",
        "missing, changed or unsupported migrations",
    )
    .with_public("Run the matching database migrations before starting the API.")
}
pub fn state_failure() -> Failure {
    Failure::new(
        Kind::Internal,
        "postgres.invalid_state",
        "invalid persisted state",
    )
}
pub fn failure(error: sqlx::Error) -> Failure {
    let kind = match &error {
        sqlx::Error::PoolTimedOut => Kind::Timeout,
        sqlx::Error::PoolClosed | sqlx::Error::Io(_) | sqlx::Error::Tls(_) => Kind::Unavailable,
        sqlx::Error::Database(db) => match db.code().as_deref() {
            Some("57014") => Kind::Timeout,
            Some("40001" | "40P01" | "55P03" | "53300" | "53400" | "57P01" | "57P02" | "57P03") => {
                Kind::Unavailable
            }
            Some(code) if code.starts_with("08") => Kind::Unavailable,
            _ => Kind::Internal,
        },
        _ => Kind::Internal,
    };
    Failure::new(kind, "postgres.failure", "PostgreSQL operation failed").with_cause(error)
}
pub fn commit_failure(error: sqlx::Error) -> Failure {
    let rejected = match &error {
        sqlx::Error::Database(db) => db
            .code()
            .is_some_and(|c| c.starts_with("23") || c.starts_with("25") || c.starts_with("40")),
        _ => false,
    };
    if rejected {
        return failure(error);
    }
    Failure::new(
        Kind::Unavailable,
        "postgres.commit_unknown",
        "transaction commit outcome is unknown",
    )
    .with_public("The write outcome is uncertain. Read current state before trying again.")
    .with_cause(error)
}
pub async fn read<T>(
    scope: &dyn Scope,
    work: impl Future<Output = Result<T, sqlx::Error>>,
) -> Result<T, Failure> {
    scope.check()?;
    let result = work.await.map_err(failure)?;
    scope.check()?;
    Ok(result)
}
pub async fn begin(
    scope: &dyn Scope,
    pool: &PgPool,
) -> Result<Transaction<'static, Postgres>, Failure> {
    read(
        scope,
        pool.begin_with("BEGIN ISOLATION LEVEL READ COMMITTED"),
    )
    .await
}
/// No retries. Dropped SQLx transactions queue rollback before connection reuse.
/// Returned pre-commit errors explicitly roll back under an independent cleanup budget.
pub async fn finish<T>(
    scope: &dyn Scope,
    tx: Transaction<'static, Postgres>,
    result: Result<T, Failure>,
) -> Result<T, Failure> {
    let result = result.and_then(|value| {
        scope.check()?;
        Ok(value)
    });
    match result {
        Ok(value) => {
            tx.commit().await.map_err(commit_failure)?;
            Ok(value)
        }
        Err(error) => {
            let _ = tokio::time::timeout(Duration::from_secs(2), tx.rollback()).await;
            Err(error)
        }
    }
}
pub fn constraint(error: &sqlx::Error, name: &str) -> bool {
    matches!(error,sqlx::Error::Database(db) if db.code().as_deref()==Some("23505") && db.constraint()==Some(name))
}
pub fn id(row: &PgRow, column: &str) -> Result<Id, Failure> {
    Id::parse(&row.try_get::<String, _>(column).map_err(failure)?).map_err(|_| state_failure())
}
pub fn optional_id(row: &PgRow, column: &str) -> Result<Option<Id>, Failure> {
    row.try_get::<Option<String>, _>(column)
        .map_err(failure)?
        .map(|v| Id::parse(&v).map_err(|_| state_failure()))
        .transpose()
}
pub fn micros(row: &PgRow, column: &str) -> Result<u64, Failure> {
    let value: chrono::DateTime<chrono::Utc> = row.try_get(column).map_err(failure)?;
    let micros = value.timestamp_micros();
    if micros < 0 || micros as u64 > events::MAX_TIME || value.timestamp_subsec_nanos() % 1000 != 0
    {
        return Err(state_failure());
    }
    Ok(micros as u64)
}
pub fn optional_micros(row: &PgRow, column: &str) -> Result<Option<u64>, Failure> {
    let value: Option<chrono::DateTime<chrono::Utc>> = row.try_get(column).map_err(failure)?;
    value
        .map(|v| {
            let n = v.timestamp_micros();
            if n < 0 || n as u64 > events::MAX_TIME || v.timestamp_subsec_nanos() % 1000 != 0 {
                Err(state_failure())
            } else {
                Ok(n as u64)
            }
        })
        .transpose()
}
pub fn time(micros: u64) -> Result<chrono::DateTime<chrono::Utc>, Failure> {
    if micros > events::MAX_TIME {
        return Err(state_failure());
    }
    chrono::DateTime::from_timestamp_micros(micros as i64).ok_or_else(state_failure)
}
pub fn version(row: &PgRow, column: &str) -> Result<u64, Failure> {
    let v: i64 = row.try_get(column).map_err(failure)?;
    if v <= 0 || v as u64 > events::MAX_VERSION {
        return Err(state_failure());
    }
    Ok(v as u64)
}
