pub mod events;
pub mod memory;
pub mod security;

pub mod postgres;
mod postgres_outbox;

pub mod email;
pub mod reset_queue;

struct Mutation<'a> {
    expected: &'a crate::domain::Secret,
    hash: crate::domain::Secret,
    now: crate::domain::Timestamp,
    reset: bool,
}
