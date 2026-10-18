//! Identity-owned credentials, session checkpoints and required integration facts.
mod recovery;
mod session_eligibility;
use crate::{
    app::{
        self,
        ports::{CommittedFact, PortFuture, Rotation, Scope, SessionEvidence, Store},
    },
    domain::{
        self, Action, Digest, DisplayName, Fact, Login, Principal, Secret, Session, SessionState,
        Timestamp,
    },
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
const PRINCIPAL: &str = "id::text,login,display_name,password_hash,created_at";
const SESSION: &str = "id::text,principal_id::text,generation,access_digest,refresh_digest,created_at,updated_at,access_expires_at,expires_at,revoked_at";
fn principal(row: PgRow) -> Result<Principal, Failure> {
    let login: String = row.try_get("login").map_err(pg::failure)?;
    let name: String = row.try_get("display_name").map_err(pg::failure)?;
    let l = Login::new(&login).map_err(|_| domain::invalid_state())?;
    let n = DisplayName::new(&name).map_err(|_| domain::invalid_state())?;
    if l.value() != login || n.value() != name {
        return Err(domain::invalid_state());
    }
    Principal::new(
        pg::id(&row, "id")?,
        l,
        n,
        Secret::new(row.try_get("password_hash").map_err(pg::failure)?),
        Timestamp::from_micros(pg::micros(&row, "created_at")?)?,
    )
}
fn session(row: PgRow) -> Result<Session, Failure> {
    Session::restore(SessionState {
        id: pg::id(&row, "id")?,
        principal_id: pg::id(&row, "principal_id")?,
        generation: pg::version(&row, "generation")?,
        access: Digest::parse(row.try_get("access_digest").map_err(pg::failure)?)?,
        refresh: Digest::parse(row.try_get("refresh_digest").map_err(pg::failure)?)?,
        created_at: Timestamp::from_micros(pg::micros(&row, "created_at")?)?,
        updated_at: Timestamp::from_micros(pg::micros(&row, "updated_at")?)?,
        access_expires_at: Timestamp::from_micros(pg::micros(&row, "access_expires_at")?)?,
        expires_at: Timestamp::from_micros(pg::micros(&row, "expires_at")?)?,
        revoked_at: pg::optional_micros(&row, "revoked_at")?
            .map(Timestamp::from_micros)
            .transpose()?,
    })
}
impl PostgresStore {
    pub fn new(pool: PgPool, ids: Arc<dyn Generator>) -> Result<Self, Failure> {
        let outbox = pg::outbox::Leased::new(
            pool.clone(),
            ids.new_id()?,
            super::postgres_outbox::queries(),
            |m, stream| {
                if m.producer != "identity" || m.account_id.is_some() {
                    return false;
                }
                let Ok(data) = serde_json::from_slice::<serde_json::Value>(&m.data) else {
                    return false;
                };
                let Some(principal) = data.get("principal_id").and_then(|v| v.as_str()) else {
                    return false;
                };
                m.actor_id.as_str() == principal
                    && data
                        .get("session_id")
                        .and_then(|v| v.as_str())
                        .unwrap_or(principal)
                        == stream
            },
            super::postgres_outbox::exhausted,
        );
        Ok(Self { pool, ids, outbox })
    }
    async fn append(
        &self,
        scope: &dyn Scope,
        conn: &mut PgConnection,
        fact: Fact,
    ) -> Result<(), Failure> {
        scope.check()?;
        let stream = fact
            .session_id
            .as_ref()
            .unwrap_or(&fact.principal_id)
            .clone();
        let message = super::events::message(&CommittedFact {
            id: self.ids.new_id()?,
            fact,
            metadata: scope.metadata(),
        })?;
        sqlx::query(
            r#"INSERT INTO identity.outbox(event_id,
            stream_id,
            aggregate_version,
            envelope) VALUES($1::uuid,
            $2::uuid,
            $3,
            $4)"#,
        )
        .bind(message.id.as_str())
        .bind(stream.as_str())
        .bind(message.version as i64)
        .bind(pg::outbox::encode(&message)?)
        .execute(conn)
        .await
        .map_err(pg::failure)?;
        Ok(())
    }
    async fn marker(conn: &mut PgConnection, s: &Session) -> Result<(), Failure> {
        let s = s.state();
        sqlx::query(
            "INSERT INTO identity.refresh_credentials(digest,session_id,generation)
             VALUES($1,$2::uuid,$3)",
        )
        .bind(s.refresh.value())
        .bind(s.id.as_str())
        .bind(s.generation as i64)
        .execute(conn)
        .await
        .map_err(pg::failure)?;
        Ok(())
    }
    async fn load_refresh(
        conn: &mut PgConnection,
        digest: &Digest,
    ) -> Result<(Session, u64), Failure> {
        let marker = sqlx::query(
            "SELECT session_id::text,generation FROM identity.refresh_credentials WHERE digest=$1",
        )
        .bind(digest.value())
        .fetch_optional(&mut *conn)
        .await
        .map_err(pg::failure)?
        .ok_or_else(domain::credential_refused)?;
        let id = pg::id(&marker, "session_id")?;
        let generation = pg::version(&marker, "generation")?;
        let row = sqlx::query(&format!(
            "SELECT {SESSION} FROM identity.sessions WHERE id=$1::uuid FOR UPDATE"
        ))
        .bind(id.as_str())
        .fetch_optional(conn)
        .await
        .map_err(pg::failure)?
        .ok_or_else(domain::invalid_state)?;
        Ok((session(row)?, generation))
    }
    async fn save(conn: &mut PgConnection, next: &Session) -> Result<(), Failure> {
        let s = next.state();
        let changed = sqlx::query(
            r#"UPDATE identity.sessions
            SET generation=$2,
            access_digest=$3,
            refresh_digest=$4,
            updated_at=$5,
            access_expires_at=$6,
            revoked_at=$7
            WHERE id=$1::uuid
            AND generation=$8"#,
        )
        .bind(s.id.as_str())
        .bind(s.generation as i64)
        .bind(s.access.value())
        .bind(s.refresh.value())
        .bind(pg::time(s.updated_at.micros())?)
        .bind(pg::time(s.access_expires_at.micros())?)
        .bind(s.revoked_at.map(|v| pg::time(v.micros())).transpose()?)
        .bind((s.generation - 1) as i64)
        .execute(conn)
        .await
        .map_err(pg::failure)?;
        if changed.rows_affected() != 1 {
            return Err(domain::invalid_state());
        }
        Ok(())
    }
}
impl Store for PostgresStore {
    fn by_login<'a>(
        &'a self,
        s: &'a dyn Scope,
        login: &'a str,
    ) -> PortFuture<'a, Option<Principal>> {
        Box::pin(async move {
            pg::read(
                s,
                sqlx::query(&format!(
                    "SELECT {PRINCIPAL} FROM identity.principals WHERE login=$1"
                ))
                .bind(login)
                .fetch_optional(&self.pool),
            )
            .await?
            .map(principal)
            .transpose()
        })
    }
    fn by_id<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<Principal>> {
        Box::pin(async move {
            pg::read(
                s,
                sqlx::query(&format!(
                    "SELECT {PRINCIPAL} FROM identity.principals WHERE id=$1::uuid"
                ))
                .bind(id.as_str())
                .fetch_optional(&self.pool),
            )
            .await?
            .map(principal)
            .transpose()
        })
    }
    fn register<'a>(&'a self, scope: &'a dyn Scope, p: Principal) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                sqlx::query(
                    r#"INSERT INTO identity.principals(id,
            login,
            display_name,
            password_hash,
            created_at) VALUES($1::uuid,
            $2,
            $3,
            $4,
            $5)"#,
                )
                .bind(p.id().as_str())
                .bind(p.login())
                .bind(p.display_name())
                .bind(p.password_hash().expose())
                .bind(pg::time(p.created_at().micros())?)
                .execute(&mut *tx)
                .await
                .map_err(|e| {
                    if pg::constraint(&e, "principals_login_key") {
                        app::login_exists()
                    } else {
                        pg::failure(e)
                    }
                })?;
                self.append(scope, &mut tx, p.fact()).await
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
    fn issue_checked<'a>(
        &'a self,
        scope: &'a dyn Scope,
        s: Session,
        expected: &'a Secret,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            Session::restore(s.state().clone())?;
            if s.state().generation != 1 || s.state().revoked_at.is_some() {
                return Err(domain::invalid_state());
            }
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                let v = s.state();
                let p = principal(
                    sqlx::query(&format!(
                        "SELECT {PRINCIPAL} FROM identity.principals WHERE id=$1::uuid FOR UPDATE"
                    ))
                    .bind(v.principal_id.as_str())
                    .fetch_optional(&mut *tx)
                    .await
                    .map_err(pg::failure)?
                    .ok_or_else(domain::invalid_state)?,
                )?;
                if p.password_hash().expose() != expected.expose() {
                    return Err(domain::credential_refused());
                }
                sqlx::query(
                    r#"INSERT INTO identity.sessions(id,
            principal_id,
            generation,
            access_digest,
            refresh_digest,
            created_at,
            updated_at,
            access_expires_at,
            expires_at,
            revoked_at) VALUES($1::uuid,
            $2::uuid,
            $3,
            $4,
            $5,
            $6,
            $7,
            $8,
            $9,
            NULL)"#,
                )
                .bind(v.id.as_str())
                .bind(v.principal_id.as_str())
                .bind(v.generation as i64)
                .bind(v.access.value())
                .bind(v.refresh.value())
                .bind(pg::time(v.created_at.micros())?)
                .bind(pg::time(v.updated_at.micros())?)
                .bind(pg::time(v.access_expires_at.micros())?)
                .bind(pg::time(v.expires_at.micros())?)
                .execute(&mut *tx)
                .await
                .map_err(pg::failure)?;
                Self::marker(&mut tx, &s).await?;
                self.append(scope, &mut tx, s.fact(Action::Issued)).await
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
    fn rotate<'a>(
        &'a self,
        scope: &'a dyn Scope,
        previous: Digest,
        access: Digest,
        refresh: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, Rotation> {
        Box::pin(async move {
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                let (current, generation) = Self::load_refresh(&mut tx, &previous).await?;
                if !current.renewable(now) {
                    return Err(domain::credential_refused());
                }
                let reused =
                    generation != current.state().generation || previous != current.state().refresh;
                let next = if reused {
                    current.revoke(now)?
                } else {
                    current.rotate(access, refresh, now)?
                };
                Self::save(&mut tx, &next).await?;
                if !reused {
                    Self::marker(&mut tx, &next).await?;
                }
                self.append(
                    scope,
                    &mut tx,
                    next.fact(if reused {
                        Action::RefreshReused
                    } else {
                        Action::Renewed
                    }),
                )
                .await?;
                Ok(if reused {
                    Rotation::Reused
                } else {
                    Rotation::Rotated(next)
                })
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
    fn revoke<'a>(
        &'a self,
        scope: &'a dyn Scope,
        refresh: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut tx = pg::begin(scope, &self.pool).await?;
            let result = async {
                let (current, _) = Self::load_refresh(&mut tx, &refresh).await?;
                if current.state().revoked_at.is_some() {
                    return Ok(());
                }
                let next = current.revoke(now)?;
                Self::save(&mut tx, &next).await?;
                self.append(scope, &mut tx, next.fact(Action::SignedOut))
                    .await
            }
            .await;
            pg::finish(scope, tx, result).await
        })
    }
    fn access_session<'a>(
        &'a self,
        scope: &'a dyn Scope,
        access: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, SessionEvidence> {
        Box::pin(async move {
            let row = pg::read(
                scope,
                sqlx::query(&format!(
                    "SELECT {SESSION} FROM identity.sessions WHERE access_digest=$1"
                ))
                .bind(access.value())
                .fetch_optional(&self.pool),
            )
            .await?
            .ok_or_else(domain::credential_refused)?;
            let session = session(row)?;
            if !session.active(now) {
                return Err(domain::credential_refused());
            }
            let s = session.state();
            Ok(SessionEvidence {
                principal_id: s.principal_id.clone(),
                session_id: s.id.clone(),
                expires_at: s.expires_at,
            })
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
