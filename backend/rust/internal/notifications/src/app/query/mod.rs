use crate::app::{self, Dependencies, ListInput, Page, Target, ports::Scope};
pub mod installation;
use kernel::Failure;
#[derive(Clone)]
pub struct ListInbox(pub Dependencies);
impl ListInbox {
    pub async fn handle(&self, s: &dyn Scope, input: ListInput) -> Result<Page, Failure> {
        app::authorize(s, self.0.access.as_ref(), &input.target).await?;
        input.validate()?;
        self.0.store.list(s, input).await
    }
}
#[derive(Clone)]
pub struct GetPreferences(pub Dependencies);
impl GetPreferences {
    pub async fn handle(&self, s: &dyn Scope, t: &Target) -> Result<bool, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        self.0.store.media_enabled(s, t).await
    }
}
