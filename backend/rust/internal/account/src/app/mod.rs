pub mod command;
pub mod ports;
pub mod query;
use kernel::{Failure, Kind};
use ports::{Directory, Store};
use std::sync::Arc;

#[derive(Clone)]
pub struct Dependencies {
    pub store: Arc<dyn Store>,
    pub directory: Arc<dyn Directory>,
    pub clock: Arc<dyn kernel::clock::Clock>,
    pub ids: Arc<dyn kernel::id::Generator>,
}
pub fn not_found() -> Failure {
    Failure::new(Kind::NotFound, "account.not_found", "account missing")
        .with_public("The account was not found.")
}
pub fn denied() -> Failure {
    Failure::new(
        Kind::Forbidden,
        "account.access_denied",
        "account ownership refused",
    )
    .with_public("You cannot access this account.")
}
pub fn conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "account.version_conflict",
        "account version changed",
    )
    .with_public("The account changed. Reload it before saving.")
}
pub fn capacity() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "account.memory_capacity",
        "account memory capacity reached",
    )
    .with_public("The local account store is full.")
}
pub fn invalid_page() -> Failure {
    Failure::new(
        Kind::Invalid,
        "account.invalid_page",
        "invalid account page",
    )
    .with_public("Send a valid account cursor and a limit from 1 to 100.")
}
pub fn required() -> Failure {
    Failure::new(
        Kind::Unauthenticated,
        "principal.authentication_required",
        "principal required",
    )
    .with_public("Sign in to continue.")
}
