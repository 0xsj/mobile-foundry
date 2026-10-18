# Session-bound installation ownership

Claim: proof-authorized rebinding and eventual session retirement stay independent
of Identity storage when the consumer commits its own binding, tombstone and
source checkpoint together.

Origin/evidence: 2026-10-18 Rust 1.86, SQLx 0.8.6, disposable PostgreSQL 14.24;
the [Go registry](../../../../../go/notes/modules/internal/notifications/installations.md)
is the behavioral reference. Shared fixtures and real API tests provide separate
evidence from source inspection. Read in this order:

1. [Domain](../../../../internal/notifications/src/domain/installation.rs): private
   snapshots, route/token bounds, bind/disable transitions, redacted Secret ownership.
2. [Ports and input](../../../../internal/notifications/src/app/installations.rs),
   [commands](../../../../internal/notifications/src/app/command/installations.rs)
   and [query](../../../../internal/notifications/src/app/query/installation.rs):
   trusted Actor evidence, canonical proof digest and explicit mutation checkpoints.
3. [Proof adapter](../../../../internal/notifications/src/infra/security.rs) and
   [record encodings](../../../../internal/notifications/src/infra/installation_records.rs):
   separate hash namespaces for proof, target ownership and revocation input.
4. [Memory](../../../../internal/notifications/src/infra/memory/installations.rs)
   and [PostgreSQL](../../../../internal/notifications/src/infra/postgres/installations.rs):
   one owner serialization boundary for proof/CAS/target/tombstones and cleanup.
5. [Identity receiver](../../../../internal/notifications/src/infra/events/identity.rs):
   consumer-owned v1/v2 admission without importing Identity types or stores.
6. [HTTP](../../../../internal/notifications/src/transport/http/v1/installations.rs)
   and [composition](../../../../internal/notifications/src/lib.rs): trusted
   credential-v2 actor, bounded ordered DTOs, passive view and explicit subscription.

Example: installation I belongs to A/session S1 at version 1. Proof plus current
ETag explicitly rebinds I to B/session S2 at version 2. A delayed S1 revocation
retains its tombstone and inbox fingerprint but leaves I intact. Registering a
different I under S1 after that event fails; proof cannot resurrect a retired
session. A subsequent S2 retirement clears I's raw target and advances its version.
This addresses both stale cleanup and delayed creation, which a binding-only
delete would miss. See [the transferable pattern](../../../patterns/rebinding-and-retirement-tombstones.md).

Rust composition uses generic Arc<T> constrained by both store traits, then
coerces clones into independent Arc<dyn Store> and Arc<dyn Installations> ports.
Both reference the same concrete owner/mutex/pool. No foreign domain type or
trait-object upcasting is needed, and tests can substitute each consumer port.
Secret wraps an owned String with redacted formatting and zeroizing Drop;
explicit expose calls are limited to admission, hashing and persistence. SQL/HTTP
libraries still own other copies, so this is not universal secret-memory erasure.

Actual checks: make backend-installations-parity-test runs
[proof/spec/event fixtures](../../../../internal/notifications/tests/installation_admission.rs)
and real Go/Rust APIs with forty-two wire cases and all four retirement reasons.
[Store conformance](../../../../internal/notifications/tests/support/installations.rs)
runs for memory and PostgreSQL: twelve competing creates, proof refusal,
registration checkpoints, rotation/rebind, competing targets, old-event safety,
revocation races, absent-session tombstones, expiry and canceled writes.
[Domain tests](../../../../internal/notifications/tests/installations.rs) check
restoration/version/time limits and private formatting. The
[database faults](../../../../cmd/server/tests/support/installations.rs) reject
each retirement write and the second expiry-reclamation write, proving rollback.
[Handoff runner](../../../../../../scripts/installations_parity.py) kills pending
Rust/Go Identity producers, recovers v1/v2 facts across implementations and replays
the same markers without clearing newer bindings.

Gotchas: current_binding is ownership, registered is stored enablement; neither
is a send authorization. Absolute session expiry differs from access-token expiry.
There are no registration receipts or automatic mutation retry. Raw target tokens
remain plaintext in private PostgreSQL; disabled rows/proofs/tombstones/markers
have no pruning policy. No external provider, send eligibility, native Rust or CLI
selection is established. [In-app state](README.md) and push selection have separate
owners. Related: [owned secrets](../../../language/owned-secrets-and-scope-adapters.md),
[transaction lifetime](../../../substrate/sqlx-transactions-and-pool-lifetime.md).
Next: how should a sender recheck session eligibility without holding this registry
guard across a foreign call or clearing a token that has since rotated?
