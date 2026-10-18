use crate::{
    app::{self, installations::InstallationProofs, ports::Scope},
    domain::installation::Secret,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use kernel::Failure;
use sha2::{Digest, Sha256};
pub struct Proofs;
impl InstallationProofs for Proofs {
    fn digest(&self, s: &dyn Scope, raw: &Secret) -> Result<Secret, Failure> {
        s.check()?;
        let value = raw.expose();
        if value.len() != 46 || !value.starts_with("i1.") {
            return Err(app::invalid());
        }
        let bytes = zeroize::Zeroizing::new(
            URL_SAFE_NO_PAD
                .decode(&value[3..])
                .map_err(|_| app::invalid())?,
        );
        if bytes.len() != 32 || URL_SAFE_NO_PAD.encode(&*bytes) != value[3..] {
            return Err(app::invalid());
        }
        let mut hash = Sha256::new();
        hash.update(b"notifications.installation-proof.v1\0");
        hash.update(value.as_bytes());
        Ok(Secret::new(format!("{:x}", hash.finalize())))
    }
}
