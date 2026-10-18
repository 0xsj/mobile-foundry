use crate::app::{AccountAccess, PortFuture, Scope};
use kernel::id::Id;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
pub const ACCOUNT_ACCESS: &str = "account.access.v1";
pub struct Client(pub Arc<dyn capability::Invoker>);
impl AccountAccess for Client {
    fn allowed<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
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
            let wire = serde_json::to_vec(&Request {
                principal_id: actor.as_str(),
                account_id: account.as_str(),
                action: "account.read",
            })
            .map_err(|_| capability::invalid_request())?;
            let result = self.0.invoke(s, ACCOUNT_ACCESS, &wire).await?;
            let result: Response =
                capability::decode(&result).map_err(|_| capability::invalid_response())?;
            result.allowed.ok_or_else(capability::invalid_response)
        })
    }
}
