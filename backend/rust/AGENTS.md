# Rust backend development

The [memory delivery/Audit slice](../../contracts/behavior/rust-audit.md) and
[PostgreSQL core](../../contracts/behavior/rust-postgres.md) are implemented. Preserve producer state/outbox and Audit evidence/inbox atomicity,
stable replay identity, explicit negative-versus-failure answers and owned worker
shutdown. Fence/join every delivery worker after HTTP drain and before closing
module resources/pools. Use the explicit disposable PostgreSQL acceptance for
durable evidence; memory acceptance does not establish restart recovery.
Preserve exact applied migration bytes and keep the artifact catalog in process
composition. Never add foreign SQL or silently narrow unsupported persisted evidence.

The [Notes slice](../../contracts/behavior/rust-notes.md) owns records/receipts.
Authorize every operation including replay and lookup; check replay before current
CAS/existence; preserve scoped command input and exact v1 fingerprint bytes.
Commit receipt claims/finalization with the record and retain the deferred guard.
Notes Changes is optional/lossy and no broadcast adapter is currently selected.

The [Files slice](../../contracts/behavior/rust-files.md) owns metadata/receipts/
required events separately from Objects. Keep exact v1 fingerprint bytes and
Go-compatible persisted snapshots. Authorize replays, commit tombstones before
cleanup, and repeat cleanup only through the original deletion command. Retain
per-file outbox ordering and optional Audit file_id without changing prior
fingerprints. Directory blocking jobs retain permits/handles across canceled
callers and close futures; join them before pools. Preserve the copied applied SQL.

The [recovery slice](../../contracts/behavior/rust-recovery.md) preserves the
principal credential checkpoint and principal-before-challenge/session lock order.
Commit code consumption, password mutation, session revocation and required facts
atomically. Keep v1 revocation reasons closed; password reasons use v2. Mail
configuration stays private. Anonymous reset admits only bounded ephemeral work;
lookup/mail belongs to the owned worker, with no addresses/codes in logs. Fence
and join that queue before KDF/pool closure; retain join handles across canceled
close futures. Pruning is a store operation, not an implicitly selected schedule.

The [in-app Notifications slice](../../contracts/behavior/rust-notifications.md)
commits source markers with both delivered and suppressed decisions. Preserve
the v1 source fingerprint and first-read time, use the same account/principal
guard for delivery/settings/read/page snapshots, and keep foreign calls outside
that transaction. Read/preferences offer no receipts/CAS or automatic replay.
Installations has its own selected adapters; copied push histories still preserve
checksums without selecting push behavior.

The [installation registry](../../contracts/behavior/rust-installations.md) uses
trusted credential-v2 actors and independent hashed possession proofs. Preserve
proof/CAS/target ownership under the same store guard as retired-session admission.
Commit matching target cleanup, tombstone and source fingerprint atomically.
Late old-session facts must not clear a rebound installation; tombstones must also
prevent delayed creation after a revocation with no prior binding. No foreign
calls occur under this guard. Registration has no receipts or automatic replay;
registered/current_binding metadata does not establish send eligibility.

Follow [backend ownership](../AGENTS.md), the shared contracts, and
[the current parity inventory](PARITY.md). Go remains the behavioral reference;
keep Rust ownership, `Result<T, Failure>`, trait ports and async task lifetime
idiomatic. Update the inventory with actual evidence as each slice lands.

Use `cmd/server` for process wiring, `pkg` for reusable primitives and
`internal/<module>` for capability crates. Domain code has no HTTP/SQL/runtime
imports. Application code owns ports; module composition selects adapters.
Keep `app/command`, `app/query`, `infra/<adapter>` and versioned transport paths
when those responsibilities exist. Health has a query/transport and no invented
domain model. Avoid creating empty domain tutorials or unused runtime abstractions.

Each new module owns its state. Consumer-owned versioned capability adapters
answer synchronous cross-module questions. Outbox/inbox facts carry committed
independent reactions; broadcast/socket hints stay recoverable. Implement and
verify these facilities before claiming them for Rust. Never import a foreign
module's store or put adapters behind domain APIs that still expose HTTP/SQL.

Keep diagnostics/causes private; public projection is explicit. Runtime logs use
allowlisted metadata and no path/query/body/credential/panic content. Production
process composition installs the redacted panic hook before constructing adapters.
Use returned errors for expected failures and reserve panics for defects.

Own tasks/resources, stop admission before draining, join workers before pools,
and keep migrations explicit. Do not detach work to
make shutdown tests pass. Document cancellation/uncertain effects with each write.

Run `make check`, `make test`, and repository `make backend-parity-test` for
runtime changes. Add shared cases when public behavior grows; distinguish shared
corpus acceptance from full backend parity. Follow [notes](../../docs/NOTES.md)
each slice, including first-use Rust mechanics and mirrored module walkthroughs.
Register CLI generation and independently verify its output before promoting
[starter readiness](../../docs/STARTER-READINESS.md).
