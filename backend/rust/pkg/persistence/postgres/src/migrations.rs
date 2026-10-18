use crate::{begin, configuration, failure, finish, schema_failure};
use kernel::{Failure, context::Scope};
use sha2::{Digest, Sha256};
use sqlx::{PgConnection, PgPool, Row};
#[derive(Clone)]
pub struct Migration {
    pub name: &'static str,
    pub sql: &'static str,
}
#[derive(Clone)]
pub struct Schema {
    pub name: &'static str,
    pub migrations: &'static [Migration],
}
impl Schema {
    fn valid(&self) -> bool {
        !self.name.is_empty()
            && self.name.as_bytes()[0].is_ascii_lowercase()
            && self
                .name
                .bytes()
                .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_')
            && !self.migrations.is_empty()
            && self.migrations.iter().enumerate().all(|(n, m)| {
                m.name.starts_with(&format!("{:04}_", n + 1)) && m.name.ends_with(".sql")
            })
    }
    async fn applied(&self, connection: &mut PgConnection) -> Result<usize, Failure> {
        let rows = sqlx::query(&format!(
            "SELECT name,checksum FROM \"{}\".schema_migrations ORDER BY name",
            self.name
        ))
        .fetch_all(connection)
        .await
        .map_err(|e| match &e {
            sqlx::Error::Database(db)
                if [Some("42P01"), Some("3F000")].contains(&db.code().as_deref()) =>
            {
                schema_failure().with_cause(e)
            }
            _ => failure(e),
        })?;
        for (n, row) in rows.iter().enumerate() {
            let Some(expected) = self.migrations.get(n) else {
                return Err(schema_failure());
            };
            let name: String = row.try_get("name").map_err(failure)?;
            let checksum: String = row.try_get("checksum").map_err(failure)?;
            if name != expected.name
                || checksum != format!("{:x}", Sha256::digest(expected.sql.as_bytes()))
            {
                return Err(schema_failure());
            }
        }
        Ok(rows.len())
    }
    pub async fn check(&self, scope: &dyn Scope, pool: &PgPool) -> Result<(), Failure> {
        if !self.valid() {
            return Err(configuration());
        }
        scope.check()?;
        let mut connection = pool.acquire().await.map_err(failure)?;
        if self.applied(&mut connection).await? != self.migrations.len() {
            return Err(schema_failure());
        }
        scope.check()
    }
    pub async fn apply(&self, scope: &dyn Scope, pool: &PgPool) -> Result<(), Failure> {
        if !self.valid() {
            return Err(configuration());
        }
        for (index, migration) in self.migrations.iter().enumerate() {
            let mut tx = begin(scope, pool).await?;
            let result = async {
                sqlx::query("SELECT pg_advisory_xact_lock(hashtextextended($1,0))")
                    .bind(format!("mobile-foundry/migrations/{}", self.name))
                    .execute(&mut *tx)
                    .await
                    .map_err(failure)?;
                sqlx::query(&format!("CREATE SCHEMA IF NOT EXISTS \"{}\"", self.name))
                    .execute(&mut *tx)
                    .await
                    .map_err(failure)?;
                sqlx::query(&format!(
                    r#"CREATE TABLE IF NOT EXISTS "{}".schema_migrations(name text PRIMARY KEY,
            checksum text NOT NULL,
            applied_at timestamptz NOT NULL DEFAULT clock_timestamp())"#,
                    self.name
                ))
                .execute(&mut *tx)
                .await
                .map_err(failure)?;
                let count = self.applied(&mut tx).await?;
                if count > index {
                    return Ok(());
                }
                if count != index {
                    return Err(schema_failure());
                }
                sqlx::raw_sql(migration.sql)
                    .execute(&mut *tx)
                    .await
                    .map_err(failure)?;
                sqlx::query(&format!(
                    "INSERT INTO \"{}\".schema_migrations(name,checksum) VALUES($1,$2)",
                    self.name
                ))
                .bind(migration.name)
                .bind(format!("{:x}", Sha256::digest(migration.sql.as_bytes())))
                .execute(&mut *tx)
                .await
                .map_err(failure)?;
                Ok(())
            }
            .await;
            finish(scope, tx, result).await?;
        }
        self.check(scope, pool).await
    }
}
