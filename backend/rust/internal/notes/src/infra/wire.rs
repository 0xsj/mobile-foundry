//! Persisted receipt/fingerprint encoding; policy stays in the domain.
use crate::domain::{self, Action, Receipt, ReceiptState, Write};
use kernel::{Failure, id::Id};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
#[derive(Serialize, Deserialize)]
#[serde(default)]
#[derive(Default)]
struct Wire {
    command_id: String,
    account_id: String,
    principal_id: String,
    action: String,
    note_id: String,
    title: String,
    version: u64,
}
pub fn encode(r: &Receipt) -> Result<Vec<u8>, Failure> {
    let s = r.snapshot();
    serde_json::to_vec(&Wire {
        command_id: s.command_id.as_str().into(),
        account_id: s.account_id.as_str().into(),
        principal_id: s.principal_id.as_str().into(),
        action: s.action.as_str().into(),
        note_id: s.note_id.as_str().into(),
        title: s.title.clone(),
        version: s.version,
    })
    .map_err(|_| domain::invalid_state())
}
pub fn decode(bytes: &[u8]) -> Result<Receipt, Failure> {
    let w: Wire = serde_json::from_slice(bytes).map_err(|_| domain::invalid_state())?;
    let id = |s: &str| Id::parse(s).map_err(|_| domain::invalid_state());
    Receipt::restore(ReceiptState {
        command_id: id(&w.command_id)?,
        account_id: id(&w.account_id)?,
        principal_id: id(&w.principal_id)?,
        action: Action::parse(&w.action).map_err(|_| domain::invalid_state())?,
        note_id: id(&w.note_id)?,
        title: w.title,
        version: w.version,
    })
}
pub fn fingerprint(w: &Write) -> Result<Vec<u8>, Failure> {
    w.validate()?;
    #[derive(Serialize)]
    struct Input<'a> {
        command_id: &'a str,
        action: &'a str,
        note_id: &'a str,
        title: &'a str,
        expected_version: u64,
    }
    let json = serde_json::to_string(&Input {
        command_id: w.command_id.as_str(),
        action: w.action.as_str(),
        note_id: w.note_id.as_ref().map_or("", Id::as_str),
        title: &w.title,
        expected_version: w.expected,
    })
    .map_err(|_| domain::invalid_state())?;
    // Go encoding/json's default escaping is part of notes.write.v1, including
    // HTML characters and the two JavaScript separator scalars. Do not normalize titles.
    let json = json
        .replace('&', "\\u0026")
        .replace('<', "\\u003c")
        .replace('>', "\\u003e")
        .replace('\u{2028}', "\\u2028")
        .replace('\u{2029}', "\\u2029");
    let mut hash = Sha256::new();
    hash.update(b"notes.write.v1\n");
    hash.update(json.as_bytes());
    Ok(hash.finalize().to_vec())
}
