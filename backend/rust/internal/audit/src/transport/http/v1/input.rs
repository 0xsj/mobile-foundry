use crate::app;
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use kernel::{Failure, id::Id};
use serde::{
    Deserialize, Serialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
#[derive(Default, Serialize)]
struct Cursor {
    account_id: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    principal_id: String,
    after: u64,
}
impl<'de> Deserialize<'de> for Cursor {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Cursor;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one activity cursor")
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
                        "account_id" => {
                            if let Some(v) = map.next_value::<Option<String>>()? {
                                value.account_id = v;
                            }
                        }
                        "principal_id" => {
                            if let Some(v) = map.next_value::<Option<String>>()? {
                                value.principal_id = v;
                            }
                        }
                        "after" => {
                            if let Some(v) = map.next_value::<Option<u64>>()? {
                                value.after = v;
                            }
                        }
                        _ => return Err(de::Error::custom("unknown activity cursor field")),
                    }
                }
                Ok(value)
            }
        }
        decoder.deserialize_map(Fields)
    }
}
pub fn parse(
    raw: &str,
    account: Option<&Id>,
    principal: Option<&Id>,
) -> Result<(u64, usize), Failure> {
    let params = httpx::query::unique(raw).map_err(|_| app::invalid_query())?;
    if params
        .keys()
        .any(|key| !["limit", "cursor"].contains(&key.as_str()))
    {
        return Err(app::invalid_query());
    }
    // Go Audit accepts Atoi forms (+1 and 01); Account's canonical integer policy differs.
    let limit = if let Some(value) = params.get("limit") {
        value.parse::<usize>().map_err(|_| app::invalid_query())?
    } else {
        50
    };
    if !(1..=100).contains(&limit) {
        return Err(app::invalid_query());
    }
    let mut after = 0;
    if let Some(value) = params.get("cursor") {
        if value.is_empty() || value.len() > 256 {
            return Err(app::invalid_query());
        }
        let value = value.replace(['\r', '\n'], ""); // Go's strict raw base64 still ignores CR/LF.
        let bytes = URL_SAFE_NO_PAD
            .decode(value)
            .map_err(|_| app::invalid_query())?;
        let cursor: Cursor = capability::decode(&bytes).map_err(|_| app::invalid_query())?;
        if cursor.account_id != account.map_or("", Id::as_str)
            || cursor.principal_id != principal.map_or("", Id::as_str)
            || cursor.after == 0
            || cursor.after > crate::domain::MAX_VERSION
        {
            return Err(app::invalid_query());
        }
        after = cursor.after;
    }
    Ok((after, limit))
}
pub fn cursor(account: Option<&Id>, principal: Option<&Id>, after: u64) -> Result<String, Failure> {
    let bytes = serde_json::to_vec(&Cursor {
        account_id: account.map_or("", Id::as_str).into(),
        principal_id: principal.map_or("", Id::as_str).into(),
        after,
    })
    .map_err(|_| capability::invalid_response())?;
    Ok(URL_SAFE_NO_PAD.encode(bytes))
}
