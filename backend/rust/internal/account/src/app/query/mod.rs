use super::{
    Dependencies, denied, invalid_page, not_found,
    ports::{Page, Scope},
};
use crate::domain::{self, Account};
use kernel::{Failure, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct Access(pub Arc<dyn super::ports::Store>);
impl Access {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        actor: &Id,
        account: &Id,
        action: &str,
    ) -> Result<bool, Failure> {
        scope.check()?;
        if !["account.read", "account.rename"].contains(&action) {
            return Err(Failure::new(
                kernel::Kind::Invalid,
                "account.invalid_action",
                "unsupported account action",
            )
            .with_public("Use a supported account action."));
        }
        Ok(self
            .0
            .get(scope, account)
            .await?
            .is_some_and(|a| a.owned_by(actor)))
    }
}
#[derive(Clone)]
pub struct Get(pub Dependencies);
#[derive(Clone)]
pub struct List(pub Dependencies);
impl Get {
    pub async fn handle(&self, scope: &dyn Scope, actor: &Id, id: &Id) -> Result<Account, Failure> {
        scope.check()?;
        let account = self.0.store.get(scope, id).await?.ok_or_else(not_found)?;
        if !account.owned_by(actor) {
            return Err(denied());
        }
        Ok(account)
    }
}
impl List {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        actor: &Id,
        after: Option<&Id>,
        limit: usize,
    ) -> Result<Page, Failure> {
        scope.check()?;
        if !(1..=100).contains(&limit) {
            return Err(invalid_page());
        }
        let mut rows = self
            .0
            .directory
            .list(scope, actor, after, limit + 1)
            .await?;
        if rows.len() > limit + 1 {
            return Err(domain::invalid_state());
        }
        let mut prior = after;
        for row in &rows {
            let snapshot = row.snapshot();
            Account::restore(snapshot.clone())?;
            if !row.owned_by(actor) || prior.is_some_and(|id| snapshot.id <= *id) {
                return Err(domain::invalid_state());
            }
            prior = Some(&snapshot.id);
        }
        let next = if rows.len() > limit {
            rows.truncate(limit);
            Some(rows[limit - 1].snapshot().id.clone())
        } else {
            None
        };
        Ok(Page {
            accounts: rows,
            next,
        })
    }
}
