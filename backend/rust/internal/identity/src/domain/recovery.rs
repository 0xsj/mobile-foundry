//! Explicit recovery contact/purpose policy; login is not a verified address.
use super::{Digest, Principal, Secret, Timestamp, invalid_input, invalid_state};
use kernel::{Failure, id::Id};
use std::fmt;
pub const CHALLENGE_LIFETIME: u64 = 20 * 60 * 1_000_000;
pub fn email(value: &str) -> Result<String, Failure> {
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
    let Some((local, host)) = value.split_once('@') else {
        return Err(invalid_input());
    };
    let alnum = |b: u8| b.is_ascii_lowercase() || b.is_ascii_digit();
    if value.len() > 254
        || !(1..=64).contains(&local.len())
        || !local.bytes().next().is_some_and(alnum)
        || !local
            .bytes()
            .all(|b| alnum(b) || b".!#$%&'*+/=?^_`{|}~-".contains(&b))
        || local.ends_with('.')
        || value.contains("..")
    {
        return Err(invalid_input());
    }
    let labels: Vec<_> = host.split('.').collect();
    if labels.len() < 2
        || labels.iter().any(|label| {
            label.is_empty()
                || label.len() > 63
                || !label.bytes().next().is_some_and(alnum)
                || !label.bytes().last().is_some_and(alnum)
                || !label.bytes().all(|b| alnum(b) || b == b'-')
        })
    {
        return Err(invalid_input());
    }
    Ok(value)
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Purpose {
    VerifyEmail,
    ResetPassword,
}
impl Purpose {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::VerifyEmail => "verify_email",
            Self::ResetPassword => "reset_password",
        }
    }
    pub fn parse(s: &str) -> Result<Self, Failure> {
        match s {
            "verify_email" => Ok(Self::VerifyEmail),
            "reset_password" => Ok(Self::ResetPassword),
            _ => Err(invalid_state()),
        }
    }
}
#[derive(Clone)]
pub struct Contact {
    pub principal_id: Id,
    pub email: String,
    pub verified_at: Timestamp,
}
impl Contact {
    pub fn validate(&self) -> Result<(), Failure> {
        if email(&self.email).ok().as_ref() != Some(&self.email) {
            return Err(invalid_state());
        }
        Ok(())
    }
}
impl fmt::Debug for Contact {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Contact { redacted }")
    }
}
#[derive(Clone)]
pub struct Challenge {
    pub principal_id: Id,
    pub purpose: Purpose,
    pub email: String,
    pub digest: Digest,
    pub credential_hash: Secret,
    pub created_at: Timestamp,
    pub expires_at: Timestamp,
}
impl Challenge {
    pub fn validate(&self) -> Result<(), Failure> {
        if email(&self.email).ok().as_ref() != Some(&self.email)
            || self.credential_hash.expose().is_empty()
            || self.credential_hash.expose().len() > 512
            || self.created_at.micros().checked_add(CHALLENGE_LIFETIME)
                != Some(self.expires_at.micros())
        {
            return Err(invalid_state());
        }
        Ok(())
    }
    pub fn active(&self, now: Timestamp) -> bool {
        now >= self.created_at && now < self.expires_at
    }
}
impl fmt::Debug for Challenge {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Challenge { redacted }")
    }
}
impl Principal {
    pub fn with_password(&self, hash: Secret) -> Result<Self, Failure> {
        Self::new(
            self.id.clone(),
            self.login.clone(),
            self.name.clone(),
            hash,
            self.created_at,
        )
    }
}
