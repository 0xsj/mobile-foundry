use super::v2::resolve;
use crate::app::query::ResolveSession;
use kernel::context::{PortFuture, Scope};
#[path = "v1/session_eligibility.rs"]
pub mod session_eligibility;

pub const NAME: &str = "identity.credential.resolve.v1";
#[derive(Clone)]
pub struct Credential(pub ResolveSession);
impl capability::Handler for Credential {
    fn call<'a>(&'a self, scope: &'a dyn Scope, wire: &'a [u8]) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let evidence = resolve(&self.0, scope, wire).await?;
            serde_json::to_vec(&serde_json::json!({"principal_id": evidence.principal_id.as_str()}))
                .map_err(|_| capability::invalid_response())
        })
    }
}
