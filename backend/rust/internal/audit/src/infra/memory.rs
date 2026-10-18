use crate::{
    app::{
        self,
        ports::{Page, PortFuture, PrincipalStore, Record, Scope, Store},
    },
    domain::{self, Entry, Evidence},
};
use kernel::{Failure, id::Id};
use std::{
    collections::BTreeMap,
    sync::Mutex,
    time::{Duration, UNIX_EPOCH},
};
#[derive(Default)]
struct State {
    records: Vec<Record>,
    inbox: BTreeMap<(String, Id), Evidence>,
}
#[derive(Default)]
pub struct MemoryStore {
    state: Mutex<State>,
}
impl MemoryStore {
    fn page(
        &self,
        scope: &dyn Scope,
        id: &Id,
        after: u64,
        limit: usize,
        personal: bool,
    ) -> Result<Page, Failure> {
        if !(1..=100).contains(&limit) || after > domain::MAX_VERSION {
            return Err(app::invalid_query());
        }
        let state = self.state.lock().map_err(|_| internal())?;
        scope.check()?;
        let mut rows: Vec<Record> = state
            .records
            .iter()
            .filter(|r| {
                r.sequence > after
                    && (if personal {
                        &r.entry.evidence().principal_id
                    } else {
                        &r.entry.evidence().account_id
                    })
                    .as_ref()
                        == Some(id)
            })
            .take(limit + 1)
            .cloned()
            .collect();
        let next = if rows.len() > limit {
            rows.truncate(limit);
            Some(rows[limit - 1].sequence)
        } else {
            None
        };
        Ok(Page {
            records: rows,
            next,
        })
    }
}
fn internal() -> Failure {
    Failure::new(
        kernel::Kind::Internal,
        "audit.invalid_state",
        "audit state unavailable",
    )
}
impl Store for MemoryStore {
    fn record<'a>(
        &'a self,
        scope: &'a dyn Scope,
        consumer: &'a str,
        entry: Entry,
    ) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let mut state = self.state.lock().map_err(|_| internal())?;
            scope.check()?;
            let evidence = entry.evidence();
            let key = (consumer.to_string(), evidence.event_id.clone());
            if let Some(prior) = state.inbox.get(&key) {
                return if prior == evidence {
                    Ok(())
                } else {
                    Err(app::replay())
                };
            }
            if consumer.is_empty() {
                return Err(domain::invalid());
            }
            Entry::new(
                evidence.clone(),
                UNIX_EPOCH + Duration::from_micros(entry.recorded_at()),
            )?;
            if state.records.len() >= 10_000 {
                return Err(app::capacity());
            }
            let sequence = state.records.len() as u64 + 1;
            state.inbox.insert(key, evidence.clone());
            state.records.push(Record { sequence, entry });
            Ok(())
        })
    }
    fn list<'a>(
        &'a self,
        scope: &'a dyn Scope,
        id: &'a Id,
        after: u64,
        limit: usize,
    ) -> PortFuture<'a, Page> {
        Box::pin(async move { self.page(scope, id, after, limit, false) })
    }
}
impl PrincipalStore for MemoryStore {
    fn list_principal<'a>(
        &'a self,
        scope: &'a dyn Scope,
        id: &'a Id,
        after: u64,
        limit: usize,
    ) -> PortFuture<'a, Page> {
        Box::pin(async move { self.page(scope, id, after, limit, true) })
    }
}
