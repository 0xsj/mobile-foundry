use super::{PRINCIPAL, PostgresStore, SESSION, principal, session};
use crate::infra::Mutation;
use crate::{
    app::{
        ports::{PortFuture, Scope, Store},
        recovery::{RecoveryStore, challenge_refused, contact_conflict},
    },
    domain::{
        self, Action, Digest, Fact, Principal, Secret, Timestamp,
        recovery::{Challenge, Contact, Purpose},
    },
};
use kernel::{Failure, id::Id};
use postgres_storage as pg;
use sqlx::{PgConnection, Row};
async fn lock_principal(conn: &mut PgConnection, id: &Id) -> Result<Principal, Failure> {
    let sql = format!("SELECT {PRINCIPAL} FROM identity.principals WHERE id=$1::uuid FOR UPDATE");
    principal(
        sqlx::query(&sql)
            .bind(id.as_str())
            .fetch_optional(conn)
            .await
            .map_err(pg::failure)?
            .ok_or_else(domain::credential_refused)?,
    )
}
async fn load_challenge(
    conn: &mut PgConnection,
    digest: &Digest,
    purpose: Purpose,
    now: Timestamp,
) -> Result<Challenge, Failure> {
    let row = sqlx::query(
        "SELECT principal_id::text FROM identity.challenges WHERE digest=$1 AND purpose=$2",
    )
    .bind(digest.value())
    .bind(purpose.as_str())
    .fetch_optional(&mut *conn)
    .await
    .map_err(pg::failure)?
    .ok_or_else(challenge_refused)?;
    let id = pg::id(&row, "principal_id")?;
    let p = lock_principal(conn, &id).await?;
    let row = sqlx::query(
        "SELECT email,credential_hash,created_at,expires_at FROM identity.challenges
        WHERE principal_id=$1::uuid AND purpose=$2 AND digest=$3 FOR UPDATE",
    )
    .bind(id.as_str())
    .bind(purpose.as_str())
    .bind(digest.value())
    .fetch_optional(conn)
    .await
    .map_err(pg::failure)?
    .ok_or_else(challenge_refused)?;
    let c = Challenge {
        principal_id: id,
        purpose,
        email: row.try_get("email").map_err(pg::failure)?,
        digest: digest.clone(),
        credential_hash: Secret::new(row.try_get("credential_hash").map_err(pg::failure)?),
        created_at: Timestamp::from_micros(pg::micros(&row, "created_at")?)?,
        expires_at: Timestamp::from_micros(pg::micros(&row, "expires_at")?)?,
    };
    c.validate()?;
    if !c.active(now)
        || p.password_hash().expose() != c.credential_hash.expose()
        || c.created_at < p.created_at()
    {
        return Err(challenge_refused());
    }
    Ok(c)
}
impl PostgresStore {
    async fn lifecycle(
        &self,
        s: &dyn Scope,
        conn: &mut PgConnection,
        actor: &Id,
        action: Action,
        now: Timestamp,
    ) -> Result<(), Failure> {
        let row=sqlx::query("UPDATE identity.principals SET revision=revision+1 WHERE id=$1::uuid RETURNING revision")
            .bind(actor.as_str()).fetch_one(&mut *conn).await.map_err(pg::failure)?;
        self.append(
            s,
            conn,
            Fact {
                principal_id: actor.clone(),
                session_id: None,
                action,
                version: pg::version(&row, "revision")?,
                occurred_at: now,
            },
        )
        .await
    }
    async fn change(
        &self,
        s: &dyn Scope,
        conn: &mut PgConnection,
        actor: &Id,
        mutation: Mutation<'_>,
    ) -> Result<(), Failure> {
        let Mutation {
            expected,
            hash,
            now,
            reset,
        } = mutation;
        let p = lock_principal(conn, actor).await?;
        if p.password_hash().expose() != expected.expose() || now < p.created_at() {
            return Err(domain::credential_refused());
        }
        p.with_password(hash.clone())?;
        let sql = format!(
            "SELECT {SESSION} FROM identity.sessions WHERE principal_id=$1::uuid AND revoked_at IS NULL ORDER BY id FOR UPDATE"
        );
        let rows = sqlx::query(&sql)
            .bind(actor.as_str())
            .fetch_all(&mut *conn)
            .await
            .map_err(pg::failure)?;
        for row in rows {
            let next = session(row)?.revoke(now)?;
            Self::save(conn, &next).await?;
            self.append(
                s,
                conn,
                next.fact(if reset {
                    Action::PasswordResetRevoked
                } else {
                    Action::PasswordChangeRevoked
                }),
            )
            .await?;
        }
        sqlx::query("UPDATE identity.principals SET password_hash=$1 WHERE id=$2::uuid")
            .bind(hash.expose())
            .bind(actor.as_str())
            .execute(&mut *conn)
            .await
            .map_err(pg::failure)?;
        sqlx::query("DELETE FROM identity.challenges WHERE principal_id=$1::uuid")
            .bind(actor.as_str())
            .execute(&mut *conn)
            .await
            .map_err(pg::failure)?;
        self.lifecycle(
            s,
            conn,
            actor,
            if reset {
                Action::PasswordRecovered
            } else {
                Action::PasswordChanged
            },
            now,
        )
        .await
    }
}
impl RecoveryStore for PostgresStore {
    fn contact<'a>(&'a self, s: &'a dyn Scope, actor: &'a Id) -> PortFuture<'a, Option<Contact>> {
        Box::pin(async move {
            let row = pg::read(
                s,
                sqlx::query(
                    "SELECT email,verified_at FROM identity.contacts WHERE principal_id=$1::uuid",
                )
                .bind(actor.as_str())
                .fetch_optional(&self.pool),
            )
            .await?;
            row.map(|row| {
                let c = Contact {
                    principal_id: actor.clone(),
                    email: row.try_get("email").map_err(pg::failure)?,
                    verified_at: Timestamp::from_micros(pg::micros(&row, "verified_at")?)?,
                };
                c.validate()?;
                Ok(c)
            })
            .transpose()
        })
    }
    fn by_email<'a>(
        &'a self,
        s: &'a dyn Scope,
        address: &'a str,
    ) -> PortFuture<'a, Option<Principal>> {
        Box::pin(async move {
            let row = pg::read(
                s,
                sqlx::query("SELECT principal_id::text FROM identity.contacts WHERE email=$1")
                    .bind(address)
                    .fetch_optional(&self.pool),
            )
            .await?;
            if let Some(row) = row {
                self.by_id(s, &pg::id(&row, "principal_id")?).await
            } else {
                Ok(None)
            }
        })
    }
    fn begin_challenge<'a>(&'a self, s: &'a dyn Scope, c: Challenge) -> PortFuture<'a, ()> {
        Box::pin(async move {
            c.validate()?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                let p = lock_principal(&mut tx, &c.principal_id).await?;
                if p.password_hash().expose() != c.credential_hash.expose()
                    || c.created_at < p.created_at()
                {
                    return Err(domain::credential_refused());
                }
                let existing: Option<String> = sqlx::query_scalar(
                    "SELECT email FROM identity.contacts WHERE principal_id=$1::uuid",
                )
                .bind(c.principal_id.as_str())
                .fetch_optional(&mut *tx)
                .await
                .map_err(pg::failure)?;
                if existing.as_ref().is_some_and(|v| v != &c.email) {
                    return Err(contact_conflict());
                }
                if c.purpose == Purpose::ResetPassword && existing.as_ref() != Some(&c.email) {
                    return Err(challenge_refused());
                }
                sqlx::query("INSERT INTO identity.challenges(principal_id,purpose,email,digest,credential_hash,created_at,expires_at)
                VALUES($1::uuid,$2,$3,$4,$5,$6,$7) ON CONFLICT(principal_id,purpose) DO UPDATE SET
                email=excluded.email,digest=excluded.digest,credential_hash=excluded.credential_hash,created_at=excluded.created_at,expires_at=excluded.expires_at")
                    .bind(c.principal_id.as_str())
                    .bind(c.purpose.as_str())
                    .bind(&c.email)
                    .bind(c.digest.value())
                    .bind(c.credential_hash.expose())
                    .bind(pg::time(c.created_at.micros())?)
                    .bind(pg::time(c.expires_at.micros())?)
                    .execute(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                Ok(())
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn verify_email<'a>(
        &'a self,
        s: &'a dyn Scope,
        digest: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                let c = load_challenge(&mut tx, &digest, Purpose::VerifyEmail, now).await?;
                let changed = sqlx::query("INSERT INTO identity.contacts(principal_id,email,verified_at) VALUES($1::uuid,$2,$3)
                ON CONFLICT(principal_id) DO UPDATE SET verified_at=excluded.verified_at WHERE identity.contacts.email=excluded.email")
                    .bind(c.principal_id.as_str())
                    .bind(&c.email)
                    .bind(pg::time(now.micros())?)
                    .execute(&mut *tx)
                    .await
                    .map_err(|error| {
                        if pg::constraint(&error, "contacts_email_key") {
                            contact_conflict()
                        } else {
                            pg::failure(error)
                        }
                    })?;
                if changed.rows_affected() != 1 {
                    return Err(contact_conflict());
                }
                self.lifecycle(s, &mut tx, &c.principal_id, Action::EmailVerified, now).await?;
                sqlx::query("DELETE FROM identity.challenges WHERE principal_id=$1::uuid AND purpose=$2")
                    .bind(c.principal_id.as_str())
                    .bind(c.purpose.as_str())
                    .execute(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                Ok(())
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn reset_password<'a>(
        &'a self,
        s: &'a dyn Scope,
        digest: Digest,
        hash: Secret,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                let c = load_challenge(&mut tx, &digest, Purpose::ResetPassword, now).await?;
                self.change(
                    s,
                    &mut tx,
                    &c.principal_id,
                    Mutation {
                        expected: &c.credential_hash,
                        hash,
                        now,
                        reset: true,
                    },
                )
                .await
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
    fn change_password<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        expected: Secret,
        hash: Secret,
        now: Timestamp,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = self
                .change(
                    s,
                    &mut tx,
                    actor,
                    Mutation {
                        expected: &expected,
                        hash,
                        now,
                        reset: false,
                    },
                )
                .await;
            pg::finish(s, tx, result).await
        })
    }
    fn prune_challenges<'a>(
        &'a self,
        s: &'a dyn Scope,
        now: Timestamp,
        limit: usize,
    ) -> PortFuture<'a, usize> {
        Box::pin(async move {
            if !(1..=1000).contains(&limit) {
                return Err(domain::invalid_input());
            }
            Ok(pg::read(s,sqlx::query("DELETE FROM identity.challenges WHERE (principal_id,purpose) IN
            (SELECT principal_id,purpose FROM identity.challenges WHERE expires_at<=$1 ORDER BY expires_at LIMIT $2 FOR UPDATE SKIP LOCKED)")
            .bind(pg::time(now.micros())?).bind(limit as i64).execute(&self.pool)).await?.rows_affected() as usize)
        })
    }
}
