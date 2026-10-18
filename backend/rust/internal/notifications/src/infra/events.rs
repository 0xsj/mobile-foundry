//! Receiving adapter owns the selected contract; no Files types or repositories.
pub mod identity;
use crate::app::{
    Delivery,
    command::DeliverUpload,
    ports::{PortFuture, Scope},
};
use events::{Consumer, Envelope, Subscription};
use kernel::{Failure, id::Id};
use serde::de::{self, DeserializeSeed, MapAccess, Visitor};
use std::{fmt, sync::Arc};
pub const CONSUMER: &str = "notifications.files";
pub const NAME: &str = "files.upload_completed.v1";
pub struct Receiver(pub DeliverUpload);
impl Receiver {
    pub fn subscription(self) -> Subscription {
        Subscription {
            consumer: CONSUMER.into(),
            names: vec![NAME.into()],
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
    file_id: String,
    account_id: String,
    version: u64,
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
        f.write_str("one upload completion fact")
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
            match key.as_str() {
                "version" => {
                    if let Some(v) = map.next_value::<Option<u64>>()? {
                        data.version = v;
                    }
                }
                "file_id" => {
                    if let Some(v) = map.next_value::<Option<String>>()? {
                        data.file_id = v;
                    }
                }
                "account_id" => {
                    if let Some(v) = map.next_value::<Option<String>>()? {
                        data.account_id = v;
                    }
                }
                _ => return Err(de::Error::custom("unknown upload evidence field")),
            }
        }
        Ok(data)
    }
}
pub fn admit(m: &Envelope) -> Result<Delivery, Failure> {
    m.validate()?;
    if m.producer != "files" || m.name != NAME || m.account_id.is_none() || m.version != 2 {
        return Err(events::invalid());
    }
    let mut decoder = serde_json::Deserializer::from_slice(&m.data);
    let d = Fields
        .deserialize(&mut decoder)
        .map_err(|_| capability::invalid_request())?;
    decoder.end().map_err(|_| capability::invalid_request())?;
    if d.version != m.version
        || m.account_id.as_ref().map(Id::as_str) != Some(d.account_id.as_str())
    {
        return Err(events::invalid());
    }
    Ok(Delivery {
        event_id: m.id.clone(),
        account_id: m.account_id.clone().ok_or_else(events::invalid)?,
        principal_id: m.actor_id.clone(),
        file_id: Id::parse(&d.file_id).map_err(|_| events::invalid())?,
        occurred_at: m.occurred_at,
    })
}
