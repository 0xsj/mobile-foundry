use crate::{
    app::{
        self, Delivery, ListInput, Page, Target,
        ports::{PortFuture, Scope, Store},
    },
    domain::{self, Item},
};
use kernel::{Failure, id::Id};
use std::{
    collections::{BTreeMap, BTreeSet},
    sync::{Mutex, MutexGuard},
};
#[derive(Default)]
struct State {
    push_jobs: BTreeMap<Id, crate::domain::push_job::PushJob>,
    installations: BTreeMap<Id, crate::domain::installation::Installation>,
    revoked: BTreeSet<(Id, Id)>,
    installation_inbox: BTreeMap<Id, String>,
    inbox: BTreeMap<Id, String>,
    items: BTreeMap<Id, Item>,
    disabled: BTreeSet<Target>,
    sequences: BTreeMap<Target, u64>,
}
#[derive(Default)]
pub struct MemoryStore {
    state: Mutex<State>,
}
mod installations;
mod push;
mod push_jobs;
impl MemoryStore {
    fn lock(&self, s: &dyn Scope) -> Result<MutexGuard<'_, State>, Failure> {
        let state = self.state.lock().map_err(|_| domain::invalid_state())?;
        s.check()?;
        Ok(state)
    }
}
impl Store for MemoryStore {
    fn deliver<'a>(&'a self, s: &'a dyn Scope, d: Delivery, at: u64) -> PortFuture<'a, ()> {
        Box::pin(async move {
            d.validate()?;
            if at > domain::MAX_TIME {
                return Err(app::invalid());
            }
            let hash = super::records::fingerprint(&d)?;
            let mut state = self.lock(s)?;
            if let Some(prior) = state.inbox.get(&d.event_id) {
                return if prior == &hash {
                    Ok(())
                } else {
                    Err(app::replay_conflict())
                };
            }
            if state.inbox.len() >= 10000 {
                return Err(app::capacity());
            }
            let t = d.target();
            let next = if !state.disabled.contains(&t) {
                let sequence = state
                    .sequences
                    .get(&t)
                    .copied()
                    .unwrap_or(0)
                    .checked_add(1)
                    .ok_or_else(app::capacity)?;
                Some(d.item(sequence, at)?)
            } else {
                None
            };
            s.check()?;
            if let Some(item) = next {
                state.sequences.insert(t, item.snapshot().sequence);
                state.items.insert(d.event_id.clone(), item);
            }
            state.inbox.insert(d.event_id, hash);
            Ok(())
        })
    }
    fn list<'a>(&'a self, s: &'a dyn Scope, input: ListInput) -> PortFuture<'a, Page> {
        Box::pin(async move {
            input.validate()?;
            let state = self.lock(s)?;
            let mut items = vec![];
            let mut unread_count = 0;
            for item in state.items.values() {
                let v = item.snapshot();
                if v.account_id != input.target.account || v.principal_id != input.target.actor {
                    continue;
                }
                if v.read_at.is_none() {
                    unread_count += 1;
                }
                if (input.before == 0 || v.sequence < input.before)
                    && (!input.unread_only || v.read_at.is_none())
                {
                    items.push(item.clone());
                }
            }
            items.sort_by_key(|item| std::cmp::Reverse(item.snapshot().sequence));
            let next = if items.len() > input.limit {
                items.truncate(input.limit);
                items.last().map(|i| i.snapshot().sequence)
            } else {
                None
            };
            Ok(Page {
                items,
                next,
                unread_count,
            })
        })
    }
    fn mark_read<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        id: &'a Id,
        at: u64,
    ) -> PortFuture<'a, Item> {
        Box::pin(async move {
            if at > domain::MAX_TIME {
                return Err(app::invalid());
            }
            let mut state = self.lock(s)?;
            let current = state
                .items
                .get(id)
                .filter(|i| {
                    i.snapshot().account_id == t.account && i.snapshot().principal_id == t.actor
                })
                .ok_or_else(app::missing)?;
            let next = current.mark_read(at)?;
            s.check()?;
            state.items.insert(id.clone(), next.clone());
            Ok(next)
        })
    }
    fn media_enabled<'a>(&'a self, s: &'a dyn Scope, t: &'a Target) -> PortFuture<'a, bool> {
        Box::pin(async move { Ok(!self.lock(s)?.disabled.contains(t)) })
    }
    fn set_media_enabled<'a>(
        &'a self,
        s: &'a dyn Scope,
        t: &'a Target,
        enabled: bool,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.lock(s)?;
            if !enabled && !state.disabled.contains(t) && state.disabled.len() >= 10000 {
                return Err(app::capacity());
            }
            if enabled {
                state.disabled.remove(t);
            } else {
                state.disabled.insert(t.clone());
            }
            Ok(())
        })
    }
}
