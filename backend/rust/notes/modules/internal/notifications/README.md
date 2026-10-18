# Scoped inbox and committed delivery decisions

Claim: an independently consumed event can produce a coherent inbox while
preserving both delivered and suppressed decisions through replay and restart.

Origin/evidence: 2026-10-18 Rust 1.86/SQLx 0.8.6 implementation, twenty-two shared
event cases, four fixed fingerprints, forty-three HTTP cases, actual Go/Rust API
flows and isolated PostgreSQL 14.24 rollback/restart/handoff. Read
[behavior and limits](../../../../../../contracts/behavior/rust-notifications.md).

Read these sources in order:

1. [Domain](../../../../internal/notifications/src/domain/mod.rs): private Item
   and detached Snapshot, known type/category, safe sequence and first-read transition.
2. [Application values](../../../../internal/notifications/src/app/mod.rs) and
   [ports](../../../../internal/notifications/src/app/ports.rs): scoped target,
   admitted source Delivery and consumer-owned Store/AccountAccess.
3. [Named commands](../../../../internal/notifications/src/app/command/mod.rs)
   and [queries](../../../../internal/notifications/src/app/query/mod.rs):
   authorize public operations, keep trusted event delivery independent, and
   distinguish access failure from denial.
4. [Memory](../../../../internal/notifications/src/infra/memory.rs) and
   [PostgreSQL](../../../../internal/notifications/src/infra/postgres.rs): atomic
   marker/decision/item/sequence, first-read retention and guarded count/row snapshots.
5. [Receiving adapter](../../../../internal/notifications/src/infra/events.rs)
   and [source fingerprint](../../../../internal/notifications/src/infra/records.rs):
   strict completion evidence, actor-as-recipient and exact existing v1 bytes.
   Neither imports Files or Account implementation types.
6. [HTTP](../../../../internal/notifications/src/transport/http/v1/mod.rs),
   [input/cursor admission](../../../../internal/notifications/src/transport/http/v1/input.rs)
   and [composition](../../../../internal/notifications/src/lib.rs): passive
   projection, pointer-bool null semantics and explicit subscription.
7. [Process composition](../../../../cmd/server/src/main.rs): selected sixth
   pool, frozen capability/subscription assembly, existing Files workers and
   joins before pool closure. No Notifications timer is introduced.

Example: media_in_app is false when upload completion E first reaches Notifications.
The store commits E's fingerprint without an item. Later the user enables the
preference and E is delivered again after a lost producer acknowledgment. The
inbox marker makes that replay a no-op; a fresh event can create a new item.
Ignoring the marker for muted events would turn replay into unwanted delivery.

Arrival sequence, not source timestamp, orders the inbox. One SQL scope row locks
delivery, settings, first-read changes and a page's count/rows; the memory adapter
uses its existing mutex. A filtered page can be empty while unread_count remains
positive because the count describes the entire scope. No provider/capability
call runs while the store guard is held.

Verification: make backend-notifications-parity-test executes actual upload/inbox/
read/settings/scope/page flows plus the shared corpus. [Store conformance](../../../../internal/notifications/tests/support/mod.rs)
runs against memory and PostgreSQL, including sixteen-way replay and concurrent
count/read/delivery snapshots. [Application tests](../../../../internal/notifications/tests/application.rs)
verify domain restoration and access failures. [Database fault tests](../../../../cmd/server/tests/support/notifications.rs)
prove late sequence failure rolls back item and marker, and failed read/settings
retain prior state. make backend-postgres-parity-test kills a pending Rust producer,
consumes it in Go and checks unchanged suppressed/read/settings state in Rust and Go.

Gotchas: Items reference Files; they grant no download permission and may refer to
a deleted file. Preferences are evaluated at first consumption, so old pending
events use the current preference. Read/settings have no receipt/CAS, so uncertain
writes need authoritative readback. All four Go migration files are retained for
compatibility. [Installations](installations.md) now has separate selected adapters
and acceptance; push remains unimplemented. Rust native/CLI selection remains
unregistered. Related:
[consumed suppression](../../../patterns/consumed-suppression-and-inbox-snapshots.md),
[causal delivery fence](../../../techniques/causal-delivery-fences.md),
[transaction ownership](../../../substrate/sqlx-transactions-and-pool-lifetime.md).
Next: read [the installation walkthrough](installations.md) for trusted session
evidence and independent revocation markers without Identity-store coupling.

