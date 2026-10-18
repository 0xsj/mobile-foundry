//! Trusted credential evidence shared by edges; this package does not authenticate by itself.
pub mod capabilities;
use kernel::{
    Failure, Kind,
    context::{PortFuture, Scope},
    id::Id,
};
use std::time::SystemTime;
pub struct Principal {
    pub id: Id,
    pub session_id: Id,
    pub session_expires_at: SystemTime,
}
pub trait Resolver: Send + Sync {
    fn resolve<'a>(&'a self, scope: &'a dyn Scope, token: &'a str) -> PortFuture<'a, Principal>;
}
pub fn required() -> Failure {
    Failure::new(
        Kind::Unauthenticated,
        "principal.authentication_required",
        "principal required",
    )
    .with_public("Sign in to continue.")
}
