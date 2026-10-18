# Consumed suppression and coherent inbox snapshots

Claim: deciding not to deliver is a committed consumer outcome, and the setting
used for that decision must share the inbox's serialization boundary.

Origin/evidence: 2026-10-18 [memory](../../internal/notifications/src/infra/memory.rs)
and [PostgreSQL](../../internal/notifications/src/infra/postgres.rs) adapters,
shared source fingerprints, store conformance and actual Go/Rust restart handoff.

At-least-once delivery can replay any source event after its consumer already
committed. If muted events leave no marker, enabling the preference changes the
meaning of replay. Notifications instead records the original source fingerprint
with either an item or a suppression decision. Preference and arrival time are
excluded from that fingerprint: they describe consumer state, not source input.

For example, E first arrives while muted. A marker commits, no item appears, and
the scope sequence does not advance. After unmuting, replay E remains suppressed;
fresh event F can allocate the next arrival position. If E's file/recipient/time
changes under the same identity, replay conflicts even if E was suppressed.

```rust
// Conceptual: all effects below share the same owned commit.
if prior_source_matches(event)? {
    return Ok(());
}
let preference = lock_scope(target).await?;
if preference.enabled {
    append_item_and_advance_sequence(event).await?;
}
commit_source_decision(event.fingerprint()).await?;
```

The SQL adapter claims the inbox identity first and then locks its scope row.
A conflicting duplicate reads the winning committed marker without deciding
again. Under that same scope lock, a page counts unread items and selects rows;
delivery and read changes cannot interleave those statements into an incoherent
snapshot. Source time does not order delivery, and pagination is not a frozen feed.

Verification: [conformance](../../internal/notifications/tests/support/mod.rs)
asserts retained suppression, same-ID changed-input conflict, first-read preservation,
descending arrival order and concurrent count/row consistency. Fault tests fail
after item insertion, proving marker/item/sequence rollback. Real PostgreSQL
handoff replays both suppressed and delivered sources between Go and Rust.
Run make backend-notifications-parity-test and make backend-postgres-parity-test.

Gotchas: current preference applies only to first consumption. It does not cancel
an existing item or rewrite old decisions. Store guards never wrap foreign calls.
Read/preference assignments have no offline command receipts or automatic retry.
Related: [module walkthrough](../modules/internal/notifications/README.md),
[state/outbox/inbox boundaries](state-outbox-and-inbox-boundaries.md).
Next: how should retention remove consumer evidence without reviving old messages?

