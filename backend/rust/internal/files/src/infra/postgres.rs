//! Metadata, receipt claim/finalization and required fact share an owned transaction.
use crate::{
    app::{
        self, Action, Command, Receipt, Target,
        ports::{PortFuture, Scope, Store},
    },
    domain::{self, File},
};
use kernel::{
    Failure,
    id::{Generator, Id},
};
use postgres_storage as pg;
use sqlx::{PgPool, Row, postgres::PgRow};
use std::sync::Arc;
pub struct PostgresStore {
    pool: PgPool,
    ids: Arc<dyn Generator>,
    outbox: pg::outbox::Leased,
}
const COLUMNS: &str = "id::text,account_id::text,status,version,snapshot";
fn restore(row: PgRow) -> Result<File, Failure> {
    let f = super::wire::decode(row.try_get("snapshot").map_err(pg::failure)?)?;
    let s = f.snapshot();
    if s.id != pg::id(&row, "id")?
        || s.account_id != pg::id(&row, "account_id")?
        || s.status.as_str() != row.try_get::<String, _>("status").map_err(pg::failure)?
        || s.version != pg::version(&row, "version")?
    {
        return Err(domain::invalid_state());
    }
    Ok(f)
}
fn receipt(row: PgRow, t: &Target, key: &str) -> Result<Receipt, Failure> {
    super::wire::admit_receipt(
        super::wire::decode_receipt(
            row.try_get::<Option<serde_json::Value>, _>("result")
                .map_err(pg::failure)?
                .ok_or_else(domain::invalid_state)?,
        )?,
        t,
        key,
    )
}
impl PostgresStore {
    pub fn new(pool: PgPool, ids: Arc<dyn Generator>) -> Result<Self, Failure> {
        let outbox = pg::outbox::Leased::new(
            pool.clone(),
            ids.new_id()?,
            queries(),
            |m, stream| {
                m.producer == "files"
                    && m.account_id.is_some()
                    && serde_json::from_slice::<serde_json::Value>(&m.data)
                        .ok()
                        .is_some_and(|d| d.get("file_id").and_then(|v| v.as_str()) == Some(stream))
            },
            app::exhausted,
        );
        Ok(Self { pool, ids, outbox })
    }
}
impl Store for PostgresStore {
    fn apply<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        c: Command,
        candidate: Option<Id>,
        now: u64,
    ) -> PortFuture<'a, Receipt> {
        Box::pin(async move {
            c.validate()?;
            let hash = super::wire::fingerprint(&c)?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async{
                if !c.key.is_empty(){
                    let claim=sqlx::query("INSERT INTO files.receipts(account_id,principal_id,key,fingerprint) VALUES($1::uuid,$2::uuid,$3,$4) ON CONFLICT DO NOTHING")
                        .bind(t.account.as_str()).bind(t.actor.as_str()).bind(&c.key).bind(&hash).execute(&mut *tx).await.map_err(pg::failure)?;
                    if claim.rows_affected()==0{
                        let row=sqlx::query("SELECT fingerprint,result FROM files.receipts WHERE account_id=$1::uuid AND principal_id=$2::uuid AND key=$3")
                            .bind(t.account.as_str()).bind(t.actor.as_str()).bind(&c.key).fetch_one(&mut *tx).await.map_err(pg::failure)?;
                        if row.try_get::<Vec<u8>,_>("fingerprint").map_err(pg::failure)?!=hash{return Err(app::key_conflict());}
                        return receipt(row,t,&c.key);
                    }
                }
                let current=if let Some(id)=&c.file_id{
                    sqlx::query(&format!("SELECT {COLUMNS} FROM files.records WHERE account_id=$1::uuid AND id=$2::uuid FOR UPDATE"))
                        .bind(t.account.as_str()).bind(id.as_str()).fetch_optional(&mut *tx).await.map_err(pg::failure)?.map(restore).transpose()?
                }else{None};
                let f=app::plan(t,&c,current.as_ref(),candidate,now)?;let v=f.snapshot();
                let query=if c.action==Action::Request{
                    sqlx::query("INSERT INTO files.records(id,account_id,status,version,snapshot) VALUES($1::uuid,$2::uuid,$3,$4,$5)")
                        .bind(v.id.as_str()).bind(v.account_id.as_str()).bind(v.status.as_str()).bind(v.version as i64).bind(super::wire::encode(&f)?)
                }else{
                    sqlx::query("UPDATE files.records SET status=$3,version=$4,snapshot=$5 WHERE id=$1::uuid AND account_id=$2::uuid")
                        .bind(v.id.as_str()).bind(v.account_id.as_str()).bind(v.status.as_str()).bind(v.version as i64).bind(super::wire::encode(&f)?)
                };
                let changed=query.execute(&mut *tx).await.map_err(|e|if pg::constraint(&e,"records_pkey"){app::conflict()}else{pg::failure(e)})?;
                if changed.rows_affected()!=1{return Err(domain::invalid_state());}
                let message=super::events::message(v,&t.actor,s.metadata(),self.ids.as_ref())?;
                sqlx::query("INSERT INTO files.outbox(event_id,stream_id,aggregate_version,envelope) VALUES($1::uuid,$2::uuid,$3,$4)")
                    .bind(message.id.as_str()).bind(v.id.as_str()).bind(v.version as i64).bind(pg::outbox::encode(&message)?).execute(&mut *tx).await.map_err(pg::failure)?;
                let r=Receipt{key:c.key.clone(),action:c.action,actor:t.actor.clone(),file:v.clone()};
                if !c.key.is_empty(){
                    let finalized=sqlx::query("UPDATE files.receipts SET result=$4 WHERE account_id=$1::uuid AND principal_id=$2::uuid AND key=$3")
                        .bind(t.account.as_str()).bind(t.actor.as_str()).bind(&c.key).bind(super::wire::encode_receipt(&r)?).execute(&mut *tx).await.map_err(pg::failure)?;
                    if finalized.rows_affected()!=1{return Err(domain::invalid_state());}
                }Ok(r)
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn replay<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        c: &'a Command,
    ) -> PortFuture<'a, Option<Receipt>> {
        Box::pin(async move {
            c.validate()?;
            s.check()?;
            if c.key.is_empty() {
                return Ok(None);
            }
            let row=pg::read(s,sqlx::query("SELECT fingerprint,result FROM files.receipts WHERE account_id=$1::uuid AND principal_id=$2::uuid AND key=$3")
                .bind(t.account.as_str()).bind(t.actor.as_str()).bind(&c.key).fetch_optional(&self.pool)).await?;
            row.map(|row| {
                if row
                    .try_get::<Vec<u8>, _>("fingerprint")
                    .map_err(pg::failure)?
                    != super::wire::fingerprint(c)?
                {
                    return Err(app::key_conflict());
                }
                receipt(row, t, &c.key)
            })
            .transpose()
        })
    }
    fn receipt<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        key: &'a str,
    ) -> PortFuture<'a, Option<Receipt>> {
        Box::pin(async move {
            pg::read(s,sqlx::query("SELECT result FROM files.receipts WHERE account_id=$1::uuid AND principal_id=$2::uuid AND key=$3")
                .bind(t.account.as_str()).bind(t.actor.as_str()).bind(key).fetch_optional(&self.pool)).await?.map(|row|receipt(row,t,key)).transpose()
        })
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, a: &'a Id, id: &'a Id) -> PortFuture<'a, Option<File>> {
        Box::pin(async move {
            pg::read(
                s,
                sqlx::query(&format!(
                    "SELECT {COLUMNS} FROM files.records WHERE account_id=$1::uuid AND id=$2::uuid"
                ))
                .bind(a.as_str())
                .bind(id.as_str())
                .fetch_optional(&self.pool),
            )
            .await?
            .map(restore)
            .transpose()
        })
    }
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<File>> {
        Box::pin(async move {
            if !(1..=101).contains(&limit) {
                return Err(app::invalid());
            }
            pg::read(s,sqlx::query(&format!("SELECT {COLUMNS} FROM files.records WHERE account_id=$1::uuid AND id>$2::uuid AND status<>'deleted' ORDER BY id LIMIT $3"))
                .bind(a.as_str()).bind(after.map_or("00000000-0000-0000-0000-000000000000",Id::as_str)).bind(limit as i64).fetch_all(&self.pool)).await?.into_iter().map(restore).collect()
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
    fn failed<'a>(&'a self, s: &'a dyn Scope, id: &'a Id, code: &'a str) -> PortFuture<'a, ()> {
        Box::pin(self.outbox.failed(s, id, code))
    }
    fn ready<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(self.outbox.ready(s))
    }
}
fn queries() -> pg::outbox::Queries {
    pg::outbox::Queries {
        pending: "SELECT event_id::text,stream_id::text,aggregate_version,envelope FROM files.outbox candidate WHERE delivered_at IS NULL AND NOT dead AND (lease_until IS NULL OR lease_until<=clock_timestamp()) AND NOT EXISTS(SELECT 1 FROM files.outbox prior WHERE prior.stream_id=candidate.stream_id AND prior.sequence<candidate.sequence AND prior.delivered_at IS NULL) ORDER BY sequence FOR UPDATE SKIP LOCKED LIMIT 1",
        claim: "UPDATE files.outbox SET lease_owner=$1::uuid,lease_until=clock_timestamp()+interval '5 seconds' WHERE event_id=$2::uuid",
        poison: "UPDATE files.outbox SET dead=true,failure_code='invalid_envelope',lease_owner=NULL,lease_until=NULL WHERE event_id=$1::uuid",
        delivered: "UPDATE files.outbox SET delivered_at=clock_timestamp(),lease_owner=NULL,lease_until=NULL WHERE event_id=$1::uuid AND lease_owner=$2::uuid AND delivered_at IS NULL AND NOT dead",
        failed: "UPDATE files.outbox SET attempts=attempts+1,failure_code=$3,dead=(attempts+1>=5),lease_owner=NULL,lease_until=NULL WHERE event_id=$1::uuid AND lease_owner=$2::uuid AND delivered_at IS NULL AND NOT dead",
        ready: "SELECT EXISTS(SELECT 1 FROM files.outbox WHERE dead)",
    }
}
