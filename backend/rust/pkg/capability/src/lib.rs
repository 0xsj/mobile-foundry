//! Versioned synchronous invocation mechanics. Providers retain business authority.
use kernel::{
    Failure, Kind,
    context::{Metadata, PortFuture, Scope},
};
use serde::de::DeserializeOwned;
use std::{collections::BTreeMap, sync::Arc, time::Duration};
use tokio::time::Instant;

pub const MAX_BYTES: usize = 16_384;
pub const BUDGET: Duration = Duration::from_secs(2);
pub trait Handler: Send + Sync {
    fn call<'a>(&'a self, scope: &'a dyn Scope, request: &'a [u8]) -> PortFuture<'a, Vec<u8>>;
}
/// Consumers depend on this port, never a registry or foreign module store.
pub trait Invoker: Send + Sync {
    fn invoke<'a>(
        &'a self,
        scope: &'a dyn Scope,
        name: &'a str,
        request: &'a [u8],
    ) -> PortFuture<'a, Vec<u8>>;
}
struct Provision {
    callers: Vec<String>,
    handler: Arc<dyn Handler>,
}
#[derive(Default)]
pub struct Builder {
    providers: BTreeMap<String, Provision>,
}
impl Builder {
    pub fn register(
        &mut self,
        name: &str,
        callers: &[&str],
        handler: Arc<dyn Handler>,
    ) -> Result<(), Failure> {
        if !valid_name(name)
            || callers.is_empty()
            || callers.iter().any(|c| !valid_caller(c))
            || self.providers.contains_key(name)
        {
            return Err(invalid());
        }
        self.providers.insert(
            name.into(),
            Provision {
                callers: callers.iter().map(|c| (*c).into()).collect(),
                handler,
            },
        );
        Ok(())
    }
    /// Consuming the mutable builder makes serving registration immutable.
    pub fn freeze(self) -> Arc<Registry> {
        Arc::new(Registry {
            providers: self.providers,
        })
    }
}
pub struct Registry {
    providers: BTreeMap<String, Provision>,
}
impl Registry {
    pub fn require(&self, name: &str, caller: &str) -> Result<(), Failure> {
        if !valid_name(name) || !valid_caller(caller) {
            return Err(invalid());
        }
        let provider = self.providers.get(name).ok_or_else(missing)?;
        if !provider.callers.iter().any(|c| c == caller) {
            return Err(caller_denied());
        }
        Ok(())
    }
    pub fn client(self: &Arc<Self>, caller: &str) -> Result<Client, Failure> {
        if !valid_caller(caller) {
            return Err(invalid());
        }
        Ok(Client {
            registry: self.clone(),
            caller: caller.into(),
        })
    }
}
pub struct Client {
    registry: Arc<Registry>,
    caller: String,
}
struct Bounded<'a> {
    parent: &'a dyn Scope,
    deadline: Instant,
}
impl Scope for Bounded<'_> {
    fn check(&self) -> Result<(), Failure> {
        self.parent.check()?;
        if Instant::now() >= self.deadline {
            return Err(timeout());
        }
        Ok(())
    }
    fn metadata(&self) -> Metadata {
        self.parent.metadata()
    }
}
impl Invoker for Client {
    fn invoke<'a>(
        &'a self,
        scope: &'a dyn Scope,
        name: &'a str,
        request: &'a [u8],
    ) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            scope.check()?;
            self.registry.require(name, &self.caller)?;
            let provider = &self.registry.providers[name];
            validate_object(request)?;
            let bounded = Bounded {
                parent: scope,
                deadline: Instant::now() + BUDGET,
            };
            let result =
                tokio::time::timeout_at(bounded.deadline, provider.handler.call(&bounded, request))
                    .await
                    .map_err(|_| timeout())?;
            bounded.check()?;
            let response = result.map_err(|error| {
                // Cross the same public boundary as a remote provider; discard diagnostics/causes.
                let view = error.public();
                Failure::new(
                    view.kind,
                    view.code.unwrap_or_default(),
                    "capability provider failed",
                )
                .with_public(view.message)
                .with_fields(view.fields)
            })?;
            validate_object(&response).map_err(|_| invalid_response())?;
            bounded.check()?;
            Ok(response)
        })
    }
}
fn validate_object(wire: &[u8]) -> Result<(), Failure> {
    use serde::Deserialize;
    if wire.len() > MAX_BYTES || wire.iter().find(|c| !c.is_ascii_whitespace()) != Some(&b'{') {
        return Err(invalid_request());
    }
    let mut decoder = serde_json::Deserializer::from_slice(wire);
    serde::de::IgnoredAny::deserialize(&mut decoder).map_err(|_| invalid_request())?;
    decoder.end().map_err(|_| invalid_request())
}
/// One bounded object, no unknown fields or trailing values. DTO policy remains owned.
pub fn decode<T: DeserializeOwned>(wire: &[u8]) -> Result<T, Failure> {
    if wire.len() > MAX_BYTES || wire.iter().find(|c| !c.is_ascii_whitespace()) != Some(&b'{') {
        return Err(invalid_request());
    }
    let mut decoder = serde_json::Deserializer::from_slice(wire);
    let mut unknown = false;
    let value = serde_ignored::deserialize(&mut decoder, |_| unknown = true)
        .map_err(|_| invalid_request())?;
    decoder.end().map_err(|_| invalid_request())?;
    if unknown {
        return Err(invalid_request());
    }
    Ok(value)
}
fn word(value: &str) -> bool {
    value.as_bytes().first().is_some_and(u8::is_ascii_lowercase)
        && value
            .bytes()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == b'_')
}
pub fn valid_caller(value: &str) -> bool {
    value.len() <= 64 && word(value)
}
pub fn valid_name(value: &str) -> bool {
    let parts: Vec<_> = value.split('.').collect();
    parts.len() >= 3
        && parts[..parts.len() - 1].iter().all(|p| word(p))
        && parts.last().is_some_and(|p| {
            p.strip_prefix('v').is_some_and(|v| {
                !v.is_empty()
                    && v.len() <= 6
                    && !v.starts_with('0')
                    && v.bytes().all(|c| c.is_ascii_digit())
            })
        })
}
fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "capability.invalid",
        "invalid capability invocation or registration",
    )
}
fn missing() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "capability.not_provided",
        "required capability missing",
    )
    .with_public("A required service is unavailable.")
}
fn caller_denied() -> Failure {
    Failure::new(
        Kind::Forbidden,
        "capability.caller_not_allowed",
        "capability caller refused",
    )
    .with_public("This service cannot perform this operation.")
}
pub fn invalid_request() -> Failure {
    Failure::new(
        Kind::Invalid,
        "capability.invalid_request",
        "invalid capability request",
    )
    .with_public("Capability input is invalid.")
}
pub fn invalid_response() -> Failure {
    Failure::new(
        Kind::Internal,
        "capability.invalid_response",
        "invalid capability response",
    )
}
fn timeout() -> Failure {
    Failure::new(Kind::Timeout, "", "capability budget elapsed")
}
