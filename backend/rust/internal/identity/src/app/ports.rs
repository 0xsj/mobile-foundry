//! Consumer-owned ports. No HTTP, persistence or runtime objects enter application calls.
use crate::domain::{Digest, Fact, Password, Principal, Secret, Session, Timestamp};
pub use kernel::context::{Metadata, PortFuture, Scope};
use kernel::{Failure, id::Id};

pub struct SessionEvidence {
    pub principal_id: Id,
    pub session_id: Id,
    pub expires_at: Timestamp,
}
pub trait AttemptGate: Send + Sync {
    fn allow(&self, address: std::net::IpAddr, endpoint: &'static str) -> Result<(), Failure>;
}
pub trait Passwords: Send + Sync {
    fn hash<'a>(&'a self, scope: &'a dyn Scope, password: Password) -> PortFuture<'a, Secret>;
    fn verify<'a>(
        &'a self,
        scope: &'a dyn Scope,
        hash: Secret,
        password: Password,
    ) -> PortFuture<'a, bool>;
    fn dummy<'a>(&'a self, scope: &'a dyn Scope, password: Password) -> PortFuture<'a, ()>;
}
#[derive(Clone, Copy)]
pub enum Purpose {
    Access,
    Refresh,
    VerifyEmail,
    ResetPassword,
}
pub struct Issued {
    pub raw: Secret,
    pub digest: Digest,
}
pub trait Tokens: Send + Sync {
    fn issue(&self, scope: &dyn Scope, purpose: Purpose) -> Result<Issued, Failure>;
    fn digest(
        &self,
        scope: &dyn Scope,
        token: &Secret,
        purpose: Purpose,
    ) -> Result<Digest, Failure>;
}
pub enum Rotation {
    Rotated(Session),
    Reused,
}
pub trait Store: Send + Sync {
    fn by_login<'a>(
        &'a self,
        scope: &'a dyn Scope,
        login: &'a str,
    ) -> PortFuture<'a, Option<Principal>>;
    fn by_id<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<Principal>>;
    fn register<'a>(&'a self, scope: &'a dyn Scope, principal: Principal) -> PortFuture<'a, ()>;
    fn issue_checked<'a>(
        &'a self,
        scope: &'a dyn Scope,
        session: Session,
        expected_hash: &'a Secret,
    ) -> PortFuture<'a, ()>;
    /// Reused means the revocation/fact committed; returning Err would lose that distinction.
    fn rotate<'a>(
        &'a self,
        scope: &'a dyn Scope,
        previous: Digest,
        access: Digest,
        refresh: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, Rotation>;
    fn revoke<'a>(
        &'a self,
        scope: &'a dyn Scope,
        refresh: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, ()>;
    fn access<'a>(
        &'a self,
        scope: &'a dyn Scope,
        access: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, Id> {
        Box::pin(async move { Ok(self.access_session(scope, access, now).await?.principal_id) })
    }
    fn access_session<'a>(
        &'a self,
        scope: &'a dyn Scope,
        access: Digest,
        now: Timestamp,
    ) -> PortFuture<'a, SessionEvidence>;
}

/// Retained local facts. A matching stable-ID envelope commits in the owned outbox;
/// this record is not an Audit consumer receipt.
#[derive(Clone, Debug)]
pub struct CommittedFact {
    pub id: Id,
    pub fact: Fact,
    pub metadata: Metadata,
}
