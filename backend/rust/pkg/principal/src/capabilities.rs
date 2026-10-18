//! Private credential capability adapter selected at the HTTP edge.
use crate::{Principal, Resolver as PrincipalResolver, required};
use kernel::context::{PortFuture, Scope};
use kernel::id::Id;
use serde::{Deserialize, Serialize};
use std::{
    sync::Arc,
    time::{Duration, UNIX_EPOCH},
};
use zeroize::Zeroizing;

pub const CREDENTIAL: &str = "identity.credential.resolve.v2";
pub struct Resolver(pub Arc<dyn capability::Invoker>);
#[derive(Deserialize)]
struct Evidence {
    principal_id: String,
    session_id: String,
    session_expires_at: String,
}
#[derive(Serialize)]
struct CredentialRequest<'a> {
    access_token: &'a str,
}
impl PrincipalResolver for Resolver {
    fn resolve<'a>(&'a self, scope: &'a dyn Scope, token: &'a str) -> PortFuture<'a, Principal> {
        Box::pin(async move {
            scope.check()?;
            if token.len() != 46 {
                return Err(required());
            }
            let request = Zeroizing::new(
                serde_json::to_vec(&CredentialRequest {
                    access_token: token,
                })
                .map_err(|_| capability::invalid_request())?,
            );
            let wire = self.0.invoke(scope, CREDENTIAL, &request).await?;
            let response: Evidence =
                capability::decode(&wire).map_err(|_| capability::invalid_response())?;
            let id =
                Id::parse(&response.principal_id).map_err(|_| capability::invalid_response())?;
            let session_id =
                Id::parse(&response.session_id).map_err(|_| capability::invalid_response())?;
            let time = chrono::DateTime::parse_from_rfc3339(&response.session_expires_at)
                .map_err(|_| capability::invalid_response())?;
            let micros = u64::try_from(time.timestamp_micros())
                .map_err(|_| capability::invalid_response())?;
            if micros > 253_402_300_799_999_999 {
                return Err(capability::invalid_response());
            }
            scope.check()?;
            Ok(Principal {
                id,
                session_id,
                session_expires_at: UNIX_EPOCH + Duration::from_micros(micros),
            })
        })
    }
}
pub struct Deny;
impl PrincipalResolver for Deny {
    fn resolve<'a>(&'a self, scope: &'a dyn Scope, _: &'a str) -> PortFuture<'a, Principal> {
        Box::pin(async move {
            scope.check()?;
            Err(required())
        })
    }
}
