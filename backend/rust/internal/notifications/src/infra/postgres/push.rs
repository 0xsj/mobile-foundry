use super::{
    PostgresStore,
    installations::{load, lock, save},
};
use crate::app::{
    ports::{PortFuture, Scope},
    push::{self, PushAddress, PushLease, PushTargets},
};
use postgres_storage as pg;
impl PushTargets for PostgresStore {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        lease: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, PushAddress> {
        Box::pin(async move {
            lease.validate()?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                lock(&mut tx).await?;
                let i = load(&mut tx, &lease.installation_id)
                    .await?
                    .ok_or_else(push::target_changed)?;
                let v = i.snapshot();
                if !lease.matches(v) || !v.enabled || now < v.created_at || now >= v.expires_at {
                    return Err(push::target_changed());
                }
                let revoked: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM notifications.revoked_installation_sessions WHERE principal_id=$1::uuid AND session_id=$2::uuid)")
                    .bind(lease.principal_id.as_str())
                    .bind(lease.session_id.as_str())
                    .fetch_one(&mut *tx)
                    .await
                    .map_err(pg::failure)?;
                if revoked {
                    return Err(push::target_changed());
                }
                Ok(PushAddress {
                    lease: lease.clone(),
                    spec: v.spec.clone(),
                    registered_at: v.updated_at,
                })
            }.await;
            pg::finish(s, tx, result).await
        })
    }
    fn retire<'a>(
        &'a self,
        s: &'a dyn Scope,
        lease: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            lease.validate()?;
            let mut tx = pg::begin(s, &self.pool).await?;
            let result = async {
                lock(&mut tx).await?;
                let Some(prior) = load(&mut tx, &lease.installation_id)
                    .await?
                    .filter(|i| lease.matches(i.snapshot()) && i.snapshot().enabled)
                else {
                    return Ok(false);
                };
                save(&mut tx, &prior.disable(now)?).await?;
                Ok(true)
            }
            .await;
            pg::finish(s, tx, result).await
        })
    }
}
