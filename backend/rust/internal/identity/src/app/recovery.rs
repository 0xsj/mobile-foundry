use crate::{
    app::ports::{PortFuture, Scope},
    domain::{
        self, Principal, Secret, Timestamp,
        recovery::{Challenge, Contact, Purpose},
    },
};
use kernel::{Failure, Kind, id::Id};
use std::fmt;
pub fn challenge_refused() -> Failure {
    Failure::new(
        Kind::Invalid,
        "identity.challenge_refused",
        "challenge invalid, consumed or expired",
    )
    .with_public("Request a new verification or recovery code.")
}
pub fn contact_conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "identity.contact_conflict",
        "verified address cannot be assigned",
    )
    .with_public("That recovery address cannot be assigned.")
}
pub fn mail_unavailable() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "identity.mail_unavailable",
        "identity mail could not be accepted",
    )
    .with_public("Email is unavailable. Try again later.")
}
pub trait RecoveryStore: Send + Sync {
    fn contact<'a>(&'a self, s: &'a dyn Scope, actor: &'a Id) -> PortFuture<'a, Option<Contact>>;
    fn by_email<'a>(
        &'a self,
        s: &'a dyn Scope,
        email: &'a str,
    ) -> PortFuture<'a, Option<Principal>>;
    fn begin_challenge<'a>(&'a self, s: &'a dyn Scope, c: Challenge) -> PortFuture<'a, ()>;
    fn verify_email<'a>(
        &'a self,
        s: &'a dyn Scope,
        digest: domain::Digest,
        now: Timestamp,
    ) -> PortFuture<'a, ()>;
    fn reset_password<'a>(
        &'a self,
        s: &'a dyn Scope,
        digest: domain::Digest,
        hash: Secret,
        now: Timestamp,
    ) -> PortFuture<'a, ()>;
    fn change_password<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        expected: Secret,
        hash: Secret,
        now: Timestamp,
    ) -> PortFuture<'a, ()>;
    fn prune_challenges<'a>(
        &'a self,
        s: &'a dyn Scope,
        now: Timestamp,
        limit: usize,
    ) -> PortFuture<'a, usize>;
}
pub struct Mail {
    pub to: String,
    pub purpose: Purpose,
    pub token: Secret,
}
impl fmt::Debug for Mail {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Mail { redacted }")
    }
}
pub trait Mailer: Send + Sync {
    fn send<'a>(&'a self, s: &'a dyn Scope, mail: Mail) -> PortFuture<'a, ()>;
}

/// Anonymous admission only; lookup and delivery belong to the selected worker.
pub trait ResetRequests: Send + Sync {
    fn enqueue(&self, s: &dyn Scope, address: &str) -> Result<(), Failure>;
}
