use crate::app::{self, ListInput, Target};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use kernel::{Failure, id::Id};
use serde::{
    Deserialize, Serialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
pub struct Preference(pub Option<bool>);
impl<'de> Deserialize<'de> for Preference {
    fn deserialize<D: de::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Preference;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one media preference object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Preference, A::Error> {
                let mut enabled = None;
                while let Some(key) = map.next_key::<String>()? {
                    if !key.eq_ignore_ascii_case("media_in_app") {
                        return Err(de::Error::custom("unknown preference field"));
                    }
                    // Go's pointer boolean resets on null, including after a previous value.
                    enabled = map.next_value::<Option<bool>>()?;
                }
                Ok(Preference(enabled))
            }
        }
        d.deserialize_map(Fields)
    }
}
#[derive(Serialize, Deserialize)]
struct Cursor {
    v: u8,
    principal_id: String,
    account_id: String,
    before: u64,
    unread: bool,
}
pub fn cursor(t: &Target, before: u64, unread: bool) -> Result<String, Failure> {
    Ok(URL_SAFE_NO_PAD.encode(
        serde_json::to_vec(&Cursor {
            v: 1,
            principal_id: t.actor.to_string(),
            account_id: t.account.to_string(),
            before,
            unread,
        })
        .map_err(|_| app::invalid())?,
    ))
}
pub fn page(raw: Option<&str>, t: Target) -> Result<ListInput, Failure> {
    if raw == Some("") {
        return Err(app::invalid());
    }
    let values = httpx::query::unique(raw.unwrap_or("")).map_err(|_| app::invalid())?;
    if values
        .iter()
        .any(|(k, v)| !["limit", "cursor", "unread"].contains(&k.as_str()) || v.is_empty())
    {
        return Err(app::invalid());
    }
    let limit = if let Some(v) = values.get("limit") {
        let n: usize = v.parse().map_err(|_| app::invalid())?;
        if n.to_string() != *v {
            return Err(app::invalid());
        }
        n
    } else {
        20
    };
    let unread_only = match values.get("unread").map(String::as_str) {
        None | Some("false") => false,
        Some("true") => true,
        _ => return Err(app::invalid()),
    };
    let before = if let Some(raw) = values.get("cursor") {
        if raw.len() > 512 {
            return Err(app::invalid());
        }
        let bytes = URL_SAFE_NO_PAD.decode(raw).map_err(|_| app::invalid())?;
        let c: Cursor = serde_json::from_slice(&bytes).map_err(|_| app::invalid())?;
        if c.v != 1
            || c.account_id != t.account.as_str()
            || c.principal_id != t.actor.as_str()
            || c.unread != unread_only
            || !(1..=crate::domain::MAX_SEQUENCE).contains(&c.before)
            || cursor(&t, c.before, c.unread)? != *raw
        {
            return Err(app::invalid());
        }
        c.before
    } else {
        0
    };
    Ok(ListInput {
        target: t,
        before,
        limit,
        unread_only,
    })
}
pub fn id(raw: &str) -> Result<Id, Failure> {
    Id::parse(&httpx::query::component(raw, false).map_err(|_| kernel::id::invalid())?)
}
