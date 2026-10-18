//! Selected evidence, never producer aggregate types or arbitrary event payloads.
use kernel::{Failure, Kind, id::Id};
use std::time::{SystemTime, UNIX_EPOCH};
const MAX_TIME: u64 = 253_402_300_799_999_999;
pub const MAX_VERSION: u64 = 9_007_199_254_740_991;
pub fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "audit.invalid_entry",
        "invalid admitted audit evidence",
    )
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Evidence {
    pub event_id: Id,
    pub account_id: Option<Id>,
    pub actor_id: Id,
    pub action: String,
    pub version: u64,
    pub occurred_at: u64,
    pub request_id: String,
    pub correlation_id: Option<String>,
    pub principal_id: Option<Id>,
    pub session_id: Option<Id>,
    pub reason: Option<String>,
    pub file_id: Option<Id>,
}
#[derive(Clone, Debug)]
pub struct Entry {
    evidence: Evidence,
    recorded_at: u64,
}
impl Entry {
    pub fn new(e: Evidence, now: SystemTime) -> Result<Self, Failure> {
        let valid = if e.account_id.is_some()
            && e.principal_id.is_none()
            && e.session_id.is_none()
            && e.reason.is_none()
        {
            if e.file_id.is_some() {
                (e.action == "files.upload_requested" && e.version == 1)
                    || (e.action == "files.upload_completed" && e.version == 2)
                    || (e.action == "files.deleted" && [2, 3].contains(&e.version))
            } else {
                (e.action == "account.created" && e.version == 1)
                    || (e.action == "account.renamed" && e.version >= 2)
            }
        } else if e.account_id.is_none()
            && e.file_id.is_none()
            && e.principal_id.as_ref() == Some(&e.actor_id)
        {
            match e.action.as_str() {
                "identity.principal.registered" => {
                    e.session_id.is_none() && e.version == 1 && e.reason.is_none()
                }
                "identity.email.verified"
                | "identity.password.changed"
                | "identity.password.recovered" => {
                    e.session_id.is_none() && e.version >= 2 && e.reason.is_none()
                }
                "identity.session.issued" => {
                    e.session_id.is_some() && e.version == 1 && e.reason.is_none()
                }
                "identity.session.renewed" => {
                    e.session_id.is_some() && e.version >= 2 && e.reason.is_none()
                }
                "identity.session.revoked" => {
                    e.session_id.is_some()
                        && e.version >= 2
                        && e.reason.as_deref().is_some_and(|r| {
                            [
                                "sign_out",
                                "refresh_reuse",
                                "password_change",
                                "password_reset",
                            ]
                            .contains(&r)
                        })
                }
                _ => false,
            }
        } else {
            false
        };
        if !valid || !(1..=MAX_VERSION).contains(&e.version) || e.occurred_at > MAX_TIME {
            return Err(invalid());
        }
        let recorded_at: u64 = now
            .duration_since(UNIX_EPOCH)
            .map_err(|_| invalid())?
            .as_micros()
            .try_into()
            .map_err(|_| invalid())?;
        if recorded_at > MAX_TIME {
            return Err(invalid());
        }
        Ok(Self {
            evidence: e,
            recorded_at,
        })
    }
    pub fn evidence(&self) -> &Evidence {
        &self.evidence
    }
    pub fn recorded_at(&self) -> u64 {
        self.recorded_at
    }
}
