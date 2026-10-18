//! Go-compatible persisted snapshots/receipts and immutable v1 command digests.
use crate::{
    app::{self, Action, Command, Receipt},
    domain::{self, File, Snapshot, Spec, Status},
};
use kernel::{Failure, id::Id};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
#[derive(Serialize, Deserialize)]
#[allow(non_snake_case)]
struct SpecWire {
    Name: String,
    ContentType: String,
    ByteSize: u64,
    SHA256: String,
}
#[derive(Serialize, Deserialize)]
#[allow(non_snake_case)]
struct SnapshotWire {
    ID: String,
    AccountID: String,
    OwnerID: String,
    Spec: SpecWire,
    Status: String,
    Version: u64,
    CreatedAt: String,
    UpdatedAt: String,
}
fn project(s: &Snapshot) -> Result<SnapshotWire, Failure> {
    File::restore(s.clone())?;
    Ok(SnapshotWire {
        ID: s.id.to_string(),
        AccountID: s.account_id.to_string(),
        OwnerID: s.owner_id.to_string(),
        Spec: SpecWire {
            Name: s.spec.name.clone(),
            ContentType: s.spec.content_type.clone(),
            ByteSize: s.spec.byte_size,
            SHA256: s.spec.sha256.clone(),
        },
        Status: s.status.as_str().into(),
        Version: s.version,
        CreatedAt: events::time(s.created_at)?,
        UpdatedAt: events::time(s.updated_at)?,
    })
}
fn restore(w: SnapshotWire) -> Result<File, Failure> {
    let id = |s: &str| Id::parse(s).map_err(|_| domain::invalid_state());
    let time = |s: &str| -> Result<u64, Failure> {
        let t = chrono::DateTime::parse_from_rfc3339(s).map_err(|_| domain::invalid_state())?;
        if !s.ends_with('Z') || t.timestamp_subsec_nanos() % 1000 != 0 {
            return Err(domain::invalid_state());
        }
        t.timestamp_micros()
            .try_into()
            .map_err(|_| domain::invalid_state())
    };
    File::restore(Snapshot {
        id: id(&w.ID)?,
        account_id: id(&w.AccountID)?,
        owner_id: id(&w.OwnerID)?,
        spec: Spec {
            name: w.Spec.Name,
            content_type: w.Spec.ContentType,
            byte_size: w.Spec.ByteSize,
            sha256: w.Spec.SHA256,
        },
        status: Status::parse(&w.Status)?,
        version: w.Version,
        created_at: time(&w.CreatedAt)?,
        updated_at: time(&w.UpdatedAt)?,
    })
}
pub fn encode(f: &File) -> Result<serde_json::Value, Failure> {
    serde_json::to_value(project(f.snapshot())?).map_err(|_| domain::invalid_state())
}
pub fn decode(v: serde_json::Value) -> Result<File, Failure> {
    restore(serde_json::from_value(v).map_err(|_| domain::invalid_state())?)
}
#[derive(Serialize, Deserialize)]
#[allow(non_snake_case)]
struct ReceiptWire {
    Key: String,
    Action: String,
    Actor: String,
    File: SnapshotWire,
}
pub fn encode_receipt(r: &Receipt) -> Result<serde_json::Value, Failure> {
    r.validate(true)?;
    serde_json::to_value(ReceiptWire {
        Key: r.key.clone(),
        Action: r.action.as_str().into(),
        Actor: r.actor.to_string(),
        File: project(&r.file)?,
    })
    .map_err(|_| domain::invalid_state())
}
pub fn decode_receipt(v: serde_json::Value) -> Result<Receipt, Failure> {
    let w: ReceiptWire = serde_json::from_value(v).map_err(|_| domain::invalid_state())?;
    let r = Receipt {
        key: w.Key,
        action: Action::parse(&w.Action).map_err(|_| domain::invalid_state())?,
        actor: Id::parse(&w.Actor).map_err(|_| domain::invalid_state())?,
        file: restore(w.File)?.snapshot().clone(),
    };
    r.validate(true)?;
    Ok(r)
}
pub fn fingerprint(c: &Command) -> Result<Vec<u8>, Failure> {
    c.validate()?;
    #[derive(Serialize)]
    struct Input<'a> {
        action: &'a str,
        file_id: &'a str,
        name: &'a str,
        content_type: &'a str,
        byte_size: u64,
        sha256: &'a str,
        expected_version: u64,
    }
    let bytes = serde_json::to_string(&Input {
        action: c.action.as_str(),
        file_id: c.file_id.as_ref().map_or("", Id::as_str),
        name: c.spec.as_ref().map_or("", |s| s.name.as_str()),
        content_type: c.spec.as_ref().map_or("", |s| s.content_type.as_str()),
        byte_size: c.spec.as_ref().map_or(0, |s| s.byte_size),
        sha256: c.spec.as_ref().map_or("", |s| s.sha256.as_str()),
        expected_version: c.expected,
    })
    .map_err(|_| domain::invalid_state())?;
    let bytes = bytes
        .replace('&', "\\u0026")
        .replace('<', "\\u003c")
        .replace('>', "\\u003e")
        .replace('\u{2028}', "\\u2028")
        .replace('\u{2029}', "\\u2029");
    let mut hash = Sha256::new();
    hash.update(b"files.command.v1\n");
    hash.update(bytes);
    Ok(hash.finalize().to_vec())
}
pub fn admit_receipt(r: Receipt, t: &app::Target, key: &str) -> Result<Receipt, Failure> {
    if r.key != key || r.actor != t.actor || r.file.account_id != t.account {
        return Err(domain::invalid_state());
    }
    Ok(r)
}
