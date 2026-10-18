pub mod command;
pub mod ports;
pub mod query;
use kernel::{Failure, Kind};
pub fn denied() -> Failure {
    Failure::new(Kind::Forbidden, "audit.access_denied", "audit scope denied")
        .with_public("You cannot read this account's activity.")
}
pub fn invalid_query() -> Failure {
    Failure::new(
        Kind::Invalid,
        "audit.invalid_query",
        "invalid audit position or limit",
    )
    .with_public("Use a valid activity cursor and a limit from 1 to 100.")
}
pub fn replay() -> Failure {
    Failure::new(
        Kind::Invalid,
        "audit.event_conflict",
        "event identity reused with different evidence",
    )
}
pub fn capacity() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "audit.memory_capacity",
        "audit memory capacity reached",
    )
}
