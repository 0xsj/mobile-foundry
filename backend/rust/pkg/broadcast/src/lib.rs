//! Bounded, coalesced, ephemeral dirty signals; no business data or replay log.
#[cfg(test)]
mod tests;
use kernel::{Failure, Kind};
use std::{
    collections::BTreeMap,
    fmt,
    sync::{Arc, Mutex},
};
use tokio::sync::mpsc;
pub fn capacity() -> Failure {
    Failure::new(
        Kind::RateLimited,
        "broadcast.capacity",
        "subscription capacity exhausted",
    )
}
pub fn closed() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "broadcast.closed",
        "broadcast is stopping",
    )
}
struct Entry {
    key: String,
    owner: String,
    dirty: mpsc::Sender<()>,
}
#[derive(Default)]
struct State {
    entries: BTreeMap<u64, Entry>,
    next: u64,
    closed: bool,
}
pub struct Hub {
    state: Mutex<State>,
    maximum: usize,
    per_owner: usize,
}
impl fmt::Debug for Hub {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Hub { redacted }")
    }
}
pub struct Watch {
    hub: Arc<Hub>,
    id: u64,
    receiver: mpsc::Receiver<()>,
    released: bool,
}
impl fmt::Debug for Watch {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Watch { redacted }")
    }
}
impl Hub {
    pub fn new(maximum: usize, per_owner: usize) -> Self {
        Self {
            state: Mutex::new(State::default()),
            maximum,
            per_owner,
        }
    }
    pub fn watch(self: &Arc<Self>, key: &str, owner: &str) -> Result<Watch, Failure> {
        let mut s = self.state.lock().map_err(|_| closed())?;
        if s.closed {
            return Err(closed());
        }
        if key.is_empty()
            || owner.is_empty()
            || s.entries.len() >= self.maximum
            || s.entries.values().filter(|e| e.owner == owner).count() >= self.per_owner
        {
            return Err(capacity());
        }
        let id = s.next.checked_add(1).ok_or_else(capacity)?;
        s.next = id;
        let (dirty, receiver) = mpsc::channel(1);
        s.entries.insert(
            id,
            Entry {
                key: key.into(),
                owner: owner.into(),
                dirty,
            },
        );
        Ok(Watch {
            hub: self.clone(),
            id,
            receiver,
            released: false,
        })
    }
    /// Best effort, one buffered dirty bit. Full/closed recipients cannot block writers.
    pub fn publish(&self, key: &str) {
        if let Ok(s) = self.state.lock() {
            for entry in s.entries.values().filter(|e| e.key == key) {
                let _ = entry.dirty.try_send(());
            }
        }
    }
    pub fn close(&self) {
        if let Ok(mut s) = self.state.lock() {
            s.closed = true;
            s.entries.clear();
        }
    }
}
impl Watch {
    pub async fn next(&mut self) -> bool {
        self.receiver.recv().await.is_some()
    }
    pub fn close(&mut self) {
        if !self.released {
            if let Ok(mut s) = self.hub.state.lock() {
                s.entries.remove(&self.id);
            }
            self.receiver.close();
            self.released = true;
        }
    }
}
impl Drop for Watch {
    fn drop(&mut self) {
        self.close();
    }
}
