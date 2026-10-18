use crate::app::{Feed, PortFuture, Subscription};
use kernel::{Failure, id::Id};
use std::sync::Arc;
pub struct Local(pub Arc<broadcast::Hub>);
struct Watch(broadcast::Watch);
impl Subscription for Watch {
    fn next(&mut self) -> PortFuture<'_, bool> {
        Box::pin(async move { Ok(self.0.next().await) })
    }
}
impl Feed for Local {
    fn watch(&self, actor: &Id, account: &Id) -> Result<Box<dyn Subscription>, Failure> {
        Ok(Box::new(Watch(
            self.0.watch(&format!("notes.{account}"), actor.as_str())?,
        )))
    }
}
