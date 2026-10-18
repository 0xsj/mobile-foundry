//! Audit-owned evidence, commit-ordered cursor and consumer inbox in one transaction.
use crate::{
    app::{
        self,
        ports::{Page, PortFuture, PrincipalStore, Record, Scope, Store},
    },
    domain::{self, Entry, Evidence},
};
use kernel::{Failure, id::Id};
use postgres_storage as pg;
use sha2::{Digest, Sha256};
use sqlx::{PgPool, Row, postgres::PgRow};
use std::time::{Duration, UNIX_EPOCH};
pub struct PostgresStore {
    pool: PgPool,
}
// Field order/capitalization and empty AccountID preserve Go's existing fingerprint.
pub fn fingerprint(e: &Evidence) -> Result<String, Failure> {
    #[derive(serde::Serialize)]
    #[allow(non_snake_case)]
    struct Wire<'a> {
        EventID: &'a str,
        AccountID: &'a str,
        ActorID: &'a str,
        Action: &'a str,
        Version: u64,
        OccurredAt: String,
        RequestID: &'a str,
        CorrelationID: &'a str,
        #[serde(skip_serializing_if = "Option::is_none")]
        PrincipalID: Option<&'a str>,
        #[serde(skip_serializing_if = "Option::is_none")]
        SessionID: Option<&'a str>,
        #[serde(skip_serializing_if = "Option::is_none")]
        Reason: Option<&'a str>,
        #[serde(skip_serializing_if = "Option::is_none")]
        FileID: Option<&'a str>,
    }
    let bytes = serde_json::to_vec(&Wire {
        EventID: e.event_id.as_str(),
        AccountID: e.account_id.as_ref().map_or("", Id::as_str),
        ActorID: e.actor_id.as_str(),
        Action: &e.action,
        Version: e.version,
        OccurredAt: events::time(e.occurred_at)?,
        RequestID: &e.request_id,
        CorrelationID: e.correlation_id.as_deref().unwrap_or(""),
        PrincipalID: e.principal_id.as_ref().map(Id::as_str),
        SessionID: e.session_id.as_ref().map(Id::as_str),
        Reason: e.reason.as_deref(),
        FileID: e.file_id.as_ref().map(Id::as_str),
    })
    .map_err(|_| pg::state_failure())?;
    Ok(format!("{:x}", Sha256::digest(bytes)))
}
fn restore(row: PgRow) -> Result<Record, Failure> {
    let correlation: String = row.try_get("correlation_id").map_err(pg::failure)?;
    let reason: String = row.try_get("reason").map_err(pg::failure)?;
    let evidence = Evidence {
        event_id: pg::id(&row, "event_id")?,
        account_id: pg::optional_id(&row, "account_id")?,
        actor_id: pg::id(&row, "actor_id")?,
        action: row.try_get("action").map_err(pg::failure)?,
        version: pg::version(&row, "version")?,
        occurred_at: pg::micros(&row, "occurred_at")?,
        request_id: row.try_get("request_id").map_err(pg::failure)?,
        correlation_id: (!correlation.is_empty()).then_some(correlation),
        principal_id: pg::optional_id(&row, "principal_id")?,
        session_id: pg::optional_id(&row, "session_id")?,
        reason: (!reason.is_empty()).then_some(reason),
        file_id: pg::optional_id(&row, "file_id")?,
    };
    let recorded = pg::micros(&row, "recorded_at")?;
    Ok(Record {
        sequence: pg::version(&row, "sequence")?,
        entry: Entry::new(evidence, UNIX_EPOCH + Duration::from_micros(recorded))
            .map_err(|_| pg::state_failure())?,
    })
}
impl PostgresStore {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
    async fn page(
        &self,
        scope: &dyn Scope,
        id: &Id,
        after: u64,
        limit: usize,
        personal: bool,
    ) -> Result<Page, Failure> {
        if !(1..=100).contains(&limit) || after > domain::MAX_VERSION {
            return Err(app::invalid_query());
        }
        let column = if personal {
            "principal_id"
        } else {
            "account_id"
        };
        let rows = pg::read(
            scope,
            sqlx::query(&format!(
                r#"SELECT sequence,
            event_id::text,
            account_id::text,
            actor_id::text,
            action,
            version,
            occurred_at,
            recorded_at,
            request_id,
            correlation_id,
            principal_id::text,
            session_id::text,
            reason,
            file_id::text
            FROM audit.entries
            WHERE {column}=$1::uuid
            AND sequence>$2
            ORDER BY sequence
            LIMIT $3"#
            ))
            .bind(id.as_str())
            .bind(after as i64)
            .bind((limit + 1) as i64)
            .fetch_all(&self.pool),
        )
        .await?;
        let mut records = rows
            .into_iter()
            .map(restore)
            .collect::<Result<Vec<_>, _>>()?;
        let next = if records.len() > limit {
            records.truncate(limit);
            Some(records[limit - 1].sequence)
        } else {
            None
        };
        Ok(Page { records, next })
    }
}
impl Store for PostgresStore {
    fn record<'a>(
        &'a self,
        scope: &'a dyn Scope,
        consumer: &'a str,
        entry: Entry,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if consumer.is_empty() || consumer.len() > 128 {
                return Err(domain::invalid());
            }
            Entry::new(
                entry.evidence().clone(),
                UNIX_EPOCH + Duration::from_micros(entry.recorded_at()),
            )?;
            let fingerprint = fingerprint(entry.evidence())?;
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                let sequence: i64 = sqlx::query_scalar(
                    "SELECT next_sequence FROM audit.append_state WHERE singleton=true FOR UPDATE"
                ).fetch_one(&mut *tx).await.map_err(pg::failure)?;
                let e = entry.evidence();
                let prior: Option<String> = sqlx::query_scalar(
                    "SELECT fingerprint FROM audit.inbox WHERE consumer=$1 AND event_id=$2::uuid"
                ).bind(consumer).bind(e.event_id.as_str())
                    .fetch_optional(&mut *tx).await.map_err(pg::failure)?;
                if let Some(prior) = prior {
                    return if prior == fingerprint { Ok(()) } else { Err(app::replay()) };
                }
                if sequence < 1 || sequence as u64 >= domain::MAX_VERSION {
                    return Err(domain::invalid());
                }
                sqlx::query(
                    "INSERT INTO audit.entries(
                        sequence,event_id,account_id,actor_id,action,version,
                        occurred_at,recorded_at,request_id,correlation_id,
                        principal_id,session_id,reason,file_id)
                     VALUES($1,$2::uuid,$3::uuid,$4::uuid,$5,$6,$7,$8,$9,$10,$11::uuid,$12::uuid,$13,$14::uuid)"
                )
                    .bind(sequence)
                    .bind(e.event_id.as_str())
                    .bind(e.account_id.as_ref().map(Id::as_str))
                    .bind(e.actor_id.as_str())
                    .bind(&e.action)
                    .bind(e.version as i64)
                    .bind(pg::time(e.occurred_at)?)
                    .bind(pg::time(entry.recorded_at())?)
                    .bind(&e.request_id)
                    .bind(e.correlation_id.as_deref().unwrap_or(""))
                    .bind(e.principal_id.as_ref().map(Id::as_str))
                    .bind(e.session_id.as_ref().map(Id::as_str))
                    .bind(e.reason.as_deref().unwrap_or(""))
                    .bind(e.file_id.as_ref().map(Id::as_str))
                    .execute(&mut *tx).await.map_err(pg::failure)?;
                sqlx::query(
                    "INSERT INTO audit.inbox(consumer,event_id,evidence_sequence,fingerprint)
                     VALUES($1,$2::uuid,$3,$4)"
                ).bind(consumer).bind(e.event_id.as_str()).bind(sequence).bind(&fingerprint)
                    .execute(&mut *tx).await.map_err(pg::failure)?;
                sqlx::query(
                    "UPDATE audit.append_state SET next_sequence=next_sequence+1 WHERE singleton=true"
                ).execute(&mut *tx).await.map_err(pg::failure)?;
                Ok(())
            }.await;
            pg::finish(scope, tx, result).await
        })
    }
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        after: u64,
        n: usize,
    ) -> PortFuture<'a, Page> {
        Box::pin(self.page(s, id, after, n, false))
    }
}
impl PrincipalStore for PostgresStore {
    fn list_principal<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        after: u64,
        n: usize,
    ) -> PortFuture<'a, Page> {
        Box::pin(self.page(s, id, after, n, true))
    }
}
