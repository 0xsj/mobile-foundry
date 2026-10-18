//! Canonical nonzero UUID values and a fallible generation port.
use crate::{Failure, Kind};
use std::fmt;

#[derive(Clone, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct Id(String);

impl Id {
    /// Like Go's ID parser: lowercase, hyphenated, nonzero UUID; no version restriction.
    pub fn parse(value: &str) -> Result<Self, Failure> {
        let parsed = uuid::Uuid::parse_str(value).ok();
        match parsed {
            Some(uuid) if !uuid.is_nil() && uuid.to_string() == value => Ok(Self(value.into())),
            _ => Err(invalid()),
        }
    }
    pub fn as_str(&self) -> &str {
        &self.0
    }
}
pub fn invalid() -> Failure {
    Failure::new(Kind::Invalid, "id.invalid", "invalid canonical UUID")
        .with_public("Provide a valid identifier.")
}
impl fmt::Display for Id {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.as_str())
    }
}
impl fmt::Debug for Id {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_tuple("Id").field(&self.0).finish()
    }
}

pub trait Generator: Send + Sync {
    fn new_id(&self) -> Result<Id, Failure>;
}

/// OS entropy failure is returned, never converted to a panic or predictable fallback.
#[derive(Clone, Copy, Default)]
pub struct RandomIds;
impl Generator for RandomIds {
    fn new_id(&self) -> Result<Id, Failure> {
        generate(|bytes| {
            getrandom::fill(bytes).map_err(|cause| {
                Failure::new(
                    Kind::Internal,
                    "id.entropy_failed",
                    "OS entropy unavailable",
                )
                .with_cause(cause)
            })
        })
    }
}

fn generate(fill: impl FnOnce(&mut [u8]) -> Result<(), Failure>) -> Result<Id, Failure> {
    let mut bytes = [0u8; 16];
    fill(&mut bytes)?;
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    Ok(Id(uuid::Uuid::from_bytes(bytes).to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn random_bits_and_entropy_failure_preserve_meaning() {
        let zero = generate(|bytes| {
            bytes.fill(0);
            Ok(())
        })
        .unwrap();
        assert_eq!(zero.as_str(), "00000000-0000-4000-8000-000000000000");
        let error = generate(|_| {
            Err(Failure::new(Kind::Internal, "fixture.entropy", "SECRET")
                .with_cause(std::io::Error::other("SECRET source")))
        })
        .unwrap_err();
        assert!(std::error::Error::source(&error).is_some());
        assert_eq!(error.public().code, None);
        let first = RandomIds.new_id().unwrap();
        let second = RandomIds.new_id().unwrap();
        assert_ne!(first, second);
        assert_eq!(first.as_str().as_bytes()[14], b'4');
        assert!(b"89ab".contains(&first.as_str().as_bytes()[19]));
    }
}
