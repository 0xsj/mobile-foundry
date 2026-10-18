use super::{
    Dependencies, conflict, denied, not_found,
    ports::{BootstrapResult, Scope},
};
use crate::domain::{self, Account, Action};
use kernel::{Failure, Kind, id::Id};

#[derive(Clone)]
pub struct Create(pub Dependencies);
#[derive(Clone)]
pub struct Bootstrap(pub Dependencies);
#[derive(Clone)]
pub struct Rename(pub Dependencies);

fn candidate(
    deps: &Dependencies,
    scope: &dyn Scope,
    actor: &Id,
    value: &str,
) -> Result<Account, Failure> {
    scope.check()?;
    let name = domain::name(value)?;
    let id = deps.ids.new_id().map_err(|cause| {
        Failure::new(
            Kind::Internal,
            "account.id_failed",
            "account ID generation failed",
        )
        .with_cause(cause)
    })?;
    Account::new(id, actor.clone(), &name, deps.clock.now())
}
impl Create {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        actor: &Id,
        name: &str,
    ) -> Result<Account, Failure> {
        let account = candidate(&self.0, scope, actor, name)?;
        let fact = account.fact(Action::Created, actor)?;
        self.0.store.create(scope, account.clone(), fact).await?;
        Ok(account)
    }
}
impl Bootstrap {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        actor: &Id,
        name: &str,
    ) -> Result<BootstrapResult, Failure> {
        let account = candidate(&self.0, scope, actor, name)?;
        let fact = account.fact(Action::Created, actor)?;
        let result = self
            .0
            .directory
            .bootstrap(scope, account.clone(), fact)
            .await?;
        Account::restore(result.account.snapshot().clone())?;
        if !result.account.owned_by(actor)
            || (result.created && result.account.snapshot() != account.snapshot())
        {
            return Err(domain::invalid_state());
        }
        Ok(result)
    }
}
impl Rename {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        actor: &Id,
        id: &Id,
        name: &str,
        expected: u64,
    ) -> Result<Account, Failure> {
        scope.check()?;
        let current = self.0.store.get(scope, id).await?.ok_or_else(not_found)?;
        if !current.owned_by(actor) {
            return Err(denied());
        }
        if expected == 0 || current.snapshot().version != expected {
            return Err(conflict());
        }
        let next = current.rename(name, self.0.clock.now())?;
        let fact = next.fact(Action::Renamed, actor)?;
        self.0
            .store
            .rename(scope, next.clone(), expected, fact)
            .await?;
        Ok(next)
    }
}
