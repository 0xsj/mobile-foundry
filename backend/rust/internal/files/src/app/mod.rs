pub mod command;
pub mod ports;
pub mod query;
use crate::domain::{self, File, Snapshot, Spec, Status};
use kernel::{Failure, Kind, id::Id};
use std::sync::Arc;
pub fn missing() -> Failure {
    Failure::new(Kind::NotFound, "files.not_found", "file not found")
        .with_public("This file is not available.")
}
pub fn missing_receipt() -> Failure {
    Failure::new(
        Kind::NotFound,
        "files.receipt_not_found",
        "file receipt not found",
    )
    .with_public("No committed receipt was found.")
}
pub fn forbidden() -> Failure {
    Failure::new(
        Kind::Forbidden,
        "files.account_forbidden",
        "file account access denied",
    )
    .with_public("You cannot access files in this account.")
}
pub fn conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "files.version_conflict",
        "file version changed",
    )
    .with_public("Refresh the file before trying again.")
}
pub fn key_conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "files.command_conflict",
        "idempotency key reused with different input",
    )
    .with_public("This command key belongs to a different operation.")
}
pub fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "files.invalid_input",
        "invalid files application input",
    )
    .with_public("Check the file request fields.")
}
pub fn content() -> Failure {
    Failure::new(
        Kind::Invalid,
        "files.content_mismatch",
        "file bytes do not match declared size or digest",
    )
    .with_public("The content does not match the declared size or SHA-256 digest.")
}
pub fn object_missing() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "files.object_missing",
        "file object not present",
    )
    .with_public("Upload the file content before completing the upload.")
}
pub fn storage() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "files.storage_unavailable",
        "file object store unavailable",
    )
    .with_public("File storage is temporarily unavailable.")
}
pub fn capacity() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "files.capacity",
        "memory file metadata capacity reached",
    )
}
pub fn exhausted() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "files.delivery_exhausted",
        "files event delivery exhausted retries",
    )
    .with_public("Files event delivery needs attention.")
}
pub fn valid_key(key: &str) -> bool {
    events::valid_token(key)
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Action {
    Request,
    Complete,
    Delete,
}
impl Action {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Request => "request_upload",
            Self::Complete => "complete_upload",
            Self::Delete => "delete",
        }
    }
    pub fn parse(s: &str) -> Result<Self, Failure> {
        match s {
            "request_upload" => Ok(Self::Request),
            "complete_upload" => Ok(Self::Complete),
            "delete" => Ok(Self::Delete),
            _ => Err(invalid()),
        }
    }
}
#[derive(Clone, Debug)]
pub struct Command {
    pub action: Action,
    pub key: String,
    pub file_id: Option<Id>,
    pub spec: Option<Spec>,
    pub expected: u64,
}
impl Command {
    pub fn validate(&self) -> Result<(), Failure> {
        if !self.key.is_empty() && !valid_key(&self.key) {
            return Err(invalid());
        }
        if self.action == Action::Request {
            let spec = self.spec.as_ref().ok_or_else(invalid)?;
            let normalized = spec.clone().normalize()?;
            if &normalized != spec || self.file_id.is_some() || self.expected != 0 {
                return Err(invalid());
            }
        } else if self.file_id.is_none() || !(1..=3).contains(&self.expected) || self.spec.is_some()
        {
            return Err(invalid());
        }
        Ok(())
    }
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Receipt {
    pub key: String,
    pub action: Action,
    pub actor: Id,
    pub file: Snapshot,
}
impl Receipt {
    pub fn validate(&self, keyed: bool) -> Result<(), Failure> {
        File::restore(self.file.clone())?;
        if (keyed && !valid_key(&self.key))
            || (!self.key.is_empty() && !valid_key(&self.key))
            || !matches!(
                (self.action, self.file.status),
                (Action::Request, Status::Pending)
                    | (Action::Complete, Status::Available)
                    | (Action::Delete, Status::Deleted)
            )
        {
            return Err(domain::invalid_state());
        }
        Ok(())
    }
}
#[derive(Clone)]
pub struct Target {
    pub actor: Id,
    pub account: Id,
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
pub fn plan(
    t: &Target,
    c: &Command,
    current: Option<&File>,
    candidate: Option<Id>,
    now: u64,
) -> Result<File, Failure> {
    c.validate()?;
    if c.action == Action::Request {
        return File::new(
            candidate.ok_or_else(invalid)?,
            t.account.clone(),
            t.actor.clone(),
            c.spec.clone().ok_or_else(invalid)?,
            now,
        );
    }
    let current = current
        .filter(|f| f.snapshot().status != Status::Deleted)
        .ok_or_else(missing)?;
    let v = current.snapshot();
    if v.account_id != t.account || Some(&v.id) != c.file_id.as_ref() {
        return Err(domain::invalid_state());
    }
    if v.version != c.expected {
        return Err(conflict());
    }
    if c.action == Action::Complete {
        current.complete(now)
    } else {
        current.delete(now)
    }
}
#[derive(Clone)]
pub struct Dependencies {
    pub store: Arc<dyn ports::Store>,
    pub objects: Arc<dyn ports::Objects>,
    pub access: Arc<dyn ports::AccountAccess>,
    pub ids: Arc<dyn kernel::id::Generator>,
    pub clock: Arc<dyn kernel::clock::Clock>,
}
pub fn now(clock: &dyn kernel::clock::Clock) -> Result<u64, Failure> {
    clock
        .now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| domain::invalid_state())?
        .as_micros()
        .try_into()
        .map_err(|_| domain::invalid_state())
}
