# Rust account Notifications

Established 2026-10-18. Rust implements the in-app subset of
[Notifications behavior](notifications.md) and [HTTP v1](../http/notifications-v1.md).
Go remains the reference. [Installations and Identity retirement](rust-installations.md)
have separate selected adapters and acceptance; external push remains unimplemented.

Notifications owns private Item state, named delivery/read/preference handlers
and consumer-owned Store/AccountAccess ports. No Files/Account crate, domain or
SQL enters its core. Public use cases resolve the principal at the edge and
authorize through account.access.v1 with caller notifications before accessing
the selected store. Provider failure remains a failure, not a negative decision.

The sole curated source is files.upload_completed.v1, producer files, version 2.
The receiving adapter checks payload/envelope IDs, version and shape, then maps
the actor to the recipient. It does not infer membership, current ownership or
an audience. Items retain identifiers, known type/category and times; no names,
hashes, content, credentials, URLs or request/correlation metadata are retained.
This is an uploader confirmation, not a public arbitrary-send API.

notifications.files stores the exact source fingerprint with either an admitted
item or a suppressed decision. Its existing v1 NUL-separated encoding excludes
arrival time and current preferences. Exact replay preserves first state, including
suppression after unmuting. Changed input under the same source identity conflicts.
Memory commits decision/item/sequence under one mutex; PostgreSQL claims the
marker and commits it with the scoped preference decision, optional item and
sequence in one transaction. Producer outbox/Audit/inbox remain independently owned.

Delivery, preference writes, mark-read and page snapshots share the account/principal
guard. PostgreSQL locks scope_state before those operations; no foreign call is
held inside that transaction. Lists use newest committed per-scope arrival sequence,
not source occurrence time. Count and rows share a snapshot; count covers all
unread items regardless of page/filter. Pages over time are not a frozen feed.

Mark-read preserves the first read_at. Preference PUT assigns the complete boolean;
last committed assignment wins. Neither mutation has receipts/CAS or automatic
replay. Interruption after commit remains uncertain; read back current state.
Muting affects future first delivery decisions and leaves existing items intact.
An unconsumed backlog evaluates the preference when actually delivered.

HTTP supplies list/HEAD, mark-read and preference GET/HEAD/PUT. Canonical cursors
bind account/principal/unread filter and an exclusive sequence upper bound. Pointer-bool
DTO decoding preserves ordered duplicates, case folding, null reset and earlier
type-error refusal. Missing/null preference fails. Read/settings routes reject
query, unsupported bodies and mutation receipt/precondition headers; list follows
its own Go admission. JSON stays bounded to 16 KiB and existing request deadlines.

Memory is bounded at 10000 source decisions and 10000 disabled scopes; state is
ephemeral. PostgreSQL selects its own pool/schema with NOTIFICATIONS_DATABASE_URL
falling back to DATABASE_URL; present empty is invalid. Migration histories are
copied byte-for-byte through 0004 for Go handoff. Installations uses 0002 through
its separate acceptance; push tables remain unselected. Startup only checks
history; cmd/migrate applies it.
No new polling worker is selected: existing Files outbox workers invoke the
consumer, and normal owned shutdown joins them before pools close.

Evidence: twenty-two event admission cases, four fixed source fingerprints,
forty-three HTTP cases and real Go/Rust upload→inbox, first-read, mute/unmute,
scope isolation, pagination/filter/cursor, private logs and shutdown checks.
Rust additionally verifies domain restoration, sixteen-way duplicate delivery,
concurrent count/row snapshots, source-input conflict, suppressed replay and
denied/unavailable access across all public use cases. Disposable PostgreSQL
injects sequence/item/marker, read and preference faults, exercises six separate
owner databases and kills a Rust API with a pending Files event. Go consumes
that source as suppressed; subsequent Go/Rust replay preserves suppression,
items, first read and settings through the same stored fingerprints.

Run make backend-notifications-parity-test, make rust-check rust-test and
make backend-postgres-parity-test. Dates use the existing Rust admitted UTC
microsecond range 1970–9999; this does not reproduce every arbitrary Go time.Time
domain value. Remaining in-app/push scope: push providers/
jobs, read-all/delete, richer audiences/types, retention/erasure, live hints and
connected native Rust acceptance. Standalone extraction has separate
[starter acceptance](rust-starter.md); read [the parity inventory](../../backend/rust/PARITY.md).

