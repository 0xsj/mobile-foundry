use super::{PostgresStore, SESSION, session as restore};
use crate::{
    app::{
        ports::{PortFuture, Scope},
        query::session_eligibility::SessionStatus,
    },
    domain::Timestamp,
};
use kernel::id::Id;
use postgres_storage as pg;
impl SessionStatus for PostgresStore {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        principal: &'a Id,
        session: &'a Id,
        now: Timestamp,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            let query = format!(
                "SELECT {SESSION} FROM identity.sessions WHERE id=$1::uuid AND principal_id=$2::uuid"
            );
            let row = pg::read(
                s,
                sqlx::query(&query)
                    .bind(session.as_str())
                    .bind(principal.as_str())
                    .fetch_optional(&self.pool),
            )
            .await?;
            row.map(restore)
                .transpose()
                .map(|v| v.is_some_and(|v| v.renewable(now)))
        })
    }
}
