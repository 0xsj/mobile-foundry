use crate::domain::Entry;
pub use kernel::context::{PortFuture, Scope};
use kernel::id::Id;
#[derive(Clone, Debug)]
pub struct Record {
    pub sequence: u64,
    pub entry: Entry,
}
pub struct Page {
    pub records: Vec<Record>,
    pub next: Option<u64>,
}
pub trait Store: Send + Sync {
    fn record<'a>(
        &'a self,
        scope: &'a dyn Scope,
        consumer: &'a str,
        entry: Entry,
    ) -> PortFuture<'a, ()>;
    fn list<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: &'a Id,
        after: u64,
        limit: usize,
    ) -> PortFuture<'a, Page>;
}
pub trait PrincipalStore: Send + Sync {
    fn list_principal<'a>(
        &'a self,
        scope: &'a dyn Scope,
        principal: &'a Id,
        after: u64,
        limit: usize,
    ) -> PortFuture<'a, Page>;
}
pub trait AccountAccess: Send + Sync {
    fn allowed<'a>(
        &'a self,
        scope: &'a dyn Scope,
        principal: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool>;
}
