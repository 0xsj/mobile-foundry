//! Selected public facts, mapped before the producer commits state and outbox together.
use crate::{app::ports::CommittedFact, domain::Action};
use kernel::Failure;
pub fn message(record: &CommittedFact) -> Result<events::Envelope, Failure> {
    let f = &record.fact;
    let name = match f.action {
        Action::Created if f.version == 1 => "account.created.v1",
        Action::Renamed if f.version >= 2 => "account.renamed.v1",
        _ => return Err(events::invalid()),
    };
    let message = events::Envelope {
        id: record.id.clone(),
        name: name.into(),
        producer: "account".into(),
        account_id: Some(f.account_id.clone()),
        actor_id: f.actor_id.clone(),
        version: f.version,
        occurred_at: f.occurred_at,
        metadata: record.metadata.clone(),
        data: serde_json::to_vec(
            &serde_json::json!({"account_id":f.account_id.as_str(),"version":f.version}),
        )
        .map_err(|_| events::invalid())?,
    };
    message.validate()?;
    Ok(message)
}
