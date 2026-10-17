# Files integration facts v1

Producer: files. Consumer currently selected: audit.files. Delivery uses the
existing generic envelope/outbox/inbox protocol and is at-least-once.

| Name | Meaning | Aggregate version |
| --- | --- | --- |
| files.upload_requested.v1 | Pending file metadata committed | 1 |
| files.upload_completed.v1 | Declared bytes verified and available metadata committed | 2 |
| files.deleted.v1 | File became inaccessible through a committed tombstone | 2 for a pending file, 3 for an available file |

Each payload contains file_id, account_id and version. IDs are canonical nonzero
UUIDs. Payload account/version must match the envelope's account_id and
aggregate_version. Envelope actor_id is the admitted principal, occurred_at is
the state's UTC microsecond timestamp, and request/correlation metadata is selected
outside the domain. The stream is one file, not the whole account.

Names, media types, hashes, bytes, object keys and URLs are excluded. Deletion
means logical inaccessibility; it does not attest to completed object cleanup.
Producer state, receipt (when keyed) and outbox append are atomic. Receivers validate
shape, producer, name, IDs, versions and state/action meaning, then map to their
own application input. Audit retains file_id as the subject alongside account,
actor, action/version/time and causal IDs, with inbox/evidence committed together.
An existing Account/Identity fingerprint is unchanged when the new optional subject
is absent. Do not import Files domain/application types into Audit.

Broker replacement, Realtime projection, notification delivery, privacy export/
erasure integration and operational event retention remain separate capabilities.
