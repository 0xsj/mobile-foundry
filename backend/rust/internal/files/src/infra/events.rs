use crate::domain::{Snapshot, Status};
use kernel::{
    Failure,
    context::Metadata,
    id::{Generator, Id},
};
pub fn message(
    s: &Snapshot,
    actor: &Id,
    metadata: Metadata,
    ids: &dyn Generator,
) -> Result<events::Envelope, Failure> {
    crate::domain::File::restore(s.clone())?;
    let action = match s.status {
        Status::Pending => "upload_requested",
        Status::Available => "upload_completed",
        Status::Deleted => "deleted",
    };
    let m=events::Envelope{id:ids.new_id()?,name:format!("files.{action}.v1"),producer:"files".into(),account_id:Some(s.account_id.clone()),actor_id:actor.clone(),version:s.version,occurred_at:s.updated_at,metadata,
        data:serde_json::to_vec(&serde_json::json!({"file_id":s.id.as_str(),"account_id":s.account_id.as_str(),"version":s.version})).map_err(|_|events::invalid())?};
    m.validate()?;
    Ok(m)
}
