use crate::{
    app::{
        self,
        installations::{Actor, Registry},
        ports::Scope,
    },
    domain::installation::{Installation, Secret},
};
use kernel::{Failure, id::Id};
#[derive(Clone)]
pub struct GetInstallation(pub Registry);
impl GetInstallation {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Actor,
        id: &Id,
        raw: Secret,
    ) -> Result<Installation, Failure> {
        let now = app::now(self.0.clock.as_ref())?;
        actor.validate(now)?;
        let proof = self.0.proofs.digest(s, &raw)?;
        self.0.store.get(s, actor, id, &proof, now).await
    }
}
