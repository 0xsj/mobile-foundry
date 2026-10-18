# State/outbox and evidence/inbox boundaries

Claim: the producer's atomic commit and the consumer's deduplicated commit are
two separate guarantees, connected by replayable delivery rather than one transaction.

Origin/evidence: 2026-10-18 Account/Identity metadata-failure tests, Audit concurrent
replay tests, shared contract corpora and the actual
[lost acknowledgment composition check](../../cmd/server/tests/audit_delivery.rs).

The producer prepares an integration envelope before changing its own state.
One owner lock then commits state and required outbox record together. Publishing
afterward never substitutes for that append. A publisher can fail independently,
so the HTTP write can succeed while Audit is temporarily behind.

The consumer validates its contract and maps selected evidence. It appends that
evidence with a marker keyed by consumer/event ID in its own lock. Identical
delivery succeeds without changing first recording time; the same identity with
different evidence is a conflict. Independent consumers retain independent markers.

Example: Audit commits an Account event, but the producer misses acknowledgment.
Retry sends the original event ID. Audit recognizes its marker, preserves the
entry/time, and allows the producer to settle delivered. Reallocating an event
ID on retry would defeat this guarantee and duplicate apparent activity.

Versions belong to source transitions; Audit append sequence belongs to receipt.
Neither supplies a global event order. Account and personal pages can have gaps
from other scopes' entries, and a cursor cannot bypass current authorization.
The [Audit reader](../../internal/audit/src/app/query/mod.rs) keeps capability
failure separate from a successful negative decision.

Gotchas: the memory adapter establishes local atomicity, not restart durability or exactly-once
execution. Required metadata/payload failure must occur before mutation. Never
hold an owner lock across a foreign call. HTTP commands have separate replay
contracts; event retries do not authorize resending registration or rename.
Used in [Events](../modules/pkg/events/README.md) and
[Audit](../modules/internal/audit/README.md). Verification:
`make backend-audit-parity-test`, `make rust-test`. Next: which schema/lease rules
will preserve these two commit boundaries in PostgreSQL?

The [PostgreSQL extension](../modules/pkg/persistence/postgres/README.md) now
verifies owned transactions and restart replay separately. It preserves the
same producer/consumer boundaries without introducing a shared transaction.
