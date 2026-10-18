//! Producer-owned lease SQL; independent streams may advance concurrently.
use postgres_storage::outbox::Queries;
pub fn queries() -> Queries {
    Queries {
        pending: r#"SELECT event_id::text, stream_id::text AS stream_id, aggregate_version,envelope
 FROM identity.outbox candidate WHERE delivered_at IS NULL AND NOT dead
 AND (lease_until IS NULL OR lease_until<=clock_timestamp())
 AND NOT EXISTS(SELECT 1 FROM identity.outbox prior WHERE prior.stream_id=candidate.stream_id
 AND prior.sequence<candidate.sequence AND prior.delivered_at IS NULL)
 ORDER BY sequence FOR UPDATE SKIP LOCKED LIMIT 1"#,
        claim: "UPDATE identity.outbox SET lease_owner=$1::uuid,lease_until=clock_timestamp()+interval '5 seconds' WHERE event_id=$2::uuid",
        poison: "UPDATE identity.outbox SET dead=true,failure_code='invalid_envelope',lease_owner=NULL,lease_until=NULL WHERE event_id=$1::uuid",
        delivered: "UPDATE identity.outbox SET delivered_at=clock_timestamp(),lease_owner=NULL,lease_until=NULL WHERE event_id=$1::uuid AND lease_owner=$2::uuid AND delivered_at IS NULL AND NOT dead",
        failed: "UPDATE identity.outbox SET attempts=attempts+1,failure_code=$3,dead=(attempts+1>=5),lease_owner=NULL,lease_until=NULL WHERE event_id=$1::uuid AND lease_owner=$2::uuid AND delivered_at IS NULL AND NOT dead",
        ready: "SELECT EXISTS(SELECT 1 FROM identity.outbox WHERE dead)",
    }
}
pub fn exhausted() -> kernel::Failure {
    kernel::Failure::new(
        kernel::Kind::Unavailable,
        "identity.delivery_exhausted",
        "event delivery exhausted retries",
    )
    .with_public("Identity event delivery needs attention.")
}
