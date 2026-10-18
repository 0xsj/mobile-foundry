use crate::domain::{Note, Receipt, Write};
pub use kernel::context::{PortFuture, Scope};
use kernel::id::Id;
/// Record changes and immutable receipt commit atomically; replay precedes CAS/existence.
pub trait Store: Send + Sync {
    fn apply<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        actor: &'a Id,
        write: Write,
        candidate: Option<Id>,
    ) -> PortFuture<'a, Receipt>;
    fn receipt<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        actor: &'a Id,
        command: &'a Id,
    ) -> PortFuture<'a, Option<Receipt>>;
    fn get<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        note: &'a Id,
    ) -> PortFuture<'a, Option<Note>>;
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<Note>>;
}
pub trait AccountAccess: Send + Sync {
    fn allowed<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool>;
}
/// Optional lossy invalidation, never a durable fact or permission to replay a write.
pub trait Changes: Send + Sync {
    fn changed(&self, account: &Id);
}
