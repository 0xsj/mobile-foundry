//! Owned registry guard, proof/CAS/target state and revocation evidence.
use super::PostgresStore;
use crate::{
    app::{
        self,
        installations::{self, Actor, Installations, Mutation, Revocation},
        ports::{PortFuture, Scope},
    },
    domain::{
        self,
        installation::{Installation, InstallationSpec, PushRoute, Secret, Snapshot},
    },
    infra::installation_records as records,
};
use kernel::{Failure, id::Id};
use postgres_storage as pg;
use sqlx::{PgConnection, Row, postgres::PgRow};
const COLUMNS: &str = "id::text,principal_id::text,session_id::text,registration_id::text,proof_digest,provider,application,environment,token,token_key,version,enabled,created_at,updated_at,expires_at";
async fn lock(conn: &mut PgConnection) -> Result<(), Failure> {
    let guard: bool = sqlx::query_scalar(
        "SELECT singleton FROM notifications.installation_guard WHERE singleton FOR UPDATE",
    )
    .fetch_one(conn)
    .await
    .map_err(pg::failure)?;
    if !guard {
        return Err(domain::invalid_state());
    }
    Ok(())
}
async fn actor(conn: &mut PgConnection, a: &Actor, now: u64) -> Result<(), Failure> {
    a.validate(now)?;
    let revoked:bool=sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM notifications.revoked_installation_sessions WHERE principal_id=$1::uuid AND session_id=$2::uuid)")
        .bind(a.principal.as_str()).bind(a.session.as_str()).fetch_one(conn).await.map_err(pg::failure)?;
    if revoked {
        return Err(installations::session_required());
    }
    Ok(())
}
fn restore(row: PgRow) -> Result<Installation, Failure> {
    let key: Option<String> = row.try_get("token_key").map_err(pg::failure)?;
    let s = Snapshot {
        id: pg::id(&row, "id")?,
        principal_id: pg::id(&row, "principal_id")?,
        session_id: pg::id(&row, "session_id")?,
        registration_id: pg::id(&row, "registration_id")?,
        proof_digest: Secret::new(row.try_get("proof_digest").map_err(pg::failure)?),
        spec: InstallationSpec {
            route: PushRoute {
                provider: row.try_get("provider").map_err(pg::failure)?,
                application: row.try_get("application").map_err(pg::failure)?,
                environment: row.try_get("environment").map_err(pg::failure)?,
            },
            token: Secret::new(row.try_get("token").map_err(pg::failure)?),
        },
        version: pg::version(&row, "version")?,
        enabled: row.try_get("enabled").map_err(pg::failure)?,
        created_at: pg::micros(&row, "created_at")?,
        updated_at: pg::micros(&row, "updated_at")?,
        expires_at: pg::micros(&row, "expires_at")?,
    };
    if (s.enabled && key.as_ref() != Some(&records::target(&s.spec)))
        || (!s.enabled && key.is_some())
    {
        return Err(domain::invalid_state());
    }
    Installation::restore(s)
}
async fn load(conn: &mut PgConnection, id: &Id) -> Result<Option<Installation>, Failure> {
    sqlx::query(&format!(
        "SELECT {COLUMNS} FROM notifications.installations WHERE id=$1::uuid"
    ))
    .bind(id.as_str())
    .fetch_optional(conn)
    .await
    .map_err(pg::failure)?
    .map(restore)
    .transpose()
}
fn admit(i: Option<Installation>, proof: &Secret) -> Result<Installation, Failure> {
    i.filter(|i| records::proof_matches(&i.snapshot().proof_digest, proof))
        .ok_or_else(installations::missing)
}
async fn save(conn: &mut PgConnection, i: &Installation) -> Result<(), Failure> {
    let v = i.snapshot();
    let key = v.enabled.then(|| records::target(&v.spec));
    sqlx::query("INSERT INTO notifications.installations(id,principal_id,session_id,registration_id,proof_digest,provider,application,environment,token,token_key,version,enabled,created_at,updated_at,expires_at)
        VALUES($1::uuid,$2::uuid,$3::uuid,$4::uuid,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
        ON CONFLICT(id) DO UPDATE SET principal_id=EXCLUDED.principal_id,session_id=EXCLUDED.session_id,registration_id=EXCLUDED.registration_id,provider=EXCLUDED.provider,application=EXCLUDED.application,environment=EXCLUDED.environment,token=EXCLUDED.token,token_key=EXCLUDED.token_key,version=EXCLUDED.version,enabled=EXCLUDED.enabled,updated_at=EXCLUDED.updated_at,expires_at=EXCLUDED.expires_at")
        .bind(v.id.as_str()).bind(v.principal_id.as_str()).bind(v.session_id.as_str()).bind(v.registration_id.as_str()).bind(v.proof_digest.expose())
        .bind(&v.spec.route.provider).bind(&v.spec.route.application).bind(&v.spec.route.environment).bind(v.spec.token.expose()).bind(key)
        .bind(v.version as i64).bind(v.enabled).bind(pg::time(v.created_at)?).bind(pg::time(v.updated_at)?).bind(pg::time(v.expires_at)?)
        .execute(conn).await.map_err(pg::failure)?;
    Ok(())
}
impl Installations for PostgresStore {
    fn register<'a>(
        &'a self,
        s: &'a dyn Scope,
        m: Mutation,
        now: u64,
    ) -> PortFuture<'a, Installation> {
        Box::pin(async move {
            m.validate(now)?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                lock(&mut tx).await?;
                actor(&mut tx, &m.actor, now).await?;
                let next = if let Some(prior) = load(&mut tx, &m.id).await? {
                    let prior = admit(Some(prior), &m.proof_digest)?;
                    let v = prior.snapshot();
                    if m.expected == 0
                        || v.version != m.expected
                        || v.registration_id == m.registration_id
                    {
                        return Err(installations::conflict());
                    }
                    prior.bind(
                        m.actor.principal.clone(),
                        m.actor.session.clone(),
                        m.registration_id.clone(),
                        m.spec.clone(),
                        now,
                        m.actor.expires_at,
                    )?
                } else {
                    if m.expected != 0 {
                        return Err(installations::missing());
                    }
                    m.create(now)?
                };
                let query = format!("SELECT {COLUMNS} FROM notifications.installations WHERE token_key=$1 AND id<>$2::uuid");
                let other = sqlx::query(&query)
                    .bind(records::target(&m.spec))
                    .bind(m.id.as_str())
                    .fetch_optional(&mut *tx)
                    .await
                    .map_err(pg::failure)?
                    .map(restore)
                    .transpose()?;
                if let Some(other) = other {
                    if now < other.snapshot().expires_at {
                        return Err(installations::target_conflict());
                    }
                    save(&mut tx, &other.disable(now)?).await?;
                }
                save(&mut tx, &next).await?;
                Ok(next)
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn get<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Actor,
        id: &'a Id,
        proof: &'a Secret,
        now: u64,
    ) -> PortFuture<'a, Installation> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                lock(&mut tx).await?;
                actor(&mut tx, a, now).await?;
                admit(load(&mut tx, id).await?, proof)
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
    fn unregister<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Actor,
        id: &'a Id,
        proof: &'a Secret,
        expected: u64,
        now: u64,
    ) -> PortFuture<'a, Installation> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                lock(&mut tx).await?;
                actor(&mut tx, a, now).await?;
                let prior = admit(load(&mut tx, id).await?, proof)?;
                let v = prior.snapshot();
                if !a.owns(v) {
                    return Err(installations::binding());
                }
                if !v.enabled {
                    return Ok(prior);
                }
                if v.version != expected {
                    return Err(installations::conflict());
                }
                let next = prior.disable(now)?;
                save(&mut tx, &next).await?;
                Ok(next)
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
    fn retire_session<'a>(&'a self, s: &'a dyn Scope, r: Revocation) -> PortFuture<'a, ()> {
        Box::pin(async move {
            r.validate()?;
            let hash = records::revocation_fingerprint(&r)?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                lock(&mut tx).await?;
                let prior: Option<String> = sqlx::query_scalar("SELECT fingerprint FROM notifications.installation_inbox WHERE event_id=$1::uuid")
                    .bind(r.event_id.as_str())
                    .fetch_optional(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                if let Some(prior) = prior {
                    return if prior == hash {
                        Ok(())
                    } else {
                        Err(app::replay_conflict())
                    };
                }
                sqlx::query("INSERT INTO notifications.revoked_installation_sessions(principal_id,session_id) VALUES($1::uuid,$2::uuid) ON CONFLICT DO NOTHING")
                    .bind(r.principal_id.as_str())
                    .bind(r.session_id.as_str())
                    .execute(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                let query = format!("SELECT {COLUMNS} FROM notifications.installations WHERE principal_id=$1::uuid AND session_id=$2::uuid AND enabled");
                let rows = sqlx::query(&query)
                    .bind(r.principal_id.as_str())
                    .bind(r.session_id.as_str())
                    .fetch_all(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                for row in rows {
                    let next = restore(row)?.disable(r.occurred_at)?;
                    save(&mut tx, &next).await?;
                }
                sqlx::query("INSERT INTO notifications.installation_inbox(event_id,fingerprint) VALUES($1::uuid,$2)")
                    .bind(r.event_id.as_str())
                    .bind(hash)
                    .execute(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                Ok(())
            }.await;
            pg::finish(s, tx, result).await
        })
    }
}
