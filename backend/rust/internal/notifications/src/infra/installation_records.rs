use crate::{
    app::installations::Revocation,
    domain::installation::{InstallationSpec, Secret},
};
use kernel::Failure;
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;
pub fn proof_matches(want: &Secret, got: &Secret) -> bool {
    want.expose().len() == 64
        && got.expose().len() == 64
        && bool::from(want.expose().as_bytes().ct_eq(got.expose().as_bytes()))
}
pub fn target(spec: &InstallationSpec) -> String {
    let wire = zeroize::Zeroizing::new(
        [
            "notifications.target.v1",
            spec.route.provider.as_str(),
            spec.route.application.as_str(),
            spec.route.environment.as_str(),
            spec.token.expose(),
        ]
        .join("\0"),
    );
    format!("{:x}", Sha256::digest(wire.as_bytes()))
}
pub fn revocation_fingerprint(r: &Revocation) -> Result<String, Failure> {
    r.validate()?;
    let version = r.version.to_string();
    let time = events::time(r.occurred_at)?;
    let wire = [
        "notifications.session-revocation.v1",
        r.event_id.as_str(),
        r.principal_id.as_str(),
        r.session_id.as_str(),
        version.as_str(),
        r.reason.as_str(),
        time.as_str(),
    ]
    .join("\0");
    Ok(format!("{:x}", Sha256::digest(wire.as_bytes())))
}
