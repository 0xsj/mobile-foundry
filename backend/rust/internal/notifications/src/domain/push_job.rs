//! Retained intent and fenced execution. No address, credential or provider content.
use super::{MAX_SEQUENCE, MAX_TIME, invalid_state};
use kernel::{Failure, Kind, id::Id};
use std::fmt;
pub const LEASE: u64 = 30_000_000;
pub const LIFETIME: u64 = 900_000_000;
pub const MAX_ATTEMPTS: u8 = 5;
pub fn claim_lost() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.push_job_claim_lost",
        "push job claim lost or expired",
    )
}
pub fn recovery_refused() -> Failure {
    Failure::new(
        Kind::Conflict,
        "notifications.push_job_recovery_refused",
        "job cannot be canceled or replaced with this acknowledgement",
    )
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Status {
    Queued,
    Leased,
    Sending,
    Accepted,
    InvalidTarget,
    Blocked,
    Failed,
    Uncertain,
    Expired,
    Canceled,
}
impl Status {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Queued => "queued",
            Self::Leased => "leased",
            Self::Sending => "sending",
            Self::Accepted => "accepted",
            Self::InvalidTarget => "invalid_target",
            Self::Blocked => "blocked",
            Self::Failed => "failed",
            Self::Uncertain => "uncertain",
            Self::Expired => "expired",
            Self::Canceled => "canceled",
        }
    }
    pub fn parse(v: &str) -> Result<Self, Failure> {
        [
            Self::Queued,
            Self::Leased,
            Self::Sending,
            Self::Accepted,
            Self::InvalidTarget,
            Self::Blocked,
            Self::Failed,
            Self::Uncertain,
            Self::Expired,
            Self::Canceled,
        ]
        .into_iter()
        .find(|s| s.as_str() == v)
        .ok_or_else(invalid_state)
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Code {
    None,
    PreflightUnavailable,
    AttemptsExhausted,
    TargetIneligible,
    ProviderUnconfirmed,
    WorkerLost,
    CleanupUnconfirmed,
    Expired,
    UserCanceled,
}
impl Code {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::None => "",
            Self::PreflightUnavailable => "preflight_unavailable",
            Self::AttemptsExhausted => "attempts_exhausted",
            Self::TargetIneligible => "target_ineligible",
            Self::ProviderUnconfirmed => "provider_unconfirmed",
            Self::WorkerLost => "worker_lost",
            Self::CleanupUnconfirmed => "cleanup_unconfirmed",
            Self::Expired => "expired",
            Self::UserCanceled => "user_canceled",
        }
    }
    pub fn parse(v: &str) -> Result<Self, Failure> {
        [
            Self::None,
            Self::PreflightUnavailable,
            Self::AttemptsExhausted,
            Self::TargetIneligible,
            Self::ProviderUnconfirmed,
            Self::WorkerLost,
            Self::CleanupUnconfirmed,
            Self::Expired,
            Self::UserCanceled,
        ]
        .into_iter()
        .find(|s| s.as_str() == v)
        .ok_or_else(invalid_state)
    }
}
#[derive(Clone, PartialEq, Eq)]
pub struct Intent {
    pub id: Id,
    pub installation_id: Id,
    pub principal_id: Id,
    pub session_id: Id,
    pub installation_version: u64,
    pub retry_of: Option<Id>,
    pub duplicate_risk_accepted: bool,
}
#[derive(Clone, PartialEq, Eq)]
pub struct Snapshot {
    pub intent: Intent,
    pub status: Status,
    pub attempts: u8,
    pub claim: Option<Id>,
    pub created_at: u64,
    pub updated_at: u64,
    pub expires_at: u64,
    pub next_at: u64,
    pub lease_until: Option<u64>,
    pub code: Code,
    pub retired: bool,
}
#[derive(Clone, PartialEq, Eq)]
pub struct PushJob {
    state: Snapshot,
}
impl fmt::Debug for Intent {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushJobIntent { redacted }")
    }
}
impl fmt::Debug for Snapshot {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushJobSnapshot { redacted }")
    }
}
impl fmt::Debug for PushJob {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushJob { redacted }")
    }
}
impl PushJob {
    pub fn new(intent: Intent, now: u64) -> Result<Self, Failure> {
        Self::restore(Snapshot {
            intent,
            status: Status::Queued,
            attempts: 0,
            claim: None,
            created_at: now,
            updated_at: now,
            expires_at: now.checked_add(LIFETIME).ok_or_else(invalid_state)?,
            next_at: now,
            lease_until: None,
            code: Code::None,
            retired: false,
        })
    }
    pub fn restore(s: Snapshot) -> Result<Self, Failure> {
        let i = &s.intent;
        if !(1..=MAX_SEQUENCE).contains(&i.installation_version)
            || i.retry_of.as_ref() == Some(&i.id)
            || (i.retry_of.is_none() && i.duplicate_risk_accepted)
            || s.updated_at < s.created_at
            || s.created_at.checked_add(LIFETIME) != Some(s.expires_at)
            || s.next_at < s.created_at
            || s.attempts > MAX_ATTEMPTS
            || [s.created_at, s.updated_at, s.expires_at, s.next_at]
                .into_iter()
                .any(|t| t > MAX_TIME)
            || s.lease_until.is_some_and(|t| t > MAX_TIME)
        {
            return Err(invalid_state());
        }
        let active = matches!(s.status, Status::Leased | Status::Sending);
        if (active
            && (s.claim.is_none()
                || s.lease_until.is_none_or(|t| t <= s.updated_at)
                || s.attempts == 0))
            || (!active && (s.claim.is_some() || s.lease_until.is_some()))
            || (s.retired && s.status != Status::InvalidTarget)
        {
            return Err(invalid_state());
        }
        let valid = match s.status {
            Status::Queued | Status::Leased | Status::Sending => {
                matches!(s.code, Code::None | Code::PreflightUnavailable)
            }
            Status::Accepted => s.code == Code::None && s.attempts > 0,
            Status::InvalidTarget => {
                matches!(s.code, Code::None | Code::CleanupUnconfirmed) && s.attempts > 0
            }
            Status::Blocked => s.code == Code::TargetIneligible && s.attempts > 0,
            Status::Failed => s.code == Code::AttemptsExhausted && s.attempts == MAX_ATTEMPTS,
            Status::Uncertain => {
                matches!(s.code, Code::ProviderUnconfirmed | Code::WorkerLost) && s.attempts > 0
            }
            Status::Expired => s.code == Code::Expired,
            Status::Canceled => s.code == Code::UserCanceled,
        };
        if !valid {
            return Err(invalid_state());
        }
        Ok(Self { state: s })
    }
    pub fn snapshot(&self) -> &Snapshot {
        &self.state
    }
    pub fn same_intent(&self, other: &Self) -> bool {
        self.state.intent == other.state.intent
    }
    pub fn due(&self, now: u64) -> bool {
        let s = &self.state;
        match s.status {
            Status::Queued => now >= s.next_at || now >= s.expires_at,
            Status::Leased => s.lease_until.is_some_and(|t| now >= t) || now >= s.expires_at,
            Status::Sending => s.lease_until.is_some_and(|t| now >= t),
            _ => false,
        }
    }
    pub fn claim_next(&self, token: Id, now: u64) -> Result<(Self, bool), Failure> {
        if now < self.state.created_at || now > MAX_TIME {
            return Err(invalid_state());
        }
        if !self.due(now) {
            return Ok((self.clone(), false));
        }
        let mut s = self.state.clone();
        s.updated_at = now;
        if s.status == Status::Sending {
            s.status = Status::Uncertain;
            s.code = Code::WorkerLost;
        } else if now >= s.expires_at {
            s.status = Status::Expired;
            s.code = Code::Expired;
        } else if s.attempts == MAX_ATTEMPTS {
            s.status = Status::Failed;
            s.code = Code::AttemptsExhausted;
        } else {
            s.status = Status::Leased;
            s.claim = Some(token);
            s.lease_until = Some(now.checked_add(LEASE).ok_or_else(invalid_state)?);
            s.attempts += 1;
            return Ok((Self::restore(s)?, true));
        }
        s.claim = None;
        s.lease_until = None;
        Ok((Self::restore(s)?, false))
    }
    pub fn begin(&self, token: &Id, now: u64) -> Result<Self, Failure> {
        let mut s = self.state.clone();
        if s.status != Status::Leased
            || s.claim.as_ref() != Some(token)
            || s.lease_until.is_none_or(|t| now >= t)
            || now >= s.expires_at
            || now < s.updated_at
        {
            return Err(claim_lost());
        }
        s.status = Status::Sending;
        s.updated_at = now;
        Self::restore(s)
    }
    pub fn finish(
        &self,
        token: &Id,
        started: bool,
        status: Status,
        code: Code,
        retired: bool,
        now: u64,
    ) -> Result<Self, Failure> {
        let mut s = self.state.clone();
        let expected = if started {
            Status::Sending
        } else {
            Status::Leased
        };
        if s.status != expected
            || s.claim.as_ref() != Some(token)
            || s.lease_until.is_none_or(|t| now >= t)
            || now < s.updated_at
        {
            return Err(claim_lost());
        }
        if (started
            && !matches!(
                status,
                Status::Accepted | Status::InvalidTarget | Status::Uncertain
            ))
            || (!started && !matches!(status, Status::Queued | Status::Blocked))
        {
            return Err(invalid_state());
        }
        s.status = status;
        s.code = code;
        s.retired = retired;
        s.updated_at = now;
        s.claim = None;
        s.lease_until = None;
        if status == Status::Queued {
            if s.attempts == MAX_ATTEMPTS {
                s.status = Status::Failed;
                s.code = Code::AttemptsExhausted;
            } else {
                s.next_at = now
                    .checked_add(1_000_000u64 << (s.attempts - 1))
                    .ok_or_else(invalid_state)?;
            }
        }
        Self::restore(s)
    }
    pub fn cancel(&self, now: u64) -> Result<Self, Failure> {
        let mut s = self.state.clone();
        if s.status == Status::Canceled {
            return Ok(self.clone());
        }
        if !matches!(s.status, Status::Queued | Status::Leased) || now < s.updated_at {
            return Err(recovery_refused());
        }
        s.status = Status::Canceled;
        s.code = Code::UserCanceled;
        s.claim = None;
        s.lease_until = None;
        s.updated_at = now;
        Self::restore(s)
    }
    pub fn replacement(&self, candidate: &Self) -> Result<Self, Failure> {
        let a = &self.state;
        let b = &candidate.state;
        let x = &a.intent;
        let y = &b.intent;
        if y.retry_of.as_ref() != Some(&x.id)
            || y.id == x.id
            || y.installation_id != x.installation_id
            || y.principal_id != x.principal_id
            || y.session_id != x.session_id
            || b.status != Status::Queued
            || b.attempts != 0
            || b.created_at < a.updated_at
        {
            return Err(recovery_refused());
        }
        let allowed = match a.status {
            Status::Failed | Status::Blocked | Status::Expired | Status::Canceled => {
                !y.duplicate_risk_accepted
            }
            Status::Uncertain => y.duplicate_risk_accepted,
            _ => false,
        };
        if !allowed {
            return Err(recovery_refused());
        }
        Self::restore(b.clone())
    }
}
