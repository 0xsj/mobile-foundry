//! One-attempt, trusted preview contracts. A lease is neither auth nor a receipt.
use super::ports::{PortFuture, Scope};
use crate::domain::{
    MAX_SEQUENCE,
    installation::{InstallationSpec, Snapshot},
};
use kernel::{Failure, Kind, id::Id};
use std::fmt;
pub fn target_changed() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.push_target_changed",
        "push binding is absent, disabled, expired or changed",
    )
}
pub fn session_ineligible() -> Failure {
    Failure::new(
        Kind::Forbidden,
        "notifications.push_session_ineligible",
        "push session is not eligible",
    )
}
pub fn invalid_response() -> Failure {
    Failure::new(
        Kind::Internal,
        "notifications.push_invalid_response",
        "push gateway returned an invalid disposition",
    )
}
#[derive(Clone, PartialEq, Eq)]
pub struct PushLease {
    pub installation_id: Id,
    pub principal_id: Id,
    pub session_id: Id,
    pub version: u64,
}
impl PushLease {
    pub fn validate(&self) -> Result<(), Failure> {
        if !(1..=MAX_SEQUENCE).contains(&self.version) {
            return Err(super::invalid());
        }
        Ok(())
    }
    pub fn matches(&self, s: &Snapshot) -> bool {
        self.installation_id == s.id
            && self.principal_id == s.principal_id
            && self.session_id == s.session_id
            && self.version == s.version
    }
}
impl fmt::Debug for PushLease {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushLease { redacted }")
    }
}
#[derive(Clone)]
pub struct PushAddress {
    pub lease: PushLease,
    pub spec: InstallationSpec,
    pub registered_at: u64,
}
impl fmt::Debug for PushAddress {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushAddress { redacted }")
    }
}
/// Store operations end before a foreign capability or gateway call begins.
pub trait PushTargets: Send + Sync {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        lease: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, PushAddress>;
    /// Clears only the exact enabled owner/revision. A stale rejection is a no-op.
    fn retire<'a>(
        &'a self,
        s: &'a dyn Scope,
        lease: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, bool>;
}
/// Consumer-owned question; dependency failures are never successful negatives.
pub trait SessionEligibility: Send + Sync {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        principal: &'a Id,
        session: &'a Id,
    ) -> PortFuture<'a, bool>;
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PushDisposition {
    Accepted,
    InvalidTarget,
}
impl PushDisposition {
    /// Adapters reject unknown protocol values before producing a typed answer.
    pub fn parse(raw: &str) -> Result<Self, Failure> {
        match raw {
            "accepted" => Ok(Self::Accepted),
            "invalid_target" => Ok(Self::InvalidTarget),
            _ => Err(invalid_response()),
        }
    }
}
/// Implementations allowlist routes, validate encoding, honor Scope and attempt
/// once. Only definitive invalid-address evidence may yield InvalidTarget.
pub trait PushGateway: Send + Sync {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        address: PushAddress,
        preview_id: Id,
    ) -> PortFuture<'a, PushDisposition>;
}
#[derive(Debug)]
#[must_use = "Inspect the attempt and its retirement result before reconciling cleanup"]
pub enum PushAttempt {
    Accepted,
    /// Keep known rejection even if cleanup fails or its acknowledgement is lost.
    /// Retry/reconcile guarded cleanup, never resend to discover its outcome.
    InvalidTarget {
        retirement: Result<bool, Failure>,
    },
}
