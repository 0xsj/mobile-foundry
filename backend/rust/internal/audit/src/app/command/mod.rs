use super::ports::{Scope, Store};
use crate::domain::{Entry, Evidence};
use kernel::{Failure, clock::Clock};
use std::sync::Arc;
#[derive(Clone)]
pub struct Record {
    pub store: Arc<dyn Store>,
    pub clock: Arc<dyn Clock>,
}
impl Record {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        consumer: &str,
        evidence: Evidence,
    ) -> Result<(), Failure> {
        scope.check()?;
        let entry = Entry::new(evidence, self.clock.now())?;
        self.store.record(scope, consumer, entry).await
    }
}
