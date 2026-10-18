use crate::{
    app::{self, Target},
    domain::Spec,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use kernel::{Failure, id::Id};
use serde::{
    Deserialize, Serialize,
    de::{self, MapAccess, Visitor},
};
use std::fmt;
pub struct Upload(pub Spec);
impl<'de> Deserialize<'de> for Upload {
    fn deserialize<D: de::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct Fields;
        impl<'de> Visitor<'de> for Fields {
            type Value = Upload;
            fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                f.write_str("one file specification")
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Upload, A::Error> {
                let mut spec = Spec {
                    name: String::new(),
                    content_type: String::new(),
                    byte_size: 0,
                    sha256: String::new(),
                };
                while let Some(key) = map.next_key::<String>()? {
                    let key: String = key
                        .chars()
                        .map(|c| match c {
                            '\u{17f}' => 's',
                            '\u{212a}' => 'k',
                            c => c.to_ascii_lowercase(),
                        })
                        .collect();
                    if key == "byte_size" {
                        if let Some(size) = map.next_value::<Option<i64>>()? {
                            spec.byte_size = size as u64;
                        }
                        continue;
                    }
                    let field = match key.as_str() {
                        "name" => &mut spec.name,
                        "content_type" => &mut spec.content_type,
                        "sha256" => &mut spec.sha256,
                        _ => return Err(de::Error::custom("unknown file field")),
                    };
                    if let Some(value) = map.next_value::<Option<String>>()? {
                        *field = value;
                    }
                }
                Ok(Upload(spec))
            }
        }
        d.deserialize_map(Fields)
    }
}
pub fn path(raw: &str) -> Result<String, Failure> {
    httpx::query::component(raw, false).map_err(|_| app::invalid())
}
pub fn id(raw: &str) -> Result<Id, Failure> {
    Id::parse(&path(raw)?)
}
#[derive(Serialize, Deserialize)]
#[allow(non_snake_case)]
struct Cursor {
    v: u8,
    Account: String,
    Actor: String,
    After: String,
}
pub fn cursor(t: &Target, after: &Id) -> Result<String, Failure> {
    Ok(URL_SAFE_NO_PAD.encode(
        serde_json::to_vec(&Cursor {
            v: 1,
            Account: t.account.to_string(),
            Actor: t.actor.to_string(),
            After: after.to_string(),
        })
        .map_err(|_| app::invalid())?,
    ))
}
pub fn page(raw: &str, t: &Target) -> Result<(usize, Option<Id>), Failure> {
    let fields = httpx::query::unique(raw).map_err(|_| app::invalid())?;
    if fields
        .iter()
        .any(|(k, v)| !["limit", "cursor"].contains(&k.as_str()) || v.is_empty())
    {
        return Err(app::invalid());
    }
    let limit = if let Some(v) = fields.get("limit") {
        let n: usize = v.parse().map_err(|_| app::invalid())?;
        if n.to_string() != *v {
            return Err(app::invalid());
        }
        n
    } else {
        20
    };
    let after = fields
        .get("cursor")
        .map(|raw| -> Result<Id, Failure> {
            if raw.len() > 512 {
                return Err(app::invalid());
            }
            let wire = URL_SAFE_NO_PAD.decode(raw).map_err(|_| app::invalid())?;
            let c: Cursor = serde_json::from_slice(&wire).map_err(|_| app::invalid())?;
            let after = Id::parse(&c.After).map_err(|_| app::invalid())?;
            if c.v != 1
                || c.Account != t.account.as_str()
                || c.Actor != t.actor.as_str()
                || cursor(t, &after)? != *raw
            {
                return Err(app::invalid());
            }
            Ok(after)
        })
        .transpose()?;
    Ok((limit, after))
}
pub fn disposition(name: &str) -> String {
    if name.is_ascii() {
        let token = name
            .bytes()
            .all(|b| b > 32 && b < 127 && !b"()<>@,;:\\\"/[]?=".contains(&b));
        if token {
            return format!("attachment; filename={name}");
        }
        return format!(
            "attachment; filename=\"{}\"",
            name.replace('\\', "\\\\").replace('"', "\\\"")
        );
    }
    let encoded: String = name
        .bytes()
        .map(|b| {
            if b.is_ascii_alphanumeric() || b"!#$&+-.^_\x60|~".contains(&b) {
                (b as char).to_string()
            } else {
                format!("%{b:02X}")
            }
        })
        .collect();
    format!("attachment; filename*=utf-8''{encoded}")
}
