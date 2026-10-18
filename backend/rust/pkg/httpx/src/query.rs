//! URL decoding mechanics; endpoints own allowed parameters and public failures.
use std::collections::BTreeMap;
#[derive(Debug)]
pub struct InvalidQuery;
pub fn component(value: &str, plus: bool) -> Result<String, InvalidQuery> {
    let bytes = value.as_bytes();
    let mut result = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'%' => {
                let pair = bytes.get(i + 1..i + 3).ok_or(InvalidQuery)?;
                let digit = |b: u8| {
                    if b.is_ascii_hexdigit() {
                        (b as char).to_digit(16)
                    } else {
                        None
                    }
                };
                result.push(
                    (digit(pair[0]).ok_or(InvalidQuery)? * 16
                        + digit(pair[1]).ok_or(InvalidQuery)?) as u8,
                );
                i += 3;
            }
            b'+' if plus => {
                result.push(b' ');
                i += 1;
            }
            value => {
                result.push(value);
                i += 1;
            }
        }
    }
    String::from_utf8(result).map_err(|_| InvalidQuery)
}
pub fn unique(raw: &str) -> Result<BTreeMap<String, String>, InvalidQuery> {
    let mut parameters = BTreeMap::new();
    for part in raw.split('&').filter(|p| !p.is_empty()) {
        if part.contains(';') {
            return Err(InvalidQuery);
        }
        let (key, value) = part.split_once('=').unwrap_or((part, ""));
        if parameters
            .insert(component(key, true)?, component(value, true)?)
            .is_some()
        {
            return Err(InvalidQuery);
        }
    }
    Ok(parameters)
}
