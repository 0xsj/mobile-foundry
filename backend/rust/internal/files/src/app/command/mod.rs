use crate::app::{self, Action, Command, Dependencies, Receipt, Target, ports::Scope};
use crate::domain::{Spec, Status};
use kernel::{Failure, id::Id};
#[derive(Clone)]
pub struct RequestUpload(pub Dependencies);
impl RequestUpload {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        t: &Target,
        spec: Spec,
        key: String,
    ) -> Result<Receipt, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        let c = Command {
            action: Action::Request,
            key,
            file_id: None,
            spec: Some(spec.normalize()?),
            expected: 0,
        };
        c.validate()?;
        if let Some(r) = self.0.store.replay(s, t, &c).await? {
            return Ok(r);
        }
        self.0
            .store
            .apply(
                s,
                t,
                c,
                Some(self.0.ids.new_id()?),
                app::now(self.0.clock.as_ref())?,
            )
            .await
    }
}
#[derive(Clone)]
pub struct CompleteUpload(pub Dependencies);
impl CompleteUpload {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        t: &Target,
        id: Id,
        expected: u64,
        key: String,
    ) -> Result<Receipt, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        let c = Command {
            action: Action::Complete,
            key,
            file_id: Some(id.clone()),
            spec: None,
            expected,
        };
        c.validate()?;
        if let Some(r) = self.0.store.replay(s, t, &c).await? {
            return Ok(r);
        }
        let f = self
            .0
            .store
            .get(s, &t.account, &id)
            .await?
            .filter(|f| f.snapshot().status != Status::Deleted)
            .ok_or_else(app::missing)?;
        self.0.objects.get(s, f.snapshot()).await?;
        self.0
            .store
            .apply(s, t, c, None, app::now(self.0.clock.as_ref())?)
            .await
    }
}
#[derive(Clone)]
pub struct DeleteFile(pub Dependencies);
impl DeleteFile {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        t: &Target,
        id: Id,
        expected: u64,
        key: String,
    ) -> Result<Receipt, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        let c = Command {
            action: Action::Delete,
            key,
            file_id: Some(id),
            spec: None,
            expected,
        };
        c.validate()?;
        let r = self
            .0
            .store
            .apply(s, t, c, None, app::now(self.0.clock.as_ref())?)
            .await?;
        self.0.objects.delete(s, &r.file).await?;
        Ok(r)
    }
}
#[derive(Clone)]
pub struct UploadContent(pub Dependencies);
impl UploadContent {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        t: &Target,
        id: &Id,
        content_type: &str,
        bytes: Vec<u8>,
    ) -> Result<(), Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        let f = self
            .0
            .store
            .get(s, &t.account, id)
            .await?
            .filter(|f| f.snapshot().status != Status::Deleted)
            .ok_or_else(app::missing)?;
        if content_type != f.snapshot().spec.content_type {
            return Err(app::content());
        }
        self.0.objects.put(s, f.snapshot(), bytes).await?;
        let current = self.0.store.get(s, &t.account, id).await?;
        if current.is_none_or(|f| f.snapshot().status == Status::Deleted) {
            self.0.objects.delete(s, f.snapshot()).await?;
            return Err(app::missing());
        }
        Ok(())
    }
}
