//! Integration envelopes and delivery mechanics. Business facts and stores stay module-owned.
pub mod dispatcher;
pub mod memory;
use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
    id::Id,
};
use std::{collections::BTreeMap, sync::Arc};
pub const MAX_BYTES: usize = 16_384;
pub const MAX_VERSION: u64 = 9_007_199_254_740_991;
pub const MAX_TIME: u64 = 253_402_300_799_999_999;

#[derive(Clone)]
pub struct Envelope {
    pub id: Id,
    pub name: String,
    pub producer: String,
    pub account_id: Option<Id>,
    pub actor_id: Id,
    pub version: u64,
    pub occurred_at: u64,
    pub metadata: Metadata,
    pub data: Vec<u8>,
}
impl Envelope {
    pub fn validate(&self) -> Result<(), Failure> {
        if !capability::valid_name(&self.name)
            || !capability::valid_caller(&self.producer)
            || !(1..=MAX_VERSION).contains(&self.version)
            || self.occurred_at > MAX_TIME
            || self.data.len() > MAX_BYTES
        {
            return Err(invalid());
        }
        let mut decoder = serde_json::Deserializer::from_slice(&self.data);
        use serde::Deserialize;
        if self.data.iter().find(|c| !c.is_ascii_whitespace()) != Some(&b'{') {
            return Err(invalid());
        }
        serde::de::IgnoredAny::deserialize(&mut decoder).map_err(|_| invalid())?;
        decoder.end().map_err(|_| invalid())?;
        if !self.metadata.request_id.is_empty() && !valid_token(&self.metadata.request_id) {
            return Err(invalid());
        }
        if self
            .metadata
            .correlation_id
            .as_ref()
            .is_some_and(|v| !v.is_empty() && !valid_token(v))
        {
            return Err(invalid());
        }
        Ok(())
    }
}
pub fn valid_token(value: &str) -> bool {
    (1..=128).contains(&value.len())
        && value
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || b"._:-".contains(&c))
}
pub fn valid_consumer(value: &str) -> bool {
    value.contains('.') && value.split('.').all(capability::valid_caller)
}
pub fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "events.invalid",
        "invalid integration envelope or subscription",
    )
}
pub trait Consumer: Send + Sync {
    fn handle<'a>(&'a self, scope: &'a dyn Scope, message: Envelope) -> PortFuture<'a, ()>;
}
pub trait Publisher: Send + Sync {
    fn publish<'a>(&'a self, scope: &'a dyn Scope, message: Envelope) -> PortFuture<'a, ()>;
}
pub struct Subscription {
    pub consumer: String,
    pub names: Vec<String>,
    pub handler: Arc<dyn Consumer>,
}
#[derive(Default)]
pub struct Builder {
    subscriptions: BTreeMap<String, Subscription>,
}
impl Builder {
    pub fn register(&mut self, subscription: Subscription) -> Result<(), Failure> {
        if !valid_consumer(&subscription.consumer)
            || subscription.names.is_empty()
            || subscription
                .names
                .iter()
                .any(|v| !capability::valid_name(v))
            || self.subscriptions.contains_key(&subscription.consumer)
        {
            return Err(invalid());
        }
        self.subscriptions
            .insert(subscription.consumer.clone(), subscription);
        Ok(())
    }
    pub fn freeze(self) -> Arc<MemoryBus> {
        Arc::new(MemoryBus {
            subscriptions: self.subscriptions,
        })
    }
}
pub struct MemoryBus {
    subscriptions: BTreeMap<String, Subscription>,
}
impl Publisher for MemoryBus {
    fn publish<'a>(&'a self, scope: &'a dyn Scope, message: Envelope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            scope.check()?;
            message.validate()?;
            let mut count = 0;
            let mut failed = None;
            for subscription in self
                .subscriptions
                .values()
                .filter(|s| s.names.contains(&message.name))
            {
                scope.check()?;
                count += 1;
                if let Err(error) = subscription.handler.handle(scope, message.clone()).await {
                    if failed.is_none() {
                        failed = Some(error);
                    }
                }
            }
            scope.check()?;
            if count == 0 {
                return Err(Failure::new(
                    Kind::Unavailable,
                    "events.no_consumer",
                    "no event consumer",
                ));
            }
            failed.map_or(Ok(()), Err)
        })
    }
}
pub fn time(micros: u64) -> Result<String, Failure> {
    if micros > MAX_TIME {
        return Err(invalid());
    }
    let value = chrono::DateTime::from_timestamp_micros(micros as i64).ok_or_else(invalid)?;
    let base = value.format("%Y-%m-%dT%H:%M:%S").to_string();
    let fraction = value.timestamp_subsec_micros();
    Ok(if fraction == 0 {
        format!("{base}Z")
    } else {
        format!("{base}.{}Z", format!("{fraction:06}").trim_end_matches('0'))
    })
}
