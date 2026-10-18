# Account ownership and personal scope

PostgreSQL follow-up, 2026-10-18: the [owned SQL adapter](../../../../internal/account/src/infra/postgres.rs)
now implements the same application ports. Read [shared transaction mechanics](../../pkg/persistence/postgres/README.md)
and [restart/handoff acceptance](../../../techniques/durable-restart-and-language-handoff.md).
`make backend-postgres-parity-test` verifies actual durable behavior separately
from the memory acceptance below. Module composition selects the adapter without
foreign repositories or SQL/domain coupling.

Claim: an Account module can answer owner-scoped product operations without
sharing Identity state or treating a selected account as part of authentication.

Origin/evidence: 2026-10-18 implementation, six Account Rust tests, seven shared
name cases, forty HTTP fixtures and real Go/Rust process flows. Read the
[implemented behavior](../../../../../../contracts/behavior/rust-account.md)
beside the [Go wire baseline](../../../../../../contracts/http/account-audit-v1.md).

Source reading order:

1. [Domain](../../../../internal/account/src/domain/mod.rs): private aggregate,
   snapshot admission, names, monotonic microsecond time/version and fact shape.
2. [Application ports](../../../../internal/account/src/app/ports.rs),
   [commands](../../../../internal/account/src/app/command/mod.rs) and
   [queries](../../../../internal/account/src/app/query/mod.rs): distinct creation,
   repeatable bootstrap, owner reads, paging and versioned rename.
3. [Memory adapter](../../../../internal/account/src/infra/memory.rs): one lock
   owns aggregates, mapping and required facts. Allocation/capacity/scope failures
   are checked before writes; the final rename version check happens here.
4. [Credential adapter](../../../../internal/account/src/infra/capabilities.rs):
   compatibility exports now select the shared Principal resolver, which invokes
   the credential contract and validates session evidence. Cargo has no Identity dependency.
5. [HTTP](../../../../internal/account/src/transport/http/v1/mod.rs) and
   [input/cursor policy](../../../../internal/account/src/transport/http/v1/input.rs):
   authentication precedes input, strong ETags guard writes, cursor ownership
   does not replace authorization. [Composition](../../../../internal/account/src/lib.rs)
   selects the memory adapter and receives the resolver as a port.
6. [Event mapping](../../../../internal/account/src/infra/events.rs) and
   [AccountAccess provider](../../../../internal/account/src/transport/capability/v1.rs):
   commit selected envelopes with state, answer current owner policy, and leave
   Audit receiving/storage to [its own module](../audit/README.md).

Example: ordinary create makes workspace A. Eight concurrent bootstraps select
one new personal workspace B, rather than adopting A. After B is renamed to
version two, bootstrap returns that current B even when its proposed name differs.
Two writes carrying version two then have one winner; the loser must read again.
The [application tests](../../../../internal/account/tests/application.rs) check
facts as well as state, and the process runner checks the same public outcomes.

Names stay out of facts. The store prepares a fact ID before mutation, then
commits the required record with the aggregate; a failed fact allocation leaves
neither an account nor a personal mapping. The mapping is distinct owned state,
so ordinary creates do not accidentally establish a default workspace.

Gotchas: bootstrap validates even repeat input, and can fail a required dependency
before discovering an existing mapping. Paging is live, not a stable snapshot.
No write receipt or automatic replay exists. A response interrupted after commit
is uncertain. Matching outbox envelopes now deliver independently to Audit;
local facts are not consumer receipts. All memory records disappear on exit.
Run `make backend-account-parity-test` and `make rust-check rust-test`.

Related: [capability boundary](../../pkg/capability/README.md),
[bootstrap and CAS](../../../patterns/personal-mapping-and-write-cas.md) and
[consuming builders](../../../language/consuming-builders-and-immutable-registries.md).
Next: how should membership policy extend the owner-only capability without foreign SQL?
