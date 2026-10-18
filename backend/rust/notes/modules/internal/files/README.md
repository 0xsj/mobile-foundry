# Files metadata and immutable objects

Claim: file lifecycle commits and physical byte effects can be independently
owned without confusing a historical receipt with current download authority.

Origin/evidence: 2026-10-18 Rust 1.86 implementation, shared Go/Rust policy/wire/
fingerprint/event checks, real API transfers and disposable PostgreSQL 14.24
rollback/process-kill/language handoff. Read
[the behavior scope](../../../../../../contracts/behavior/rust-files.md).

Read the source in this order:

1. [Domain](../../../../internal/files/src/domain/mod.rs): private File state,
   normalized Spec, strict restored Snapshot, pending/available/deleted versions.
2. [Application ports](../../../../internal/files/src/app/ports.rs) and
   [write policy](../../../../internal/files/src/app/mod.rs): scoped commands,
   immutable receipts and AccountAccess/Store/Objects ownership.
3. [Named commands](../../../../internal/files/src/app/command/mod.rs) and
   [queries](../../../../internal/files/src/app/query/mod.rs): authorize before
   replay/read, verify bytes before completion, tombstone before cleanup and
   recheck lifecycle after external byte work.
4. [Memory](../../../../internal/files/src/infra/memory.rs) and
   [PostgreSQL](../../../../internal/files/src/infra/postgres.rs): replay before
   current-state checks, atomic state/receipt/outbox and per-file ordered delivery.
5. [Object adapters](../../../../internal/files/src/infra/objects.rs): immutable
   verified bytes; bounded tracked blocking jobs for private-directory effects.
6. [Persistence codec](../../../../internal/files/src/infra/wire.rs),
   [producer](../../../../internal/files/src/infra/events.rs) and
   [HTTP](../../../../internal/files/src/transport/http/v1/mod.rs): Go-compatible
   stored snapshots/fingerprints, selected facts and transport admission.
7. [Composition](../../../../internal/files/src/lib.rs) and
   [process owner](../../../../cmd/server/src/main.rs): selected adapters, separate
   pool and object-resource drain. Audit imports no Files implementation.

Example: deletion commits deleted v3 and a receipt, then object deletion fails.
The API can return an error while the file is already inaccessible. Receipt GET
proves that metadata commit but leaves cleanup alone. Retrying the same key,
file and expected v2 returns the saved receipt and repeats physical deletion.
A completion replay after deletion still returns its historical available v2
receipt; current content GET remains refused.

Verification: make backend-files-parity-test runs 24 specs, four digests, thirteen
events and 40 HTTP cases plus binary limits against both actual APIs. It checks
concurrent replay, byte PUT/GET/HEAD, account isolation, paging and Audit metadata.
[Store conformance](../../../../internal/files/tests/support/mod.rs) runs against
memory and PostgreSQL; [application tests](../../../../internal/files/tests/application.rs)
inject event/cleanup failures and corrupt a persisted object. Private object
tests cancel a caller and cancel/restart close, proving retained task ownership.
make backend-postgres-parity-test injects outbox/finalization faults, refuses an
incomplete deferred receipt and continues a killed upload across Go/Rust processes.

Gotchas: metadata and object storage are not one transaction. Unconfirmed effects
and inaccessible orphans need explicit product recovery/retention. Trust this
private directory; it is not designed for another hostile writer. MIME is declared,
not inspected. Existing Notes code is not imported: its receipts and lifecycle
have different contracts. [In-app Notifications](../notifications/README.md) now
consumes completion independently; cloud adapters and native/CLI selection remain
separate. Related:
[logical deletion and cleanup](../../../patterns/logical-deletion-before-object-cleanup.md),
[owned blocking I/O](../../../language/owned-blocking-filesystem-jobs.md),
[historical receipts](../../../patterns/historical-receipts-before-current-state.md).
Next: which independent consumer should react to upload completion?

