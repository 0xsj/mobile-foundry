//! Endpoint-owned Go string DTO rules: case folding, ordered duplicates and null no-op.
use crate::domain::Secret;
use serde::de::{self, DeserializeSeed, MapAccess, Visitor};
use std::fmt;

#[derive(Clone, Copy)]
pub(super) enum Mode {
    Register,
    SignIn,
    Refresh,
}
pub(super) struct Input {
    pub login: String,
    pub name: String,
    pub password: Secret,
    pub refresh: Secret,
}
struct Seed(Mode);
impl<'de> DeserializeSeed<'de> for Seed {
    type Value = Input;
    fn deserialize<D: de::Deserializer<'de>>(self, deserializer: D) -> Result<Input, D::Error> {
        deserializer.deserialize_map(self)
    }
}
impl<'de> Visitor<'de> for Seed {
    type Value = Input;
    fn expecting(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("one credential object")
    }
    fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<Input, A::Error> {
        let mut result = Input {
            login: String::new(),
            name: String::new(),
            password: Secret::new(String::new()),
            refresh: Secret::new(String::new()),
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
            let allowed = match self.0 {
                Mode::Register => ["login", "display_name", "password"].contains(&key.as_str()),
                Mode::SignIn => ["login", "password"].contains(&key.as_str()),
                Mode::Refresh => key == "refresh_token",
            };
            if !allowed {
                return Err(de::Error::custom("unknown credential field"));
            }
            // Go ignores JSON null for an existing string; an earlier type error still refuses the DTO.
            if let Some(value) = map.next_value::<Option<String>>()? {
                match key.as_str() {
                    "login" => result.login = value,
                    "display_name" => result.name = value,
                    "password" => result.password = Secret::new(value),
                    _ => result.refresh = Secret::new(value),
                }
            }
        }
        Ok(result)
    }
}
pub(super) fn decode(bytes: &[u8], mode: Mode) -> Result<Input, serde_json::Error> {
    let mut decoder = serde_json::Deserializer::from_slice(bytes);
    let result = Seed(mode).deserialize(&mut decoder)?;
    decoder.end()?;
    Ok(result)
}
