//! Notes owns its receipt claim, locked current record and receipt finalization.
use crate::{
    app::ports::{PortFuture, Scope, Store},
    domain::{self, Action, Note, Receipt, Write},
};
use kernel::{Failure, id::Id};
use postgres_storage as pg;
use sqlx::{PgPool, Row, postgres::PgRow};
pub struct PostgresStore {
    pool: PgPool,
}
impl PostgresStore {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}
fn restore(row: PgRow) -> Result<Note, Failure> {
    let title: Vec<u8> = row.try_get("title").map_err(pg::failure)?;
    Note::restore(
        pg::id(&row, "id")?,
        String::from_utf8(title).map_err(|_| domain::invalid_state())?,
        pg::version(&row, "version")?,
    )
}
fn receipt(row: PgRow, account: &Id, actor: &Id, command: &Id) -> Result<Receipt, Failure> {
    let bytes: Option<Vec<u8>> = row.try_get("result").map_err(pg::failure)?;
    let r = super::wire::decode(&bytes.ok_or_else(domain::invalid_state)?)?;
    let s = r.snapshot();
    if &s.account_id != account || &s.principal_id != actor || &s.command_id != command {
        return Err(domain::invalid_state());
    }
    Ok(r)
}
impl Store for PostgresStore {
    fn apply<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        actor: &'a Id,
        w: Write,
        candidate: Option<Id>,
    ) -> PortFuture<'a, Receipt> {
        Box::pin(async move {
            w.validate()?;
            let fingerprint = super::wire::fingerprint(&w)?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async {
            let claim=sqlx::query(
                "INSERT INTO notes.receipts(account_id,principal_id,command_id,fingerprint)
                 VALUES($1::uuid,$2::uuid,$3::uuid,$4) ON CONFLICT DO NOTHING"
            ).bind(account.as_str()).bind(actor.as_str()).bind(w.command_id.as_str()).bind(&fingerprint)
                .execute(&mut *tx).await.map_err(pg::failure)?;
            if claim.rows_affected()==0 {
                // Read Committed's next statement observes a competing committed claim.
                let row=sqlx::query(
                    "SELECT fingerprint,result FROM notes.receipts
                     WHERE account_id=$1::uuid AND principal_id=$2::uuid AND command_id=$3::uuid"
                ).bind(account.as_str()).bind(actor.as_str()).bind(w.command_id.as_str())
                    .fetch_one(&mut *tx).await.map_err(pg::failure)?;
                let saved:Vec<u8>=row.try_get("fingerprint").map_err(pg::failure)?;
                if saved!=fingerprint{return Err(domain::command_conflict());}
                return receipt(row,account,actor,&w.command_id);
            }
            let current=if let Some(id)=&w.note_id {
                sqlx::query("SELECT id::text,title,version FROM notes.records
                             WHERE account_id=$1::uuid AND id=$2::uuid FOR UPDATE")
                    .bind(account.as_str()).bind(id.as_str()).fetch_optional(&mut *tx).await.map_err(pg::failure)?.map(restore).transpose()?
            }else{None};
            let r=domain::decide(&w,current.as_ref(),candidate,account,actor)?;let v=r.snapshot();
            let query=match w.action {
                Action::Create=>sqlx::query("INSERT INTO notes.records(account_id,id,title,version) VALUES($1::uuid,$2::uuid,$3,$4)")
                    .bind(account.as_str()).bind(v.note_id.as_str()).bind(v.title.as_bytes()).bind(v.version as i64),
                Action::Update=>sqlx::query("UPDATE notes.records SET title=$3,version=$4 WHERE account_id=$1::uuid AND id=$2::uuid")
                    .bind(account.as_str()).bind(v.note_id.as_str()).bind(v.title.as_bytes()).bind(v.version as i64),
                Action::Delete=>sqlx::query("DELETE FROM notes.records WHERE account_id=$1::uuid AND id=$2::uuid")
                    .bind(account.as_str()).bind(v.note_id.as_str()),
            };
            let changed=query.execute(&mut *tx).await.map_err(|e|if pg::constraint(&e,"records_pkey"){domain::conflict()}else{pg::failure(e)})?;
            if changed.rows_affected()!=1{return Err(domain::invalid_state());}
            let finalized=sqlx::query("UPDATE notes.receipts SET result=$4
                WHERE account_id=$1::uuid AND principal_id=$2::uuid AND command_id=$3::uuid")
                .bind(account.as_str()).bind(actor.as_str()).bind(w.command_id.as_str()).bind(super::wire::encode(&r)?)
                .execute(&mut *tx).await.map_err(pg::failure)?;
            if finalized.rows_affected()!=1{return Err(domain::invalid_state());}Ok(r)
        }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn receipt<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Id,
        p: &'a Id,
        c: &'a Id,
    ) -> PortFuture<'a, Option<Receipt>> {
        Box::pin(async move {
            pg::read(
                s,
                sqlx::query(
                    "SELECT result FROM notes.receipts
            WHERE account_id=$1::uuid AND principal_id=$2::uuid AND command_id=$3::uuid",
                )
                .bind(a.as_str())
                .bind(p.as_str())
                .bind(c.as_str())
                .fetch_optional(&self.pool),
            )
            .await?
            .map(|row| receipt(row, a, p, c))
            .transpose()
        })
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, a: &'a Id, id: &'a Id) -> PortFuture<'a, Option<Note>> {
        Box::pin(async move {
            pg::read(s,sqlx::query("SELECT id::text,title,version FROM notes.records WHERE account_id=$1::uuid AND id=$2::uuid")
            .bind(a.as_str()).bind(id.as_str()).fetch_optional(&self.pool)).await?.map(restore).transpose()
        })
    }
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<Note>> {
        Box::pin(async move {
            if !(1..=101).contains(&limit) {
                return Err(domain::invalid_write());
            }
            pg::read(
                s,
                sqlx::query(
                    "SELECT id::text,title,version FROM notes.records
            WHERE account_id=$1::uuid AND id>$2::uuid ORDER BY id LIMIT $3",
                )
                .bind(a.as_str())
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
