//! v1 source identity excludes delivery time, preferences and request metadata.
use crate::app::Delivery;
use kernel::Failure;
use sha2::{Digest, Sha256};
pub fn fingerprint(d: &Delivery) -> Result<String, Failure> {
    d.validate()?;
    let at = events::time(d.occurred_at)?;
    let wire = [
        "notifications.files.v1",
        d.event_id.as_str(),
        d.account_id.as_str(),
        d.principal_id.as_str(),
        d.file_id.as_str(),
        at.as_str(),
    ]
    .join("\0");
    Ok(format!("{:x}", Sha256::digest(wire.as_bytes())))
}
