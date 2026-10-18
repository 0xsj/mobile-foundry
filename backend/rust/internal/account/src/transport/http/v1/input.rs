//! Endpoint DTOs preserve Go's ordered duplicate and null-as-no-op rules.
use crate::app;
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use kernel::{Failure, id::Id};
use serde::{
    Deserialize,
    de::{self, MapAccess, Visitor},
};
use std::{collections::BTreeMap, fmt};

pub struct Name(pub String);
impl<'de> Deserialize<'de> for Name {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Name;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one account name object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Name, A::Error> {
                let mut value = String::new();
                while let Some(key) = map.next_key::<String>()? {
                    if !key.eq_ignore_ascii_case("name") {
                        return Err(de::Error::custom("unknown account field"));
                    }
                    if let Some(next) = map.next_value::<Option<String>>()? {
                        value = next;
                    }
                }
                Ok(Name(value))
            }
        }
        decoder.deserialize_map(Fields)
    }
}
#[derive(Default, serde::Serialize)]
struct Cursor {
    version: i64,
    owner_id: String,
    after: String,
}
impl<'de> Deserialize<'de> for Cursor {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Cursor;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one cursor object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Cursor, A::Error> {
                let mut value = Cursor::default();
                while let Some(key) = map.next_key::<String>()? {
                    let key: String = key
                        .chars()
                        .map(|c| {
                            if c == '\u{17f}' {
                                's'
                            } else {
                                c.to_ascii_lowercase()
                            }
                        })
                        .collect();
                    match key.as_str() {
                        "version" => {
                            if let Some(v) = map.next_value::<Option<i64>>()? {
                                value.version = v;
                            }
                        }
                        "owner_id" => {
                            if let Some(v) = map.next_value::<Option<String>>()? {
                                value.owner_id = v;
                            }
                        }
                        "after" => {
                            if let Some(v) = map.next_value::<Option<String>>()? {
                                value.after = v;
                            }
                        }
                        _ => return Err(de::Error::custom("unknown cursor field")),
                    }
                }
                Ok(value)
            }
        }
        decoder.deserialize_map(Fields)
    }
}
pub fn component(value: &str, plus: bool) -> Result<String, Failure> {
    httpx::query::component(value, plus).map_err(|_| app::invalid_page())
}
pub fn page(query: &str, owner: &Id) -> Result<(usize, Option<Id>), Failure> {
    let mut parameters = BTreeMap::new();
    for part in query.split('&').filter(|p| !p.is_empty()) {
        if part.contains(';') {
            return Err(app::invalid_page());
        }
        let (key, value) = part.split_once('=').unwrap_or((part, ""));
        let key = component(key, true)?;
        let value = component(value, true)?;
        if !["limit", "cursor"].contains(&key.as_str()) || parameters.insert(key, value).is_some() {
            return Err(app::invalid_page());
        }
    }
    let limit = if let Some(value) = parameters.get("limit") {
        let number: usize = value.parse().map_err(|_| app::invalid_page())?;
        if !(1..=100).contains(&number) || number.to_string() != *value {
            return Err(app::invalid_page());
        }
        number
    } else {
        20
    };
    let after = if let Some(value) = parameters.get("cursor") {
        if value.is_empty() || value.len() > 256 {
            return Err(app::invalid_page());
        }
        let bytes = URL_SAFE_NO_PAD
            .decode(value)
            .map_err(|_| app::invalid_page())?;
        let cursor: Cursor = capability::decode(&bytes).map_err(|_| app::invalid_page())?;
        if cursor.version != 1 || cursor.owner_id != owner.as_str() {
            return Err(app::invalid_page());
        }
        Some(Id::parse(&cursor.after).map_err(|_| app::invalid_page())?)
    } else {
        None
    };
    Ok((limit, after))
}
pub fn cursor(owner: &Id, after: &Id) -> Result<String, Failure> {
    let bytes = serde_json::to_vec(&Cursor {
        version: 1,
        owner_id: owner.as_str().into(),
        after: after.as_str().into(),
    })
    .map_err(|_| crate::domain::invalid_state())?;
    Ok(URL_SAFE_NO_PAD.encode(bytes))
}
