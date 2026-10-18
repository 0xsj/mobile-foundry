//! An inbox reference, never a copy of Files content or account authority.
pub mod installation;
pub mod push_job;
use kernel::{Failure, Kind, id::Id};
pub const MAX_SEQUENCE: u64 = 9_007_199_254_740_991;
pub const MAX_TIME: u64 = 253_402_300_799_999_999;
pub const UPLOAD_READY: &str = "files.upload_ready";
pub const MEDIA: &str = "media";
pub fn invalid_state() -> Failure {
    Failure::new(
        Kind::Internal,
        "notifications.invalid_state",
        "invalid notification state",
    )
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Snapshot {
    pub id: Id,
    pub account_id: Id,
    pub principal_id: Id,
    pub file_id: Id,
    pub sequence: u64,
    pub kind: String,
    pub category: String,
    pub occurred_at: u64,
    pub created_at: u64,
    pub read_at: Option<u64>,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Item {
    state: Snapshot,
}
impl Item {
    pub fn restore(s: Snapshot) -> Result<Self, Failure> {
        if !(1..=MAX_SEQUENCE).contains(&s.sequence)
            || s.kind != UPLOAD_READY
            || s.category != MEDIA
            || s.occurred_at > MAX_TIME
            || s.created_at > MAX_TIME
            || s.read_at.is_some_and(|t| t > MAX_TIME)
        {
            return Err(invalid_state());
        }
        Ok(Self { state: s })
    }
    pub fn snapshot(&self) -> &Snapshot {
        &self.state
    }
    pub fn mark_read(&self, at: u64) -> Result<Self, Failure> {
        if at > MAX_TIME {
            return Err(invalid_state());
        }
        let mut next = self.state.clone();
        next.read_at.get_or_insert(at);
        Self::restore(next)
    }
}
