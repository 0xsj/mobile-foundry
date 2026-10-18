//! Notes policy and owned values; no transport, persistence or runtime dependencies.
use kernel::{Failure, Kind, id::Id};
use std::{collections::BTreeMap, fmt};
pub const MAX_VERSION: u64 = 9_007_199_254_740_991;
pub fn valid_title(title: &str) -> bool {
    title.chars().count() <= 200
        && title
            .chars()
            .any(|c| !matches!(c, ' ' | '\t' | '\r' | '\n'))
}
pub fn invalid_title() -> Failure {
    Failure::new(Kind::Invalid, "notes.invalid_title", "invalid note title")
        .with_public("Check the highlighted field.")
        .with_fields(BTreeMap::from([(
            "title".into(),
            "Enter a title of 200 characters or fewer.".into(),
        )]))
}
pub fn invalid_write() -> Failure {
    Failure::new(Kind::Invalid, "notes.invalid_write", "invalid remote write")
        .with_public("Use an admitted command identifier, operation and version.")
}
pub fn invalid_state() -> Failure {
    Failure::new(
        Kind::Internal,
        "notes.invalid_state",
        "invalid persisted Notes state",
    )
}
pub fn missing() -> Failure {
    Failure::new(Kind::NotFound, "notes.not_found", "note absent")
        .with_public("This note no longer exists. Refresh your notes.")
}
pub fn conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notes.version_conflict",
        "note version changed",
    )
    .with_public("This note changed. Refresh before editing again.")
}
pub fn command_conflict() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notes.command_conflict",
        "committed command has different input",
    )
    .with_public("This command identifier already belongs to a different write.")
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Action {
    Create,
    Update,
    Delete,
}
impl Action {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Create => "create",
            Self::Update => "update",
            Self::Delete => "delete",
        }
    }
    pub fn parse(value: &str) -> Result<Self, Failure> {
        match value {
            "create" => Ok(Self::Create),
            "update" => Ok(Self::Update),
            "delete" => Ok(Self::Delete),
            _ => Err(invalid_write()),
        }
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct Write {
    pub command_id: Id,
    pub action: Action,
    pub note_id: Option<Id>,
    pub title: String,
    pub expected: u64,
}
impl Write {
    pub fn validate(&self) -> Result<(), Failure> {
        match self.action {
            Action::Create if self.note_id.is_some() || self.expected != 0 => {
                return Err(invalid_write());
            }
            Action::Update | Action::Delete
                if self.note_id.is_none() || !(1..MAX_VERSION).contains(&self.expected) =>
            {
                return Err(invalid_write());
            }
            _ => {}
        }
        if self.action == Action::Delete {
            if !self.title.is_empty() {
                return Err(invalid_write());
            }
        } else if !valid_title(&self.title) {
            return Err(invalid_title());
        }
        Ok(())
    }
}
impl fmt::Debug for Write {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Write { redacted }")
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct Note {
    id: Id,
    title: String,
    version: u64,
}
impl Note {
    pub fn restore(id: Id, title: String, version: u64) -> Result<Self, Failure> {
        if !valid_title(&title) || !(1..=MAX_VERSION).contains(&version) {
            return Err(invalid_state());
        }
        Ok(Self { id, title, version })
    }
    pub fn id(&self) -> &Id {
        &self.id
    }
    pub fn title(&self) -> &str {
        &self.title
    }
    pub fn version(&self) -> u64 {
        self.version
    }
}
impl fmt::Debug for Note {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Note { redacted }")
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct ReceiptState {
    pub command_id: Id,
    pub account_id: Id,
    pub principal_id: Id,
    pub action: Action,
    pub note_id: Id,
    pub title: String,
    pub version: u64,
}
#[derive(Clone, PartialEq, Eq)]
pub struct Receipt(ReceiptState);
impl Receipt {
    pub fn restore(s: ReceiptState) -> Result<Self, Failure> {
        let valid = match s.action {
            Action::Create => s.version == 1 && valid_title(&s.title),
            Action::Update => s.version >= 2 && valid_title(&s.title),
            Action::Delete => s.version >= 2 && s.title.is_empty(),
        };
        if !valid || s.version > MAX_VERSION {
            return Err(invalid_state());
        }
        Ok(Self(s))
    }
    pub fn snapshot(&self) -> &ReceiptState {
        &self.0
    }
}
impl fmt::Debug for Receipt {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Receipt { redacted }")
    }
}
pub fn decide(
    write: &Write,
    current: Option<&Note>,
    candidate: Option<Id>,
    account: &Id,
    actor: &Id,
) -> Result<Receipt, Failure> {
    write.validate()?;
    let (note_id, version) = if write.action == Action::Create {
        if current.is_some() {
            return Err(invalid_state());
        }
        (candidate.ok_or_else(invalid_state)?, 1)
    } else {
        let current = current.ok_or_else(missing)?;
        if Some(current.id()) != write.note_id.as_ref() {
            return Err(invalid_state());
        }
        if current.version() != write.expected {
            return Err(conflict());
        }
        (current.id().clone(), current.version() + 1)
    };
    Receipt::restore(ReceiptState {
        command_id: write.command_id.clone(),
        account_id: account.clone(),
        principal_id: actor.clone(),
        action: write.action,
        note_id,
        title: write.title.clone(),
        version,
    })
}
