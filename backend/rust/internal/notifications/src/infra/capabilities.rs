use crate::app::ports::{AccountAccess, PortFuture, Scope};
use kernel::id::Id;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
pub const ACCOUNT_ACCESS: &str = "account.access.v1";
pub mod sessions;
pub struct Client(pub Arc<dyn capability::Invoker>);
impl AccountAccess for Client {
    fn allowed<'a>(
        &'a self,
        scope: &'a dyn Scope,
        principal: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            #[derive(Serialize)]
            struct Request<'a> {
                principal_id: &'a str,
                account_id: &'a str,
                action: &'static str,
            }
            #[derive(Deserialize)]
            struct Response {
                allowed: Option<bool>,
            }
            let request = serde_json::to_vec(&Request {
                principal_id: principal.as_str(),
                account_id: account.as_str(),
                action: "account.read",
            })
            .map_err(|_| capability::invalid_request())?;
            let wire = self.0.invoke(scope, ACCOUNT_ACCESS, &request).await?;
            let response: Response =
                capability::decode(&wire).map_err(|_| capability::invalid_response())?;
            response.allowed.ok_or_else(capability::invalid_response)
        })
    }
}
