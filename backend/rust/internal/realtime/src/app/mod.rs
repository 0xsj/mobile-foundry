pub mod query;
use kernel::Failure;
pub use kernel::context::{PortFuture, Scope};
use kernel::id::Id;
pub trait AccountAccess: Send + Sync {
    fn allowed<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool>;
}
pub trait Subscription: Send {
    fn next(&mut self) -> PortFuture<'_, bool>;
}
pub trait Feed: Send + Sync {
    fn watch(&self, actor: &Id, account: &Id) -> Result<Box<dyn Subscription>, Failure>;
}
