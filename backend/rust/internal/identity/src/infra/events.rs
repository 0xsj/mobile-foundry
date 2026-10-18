use crate::{
    app::ports::CommittedFact,
    domain::{self, Action},
};
use kernel::Failure;
pub fn message(record: &CommittedFact) -> Result<events::Envelope, Failure> {
    let f = &record.fact;
    let (action, reason) = match f.action {
        Action::Registered if f.session_id.is_none() && f.version == 1 => {
            ("principal.registered", None)
        }
        Action::Issued if f.session_id.is_some() && f.version == 1 => ("session.issued", None),
        Action::Renewed if f.session_id.is_some() && f.version >= 2 => ("session.renewed", None),
        Action::SignedOut if f.session_id.is_some() && f.version >= 2 => {
            ("session.revoked", Some("sign_out"))
        }
        Action::RefreshReused if f.session_id.is_some() && f.version >= 2 => {
            ("session.revoked", Some("refresh_reuse"))
        }
        Action::EmailVerified if f.session_id.is_none() && f.version >= 2 => {
            ("email.verified", None)
        }
        Action::PasswordChanged if f.session_id.is_none() && f.version >= 2 => {
            ("password.changed", None)
        }
        Action::PasswordRecovered if f.session_id.is_none() && f.version >= 2 => {
            ("password.recovered", None)
        }
        Action::PasswordChangeRevoked if f.session_id.is_some() && f.version >= 2 => {
            ("session.revoked", Some("password_change"))
        }
        Action::PasswordResetRevoked if f.session_id.is_some() && f.version >= 2 => {
            ("session.revoked", Some("password_reset"))
        }
        _ => return Err(domain::invalid_state()),
    };
    #[derive(serde::Serialize)]
    struct Data<'a> {
        principal_id: &'a str,
        #[serde(skip_serializing_if = "Option::is_none")]
        session_id: Option<&'a str>,
        version: u64,
        #[serde(skip_serializing_if = "Option::is_none")]
        reason: Option<&'a str>,
    }
    let message = events::Envelope {
        id: record.id.clone(),
        name: format!(
            "identity.{action}.{}",
            if matches!(
                f.action,
                Action::PasswordChangeRevoked | Action::PasswordResetRevoked
            ) {
                "v2"
            } else {
                "v1"
            }
        ),
        producer: "identity".into(),
        account_id: None,
        actor_id: f.principal_id.clone(),
        version: f.version,
        occurred_at: f.occurred_at.micros(),
        metadata: record.metadata.clone(),
        data: serde_json::to_vec(&Data {
            principal_id: f.principal_id.as_str(),
            session_id: f.session_id.as_ref().map(|id| id.as_str()),
            version: f.version,
            reason,
        })
        .map_err(|_| domain::invalid_state())?,
    };
    message.validate()?;
    Ok(message)
}
