# Audit as an independent consumer

PostgreSQL follow-up, 2026-10-18: the [owned SQL adapter](../../../../internal/audit/src/infra/postgres.rs)
now implements the same application ports. Read [shared transaction mechanics](../../pkg/persistence/postgres/README.md)
and [restart/handoff acceptance](../../../techniques/durable-restart-and-language-handoff.md).
`make backend-postgres-parity-test` verifies actual durable behavior separately
from the memory acceptance below. Module composition selects the adapter without
foreign repositories or SQL/domain coupling.

Claim: evidence can catch up independently when Audit validates producer
contracts and commits its own evidence/inbox state without foreign repositories.

Origin/evidence: 2026-10-18 four Audit Rust tests, shared 33-event/seven-access/
24-HTTP corpora and real Go/Rust process flows. Read
[behavior](../../../../../../contracts/behavior/rust-audit.md) and
[wire](../../../../../../contracts/http/account-audit-v1.md).

Source reading order:

1. [Domain](../../../../internal/audit/src/domain/mod.rs): typed Evidence,
   permitted core actions/scopes and consumer-owned recording time.
2. [Ports](../../../../internal/audit/src/app/ports.rs),
   [Record](../../../../internal/audit/src/app/command/mod.rs) and
   [queries](../../../../internal/audit/src/app/query/mod.rs): independent command,
   principal reader and account reader with a consumer-owned AccountAccess port.
3. [Event receiver](../../../../internal/audit/src/infra/events.rs): source/name/
   payload/scope admission, ordered DTO policy and selected evidence mapping.
4. [Memory store](../../../../internal/audit/src/infra/memory.rs): one lock appends
   evidence and consumer/event marker. Identical replay is success; different
   evidence fails before mutation. First recording time stays unchanged.
5. [Capability client](../../../../internal/audit/src/infra/capabilities.rs): require
   an explicit boolean from account.access.v1. Missing reply/provider/failure is
   not a successful negative. Only a valid false maps to account-read denial.
6. [HTTP](../../../../internal/audit/src/transport/http/v1/mod.rs),
   [cursor admission](../../../../internal/audit/src/transport/http/v1/input.rs)
   and [composition](../../../../internal/audit/src/lib.rs): trusted principal,
   scoped pages, selected metadata and explicit subscriptions.

Example: Account rename commits version two and its outbox, returns HTTP, then
Audit appends evidence. A lost acknowledgment causes the same event to arrive
again. The inbox recognizes it and preserves the original entry/time, rather
than appending another apparently new activity. Concurrent replay has the same
result. These are consumer effects, not a transaction spanning both stores.

Queries authorize account scope before touching evidence; unknown/non-owner
accounts are admitted negative decisions and return 403. Personal reads derive
identity from the credential edge. Audit's global sequence interleaves scopes,
so gaps in one filtered page are expected. A cursor carries a position and scope,
not permission. The process runner exercises both kinds of cross-scope refusal.

Gotchas: initial reads can lag a successful write. Memory evidence/inbox disappears
on exit. [Recovery](../identity/recovery.md) now adds three principal v1 facts and
session.revoked.v2 with a closed password-reason vocabulary. Fifteen additional
shared event cases pass, including refusal of password reasons in v1. Existing
fingerprints stay unchanged and PostgreSQL Go/Rust handoff retains evidence.
Files now has [its own receiving adapter](../files/README.md) and thirteen shared
admission cases. Optional file_id joins SQL/HTTP evidence and the Go fingerprint
only when present. A killed-process Go/Rust Files handoff replays without another
entry; existing Account/Identity fingerprints stay unchanged. Audit has no public
Record/injection endpoint.
Run `make backend-audit-parity-test`. Related:
[commit and replay boundaries](../../../patterns/state-outbox-and-inbox-boundaries.md),
[event mechanics](../../pkg/events/README.md). Next: how should new producer vocabularies extend evidence without changing old fingerprints?
