use crate::app::{self, Dependencies, Receipt, Target, ports::Scope};
use crate::domain::{Snapshot, Status};
use kernel::{Failure, id::Id};
#[derive(Clone)]
pub struct GetFile(pub Dependencies);
impl GetFile {
    pub async fn handle(&self, s: &dyn Scope, t: &Target, id: &Id) -> Result<Snapshot, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        Ok(self
            .0
            .store
            .get(s, &t.account, id)
            .await?
            .filter(|f| f.snapshot().status != Status::Deleted)
            .ok_or_else(app::missing)?
            .snapshot()
            .clone())
    }
}
#[derive(Clone)]
pub struct DownloadFile(pub Dependencies);
impl DownloadFile {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        t: &Target,
        id: &Id,
    ) -> Result<(Snapshot, Vec<u8>), Failure> {
        let f = GetFile(self.0.clone()).handle(s, t, id).await?;
        if f.status != Status::Available {
            return Err(crate::domain::transition());
        }
        let bytes = self.0.objects.get(s, &f).await?;
        if self
            .0
            .store
            .get(s, &t.account, id)
            .await?
            .is_none_or(|v| v.snapshot().status != Status::Available)
        {
            return Err(app::missing());
        }
        Ok((f, bytes))
    }
}
#[derive(Clone)]
pub struct GetReceipt(pub Dependencies);
impl GetReceipt {
    pub async fn handle(&self, s: &dyn Scope, t: &Target, key: &str) -> Result<Receipt, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        if !app::valid_key(key) {
            return Err(app::invalid());
        }
        self.0
            .store
            .receipt(s, t, key)
            .await?
            .ok_or_else(app::missing_receipt)
    }
}
pub struct Page {
    pub files: Vec<Snapshot>,
    pub next: Option<Id>,
}
#[derive(Clone)]
pub struct ListFiles(pub Dependencies);
impl ListFiles {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        t: &Target,
        after: Option<&Id>,
        limit: usize,
    ) -> Result<Page, Failure> {
        app::authorize(s, self.0.access.as_ref(), t).await?;
        if !(1..=100).contains(&limit) {
            return Err(app::invalid());
        }
        let mut files: Vec<_> = self
            .0
            .store
            .list(s, &t.account, after, limit + 1)
            .await?
            .into_iter()
            .map(|v| v.snapshot().clone())
            .collect();
        let next = if files.len() > limit {
            files.truncate(limit);
            files.last().map(|v| v.id.clone())
        } else {
            None
        };
        Ok(Page { files, next })
    }
}
