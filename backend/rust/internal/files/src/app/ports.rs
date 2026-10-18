use crate::{
    app::{Command, Receipt, Target},
    domain::{File, Snapshot},
};
pub use kernel::context::{PortFuture, Scope};
use kernel::id::Id;
pub trait Store: Send + Sync {
    fn apply<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        c: Command,
        candidate: Option<Id>,
        now: u64,
    ) -> PortFuture<'a, Receipt>;
    fn replay<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        c: &'a Command,
    ) -> PortFuture<'a, Option<Receipt>>;
    fn receipt<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        key: &'a str,
    ) -> PortFuture<'a, Option<Receipt>>;
    fn get<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        file: &'a Id,
    ) -> PortFuture<'a, Option<File>>;
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<File>>;
}
pub trait Objects: Send + Sync {
    fn put<'a>(
        &'a self,
        s: &'a dyn Scope,
        file: &'a Snapshot,
        bytes: Vec<u8>,
    ) -> PortFuture<'a, ()>;
    fn get<'a>(&'a self, s: &'a dyn Scope, file: &'a Snapshot) -> PortFuture<'a, Vec<u8>>;
    fn delete<'a>(&'a self, s: &'a dyn Scope, file: &'a Snapshot) -> PortFuture<'a, ()>;
}
pub trait AccountAccess: Send + Sync {
    fn allowed<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool>;
}
