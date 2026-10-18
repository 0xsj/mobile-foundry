use crate::{
    app::{
        self, Action, Command, Receipt, Target,
        ports::{PortFuture, Scope, Store},
    },
    domain::{self, File, Status},
};
use kernel::{
    Failure,
    id::{Generator, Id},
};
use std::{
    collections::{BTreeMap, HashSet},
    sync::{Arc, Mutex, MutexGuard},
};
struct Saved {
    hash: Vec<u8>,
    receipt: Receipt,
}
struct Delivery {
    message: events::Envelope,
    stream: Id,
    attempts: usize,
    delivered: bool,
    dead: bool,
}
#[derive(Default)]
struct State {
    files: BTreeMap<Id, File>,
    receipts: BTreeMap<(Id, Id, String), Saved>,
    outbox: Vec<Delivery>,
}
pub struct MemoryStore {
    state: Mutex<State>,
    ids: Arc<dyn Generator>,
}
impl MemoryStore {
    pub fn new(ids: Arc<dyn Generator>) -> Self {
        Self {
            state: Mutex::new(State::default()),
            ids,
        }
    }
    fn lock(&self, s: &dyn Scope) -> Result<MutexGuard<'_, State>, Failure> {
        s.check()?;
        self.state.lock().map_err(|_| domain::invalid_state())
    }
}
fn key(t: &Target, key: &str) -> (Id, Id, String) {
    (t.account.clone(), t.actor.clone(), key.into())
}
fn replay(state: &State, t: &Target, c: &Command) -> Result<Option<Receipt>, Failure> {
    if c.key.is_empty() {
        return Ok(None);
    }
    if let Some(saved) = state.receipts.get(&key(t, &c.key)) {
        if saved.hash != super::wire::fingerprint(c)? {
            return Err(app::key_conflict());
        }
        return Ok(Some(saved.receipt.clone()));
    }
    Ok(None)
}
impl Store for MemoryStore {
    fn apply<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        c: Command,
        candidate: Option<Id>,
        now: u64,
    ) -> PortFuture<'a, Receipt> {
        Box::pin(async move {
            c.validate()?;
            let mut state = self.lock(s)?;
            if let Some(r) = replay(&state, t, &c)? {
                return Ok(r);
            }
            let current = c
                .file_id
                .as_ref()
                .and_then(|id| state.files.get(id))
                .filter(|f| f.snapshot().account_id == t.account);
            let f = app::plan(t, &c, current, candidate, now)?;
            if c.action == Action::Request && state.files.contains_key(&f.snapshot().id) {
                return Err(app::conflict());
            }
            let m =
                super::events::message(f.snapshot(), &t.actor, s.metadata(), self.ids.as_ref())?;
            if state.outbox.iter().any(|r| r.message.id == m.id) {
                return Err(domain::invalid_state());
            }
            if state.outbox.len() >= 10000
                || (c.action == Action::Request && state.files.len() >= 1000)
            {
                return Err(app::capacity());
            }
            let r = Receipt {
                key: c.key.clone(),
                action: c.action,
                actor: t.actor.clone(),
                file: f.snapshot().clone(),
            };
            let hash = super::wire::fingerprint(&c)?;
            s.check()?;
            state.files.insert(f.snapshot().id.clone(), f);
            state.outbox.push(Delivery {
                message: m,
                stream: r.file.id.clone(),
                attempts: 0,
                delivered: false,
                dead: false,
            });
            if !c.key.is_empty() {
                state.receipts.insert(
                    key(t, &c.key),
                    Saved {
                        hash,
                        receipt: r.clone(),
                    },
                );
            }
            Ok(r)
        })
    }
    fn replay<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        c: &'a Command,
    ) -> PortFuture<'a, Option<Receipt>> {
        Box::pin(async move {
            c.validate()?;
            replay(&*self.lock(s)?, t, c)
        })
    }
    fn receipt<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        k: &'a str,
    ) -> PortFuture<'a, Option<Receipt>> {
        Box::pin(async move {
            Ok(self
                .lock(s)?
                .receipts
                .get(&key(t, k))
                .map(|r| r.receipt.clone()))
        })
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, a: &'a Id, id: &'a Id) -> PortFuture<'a, Option<File>> {
        Box::pin(async move {
            Ok(self
                .lock(s)?
                .files
                .get(id)
                .filter(|f| &f.snapshot().account_id == a)
                .cloned())
        })
    }
    fn list<'a>(
        &'a self,
        s: &'a dyn Scope,
        a: &'a Id,
        after: Option<&'a Id>,
        limit: usize,
    ) -> PortFuture<'a, Vec<File>> {
        Box::pin(async move {
            if !(1..=101).contains(&limit) {
                return Err(app::invalid());
            }
            Ok(self
                .lock(s)?
                .files
                .values()
                .filter(|f| {
                    &f.snapshot().account_id == a
                        && f.snapshot().status != Status::Deleted
                        && after.is_none_or(|v| &f.snapshot().id > v)
                })
                .take(limit)
                .cloned()
                .collect())
        })
    }
}
impl events::dispatcher::Outbox for MemoryStore {
    fn pending<'a>(
        &'a self,
        s: &'a dyn Scope,
        limit: usize,
    ) -> PortFuture<'a, Vec<events::Envelope>> {
        Box::pin(async move {
            if !(1..=32).contains(&limit) {
                return Err(events::invalid());
            }
            let state = self.lock(s)?;
            let mut blocked = HashSet::new();
            let mut rows = vec![];
            for r in &state.outbox {
                if r.delivered || !blocked.insert(&r.stream) {
                    continue;
                }
                if !r.dead {
                    rows.push(r.message.clone());
                    if rows.len() == limit {
                        break;
                    }
                }
            }
            Ok(rows)
        })
    }
    fn delivered<'a>(&'a self, s: &'a dyn Scope, id: &'a Id) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let r = state
                .outbox
                .iter_mut()
                .find(|r| &r.message.id == id && !r.dead && !r.delivered)
                .ok_or_else(events::invalid)?;
            r.delivered = true;
            Ok(())
        })
    }
    fn failed<'a>(&'a self, s: &'a dyn Scope, id: &'a Id, _code: &'a str) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            let r = state
                .outbox
                .iter_mut()
                .find(|r| &r.message.id == id && !r.dead && !r.delivered)
                .ok_or_else(events::invalid)?;
            r.attempts += 1;
            r.dead = r.attempts >= 5;
            Ok(())
        })
    }
    fn ready<'a>(&'a self, s: &'a dyn Scope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if self.lock(s)?.outbox.iter().any(|r| r.dead) {
                return Err(app::exhausted());
            }
            Ok(())
        })
    }
}
