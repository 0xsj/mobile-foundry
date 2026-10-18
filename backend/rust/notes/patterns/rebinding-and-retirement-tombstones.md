# Rebinding and retirement tombstones

Claim: delayed cleanup needs both a conditional binding mutation and a retained
negative checkpoint to prevent old work from creating state after cleanup ran.

Origin/evidence: 2026-10-18
[Notifications registry](../../internal/notifications/src/infra/memory/installations.rs),
[SQL adapter](../../internal/notifications/src/infra/postgres/installations.rs),
[shared conformance](../../internal/notifications/tests/support/installations.rs)
and disposable Go/Rust crash/replay acceptance on PostgreSQL 14.24.

Identity owns authentication and commits revocation with its outbox. Notifications
owns target metadata and reacts independently. Those transactions cannot pretend
to be one atomic write. A revocation may arrive after a user has rebound the
installation, or before an old in-flight registration has reached the registry.
Deleting by installation ID alone damages a newer binding. Only deleting existing
rows still permits the delayed old registration to recreate a target.

For example, I moves from session S1 to S2 before S1's event arrives. The consumer
disables only bindings still owned by S1, adds S1's tombstone, and records the
event fingerprint in the same commit. Registration checks that tombstone under
the same guard as binding/target mutation. Either race order ends without an
enabled S1 target while retaining I's legitimate S2 binding.

```rust
// Conceptual: all three effects commit under the same owned registry guard.
disable_current_bindings(principal, session)?;
retain_revoked_session(principal, session)?;
record_source_fingerprint(event_id, fingerprint)?;
```

The fingerprint makes exact replay a no-op and rejects changed input under the
same event ID. A tombstone without the marker would repeat cleanup; a marker
without the tombstone would forget absence. PostgreSQL's singleton registry row
also serializes target uniqueness/reclamation with these changes; the memory
adapter uses one mutex. No Identity repository is needed inside that transaction.

Verification: the shared test suite covers old-event rebinding, revocation without
an existing installation, register-versus-retire and same-event input conflicts.
Database fault injection rejects cleanup, tombstone and marker writes separately,
asserting retained original token and absent partial evidence after each failure.
Real processes recover pending v1/v2 sources after SIGKILL in the other language;
replayed events preserve newer registrations. Run make backend-postgres-parity-test.

Gotchas: cleanup remains eventual and cannot promise that no sender raced it.
Retaining tombstones forever is not a general retention policy; deletion needs
a source replay horizon and delayed-command policy. The simple singleton guard
trades throughput for a clear initial serialization boundary. Related:
[module walkthrough](../modules/internal/notifications/installations.md),
[state/outbox/inbox boundaries](state-outbox-and-inbox-boundaries.md).
Next: read [dispatch ownership](../modules/internal/notifications/push.md) and
[partial cleanup outcomes](known-rejection-and-unconfirmed-cleanup.md) for checks
around a network call outside the registry transaction.
