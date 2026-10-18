//! Embed inside the producer's existing state lock, never behind a second mutex.
use crate::{Envelope, invalid};
use kernel::{Failure, id::Id};
struct Delivery {
    message: Envelope,
    attempts: usize,
    delivered: bool,
    dead: bool,
    failure_code: String,
}
#[derive(Default)]
pub struct Records {
    rows: Vec<Delivery>,
}
impl Records {
    /// Caller has already validated identity/capacity/message under its producer lock.
    /// This infallible append joins the same commit boundary as business state.
    pub fn append_admitted(&mut self, message: Envelope) {
        self.rows.push(Delivery {
            message,
            attempts: 0,
            delivered: false,
            dead: false,
            failure_code: String::new(),
        });
    }
    pub fn pending(&self, limit: usize) -> Result<Vec<Envelope>, Failure> {
        if !(1..=32).contains(&limit) {
            return Err(invalid());
        }
        Ok(self
            .rows
            .iter()
            .filter(|r| !r.delivered && !r.dead)
            .take(limit)
            .map(|r| r.message.clone())
            .collect())
    }
    pub fn delivered(&mut self, id: &Id) -> Result<(), Failure> {
        self.current(id)?.delivered = true;
        Ok(())
    }
    pub fn failed(&mut self, id: &Id, code: &str) -> Result<(), Failure> {
        let record = self.current(id)?;
        record.attempts += 1;
        record.failure_code = code.into();
        record.dead = record.attempts >= 5;
        Ok(())
    }
    fn current(&mut self, id: &Id) -> Result<&mut Delivery, Failure> {
        self.rows
            .iter_mut()
            .find(|r| r.message.id == *id && !r.delivered && !r.dead)
            .ok_or_else(invalid)
    }
    pub fn has_dead(&self) -> bool {
        self.rows.iter().any(|r| r.dead)
    }
}
