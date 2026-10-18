use crate::{
    app::{self, Dependencies},
    domain::{self, Note, Receipt},
};
use kernel::{Failure, context::Scope, id::Id};
#[derive(Clone)]
pub struct Read(pub Dependencies);
pub struct Page {
    pub notes: Vec<Note>,
    pub next: Option<Id>,
}
impl Read {
    pub async fn receipt(
        &self,
        s: &dyn Scope,
        actor: &Id,
        account: &Id,
        command: &Id,
    ) -> Result<Receipt, Failure> {
        let d = &self.0;
        app::authorize(s, d.access.as_ref(), actor, account).await?;
        let r = d
            .store
            .receipt(s, account, actor, command)
            .await?
            .ok_or_else(app::receipt_missing)?;
        let state = r.snapshot();
        if &state.account_id != account
            || &state.principal_id != actor
            || &state.command_id != command
        {
            return Err(domain::invalid_state());
        }
        Ok(r)
    }
    pub async fn get(
        &self,
        s: &dyn Scope,
        actor: &Id,
        account: &Id,
        id: &Id,
    ) -> Result<Note, Failure> {
        let d = &self.0;
        app::authorize(s, d.access.as_ref(), actor, account).await?;
        let note = d
            .store
            .get(s, account, id)
            .await?
            .ok_or_else(domain::missing)?;
        if note.id() != id {
            return Err(domain::invalid_state());
        }
        Ok(note)
    }
    pub async fn list(
        &self,
        s: &dyn Scope,
        actor: &Id,
        account: &Id,
        after: Option<&Id>,
        limit: usize,
    ) -> Result<Page, Failure> {
        let d = &self.0;
        app::authorize(s, d.access.as_ref(), actor, account).await?;
        if !(1..=100).contains(&limit) {
            return Err(domain::invalid_write());
        }
        let mut notes = d.store.list(s, account, after, limit + 1).await?;
        if notes.len() > limit + 1 {
            return Err(domain::invalid_state());
        }
        let mut previous = after;
        for note in &notes {
            if previous.is_some_and(|prior| prior >= note.id()) {
                return Err(domain::invalid_state());
            }
            previous = Some(note.id());
        }
        let next = if notes.len() > limit {
            notes.truncate(limit);
            Some(notes[limit - 1].id().clone())
        } else {
            None
        };
        Ok(Page { notes, next })
    }
}
