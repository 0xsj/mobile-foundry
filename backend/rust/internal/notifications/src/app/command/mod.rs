pub mod installations;
use crate::{
    app::{
        self, Delivery, Dependencies, Target,
        ports::{Scope, Store},
    },
    domain::Item,
};
use kernel::{Failure, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct DeliverUpload {
    pub store: Arc<dyn Store>,
    pub clock: Arc<dyn kernel::clock::Clock>,
}
impl DeliverUpload {
    pub async fn handle(&self, s: &dyn Scope, d: Delivery) -> Result<(), Failure> {
        d.validate()?;
        self.store
            .deliver(s, d, app::now(self.clock.as_ref())?)
            .await
    }
}
#[derive(Clone)]
pub struct MarkRead(pub Dependencies);
impl MarkRead {
    pub async fn handle(&self, s: &dyn Scope, t: &Target, id: &Id) -> Result<Item, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        self.0
            .store
            .mark_read(s, t, id, app::now(self.0.clock.as_ref())?)
            .await
    }
}
#[derive(Clone)]
pub struct SetMediaPreference(pub Dependencies);
impl SetMediaPreference {
    pub async fn handle(&self, s: &dyn Scope, t: &Target, enabled: bool) -> Result<(), Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        self.0.store.set_media_enabled(s, t, enabled).await
    }
}
