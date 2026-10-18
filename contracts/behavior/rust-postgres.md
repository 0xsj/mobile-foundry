# Rust PostgreSQL core

Observed 2026-10-18. This extends the Rust runtime/Identity/Account/Audit/Notes/Files/Notifications subset
through existing application ports, following the shared
[PostgreSQL ownership contract](postgres-persistence.md).

Select `STORAGE=postgres`. Account/Audit/Notes URLs fall back from
`ACCOUNT_DATABASE_URL` / `AUDIT_DATABASE_URL` / `NOTES_DATABASE_URL` /
`FILES_DATABASE_URL` / `NOTIFICATIONS_DATABASE_URL` to `DATABASE_URL`.
`AUTH_MODE=identity` also requires `IDENTITY_DATABASE_URL` or the common URL.
Present empty module overrides are errors, rather than fallback. Pool maximum is
`POSTGRES_POOL_MAX_CONNS`, default 8, range 1–128 per owner. Connections use UTC,
five-second acquire/statement and two-second lock budgets. SQL parameters carry
values; neither domains nor application ports expose SQLx.

`db-migrate` applies selected histories explicitly; `--check` and API startup are
read-only. Migration command includes Identity whenever its URL is configured;
API selects it only in identity mode. Each file/history row commits together
under an owner advisory lock. Checks reject missing, changed or future history.
An earlier successfully applied file remains applied if a later file fails.
The migrator allows thirty seconds per owner and closes its pool before exiting.
Artifacts currently match Go's exact Account/Audit/Identity/Notes/Files/Notifications histories. Additive
recovery/File DDL preserves checksums. Recovery now has separate implementation
and acceptance; Files now has a separately verified owned history/adapter.

Account aggregate/mapping/outbox changes commit together under Read Committed.
Concurrent bootstrap losers read the winner in a separate statement after the
unique-key wait. Rename locks the row, compares the original version and guards
immutable state. Identity locks the principal for session issuance, comparing
the observed password hash. Session rotation locks the session, preserves refresh
markers and commits state/outbox together. Refresh reuse commits revocation before
the command returns its credential refusal. Writes have no automatic retry.

Producers claim at most one eligible event per poll under `FOR UPDATE SKIP LOCKED`,
with a five-second lease and process-owner token. An earlier undelivered event,
including an exhausted one, blocks later events in its stream. Other streams may
advance. Stale-owner acknowledgments/failure updates are refused. Five failed
deliveries retain a dead event and fail readiness. Invalid persisted envelopes
are committed as quarantined before the poll reports failure.

Audit owns a separate pool/repository. Evidence/inbox/append position commit
together. Its singleton append row stays locked through COMMIT, so a visible
cursor cannot skip an earlier still-uncommitted position. Consumer/event replay
with the same fingerprint preserves the original evidence/time; a changed
fingerprint is a failure. Fingerprint field order/encoding matches Go's existing
core evidence, including admitted recovery/File actions. Optional file_id is
retained and omitted from older fingerprints; unsupported evidence is refused.

Returned pre-COMMIT failures attempt rollback under an independent two-second
cleanup budget. Dropping a SQLx transaction queues rollback before connection
reuse; dropping a future does not immediately interrupt the server's SQL or prove
that a COMMIT did not happen. Lost/ambiguous COMMIT acknowledgment returns an
uncertain-write failure, with no retry or rollback guarantee. Driver/database
diagnostics remain private. Shutdown drains HTTP, fences and joins all delivery
workers, drains Identity KDF jobs, then explicitly closes every selected pool.
The reset queue also stops/joins before KDF/pool closure. Readiness checks selected
workers/queue, pool access and exhausted outboxes.
Tracked directory I/O also drains before pools; a filesystem call can outlast
the HTTP-only shutdown budget.

Acceptance: `make backend-postgres-parity-test` owns an unused disposable
database and API processes. It checks migration concurrency/history/file rollback,
bootstrap/CAS races, producer rollback, refresh reuse, stale leases/quarantine,
Audit rollback/deduplication/cursor locking, canceled transaction cleanup, core
Audit HTTP flows, forced process restart, pending-event recovery and Go↔Rust core
record/fingerprint handoff. Test mutations never target an existing Compose volume.

The [Notes extension](rust-notes.md) adds its own pool/receipt migration,
atomic record/receipt finalization, deferred completeness guard, exact v1
fingerprint replay and separate module database acceptance.

The [recovery extension](rust-recovery.md) uses the existing Identity 0002/Audit
0004 histories. Password/contact/code changes and required facts commit together
under the principal lock. Acceptance injects verification/reset outbox failure,
then consumes the same code after rollback. Unconsumed codes also survive API
kill and cross-language verification/reset handoff with retained Audit evidence.

[Files](rust-files.md) adds metadata/receipt/outbox atomicity, deferred receipt
completion and a separate private object directory retained alongside the database.
Rollback tests inject outbox/finalization failures. A killed Rust upload is
completed in Go, downloaded in Rust with replayed Audit evidence, then deleted
in Rust and its deletion/historical receipt replayed in Go. Module URL acceptance
now opens five separate owner databases.

[In-app Notifications](rust-notifications.md) adds its own decision/item/preference
repository with a shared account/principal row guard. Tests fail a late sequence
update to prove item/marker rollback, then verify failed read/settings retain prior
state. Pending Files delivery survives a killed Rust API and is consumed in Go;
cross-language replay preserves suppressed decisions, items, first read and settings.
Separate URL acceptance now opens six owner databases and observes inbox delivery
through the configured Notifications pool.

[Installations](rust-installations.md) uses unchanged Notifications 0002. Shared
store conformance and injected cleanup/tombstone/marker/expiry-claim faults verify
atomic rollback. Six separate databases exercise registration and retirement
without co-located Identity tables. Killed pending v1/v2 sources recover across
Go/Rust; fingerprints replay and newer rebound targets remain intact. Copied
0003/0004 push DDL preserves history without selecting send/jobs behavior.

Limits: no database failover, killed-server COMMIT ambiguity drill, durable broker,
retention, backup/deployment profile, live SMTP-provider acceptance,
cloud object providers, push, WebSockets, generated Rust starter or native-connected Rust
selection. Migration compatibility for the core subset is not full backend parity.
