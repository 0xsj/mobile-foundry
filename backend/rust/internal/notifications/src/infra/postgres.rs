//! One scope lock serializes decisions, settings, read marks and page snapshots.
mod installations;
use crate::{
    app::{
        self, Delivery, ListInput, Page, Target,
        ports::{PortFuture, Scope, Store},
    },
    domain::{self, Item, Snapshot},
};
use kernel::{Failure, id::Id};
use postgres_storage as pg;
use sqlx::{PgConnection, PgPool, Row, postgres::PgRow};
pub struct PostgresStore {
    pool: PgPool,
}
impl PostgresStore {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}
async fn lock_scope(conn: &mut PgConnection, t: &Target) -> Result<(bool, u64), Failure> {
    sqlx::query("INSERT INTO notifications.scope_state(account_id,principal_id) VALUES($1::uuid,$2::uuid) ON CONFLICT DO NOTHING")
        .bind(t.account.as_str()).bind(t.actor.as_str()).execute(&mut *conn).await.map_err(pg::failure)?;
    let row=sqlx::query("SELECT media_enabled,next_sequence FROM notifications.scope_state WHERE account_id=$1::uuid AND principal_id=$2::uuid FOR UPDATE")
        .bind(t.account.as_str()).bind(t.actor.as_str()).fetch_one(conn).await.map_err(pg::failure)?;
    Ok((
        row.try_get("media_enabled").map_err(pg::failure)?,
        pg::version(&row, "next_sequence")?,
    ))
}
const COLUMNS: &str = "id::text,account_id::text,principal_id::text,file_id::text,sequence,type,category,occurred_at,created_at,read_at";
fn restore(row: PgRow) -> Result<Item, Failure> {
    Item::restore(Snapshot {
        id: pg::id(&row, "id")?,
        account_id: pg::id(&row, "account_id")?,
        principal_id: pg::id(&row, "principal_id")?,
        file_id: pg::id(&row, "file_id")?,
        sequence: pg::version(&row, "sequence")?,
        kind: row.try_get("type").map_err(pg::failure)?,
        category: row.try_get("category").map_err(pg::failure)?,
        occurred_at: pg::micros(&row, "occurred_at")?,
        created_at: pg::micros(&row, "created_at")?,
        read_at: pg::optional_micros(&row, "read_at")?,
    })
}
impl Store for PostgresStore {
    fn deliver<'a>(&'a self, s: &'a dyn Scope, d: Delivery, at: u64) -> PortFuture<'a, ()> {
        Box::pin(async move {
            d.validate()?;
            if at > domain::MAX_TIME {
                return Err(app::invalid());
            }
            let hash = super::records::fingerprint(&d)?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async{
                let claimed=sqlx::query("INSERT INTO notifications.inbox(consumer,event_id,fingerprint) VALUES('notifications.files',$1::uuid,$2) ON CONFLICT DO NOTHING")
                    .bind(d.event_id.as_str()).bind(&hash).execute(&mut *tx).await.map_err(pg::failure)?;
                if claimed.rows_affected()==0{
                    let prior:String=sqlx::query_scalar("SELECT fingerprint FROM notifications.inbox WHERE consumer='notifications.files' AND event_id=$1::uuid")
                        .bind(d.event_id.as_str()).fetch_one(&mut *tx).await.map_err(pg::failure)?;
                    return if prior==hash{Ok(())}else{Err(app::replay_conflict())};
                }
                let t=d.target();let (enabled,next)=lock_scope(&mut tx,&t).await?;
                if !enabled{return Ok(());}
                if next>=domain::MAX_SEQUENCE{return Err(app::capacity());}
                let item=d.item(next,at)?;let v=item.snapshot();
                sqlx::query("INSERT INTO notifications.items(id,account_id,principal_id,file_id,sequence,type,category,occurred_at,created_at) VALUES($1::uuid,$2::uuid,$3::uuid,$4::uuid,$5,$6,$7,$8,$9)")
                    .bind(v.id.as_str()).bind(v.account_id.as_str()).bind(v.principal_id.as_str()).bind(v.file_id.as_str())
                    .bind(v.sequence as i64).bind(&v.kind).bind(&v.category).bind(pg::time(v.occurred_at)?).bind(pg::time(v.created_at)?)
                    .execute(&mut *tx).await.map_err(pg::failure)?;
                sqlx::query("UPDATE notifications.scope_state SET next_sequence=next_sequence+1 WHERE account_id=$1::uuid AND principal_id=$2::uuid")
                    .bind(t.account.as_str()).bind(t.actor.as_str()).execute(&mut *tx).await.map_err(pg::failure)?;
                Ok(())
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn list<'a>(&'a self, s: &'a dyn Scope, input: ListInput) -> PortFuture<'a, Page> {
        Box::pin(async move {
            input.validate()?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async{
                lock_scope(&mut tx,&input.target).await?;
                let count:i64=sqlx::query_scalar("SELECT count(*) FROM notifications.items WHERE account_id=$1::uuid AND principal_id=$2::uuid AND read_at IS NULL")
                    .bind(input.target.account.as_str()).bind(input.target.actor.as_str()).fetch_one(&mut *tx).await.map_err(pg::failure)?;
                let unread_count:u64=count.try_into().map_err(|_|domain::invalid_state())?;
                if unread_count>domain::MAX_SEQUENCE{return Err(domain::invalid_state());}
                let rows=sqlx::query(&format!("SELECT {COLUMNS} FROM notifications.items WHERE account_id=$1::uuid AND principal_id=$2::uuid AND ($3::bigint=0 OR sequence<$3) AND (NOT $4::boolean OR read_at IS NULL) ORDER BY sequence DESC LIMIT $5"))
                    .bind(input.target.account.as_str()).bind(input.target.actor.as_str()).bind(input.before as i64).bind(input.unread_only).bind((input.limit+1) as i64)
                    .fetch_all(&mut *tx).await.map_err(pg::failure)?;
                let mut items=rows.into_iter().map(restore).collect::<Result<Vec<_>,_>>()?;
                let next=if items.len()>input.limit{items.truncate(input.limit);items.last().map(|i|i.snapshot().sequence)}else{None};
                Ok(Page{items,next,unread_count})
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn mark_read<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        id: &'a Id,
        at: u64,
    ) -> PortFuture<'a, Item> {
        Box::pin(async move {
            if at > domain::MAX_TIME {
                return Err(app::invalid());
            }
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async{
                lock_scope(&mut tx,t).await?;
                let row=sqlx::query(&format!("UPDATE notifications.items SET read_at=COALESCE(read_at,$4) WHERE account_id=$1::uuid AND principal_id=$2::uuid AND id=$3::uuid RETURNING {COLUMNS}"))
                    .bind(t.account.as_str()).bind(t.actor.as_str()).bind(id.as_str()).bind(pg::time(at)?).fetch_optional(&mut *tx).await.map_err(pg::failure)?.ok_or_else(app::missing)?;
                restore(row)
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn media_enabled<'a>(&'a self, s: &'a dyn Scope, t: &'a Target) -> PortFuture<'a, bool> {
        Box::pin(async move {
            Ok(pg::read(s,sqlx::query_scalar("SELECT media_enabled FROM notifications.scope_state WHERE account_id=$1::uuid AND principal_id=$2::uuid")
                .bind(t.account.as_str()).bind(t.actor.as_str()).fetch_optional(&self.pool)).await?.unwrap_or(true))
        })
    }
    fn set_media_enabled<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        enabled: bool,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async{
                lock_scope(&mut tx,t).await?;
                sqlx::query("UPDATE notifications.scope_state SET media_enabled=$3 WHERE account_id=$1::uuid AND principal_id=$2::uuid")
                    .bind(t.account.as_str()).bind(t.actor.as_str()).bind(enabled).execute(&mut *tx).await.map_err(pg::failure)?;Ok(())
            }.await;
            pg::finish(s, tx, result).await
        })
    }
}
