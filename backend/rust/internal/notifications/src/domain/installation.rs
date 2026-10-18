//! Session-bound delivery metadata; possession and lifecycle stay private.
use super::{MAX_SEQUENCE, MAX_TIME, invalid_state};
use kernel::{Failure, Kind, id::Id};
use std::fmt;
use zeroize::Zeroize;
#[derive(Clone, PartialEq, Eq)]
pub struct Secret(String);
impl Secret {
    pub fn new(value: String) -> Self {
        Self(value)
    }
    pub fn expose(&self) -> &str {
        &self.0
    }
}
impl Drop for Secret {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}
impl fmt::Debug for Secret {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Secret { redacted }")
    }
}
impl fmt::Display for Secret {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Debug::fmt(self, f)
    }
}
pub fn invalid_spec() -> Failure {
    Failure::new(
        Kind::Invalid,
        "notifications.invalid_installation",
        "invalid push route or opaque token",
    )
    .with_public("Check the provider, application, environment and token.")
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PushRoute {
    pub provider: String,
    pub application: String,
    pub environment: String,
}
impl PushRoute {
    pub fn validate(&self) -> Result<(), Failure> {
        if !["apns", "fcm"].contains(&self.provider.as_str())
            || !(1..=128).contains(&self.application.len())
            || !self
                .application
                .bytes()
                .next()
                .is_some_and(|b| b.is_ascii_alphanumeric())
            || !self
                .application
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
            || !["sandbox", "production"].contains(&self.environment.as_str())
            || (self.provider == "fcm" && self.environment != "production")
        {
            return Err(invalid_spec());
        }
        Ok(())
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct InstallationSpec {
    pub route: PushRoute,
    pub token: Secret,
}
impl InstallationSpec {
    pub fn validate(&self) -> Result<(), Failure> {
        self.route.validate()?;
        if !(1..=4096).contains(&self.token.expose().len())
            || !self.token.expose().bytes().all(|b| (33..=126).contains(&b))
        {
            return Err(invalid_spec());
        }
        Ok(())
    }
}
impl fmt::Debug for InstallationSpec {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("InstallationSpec { redacted }")
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct Snapshot {
    pub id: Id,
    pub principal_id: Id,
    pub session_id: Id,
    pub registration_id: Id,
    pub proof_digest: Secret,
    pub spec: InstallationSpec,
    pub version: u64,
    pub enabled: bool,
    pub created_at: u64,
    pub updated_at: u64,
    pub expires_at: u64,
}
impl fmt::Debug for Snapshot {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("InstallationSnapshot { redacted }")
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct Installation {
    state: Snapshot,
}
impl fmt::Debug for Installation {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Installation { redacted }")
    }
}
impl Installation {
    pub fn restore(s: Snapshot) -> Result<Self, Failure> {
        if !valid_digest(s.proof_digest.expose())
            || !(1..=MAX_SEQUENCE).contains(&s.version)
            || s.updated_at < s.created_at
            || s.expires_at <= s.created_at
            || s.updated_at > MAX_TIME
            || s.expires_at > MAX_TIME
            || (s.enabled && s.spec.validate().is_err())
            || (!s.enabled
                && (!s.spec.token.expose().is_empty() || s.spec.route.validate().is_err()))
        {
            return Err(invalid_state());
        }
        Ok(Self { state: s })
    }
    pub fn snapshot(&self) -> &Snapshot {
        &self.state
    }
    pub fn bind(
        &self,
        principal: Id,
        session: Id,
        registration: Id,
        spec: InstallationSpec,
        now: u64,
        expires: u64,
    ) -> Result<Self, Failure> {
        let mut s = self.state.clone();
        s.principal_id = principal;
        s.session_id = session;
        s.registration_id = registration;
        s.spec = spec;
        s.enabled = true;
        s.version = s.version.checked_add(1).ok_or_else(invalid_state)?;
        s.updated_at = now;
        s.expires_at = expires;
        if now < self.state.updated_at {
            return Err(invalid_state());
        }
        Self::restore(s)
    }
    pub fn disable(&self, at: u64) -> Result<Self, Failure> {
        if !self.state.enabled {
            return Ok(self.clone());
        }
        let mut s = self.state.clone();
        s.enabled = false;
        s.spec.token = Secret::new(String::new());
        s.version = s.version.checked_add(1).ok_or_else(invalid_state)?;
        s.updated_at = at.max(s.updated_at);
        Self::restore(s)
    }
}
pub fn valid_digest(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}
