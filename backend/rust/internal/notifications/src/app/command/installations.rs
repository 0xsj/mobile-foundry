use crate::{
    app::{
        self,
        installations::{Actor, Mutation, Registration, Registry, Revocation},
        ports::Scope,
    },
    domain::installation::{Installation, Secret},
};
use kernel::{Failure, id::Id};
#[derive(Clone)]
pub struct RegisterInstallation(pub Registry);
impl RegisterInstallation {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        input: Registration,
    ) -> Result<Installation, Failure> {
        let now = app::now(self.0.clock.as_ref())?;
        input.actor.validate(now)?;
        let digest = self.0.proofs.digest(s, &input.proof)?;
        let m = Mutation {
            actor: input.actor,
            id: input.id,
            registration_id: input.registration_id,
            proof_digest: digest,
            spec: input.spec,
            expected: input.expected,
        };
        m.validate(now)?;
        self.0.store.register(s, m, now).await
    }
}
#[derive(Clone)]
pub struct UnregisterInstallation(pub Registry);
impl UnregisterInstallation {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Actor,
        id: &Id,
        raw: Secret,
        expected: u64,
    ) -> Result<Installation, Failure> {
        let now = app::now(self.0.clock.as_ref())?;
        actor.validate(now)?;
        if !(1..=crate::domain::MAX_SEQUENCE).contains(&expected) {
            return Err(app::invalid());
        }
        let proof = self.0.proofs.digest(s, &raw)?;
        self.0
            .store
            .unregister(s, actor, id, &proof, expected, now)
            .await
    }
}
#[derive(Clone)]
pub struct RetireSession(pub std::sync::Arc<dyn app::installations::Installations>);
impl RetireSession {
    pub async fn handle(&self, s: &dyn Scope, r: Revocation) -> Result<(), Failure> {
        r.validate()?;
        self.0.retire_session(s, r).await
    }
}
