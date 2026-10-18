//! Versioned retirement evidence; no Identity implementation or store imports.
use crate::app::{
    command::installations::RetireSession,
    installations::Revocation,
    ports::{PortFuture, Scope},
};
use events::{Consumer, Envelope, Subscription};
use kernel::{Failure, id::Id};
use serde::de::{self, DeserializeSeed, MapAccess, Visitor};
use std::{fmt, sync::Arc};
pub const CONSUMER: &str = "notifications.identity";
const NAMES: [&str; 2] = ["identity.session.revoked.v1", "identity.session.revoked.v2"];
pub struct Receiver(pub RetireSession);
impl Receiver {
    pub fn subscription(self) -> Subscription {
        Subscription {
            consumer: CONSUMER.into(),
            names: NAMES.iter().map(|n| n.to_string()).collect(),
            handler: Arc::new(self),
        }
    }
}
impl Consumer for Receiver {
    fn handle<'a>(&'a self, s: &'a dyn Scope, m: Envelope) -> PortFuture<'a, ()> {
        Box::pin(async move { self.0.handle(s, admit(&m)?).await })
    }
}
#[derive(Default)]
struct Data {
    principal_id: String,
    session_id: String,
    version: u64,
    reason: String,
}
struct Fields;
impl<'de> DeserializeSeed<'de> for Fields {
    type Value = Data;
    fn deserialize<D: de::Deserializer<'de>>(self, d: D) -> Result<Data, D::Error> {
        d.deserialize_map(self)
    }
}
impl<'de> Visitor<'de> for Fields {
    type Value = Data;
    fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("one session revocation object")
    }
    fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Data, A::Error> {
        let mut data = Data::default();
        while let Some(key) = map.next_key::<String>()? {
            let key: String = key
                .chars()
                .map(|c| match c {
                    '\u{17f}' => 's',
                    '\u{212a}' => 'k',
                    c => c.to_ascii_lowercase(),
                })
                .collect();
            if key == "version" {
                if let Some(v) = map.next_value::<Option<u64>>()? {
                    data.version = v;
                }
                continue;
            }
            let target = match key.as_str() {
                "principal_id" => &mut data.principal_id,
                "session_id" => &mut data.session_id,
                "reason" => &mut data.reason,
                _ => return Err(de::Error::custom("unknown revocation field")),
            };
            if let Some(v) = map.next_value::<Option<String>>()? {
                *target = v;
            }
        }
        Ok(data)
    }
}
pub fn admit(m: &Envelope) -> Result<Revocation, Failure> {
    m.validate()?;
    if m.producer != "identity" || !NAMES.contains(&m.name.as_str()) || m.account_id.is_some() {
        return Err(events::invalid());
    }
    let mut decoder = serde_json::Deserializer::from_slice(&m.data);
    let d = Fields
        .deserialize(&mut decoder)
        .map_err(|_| capability::invalid_request())?;
    decoder.end().map_err(|_| capability::invalid_request())?;
    if d.principal_id != m.actor_id.as_str() || d.version != m.version {
        return Err(events::invalid());
    }
    let reasons = if m.name == NAMES[0] {
        ["sign_out", "refresh_reuse"]
    } else {
        ["password_change", "password_reset"]
    };
    if !reasons.contains(&d.reason.as_str()) {
        return Err(events::invalid());
    }
    let r = Revocation {
        event_id: m.id.clone(),
        principal_id: Id::parse(&d.principal_id).map_err(|_| events::invalid())?,
        session_id: Id::parse(&d.session_id).map_err(|_| events::invalid())?,
        version: m.version,
        reason: d.reason,
        occurred_at: m.occurred_at,
    };
    r.validate()?;
    Ok(r)
}
