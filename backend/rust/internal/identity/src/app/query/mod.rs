use crate::{
    app::{
        command::Dependencies,
        ports::{Purpose, Scope},
    },
    domain::{self, Principal, Secret, Timestamp},
};
use kernel::Failure;

#[derive(Clone)]
pub struct ResolveSession(pub Dependencies);
impl ResolveSession {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        token: Secret,
    ) -> Result<super::ports::SessionEvidence, Failure> {
        let digest = self.0.tokens.digest(scope, &token, Purpose::Access)?;
        self.0
            .store
            .access_session(
                scope,
                digest,
                Timestamp::from_system_time(self.0.clock.now())?,
            )
            .await
    }
}

#[derive(Clone)]
pub struct Me(pub Dependencies);
impl Me {
    pub async fn handle(&self, scope: &dyn Scope, token: Secret) -> Result<Principal, Failure> {
        let digest = self.0.tokens.digest(scope, &token, Purpose::Access)?;
        let actor = self
            .0
            .store
            .access(
                scope,
                digest,
                Timestamp::from_system_time(self.0.clock.now())?,
            )
            .await?;
        self.0
            .store
            .by_id(scope, &actor)
            .await?
            .ok_or_else(domain::invalid_state)
    }
}
