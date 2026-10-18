use crate::domain;
use kernel::{Failure, id::Id};
use serde::{
    Deserialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
pub struct Title(pub Option<String>);
impl<'de> Deserialize<'de> for Title {
    fn deserialize<D: de::Deserializer<'de>>(decoder: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Title;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one Notes title object")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Title, A::Error> {
                let mut title = None;
                while let Some(key) = map.next_key::<String>()? {
                    if !key.eq_ignore_ascii_case("title") {
                        return Err(de::Error::custom("unknown Notes field"));
                    }
                    // Go's *string field is reset by null, unlike a value string field.
                    title = map.next_value::<Option<String>>()?;
                }
                Ok(Title(title))
            }
        }
        decoder.deserialize_map(Fields)
    }
}
pub fn page(raw: &str) -> Result<(usize, Option<Id>), Failure> {
    let mut q = httpx::query::unique(raw).map_err(|_| domain::invalid_write())?;
    if q.iter()
        .any(|(k, v)| !["limit", "after"].contains(&k.as_str()) || v.is_empty())
    {
        return Err(domain::invalid_write());
    }
    let limit = if let Some(raw) = q.remove("limit") {
        let n: usize = raw.parse().map_err(|_| domain::invalid_write())?;
        if !(1..=100).contains(&n) || n.to_string() != raw {
            return Err(domain::invalid_write());
        }
        n
    } else {
        20
    };
    let after = q.remove("after").map(|s| Id::parse(&s)).transpose()?;
    Ok((limit, after))
}
pub fn path_id(raw: &str) -> Result<Id, Failure> {
    Id::parse(&httpx::query::component(raw, false).map_err(|_| kernel::id::invalid())?)
}
