use crate::domain::{Account, Fact};
pub use kernel::context::{Metadata, PortFuture, Scope};
use kernel::id::Id;
pub use principal::{Principal, Resolver as PrincipalResolver};
pub trait Store: Send + Sync {
    fn get<'a>(&'a self, scope: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, Option<Account>>;
    fn create<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        fact: Fact,
    ) -> PortFuture<'a, ()>;
    fn rename<'a>(
        &'a self,
        scope: &'a dyn Scope,
        account: Account,
        expected: u64,
        fact: Fact,
    ) -> PortFuture<'a, ()>;
}
pub struct BootstrapResult {
    pub account: Account,
    pub created: bool,
}
pub trait Directory: Send + Sync {
    fn bootstrap<'a>(
        &'a self,
        scope: &'a dyn Scope,
        candidate: Account,
        fact: Fact,
    ) -> PortFuture<'a, BootstrapResult>;
    fn list<'a>(
        &'a self,
        scope: &'a dyn Scope,
        owner: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<Account>>;
}
pub struct Page {
    pub accounts: Vec<Account>,
    pub next: Option<Id>,
}
/// Retained atomically; not an integration envelope, dispatch receipt or Audit record.
#[derive(Clone, Debug)]
pub struct CommittedFact {
    pub id: Id,
    pub fact: Fact,
    pub metadata: Metadata,
}
