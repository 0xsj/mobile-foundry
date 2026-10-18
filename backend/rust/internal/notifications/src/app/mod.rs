pub mod command;
pub mod installations;
pub mod ports;
pub mod query;
use crate::domain::{self, Item, Snapshot};
use kernel::{Failure, Kind, id::Id};
use std::sync::Arc;
pub fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "notifications.invalid_input",
        "invalid notification input",
    )
    .with_public("Check the notification request fields.")
}
pub fn forbidden() -> Failure {
    Failure::new(
        Kind::Forbidden,
        "notifications.account_forbidden",
        "notification account access denied",
    )
    .with_public("You cannot access notifications in this account.")
}
pub fn missing() -> Failure {
    Failure::new(
        Kind::NotFound,
        "notifications.not_found",
        "notification not found",
    )
    .with_public("This notification is not available.")
}
pub fn replay_conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.event_conflict",
        "source event identity reused with different input",
    )
}
pub fn capacity() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "notifications.capacity",
        "notification store capacity reached",
    )
}
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Target {
    pub actor: Id,
    pub account: Id,
}
#[derive(Clone, Debug)]
pub struct Delivery {
    pub event_id: Id,
    pub account_id: Id,
    pub principal_id: Id,
    pub file_id: Id,
    pub occurred_at: u64,
}
impl Delivery {
    pub fn validate(&self) -> Result<(), Failure> {
        if self.occurred_at > domain::MAX_TIME {
            return Err(invalid());
        }
        Ok(())
    }
    pub fn target(&self) -> Target {
        Target {
            actor: self.principal_id.clone(),
            account: self.account_id.clone(),
        }
    }
    pub fn item(&self, sequence: u64, at: u64) -> Result<Item, Failure> {
        self.validate()?;
        Item::restore(Snapshot {
            id: self.event_id.clone(),
            account_id: self.account_id.clone(),
            principal_id: self.principal_id.clone(),
            file_id: self.file_id.clone(),
            sequence,
            kind: domain::UPLOAD_READY.into(),
            category: domain::MEDIA.into(),
            occurred_at: self.occurred_at,
            created_at: at,
            read_at: None,
        })
    }
}
#[derive(Clone)]
pub struct ListInput {
    pub target: Target,
    pub before: u64,
    pub limit: usize,
    pub unread_only: bool,
}
impl ListInput {
    pub fn validate(&self) -> Result<(), Failure> {
        if self.before > domain::MAX_SEQUENCE || !(1..=100).contains(&self.limit) {
            return Err(invalid());
        }
        Ok(())
    }
}
pub struct Page {
    pub items: Vec<Item>,
    pub next: Option<u64>,
    pub unread_count: u64,
}
#[derive(Clone)]
pub struct Dependencies {
    pub store: Arc<dyn ports::Store>,
    pub access: Arc<dyn ports::AccountAccess>,
    pub clock: Arc<dyn kernel::clock::Clock>,
}
pub async fn authorize(
    s: &dyn ports::Scope,
    access: &dyn ports::AccountAccess,
    t: &Target,
) -> Result<(), Failure> {
    s.check()?;
    if !access.allowed(s, &t.actor, &t.account).await? {
        return Err(forbidden());
    }
    Ok(())
}
pub fn now(clock: &dyn kernel::clock::Clock) -> Result<u64, Failure> {
    let at: u64 = clock
        .now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| domain::invalid_state())?
        .as_micros()
        .try_into()
        .map_err(|_| domain::invalid_state())?;
    if at > domain::MAX_TIME {
        return Err(domain::invalid_state());
    }
    Ok(at)
}
