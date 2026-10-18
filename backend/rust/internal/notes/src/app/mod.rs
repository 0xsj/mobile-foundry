pub mod command;
pub mod ports;
pub mod query;
use kernel::{Failure, Kind, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct Dependencies {
    pub store: Arc<dyn ports::Store>,
    pub access: Arc<dyn ports::AccountAccess>,
    pub ids: Arc<dyn kernel::id::Generator>,
    pub changes: Option<Arc<dyn ports::Changes>>,
}
pub async fn authorize(
    s: &dyn ports::Scope,
    access: &dyn ports::AccountAccess,
    actor: &Id,
    account: &Id,
) -> Result<(), Failure> {
    s.check()?;
    if !access.allowed(s, actor, account).await? {
        return Err(Failure::new(
            Kind::Forbidden,
            "notes.account_forbidden",
            "Notes account access denied",
        )
        .with_public("You cannot access notes in this account."));
    }
    Ok(())
}
pub fn receipt_missing() -> Failure {
    Failure::new(
        Kind::NotFound,
        "notes.receipt_not_found",
        "no committed receipt observed",
    )
    .with_public("No committed receipt was found. Keep the original command before retrying.")
}
