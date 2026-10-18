//! Consumer-side contract admission; no Account/Identity implementation imports.
use crate::{
    app::{
        command::Record,
        ports::{PortFuture, Scope},
    },
    domain::Evidence,
};
use events::{Consumer, Envelope, Subscription};
use kernel::{Failure, id::Id};
use serde::de::{self, DeserializeSeed, MapAccess, Visitor};
use std::{fmt, sync::Arc};
pub const ACCOUNT: &str = "audit.account";
pub const IDENTITY: &str = "audit.identity";
pub const FILES: &str = "audit.files";
const FILE_NAMES: [&str; 3] = [
    "files.upload_requested.v1",
    "files.upload_completed.v1",
    "files.deleted.v1",
];
const IDENTITY_NAMES: [&str; 8] = [
    "identity.principal.registered.v1",
    "identity.session.issued.v1",
    "identity.session.renewed.v1",
    "identity.session.revoked.v1",
    "identity.email.verified.v1",
    "identity.password.changed.v1",
    "identity.password.recovered.v1",
    "identity.session.revoked.v2",
];
#[derive(Clone, Copy)]
pub enum Source {
    Account,
    Identity,
    Files,
}
pub struct Receiver {
    pub record: Record,
    pub source: Source,
}
impl Receiver {
    pub fn subscription(self) -> Subscription {
        let (consumer, names) = match self.source {
            Source::Account => (ACCOUNT, vec!["account.created.v1", "account.renamed.v1"]),
            Source::Identity => (IDENTITY, IDENTITY_NAMES.to_vec()),
            Source::Files => (FILES, FILE_NAMES.to_vec()),
        };
        Subscription {
            consumer: consumer.into(),
            names: names.into_iter().map(str::to_string).collect(),
            handler: Arc::new(self),
        }
    }
}
impl Consumer for Receiver {
    fn handle<'a>(&'a self, scope: &'a dyn Scope, message: Envelope) -> PortFuture<'a, ()> {
        Box::pin(async move {
            let (consumer, evidence) = admit(&message, self.source)?;
            self.record.handle(scope, consumer, evidence).await
        })
    }
}
#[derive(Default)]
struct Data {
    file_id: String,
    account_id: String,
    principal_id: String,
    session_id: String,
    version: u64,
    reason: String,
}
struct Seed(Source);
impl<'de> DeserializeSeed<'de> for Seed {
    type Value = Data;
    fn deserialize<D: de::Deserializer<'de>>(self, decoder: D) -> Result<Data, D::Error> {
        decoder.deserialize_map(self)
    }
}
impl<'de> Visitor<'de> for Seed {
    type Value = Data;
    fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("one event data object")
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
            let allowed = match self.0 {
                Source::Account => ["account_id", "version"].contains(&key.as_str()),
                Source::Files => ["file_id", "account_id", "version"].contains(&key.as_str()),
                Source::Identity => {
                    ["principal_id", "session_id", "version", "reason"].contains(&key.as_str())
                }
            };
            if !allowed {
                return Err(de::Error::custom("unknown evidence field"));
            }
            if key == "version" {
                if let Some(value) = map.next_value::<Option<u64>>()? {
                    data.version = value;
                }
                continue;
            }
            if let Some(value) = map.next_value::<Option<String>>()? {
                match key.as_str() {
                    "account_id" => data.account_id = value,
                    "file_id" => data.file_id = value,
                    "principal_id" => data.principal_id = value,
                    "session_id" => data.session_id = value,
                    _ => data.reason = value,
                }
            }
        }
        Ok(data)
    }
}
pub fn admit(message: &Envelope, source: Source) -> Result<(&'static str, Evidence), Failure> {
    message.validate()?;
    let mut decoder = serde_json::Deserializer::from_slice(&message.data);
    let data = Seed(source)
        .deserialize(&mut decoder)
        .map_err(|_| capability::invalid_request())?;
    decoder.end().map_err(|_| capability::invalid_request())?;
    if data.version != message.version {
        return Err(events::invalid());
    }
    let mut evidence = Evidence {
        event_id: message.id.clone(),
        account_id: message.account_id.clone(),
        actor_id: message.actor_id.clone(),
        action: String::new(),
        version: message.version,
        occurred_at: message.occurred_at,
        request_id: message.metadata.request_id.clone(),
        correlation_id: message
            .metadata
            .correlation_id
            .clone()
            .filter(|v| !v.is_empty()),
        principal_id: None,
        session_id: None,
        reason: None,
        file_id: None,
    };
    let consumer = match source {
        Source::Files => {
            if message.producer != "files"
                || !FILE_NAMES.contains(&message.name.as_str())
                || message.account_id.as_ref().map(Id::as_str) != Some(data.account_id.as_str())
            {
                return Err(events::invalid());
            }
            evidence.file_id = Some(Id::parse(&data.file_id).map_err(|_| events::invalid())?);
            evidence.action = message.name.trim_end_matches(".v1").into();
            FILES
        }
        Source::Account => {
            if message.producer != "account"
                || !["account.created.v1", "account.renamed.v1"].contains(&message.name.as_str())
                || message.account_id.as_ref().map(|id| id.as_str())
                    != Some(data.account_id.as_str())
            {
                return Err(events::invalid());
            }
            evidence.action = message.name.trim_end_matches(".v1").into();
            ACCOUNT
        }
        Source::Identity => {
            if message.producer != "identity"
                || message.account_id.is_some()
                || !IDENTITY_NAMES.contains(&message.name.as_str())
                || data.principal_id != message.actor_id.as_str()
            {
                return Err(events::invalid());
            }
            if message.name == "identity.session.revoked.v1"
                && !["sign_out", "refresh_reuse"].contains(&data.reason.as_str())
            {
                return Err(events::invalid());
            }
            if message.name == "identity.session.revoked.v2"
                && !["password_change", "password_reset"].contains(&data.reason.as_str())
            {
                return Err(events::invalid());
            }
            evidence.action = message
                .name
                .rsplit_once('.')
                .map(|(action, _)| action)
                .ok_or_else(events::invalid)?
                .into();
            evidence.principal_id =
                Some(Id::parse(&data.principal_id).map_err(|_| events::invalid())?);
            evidence.session_id = if data.session_id.is_empty() {
                None
            } else {
                Some(Id::parse(&data.session_id).map_err(|_| events::invalid())?)
            };
            evidence.reason = (!data.reason.is_empty()).then_some(data.reason);
            IDENTITY
        }
    };
    Ok((consumer, evidence))
}
