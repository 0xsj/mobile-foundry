pub mod command;
pub mod ports;
pub mod query;

use crate::domain::{Principal, Secret, Session};
use kernel::{Failure, Kind};

pub fn login_exists() -> Failure {
    Failure::new(
        Kind::Conflict,
        "identity.login_exists",
        "login already registered",
    )
    .with_public("That login is already registered.")
}
pub fn capacity() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "identity.memory_capacity",
        "memory identity profile is full",
    )
}
pub fn busy() -> Failure {
    Failure::new(
        Kind::RateLimited,
        "identity.attempt_limited",
        "identity attempt budget exhausted",
    )
    .with_public("Wait briefly before trying again.")
}

#[derive(Debug)]
pub struct Grant {
    pub principal: Principal,
    pub session: Session,
    pub access: Secret,
    pub refresh: Secret,
}

pub mod recovery;
