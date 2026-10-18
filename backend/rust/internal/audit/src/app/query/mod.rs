use super::{
    denied, invalid_query,
    ports::{AccountAccess, Page, PrincipalStore, Scope, Store},
};
use kernel::{Failure, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct List {
    pub store: Arc<dyn Store>,
    pub access: Arc<dyn AccountAccess>,
}
#[derive(Clone)]
pub struct ListPrincipal(pub Arc<dyn PrincipalStore>);
impl List {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        principal: &Id,
        account: &Id,
        after: u64,
        limit: usize,
    ) -> Result<Page, Failure> {
        scope.check()?;
        validate(after, limit)?;
        if !self.access.allowed(scope, principal, account).await? {
            return Err(denied());
        }
        self.store.list(scope, account, after, limit).await
    }
}
impl ListPrincipal {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        principal: &Id,
        after: u64,
        limit: usize,
    ) -> Result<Page, Failure> {
        scope.check()?;
        validate(after, limit)?;
        self.0.list_principal(scope, principal, after, limit).await
    }
}
fn validate(after: u64, limit: usize) -> Result<(), Failure> {
    if !(1..=100).contains(&limit) || after > crate::domain::MAX_VERSION {
        Err(invalid_query())
    } else {
        Ok(())
    }
}
