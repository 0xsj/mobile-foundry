//! Identity policy and immutable values. No HTTP, database or runtime imports.
pub mod recovery;
use kernel::{Failure, Kind, id::Id};
use std::{
    fmt,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use zeroize::Zeroizing;

pub const ACCESS_LIFETIME: u64 = 15 * 60 * 1_000_000;
pub const SESSION_LIFETIME: u64 = 30 * 24 * 60 * 60 * 1_000_000;
pub const MAX_GENERATION: u64 = (1 << 53) - 1;
const MAX_TIME: u64 = 253_402_300_799_999_999;

pub fn invalid_input() -> Failure {
    Failure::new(
        Kind::Invalid,
        "identity.invalid_input",
        "invalid identity input",
    )
    .with_public("Check the login, display name and password.")
}
pub fn invalid_state() -> Failure {
    Failure::new(
        Kind::Internal,
        "identity.invalid_state",
        "invalid identity state",
    )
}
pub fn credential_refused() -> Failure {
    Failure::new(
        Kind::Unauthenticated,
        "identity.invalid_credential",
        "credential is not accepted",
    )
    .with_public("Sign in again to continue.")
}

/// Debug is deliberately redacted. Explicit exposure belongs only at security/wire boundaries.
#[derive(Clone)]
pub struct Secret(Zeroizing<String>);
impl Secret {
    pub fn new(value: String) -> Self {
        Self(Zeroizing::new(value))
    }
    pub fn expose(&self) -> &str {
        &self.0
    }
}
impl fmt::Debug for Secret {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Secret { redacted }")
    }
}

#[derive(Clone, PartialEq, Eq)]
pub struct Login(String);
impl Login {
    pub fn new(value: &str) -> Result<Self, Failure> {
        // Go's simple Unicode lowercase maps U+0130 to one ASCII i.
        let value: String = value
            .trim()
            .chars()
            .flat_map(|c| {
                if c == '\u{130}' {
                    'I'.to_lowercase()
                } else {
                    c.to_lowercase()
                }
            })
            .collect();
        let bytes = value.as_bytes();
        if !(3..=128).contains(&bytes.len())
            || !bytes[0].is_ascii_alphanumeric()
            || !bytes
                .iter()
                .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b"._@+-".contains(b))
        {
            return Err(invalid_input());
        }
        Ok(Self(value))
    }
    pub fn value(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, PartialEq, Eq)]
pub struct DisplayName(String);
impl DisplayName {
    pub fn new(value: &str) -> Result<Self, Failure> {
        let value = value.trim();
        if !(1..=80).contains(&value.chars().count()) || value.chars().any(char::is_control) {
            return Err(invalid_input());
        }
        Ok(Self(value.into()))
    }
    pub fn value(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug)]
pub struct Password(Secret);
impl Password {
    pub fn new(value: Secret) -> Result<Self, Failure> {
        if value.expose().chars().count() < 12 || value.expose().len() > 256 {
            return Err(invalid_input());
        }
        Ok(Self(value))
    }
    pub fn expose(&self) -> &str {
        self.0.expose()
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Timestamp(u64);
impl Timestamp {
    pub fn from_system_time(value: SystemTime) -> Result<Self, Failure> {
        let micros = value
            .duration_since(UNIX_EPOCH)
            .map_err(|_| invalid_state())?
            .as_micros();
        Self::from_micros(micros.try_into().map_err(|_| invalid_state())?)
    }
    pub fn from_micros(value: u64) -> Result<Self, Failure> {
        if value > MAX_TIME {
            return Err(invalid_state());
        }
        Ok(Self(value))
    }
    pub fn micros(self) -> u64 {
        self.0
    }
    pub fn system_time(self) -> SystemTime {
        UNIX_EPOCH + Duration::from_micros(self.0)
    }
    fn plus(self, micros: u64) -> Result<Self, Failure> {
        Self::from_micros(self.0.checked_add(micros).ok_or_else(invalid_state)?)
    }
}

#[derive(Clone)]
pub struct Principal {
    id: Id,
    login: Login,
    name: DisplayName,
    hash: Secret,
    created_at: Timestamp,
}
impl fmt::Debug for Principal {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Principal { redacted }")
    }
}
impl Principal {
    pub fn new(
        id: Id,
        login: Login,
        name: DisplayName,
        hash: Secret,
        now: Timestamp,
    ) -> Result<Self, Failure> {
        if hash.expose().is_empty() || hash.expose().len() > 512 {
            return Err(invalid_state());
        }
        Ok(Self {
            id,
            login,
            name,
            hash,
            created_at: now,
        })
    }
    pub fn id(&self) -> &Id {
        &self.id
    }
    pub fn login(&self) -> &str {
        self.login.value()
    }
    pub fn display_name(&self) -> &str {
        self.name.value()
    }
    pub fn password_hash(&self) -> &Secret {
        &self.hash
    }
    pub fn created_at(&self) -> Timestamp {
        self.created_at
    }
    pub fn fact(&self) -> Fact {
        Fact {
            principal_id: self.id.clone(),
            session_id: None,
            action: Action::Registered,
            version: 1,
            occurred_at: self.created_at,
        }
    }
}

#[derive(Clone, PartialEq, Eq, Hash)]
pub struct Digest(String);
impl Digest {
    pub fn parse(value: String) -> Result<Self, Failure> {
        if value.len() != 64
            || !value
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
        {
            return Err(invalid_state());
        }
        Ok(Self(value))
    }
    pub fn value(&self) -> &str {
        &self.0
    }
}
impl fmt::Debug for Digest {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Digest { redacted }")
    }
}

#[derive(Clone)]
pub struct SessionState {
    pub id: Id,
    pub principal_id: Id,
    pub generation: u64,
    pub access: Digest,
    pub refresh: Digest,
    pub created_at: Timestamp,
    pub updated_at: Timestamp,
    pub access_expires_at: Timestamp,
    pub expires_at: Timestamp,
    pub revoked_at: Option<Timestamp>,
}
#[derive(Clone)]
pub struct Session(SessionState);
impl fmt::Debug for SessionState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("SessionState { redacted }")
    }
}
impl fmt::Debug for Session {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Session { redacted }")
    }
}
impl Session {
    pub fn new(
        id: Id,
        principal_id: Id,
        access: Digest,
        refresh: Digest,
        now: Timestamp,
    ) -> Result<Self, Failure> {
        Self::restore(SessionState {
            id,
            principal_id,
            generation: 1,
            access,
            refresh,
            created_at: now,
            updated_at: now,
            access_expires_at: now.plus(ACCESS_LIFETIME)?,
            expires_at: now.plus(SESSION_LIFETIME)?,
            revoked_at: None,
        })
    }
    pub fn restore(s: SessionState) -> Result<Self, Failure> {
        if !(1..=MAX_GENERATION).contains(&s.generation)
            || s.access == s.refresh
            || s.updated_at < s.created_at
            || s.expires_at != s.created_at.plus(SESSION_LIFETIME)?
            || s.access_expires_at <= s.created_at
            || (s.revoked_at.is_none() && s.access_expires_at <= s.updated_at)
            || s.access_expires_at > s.expires_at
            || s.access_expires_at > s.updated_at.plus(ACCESS_LIFETIME)?
            || s.revoked_at
                .is_some_and(|v| v < s.created_at || v != s.updated_at)
        {
            return Err(invalid_state());
        }
        Ok(Self(s))
    }
    pub fn state(&self) -> &SessionState {
        &self.0
    }
    pub fn active(&self, now: Timestamp) -> bool {
        self.renewable(now) && now < self.0.access_expires_at
    }
    pub fn renewable(&self, now: Timestamp) -> bool {
        now >= self.0.created_at && self.0.revoked_at.is_none() && now < self.0.expires_at
    }
    pub fn rotate(&self, access: Digest, refresh: Digest, now: Timestamp) -> Result<Self, Failure> {
        if !self.renewable(now) {
            return Err(credential_refused());
        }
        if now < self.0.updated_at || access == self.0.access || refresh == self.0.refresh {
            return Err(invalid_state());
        }
        let mut s = self.0.clone();
        s.generation += 1;
        s.access = access;
        s.refresh = refresh;
        s.updated_at = now;
        s.access_expires_at = now.plus(ACCESS_LIFETIME)?.min(s.expires_at);
        Self::restore(s)
    }
    pub fn revoke(&self, now: Timestamp) -> Result<Self, Failure> {
        if now < self.0.updated_at {
            return Err(invalid_state());
        }
        let mut s = self.0.clone();
        s.generation += 1;
        s.updated_at = now;
        s.revoked_at = Some(now);
        Self::restore(s)
    }
    pub fn fact(&self, action: Action) -> Fact {
        Fact {
            principal_id: self.0.principal_id.clone(),
            session_id: Some(self.0.id.clone()),
            action,
            version: self.0.generation,
            occurred_at: self.0.updated_at,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Action {
    Registered,
    Issued,
    Renewed,
    SignedOut,
    RefreshReused,
    EmailVerified,
    PasswordChanged,
    PasswordRecovered,
    PasswordChangeRevoked,
    PasswordResetRevoked,
}
#[derive(Clone, Debug)]
pub struct Fact {
    pub principal_id: Id,
    pub session_id: Option<Id>,
    pub action: Action,
    pub version: u64,
    pub occurred_at: Timestamp,
}
