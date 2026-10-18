use super::{AccountAccess, Feed, Scope, Subscription};
use kernel::{Failure, Kind, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct Subscribe {
    pub access: Arc<dyn AccountAccess>,
    pub feed: Arc<dyn Feed>,
}
impl Subscribe {
    pub async fn check(&self, s: &dyn Scope, actor: &Id, account: &Id) -> Result<(), Failure> {
        s.check()?;
        if !self.access.allowed(s, actor, account).await? {
            return Err(Failure::new(
                Kind::Forbidden,
                "realtime.account_forbidden",
                "account access refused",
            ));
        }
        Ok(())
    }
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Id,
        account: &Id,
    ) -> Result<Box<dyn Subscription>, Failure> {
        self.check(s, actor, account).await?;
        self.feed.watch(actor, account)
    }
}
