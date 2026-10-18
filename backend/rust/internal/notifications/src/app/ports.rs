use crate::{
    app::{Delivery, ListInput, Page, Target},
    domain::Item,
};
pub use kernel::context::{PortFuture, Scope};
use kernel::id::Id;
pub trait Store: Send + Sync {
    /// Marker, preference decision, optional item and scope sequence commit together.
    fn deliver<'a>(&'a self, s: &'a dyn Scope, d: Delivery, at: u64) -> PortFuture<'a, ()>;
    fn list<'a>(&'a self, s: &'a dyn Scope, input: ListInput) -> PortFuture<'a, Page>;
    fn mark_read<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        id: &'a Id,
        at: u64,
    ) -> PortFuture<'a, Item>;
    fn media_enabled<'a>(&'a self, s: &'a dyn Scope, t: &'a Target) -> PortFuture<'a, bool>;
    fn set_media_enabled<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        enabled: bool,
    ) -> PortFuture<'a, ()>;
}
pub trait AccountAccess: Send + Sync {
    fn allowed<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool>;
}
