//! Owner-scoped aggregate; no authentication provider, HTTP or persistence imports.
use kernel::{Failure, Kind, id::Id};
use std::{
    collections::BTreeMap,
    fmt,
    time::{SystemTime, UNIX_EPOCH},
};

pub const MAX_VERSION: u64 = 9_007_199_254_740_991;
pub const MAX_TIME: u64 = 253_402_300_799_999_999;
pub fn invalid_state() -> Failure {
    Failure::new(
        Kind::Internal,
        "account.invalid_state",
        "invalid account state",
    )
}
pub fn name(value: &str) -> Result<String, Failure> {
    let value = value.trim();
    if !(1..=80).contains(&value.chars().count()) || value.chars().any(char::is_control) {
        return Err(Failure::new(
            Kind::Invalid,
            "account.invalid_name",
            "invalid account name",
        )
        .with_public("Provide an account name of 1 to 80 characters.")
        .with_fields(BTreeMap::from([(
            "name".into(),
            "Use 1 to 80 characters without control characters.".into(),
        )])));
    }
    Ok(value.into())
}
pub fn micros(time: SystemTime) -> Result<u64, Failure> {
    let value: u64 = time
        .duration_since(UNIX_EPOCH)
        .map_err(|_| invalid_state())?
        .as_micros()
        .try_into()
        .map_err(|_| invalid_state())?;
    if value > MAX_TIME {
        return Err(invalid_state());
    }
    Ok(value)
}
#[derive(Clone, PartialEq, Eq)]
pub struct Snapshot {
    pub id: Id,
    pub owner_id: Id,
    pub name: String,
    pub version: u64,
    pub created_at: u64,
    pub updated_at: u64,
}
#[derive(Clone)]
pub struct Account(Snapshot);
impl fmt::Debug for Account {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Account { redacted }")
    }
}
impl Account {
    pub fn new(id: Id, owner_id: Id, value: &str, now: SystemTime) -> Result<Self, Failure> {
        let now = micros(now)?;
        Self::restore(Snapshot {
            id,
            owner_id,
            name: name(value)?,
            version: 1,
            created_at: now,
            updated_at: now,
        })
    }
    pub fn restore(s: Snapshot) -> Result<Self, Failure> {
        if name(&s.name).ok().as_ref() != Some(&s.name)
            || !(1..=MAX_VERSION).contains(&s.version)
            || s.updated_at > MAX_TIME
            || s.updated_at < s.created_at
        {
            return Err(invalid_state());
        }
        Ok(Self(s))
    }
    pub fn snapshot(&self) -> &Snapshot {
        &self.0
    }
    pub fn owned_by(&self, actor: &Id) -> bool {
        self.0.owner_id == *actor
    }
    pub fn rename(&self, value: &str, now: SystemTime) -> Result<Self, Failure> {
        let mut next = self.0.clone();
        next.name = name(value)?;
        next.version = next.version.checked_add(1).ok_or_else(invalid_state)?;
        next.updated_at = micros(now)?;
        Self::restore(next)
    }
    pub fn fact(&self, action: Action, actor: &Id) -> Result<Fact, Failure> {
        if !self.owned_by(actor)
            || (action == Action::Created && self.0.version != 1)
            || (action == Action::Renamed && self.0.version < 2)
        {
            return Err(invalid_state());
        }
        Ok(Fact {
            account_id: self.0.id.clone(),
            actor_id: actor.clone(),
            version: self.0.version,
            action,
            occurred_at: self.0.updated_at,
        })
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Action {
    Created,
    Renamed,
}
/// Required local evidence excludes mutable names and all credentials.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Fact {
    pub account_id: Id,
    pub actor_id: Id,
    pub version: u64,
    pub action: Action,
    pub occurred_at: u64,
}
