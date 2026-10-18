//! Lease mechanics. Every query and semantic stream guard is supplied by the producer.
use crate::{begin, failure, finish, read, state_failure};
use events::Envelope;
use kernel::{
    Failure,
    context::{Metadata, Scope},
    id::Id,
};
use serde::{Deserialize, Serialize};
use sqlx::{PgPool, Row};
#[derive(Serialize, Deserialize)]
struct Wire {
    id: String,
    name: String,
    producer: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    account_id: Option<String>,
    actor_id: String,
    aggregate_version: u64,
    occurred_at: String,
    #[serde(default)]
    metadata: WireMetadata,
    data: serde_json::Value,
}
#[derive(Default, Serialize, Deserialize)]
struct WireMetadata {
    #[serde(default, skip_serializing_if = "String::is_empty")]
    request_id: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    correlation_id: String,
}
pub fn encode(message: &Envelope) -> Result<serde_json::Value, Failure> {
    message.validate()?;
    serde_json::to_value(Wire {
        id: message.id.as_str().into(),
        name: message.name.clone(),
        producer: message.producer.clone(),
        account_id: message.account_id.as_ref().map(|v| v.as_str().into()),
        actor_id: message.actor_id.as_str().into(),
        aggregate_version: message.version,
        occurred_at: events::time(message.occurred_at)?,
        metadata: WireMetadata {
            request_id: message.metadata.request_id.clone(),
            correlation_id: message.metadata.correlation_id.clone().unwrap_or_default(),
        },
        data: serde_json::from_slice(&message.data).map_err(|_| state_failure())?,
    })
    .map_err(|_| state_failure())
}
pub fn decode(value: serde_json::Value) -> Result<Envelope, Failure> {
    let wire: Wire = serde_json::from_value(value).map_err(|_| state_failure())?;
    let time =
        chrono::DateTime::parse_from_rfc3339(&wire.occurred_at).map_err(|_| state_failure())?;
    if time.offset().local_minus_utc() != 0 || time.timestamp_subsec_nanos() % 1000 != 0 {
        return Err(state_failure());
    }
    let message = Envelope {
        id: Id::parse(&wire.id).map_err(|_| state_failure())?,
        name: wire.name,
        producer: wire.producer,
        account_id: wire
            .account_id
            .filter(|v| !v.is_empty())
            .map(|v| Id::parse(&v).map_err(|_| state_failure()))
            .transpose()?,
        actor_id: Id::parse(&wire.actor_id).map_err(|_| state_failure())?,
        version: wire.aggregate_version,
        occurred_at: time
            .timestamp_micros()
            .try_into()
            .map_err(|_| state_failure())?,
        metadata: Metadata {
            request_id: wire.metadata.request_id,
            correlation_id: (!wire.metadata.correlation_id.is_empty())
                .then_some(wire.metadata.correlation_id),
        },
        data: serde_json::to_vec(&wire.data).map_err(|_| state_failure())?,
    };
    message.validate().map_err(|_| state_failure())?;
    Ok(message)
}
pub struct Queries {
    pub pending: &'static str,
    pub claim: &'static str,
    pub poison: &'static str,
    pub delivered: &'static str,
    pub failed: &'static str,
    pub ready: &'static str,
}
pub struct Leased {
    pub pool: PgPool,
    owner: Id,
    queries: Queries,
    guard: fn(&Envelope, &str) -> bool,
    exhausted: fn() -> Failure,
}
impl Leased {
    pub fn new(
        pool: PgPool,
        owner: Id,
        queries: Queries,
        guard: fn(&Envelope, &str) -> bool,
        exhausted: fn() -> Failure,
    ) -> Self {
        Self {
            pool,
            owner,
            queries,
            guard,
            exhausted,
        }
    }
    pub async fn pending(&self, scope: &dyn Scope, limit: usize) -> Result<Vec<Envelope>, Failure> {
        if !(1..=32).contains(&limit) {
            return Err(events::invalid());
        }
        let mut tx = begin(scope, &self.pool).await?;
        let result = async {
            let Some(row) = sqlx::query(self.queries.pending)
                .fetch_optional(&mut *tx)
                .await
                .map_err(failure)?
            else {
                return Ok((vec![], false));
            };
            let identity: String = row.try_get("event_id").map_err(failure)?;
            let stream: String = row.try_get("stream_id").map_err(failure)?;
            let version: i64 = row.try_get("aggregate_version").map_err(failure)?;
            let message = row.try_get("envelope").map_err(failure).and_then(decode);
            let valid = message.as_ref().is_ok_and(|m| {
                m.id.as_str() == identity
                    && version > 0
                    && m.version == version as u64
                    && (self.guard)(m, &stream)
            });
            if !valid {
                sqlx::query(self.queries.poison)
                    .bind(&identity)
                    .execute(&mut *tx)
                    .await
                    .map_err(failure)?;
                return Ok((vec![], true));
            }
            sqlx::query(self.queries.claim)
                .bind(self.owner.as_str())
                .bind(&identity)
                .execute(&mut *tx)
                .await
                .map_err(failure)?;
            Ok((vec![message?], false))
        }
        .await;
        let (messages, poison) = finish(scope, tx, result).await?;
        if poison {
            return Err(state_failure());
        }
        Ok(messages)
    }
    pub async fn delivered(&self, scope: &dyn Scope, id: &Id) -> Result<(), Failure> {
        let result = read(
            scope,
            sqlx::query(self.queries.delivered)
                .bind(id.as_str())
                .bind(self.owner.as_str())
                .execute(&self.pool),
        )
        .await?;
        if result.rows_affected() != 1 {
            return Err(events::invalid());
        }
        Ok(())
    }
    pub async fn failed(&self, scope: &dyn Scope, id: &Id, code: &str) -> Result<(), Failure> {
        if code.is_empty() || code.len() > 128 {
            return Err(events::invalid());
        }
        let result = read(
            scope,
            sqlx::query(self.queries.failed)
                .bind(id.as_str())
                .bind(self.owner.as_str())
                .bind(code)
                .execute(&self.pool),
        )
        .await?;
        if result.rows_affected() != 1 {
            return Err(events::invalid());
        }
        Ok(())
    }
    pub async fn ready(&self, scope: &dyn Scope) -> Result<(), Failure> {
        let dead: bool = read(
            scope,
            sqlx::query_scalar(self.queries.ready).fetch_one(&self.pool),
        )
        .await?;
        if dead {
            return Err((self.exhausted)());
        }
        Ok(())
    }
}
