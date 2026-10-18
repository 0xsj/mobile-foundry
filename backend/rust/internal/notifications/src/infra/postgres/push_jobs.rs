use super::*;
use crate::{
    app::push_jobs::{self, JobResult, Owner, PushJobs},
    domain::push_job::{self, Code, Intent, PushJob, Snapshot, Status},
};
const COLUMNS: &str = "id::text,installation_id::text,principal_id::text,session_id::text,installation_version,status,attempts,claim::text,created_at,updated_at,expires_at,next_at,lease_until,code,retired,retry_of::text,duplicate_risk_accepted";
fn optional_id(row: &PgRow, name: &str) -> Result<Option<Id>, Failure> {
    let value: Option<String> = row.try_get(name).map_err(pg::failure)?;
    value
        .map(|v| Id::parse(&v).map_err(|_| pg::state_failure()))
        .transpose()
}
fn restore(row: PgRow) -> Result<PushJob, Failure> {
    let attempts: i32 = row.try_get("attempts").map_err(pg::failure)?;
    let status: String = row.try_get("status").map_err(pg::failure)?;
    let code: String = row.try_get("code").map_err(pg::failure)?;
    PushJob::restore(Snapshot {
        intent: Intent {
            id: pg::id(&row, "id")?,
            installation_id: pg::id(&row, "installation_id")?,
            principal_id: pg::id(&row, "principal_id")?,
            session_id: pg::id(&row, "session_id")?,
            installation_version: pg::version(&row, "installation_version")?,
            retry_of: optional_id(&row, "retry_of")?,
            duplicate_risk_accepted: row
                .try_get("duplicate_risk_accepted")
                .map_err(pg::failure)?,
        },
        status: Status::parse(&status)?,
        attempts: attempts.try_into().map_err(|_| pg::state_failure())?,
        claim: optional_id(&row, "claim")?,
        created_at: pg::micros(&row, "created_at")?,
        updated_at: pg::micros(&row, "updated_at")?,
        expires_at: pg::micros(&row, "expires_at")?,
        next_at: pg::micros(&row, "next_at")?,
        lease_until: pg::optional_micros(&row, "lease_until")?,
        code: Code::parse(&code)?,
        retired: row.try_get("retired").map_err(pg::failure)?,
    })
}
async fn load(c: &mut PgConnection, id: &Id, locked: bool) -> Result<Option<PushJob>, Failure> {
    sqlx::query(&format!(
        "SELECT {COLUMNS} FROM notifications.push_jobs WHERE id=$1::uuid {}",
        if locked { "FOR UPDATE" } else { "" }
    ))
    .bind(id.as_str())
    .fetch_optional(c)
    .await
    .map_err(pg::failure)?
    .map(restore)
    .transpose()
}
async fn save(c: &mut PgConnection, j: &PushJob) -> Result<(), Failure> {
    let v = j.snapshot();
    sqlx::query("UPDATE notifications.push_jobs SET status=$2,attempts=$3,claim=$4::uuid,updated_at=$5,next_at=$6,lease_until=$7,code=$8,retired=$9 WHERE id=$1::uuid").bind(v.intent.id.as_str()).bind(v.status.as_str()).bind(i32::from(v.attempts)).bind(v.claim.as_ref().map(Id::as_str)).bind(pg::time(v.updated_at)?).bind(pg::time(v.next_at)?).bind(v.lease_until.map(pg::time).transpose()?).bind(v.code.as_str()).bind(v.retired).execute(c).await.map_err(pg::failure)?;
    Ok(())
}
async fn guard(c: &mut PgConnection) -> Result<(), Failure> {
    sqlx::query("SELECT singleton FROM notifications.push_job_guard FOR UPDATE")
        .execute(c)
        .await
        .map_err(pg::failure)?;
    Ok(())
}
async fn insert(c: &mut PgConnection, j: &PushJob) -> Result<(), Failure> {
    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM notifications.push_jobs")
        .fetch_one(&mut *c)
        .await
        .map_err(pg::failure)?;
    if count >= 10000 {
        return Err(app::capacity());
    }
    let v = j.snapshot();
    let i = &v.intent;
    sqlx::query("INSERT INTO notifications.push_jobs(id,installation_id,principal_id,session_id,installation_version,status,attempts,created_at,updated_at,expires_at,next_at,code,retired,retry_of,duplicate_risk_accepted) VALUES($1::uuid,$2::uuid,$3::uuid,$4::uuid,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14::uuid,$15)")
        .bind(i.id.as_str()).bind(i.installation_id.as_str()).bind(i.principal_id.as_str()).bind(i.session_id.as_str()).bind(i.installation_version as i64).bind(v.status.as_str()).bind(i32::from(v.attempts)).bind(pg::time(v.created_at)?).bind(pg::time(v.updated_at)?).bind(pg::time(v.expires_at)?).bind(pg::time(v.next_at)?).bind(v.code.as_str()).bind(v.retired).bind(i.retry_of.as_ref().map(Id::as_str)).bind(i.duplicate_risk_accepted).execute(c).await.map_err(pg::failure)?;
    Ok(())
}
impl PostgresStore {
    async fn change_job(
        &self,
        s: &dyn Scope,
        id: &Id,
        change: impl FnOnce(PushJob) -> Result<PushJob, Failure>,
    ) -> Result<(), Failure> {
        let mut tx = pg::begin(s, &self.pool).await?;
        let result = async {
            let prior = load(&mut tx, id, true)
                .await?
                .ok_or_else(push_job::claim_lost)?;
            let next = change(prior)?;
            save(&mut tx, &next).await
        }
        .await;
        pg::finish(s, tx, result).await
    }
}
impl PushJobs for PostgresStore {
    fn find<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<PushJob>> {
        Box::pin(async move {
            let row = pg::read(
                s,
                sqlx::query(&format!(
                    "SELECT {COLUMNS} FROM notifications.push_jobs WHERE id=$1::uuid"
                ))
                .bind(id.as_str())
                .fetch_optional(&self.pool),
            )
            .await?;
            row.map(restore).transpose()
        })
    }
    fn queue<'a>(&'a self, s: &'a dyn Scope, job: PushJob) -> PortFuture<'a, PushJob> {
        Box::pin(async move {
            let v = job.snapshot();
            if v.status != Status::Queued || v.attempts != 0 || v.intent.retry_of.is_some() {
                return Err(app::invalid());
            }
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                guard(&mut tx).await?;
                if let Some(prior) = load(&mut tx, &v.intent.id, false).await? {
                    return if prior.same_intent(&job) {
                        Ok(prior)
                    } else {
                        Err(push_jobs::replay())
                    };
                }
                insert(&mut tx, &job).await?;
                Ok(job.clone())
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
    fn claim<'a>(
        &'a self,
        s: &'a dyn Scope,
        token: Id,
        now: u64,
    ) -> PortFuture<'a, Option<(PushJob, bool)>> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result=async{
        let row=sqlx::query(&format!("SELECT {COLUMNS} FROM notifications.push_jobs WHERE (status='queued' AND (next_at<=$1 OR expires_at<=$1)) OR (status='leased' AND (lease_until<=$1 OR expires_at<=$1)) OR (status='sending' AND lease_until<=$1) ORDER BY created_at,id FOR UPDATE SKIP LOCKED LIMIT 1")).bind(pg::time(now)?).fetch_optional(&mut *tx).await.map_err(pg::failure)?;
        let Some(row)=row else{return Ok(None)};let (next,claimed)=restore(row)?.claim_next(token,now)?;save(&mut tx,&next).await?;Ok(Some((next,claimed)))
    }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn begin<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        token: &'a Id,
        now: u64,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move { self.change_job(s, id, |j| j.begin(token, now)).await })
    }
    fn finish<'a>(
        &'a self,
        s: &'a dyn Scope,
        id: &'a Id,
        token: &'a Id,
        r: JobResult,
        now: u64,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            self.change_job(s, id, |j| {
                j.finish(token, r.started, r.status, r.code, r.retired, now)
            })
            .await
        })
    }
    fn cancel<'a>(
        &'a self,
        s: &'a dyn Scope,
        owner: &'a Owner,
        now: u64,
    ) -> PortFuture<'a, PushJob> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                let prior = load(&mut tx, &owner.id, true)
                    .await?
                    .filter(|j| owner.matches(j))
                    .ok_or_else(app::missing)?;
                let next = prior.cancel(now)?;
                if prior.snapshot().status != Status::Canceled {
                    save(&mut tx, &next).await?;
                }
                Ok(next)
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
    fn replace<'a>(&'a self, s: &'a dyn Scope, job: PushJob) -> PortFuture<'a, PushJob> {
        Box::pin(async move {
            let v = job.snapshot();
            let source = v.intent.retry_of.as_ref().ok_or_else(app::invalid)?;
            if v.status != Status::Queued || v.attempts != 0 {
                return Err(app::invalid());
            }
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                guard(&mut tx).await?;
                if let Some(prior) = load(&mut tx, &v.intent.id, false).await? {
                    return if prior.same_intent(&job) {
                        Ok(prior)
                    } else {
                        Err(push_jobs::replay())
                    };
                }
                let owner = Owner {
                    id: source.clone(),
                    installation_id: v.intent.installation_id.clone(),
                    principal_id: v.intent.principal_id.clone(),
                    session_id: v.intent.session_id.clone(),
                };
                let parent = load(&mut tx, source, true)
                    .await?
                    .filter(|j| owner.matches(j))
                    .ok_or_else(app::missing)?;
                let next = parent.replacement(&job)?;
                let exists: bool = sqlx::query_scalar(
                    "SELECT EXISTS(SELECT 1 FROM notifications.push_jobs WHERE retry_of=$1::uuid)",
                )
                .bind(source.as_str())
                .fetch_one(&mut *tx)
                .await
                .map_err(pg::failure)?;
                if exists {
                    return Err(push_jobs::replaced());
                }
                insert(&mut tx, &next).await?;
                Ok(next)
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
    fn replacement<'a>(
        &'a self,
        s: &'a dyn Scope,
        owner: &'a Owner,
    ) -> PortFuture<'a, Option<PushJob>> {
        Box::pin(async move {
            let row=pg::read(s,sqlx::query(&format!("SELECT {COLUMNS} FROM notifications.push_jobs WHERE retry_of=$1::uuid AND installation_id=$2::uuid AND principal_id=$3::uuid AND session_id=$4::uuid")).bind(owner.id.as_str()).bind(owner.installation_id.as_str()).bind(owner.principal_id.as_str()).bind(owner.session_id.as_str()).fetch_optional(&self.pool)).await?;
            row.map(restore).transpose()
        })
    }
}
