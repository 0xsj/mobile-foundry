use crate::{
    app::{
        invalid,
        ports::{PortFuture, Scope},
    },
    domain::{
        self,
        installation::{Installation, InstallationSpec, Secret, Snapshot},
    },
};
use kernel::{Failure, Kind, id::Id};
use std::{fmt, sync::Arc};
pub fn missing() -> Failure {
    Failure::new(
        Kind::NotFound,
        "notifications.installation_not_found",
        "installation or proof not accepted",
    )
    .with_public("This installation is not available with that proof.")
}
pub fn conflict() -> Failure {
    Failure::new(Kind::Conflict,"notifications.installation_conflict","installation checkpoint changed").with_public("Read the current installation before changing it. Use a new registration ID for a new registration.")
}
pub fn binding() -> Failure {
    Failure::new(
        Kind::Forbidden,
        "notifications.installation_binding",
        "installation is bound to another session",
    )
    .with_public("This session does not own the current binding.")
}
pub fn target_conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.installation_target",
        "push target already registered elsewhere",
    )
    .with_public("This target is already registered by another installation.")
}
pub fn session_required() -> Failure {
    Failure::new(
        Kind::Unauthenticated,
        "notifications.identity_session_required",
        "a current owned Identity session is required",
    )
    .with_public("Sign in with a current Identity session.")
}
#[derive(Clone, Debug)]
pub struct Actor {
    pub principal: Id,
    pub session: Id,
    pub expires_at: u64,
}
impl Actor {
    pub fn validate(&self, now: u64) -> Result<(), Failure> {
        if now >= self.expires_at || self.expires_at > domain::MAX_TIME {
            return Err(session_required());
        }
        Ok(())
    }
    pub fn owns(&self, s: &Snapshot) -> bool {
        self.principal == s.principal_id && self.session == s.session_id
    }
}
#[derive(Clone)]
pub struct Mutation {
    pub actor: Actor,
    pub id: Id,
    pub registration_id: Id,
    pub proof_digest: Secret,
    pub spec: InstallationSpec,
    pub expected: u64,
}
impl fmt::Debug for Mutation {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("InstallationMutation { redacted }")
    }
}
impl Mutation {
    pub fn validate(&self, now: u64) -> Result<(), Failure> {
        self.actor.validate(now)?;
        if self.expected > domain::MAX_SEQUENCE - 1 || self.proof_digest.expose().len() != 64 {
            return Err(invalid());
        }
        self.spec.validate()
    }
    pub fn create(&self, now: u64) -> Result<Installation, Failure> {
        Installation::restore(Snapshot {
            id: self.id.clone(),
            principal_id: self.actor.principal.clone(),
            session_id: self.actor.session.clone(),
            registration_id: self.registration_id.clone(),
            proof_digest: self.proof_digest.clone(),
            spec: self.spec.clone(),
            version: 1,
            enabled: true,
            created_at: now,
            updated_at: now,
            expires_at: self.actor.expires_at,
        })
    }
}
#[derive(Clone, Debug)]
pub struct Revocation {
    pub event_id: Id,
    pub principal_id: Id,
    pub session_id: Id,
    pub version: u64,
    pub reason: String,
    pub occurred_at: u64,
}
impl Revocation {
    pub fn validate(&self) -> Result<(), Failure> {
        if !(2..=domain::MAX_SEQUENCE).contains(&self.version)
            || ![
                "sign_out",
                "refresh_reuse",
                "password_change",
                "password_reset",
            ]
            .contains(&self.reason.as_str())
            || self.occurred_at > domain::MAX_TIME
        {
            return Err(invalid());
        }
        Ok(())
    }
}
pub trait InstallationProofs: Send + Sync {
    fn digest(&self, s: &dyn Scope, raw: &Secret) -> Result<Secret, Failure>;
}
pub trait Installations: Send + Sync {
    /// Guards proof, version, new registration ID, session tombstone and target
    /// ownership in one commit. Never replays a historical registration receipt.
    fn register<'a>(
        &'a self,
        s: &'a dyn Scope,
        m: Mutation,
        now: u64,
    ) -> PortFuture<'a, Installation>;
    /// Valid proof permits a sanitized read even after explicit owner rebinding.
    fn get<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Actor,
        id: &'a Id,
        proof: &'a Secret,
        now: u64,
    ) -> PortFuture<'a, Installation>;
    fn unregister<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Actor,
        id: &'a Id,
        proof: &'a Secret,
        expected: u64,
        now: u64,
    ) -> PortFuture<'a, Installation>;
    /// Commits matching cleanup, retired-session tombstone and source marker
    /// together. Old-session events must preserve a newer binding.
    fn retire_session<'a>(&'a self, s: &'a dyn Scope, r: Revocation) -> PortFuture<'a, ()>;
}
#[derive(Clone)]
pub struct Registry {
    pub store: Arc<dyn Installations>,
    pub proofs: Arc<dyn InstallationProofs>,
    pub clock: Arc<dyn kernel::clock::Clock>,
}
pub struct Registration {
    pub actor: Actor,
    pub id: Id,
    pub registration_id: Id,
    pub proof: Secret,
    pub spec: InstallationSpec,
    pub expected: u64,
}
impl fmt::Debug for Registration {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("InstallationRegistration { redacted }")
    }
}
