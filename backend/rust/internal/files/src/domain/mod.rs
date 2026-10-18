//! Private lifecycle state; byte storage and transport do not enter the domain.
use kernel::{Failure, Kind, id::Id};
pub const MAX_BYTES: usize = 10 * 1024 * 1024;
pub const MAX_TIME: u64 = 253_402_300_799_999_999;
pub fn invalid_spec() -> Failure {
    Failure::new(Kind::Invalid, "files.invalid_spec", "invalid file specification")
        .with_public("Use a name of 1–255 characters, a supported content type, a SHA-256 digest and between 1 byte and 10 MiB of content.")
}
pub fn invalid_state() -> Failure {
    Failure::new(
        Kind::Internal,
        "files.invalid_state",
        "invalid persisted file state",
    )
}
pub fn transition() -> Failure {
    Failure::new(
        Kind::Conflict,
        "files.invalid_transition",
        "file lifecycle transition refused",
    )
    .with_public("This file cannot perform that operation in its current state.")
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Spec {
    pub name: String,
    pub content_type: String,
    pub byte_size: u64,
    pub sha256: String,
}
impl Spec {
    pub fn normalize(mut self) -> Result<Self, Failure> {
        self.name = self.name.trim().into();
        if !(1..=255).contains(&self.name.chars().count())
            || self
                .name
                .chars()
                .any(|c| c.is_control() || c == '/' || c == '\\')
            || !(1..=MAX_BYTES as u64).contains(&self.byte_size)
            || ![
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/heic",
                "image/heif",
                "application/pdf",
                "application/octet-stream",
            ]
            .contains(&self.content_type.as_str())
            || self.sha256.len() != 64
            || !self
                .sha256
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
        {
            return Err(invalid_spec());
        }
        Ok(self)
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Status {
    Pending,
    Available,
    Deleted,
}
impl Status {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Pending => "pending_upload",
            Self::Available => "available",
            Self::Deleted => "deleted",
        }
    }
    pub fn parse(s: &str) -> Result<Self, Failure> {
        match s {
            "pending_upload" => Ok(Self::Pending),
            "available" => Ok(Self::Available),
            "deleted" => Ok(Self::Deleted),
            _ => Err(invalid_state()),
        }
    }
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Snapshot {
    pub id: Id,
    pub account_id: Id,
    pub owner_id: Id,
    pub spec: Spec,
    pub status: Status,
    pub version: u64,
    pub created_at: u64,
    pub updated_at: u64,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct File {
    state: Snapshot,
}
impl File {
    pub fn new(
        id: Id,
        account_id: Id,
        owner_id: Id,
        spec: Spec,
        now: u64,
    ) -> Result<Self, Failure> {
        Self::restore(Snapshot {
            id,
            account_id,
            owner_id,
            spec: spec.normalize()?,
            status: Status::Pending,
            version: 1,
            created_at: now,
            updated_at: now,
        })
    }
    pub fn restore(s: Snapshot) -> Result<Self, Failure> {
        let valid = match s.status {
            Status::Pending => s.version == 1,
            Status::Available => s.version == 2,
            Status::Deleted => [2, 3].contains(&s.version),
        };
        if !valid
            || s.created_at > s.updated_at
            || s.updated_at > MAX_TIME
            || s.spec.clone().normalize().ok().as_ref() != Some(&s.spec)
        {
            return Err(invalid_state());
        }
        Ok(Self { state: s })
    }
    pub fn snapshot(&self) -> &Snapshot {
        &self.state
    }
    pub fn complete(&self, now: u64) -> Result<Self, Failure> {
        if self.state.status != Status::Pending {
            return Err(transition());
        }
        self.next(Status::Available, now)
    }
    pub fn delete(&self, now: u64) -> Result<Self, Failure> {
        if self.state.status == Status::Deleted {
            return Err(transition());
        }
        self.next(Status::Deleted, now)
    }
    fn next(&self, status: Status, now: u64) -> Result<Self, Failure> {
        let mut s = self.state.clone();
        s.status = status;
        s.version += 1;
        s.updated_at = now;
        Self::restore(s)
    }
}
