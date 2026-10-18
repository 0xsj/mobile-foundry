use crate::{
    app::ports::{PortFuture, Scope, Store},
    domain::{self, Action, Note, Receipt, Write},
};
use kernel::{Failure, id::Id};
use std::{
    collections::BTreeMap,
    sync::{Mutex, MutexGuard},
};
#[derive(Default)]
struct State {
    notes: BTreeMap<(Id, Id), Note>,
    receipts: BTreeMap<(Id, Id, Id), (Write, Receipt)>,
}
#[derive(Default)]
pub struct MemoryStore {
    state: Mutex<State>,
}
impl MemoryStore {
    fn lock(&self, s: &dyn Scope) -> Result<MutexGuard<'_, State>, Failure> {
        let state = self.state.lock().map_err(|_| domain::invalid_state())?;
        s.check()?;
        Ok(state)
    }
}
impl Store for MemoryStore {
    fn apply<'a>(
        &'a self,
        s: &'a dyn Scope,
        account: &'a Id,
        actor: &'a Id,
        w: Write,
        candidate: Option<Id>,
    ) -> PortFuture<'a, Receipt> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            w.validate()?;
            let key = (account.clone(), actor.clone(), w.command_id.clone());
            if let Some((input, receipt)) = state.receipts.get(&key) {
                return if input == &w {
                    Ok(receipt.clone())
                } else {
                    Err(domain::command_conflict())
                };
            }
            let current = w
                .note_id
                .as_ref()
                .and_then(|id| state.notes.get(&(account.clone(), id.clone())));
            let r = domain::decide(&w, current, candidate, account, actor)?;
            let result = r.snapshot();
            let target = (account.clone(), result.note_id.clone());
            if w.action == Action::Create && state.notes.contains_key(&target) {
                return Err(domain::conflict());
            }
            let next = if w.action == Action::Delete {
                None
            } else {
                Some(Note::restore(
                    result.note_id.clone(),
                    result.title.clone(),
                    result.version,
                )?)
            };
            s.check()?;
            if let Some(note) = next {
                state.notes.insert(target, note);
            } else {
                state.notes.remove(&target);
            }
            state.receipts.insert(key, (w, r.clone()));
            Ok(r)
        })
    }
    fn receipt<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Id,
        p: &'a Id,
        c: &'a Id,
    ) -> PortFuture<'a, Option<Receipt>> {
        Box::pin(async move {
            Ok(self
                .lock(s)?
                .receipts
                .get(&(a.clone(), p.clone(), c.clone()))
                .map(|(_, r)| r.clone()))
        })
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, a: &'a Id, id: &'a Id) -> PortFuture<'a, Option<Note>> {
        Box::pin(async move { Ok(self.lock(s)?.notes.get(&(a.clone(), id.clone())).cloned()) })
    }
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<Note>> {
        Box::pin(async move {
            if !(1..=101).contains(&limit) {
                return Err(domain::invalid_write());
            }
            Ok(self
                .lock(s)?
                .notes
                .iter()
                .filter(|((owner, id), _)| owner == a && after.is_none_or(|after| id > after))
                .take(limit)
                .map(|(_, note)| note.clone())
                .collect())
        })
    }
}
