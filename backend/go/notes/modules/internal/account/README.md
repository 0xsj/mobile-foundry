# Account values, commands and atomic facts

An application-owned store can commit an Account transition and its required
integration message together while the domain remains independent of delivery.

Origin: first business slice, 2026-10-13; source inspection, race-enabled tests
and compiled-process smoke. This is memory execution, not persistent recovery.
The PostgreSQL follow-up below supplies separate durability evidence.

## Read the flow

1. [Domain](../../../../internal/account/domain/account.go): private Account,
   canonical creation/restoration, rename and selected scalar facts.
2. [Application port](../../../../internal/account/app/ports.go), then
   [Create](../../../../internal/account/app/command/create.go) and
   [Rename](../../../../internal/account/app/command/rename.go).
3. [Memory store](../../../../internal/account/infra/memory/store.go) and
   [producer mapping](../../../../internal/account/infra/events/producer.go).
4. [HTTP adapter](../../../../internal/account/transport/http/v1/handler.go) and
   [access provider](../../../../internal/account/transport/capability/v1/handler.go).
5. [Wiring](../../../../internal/account/module.go) and
   [selection/lifecycle](../../../../internal/account/build.go).

Create derives ownership from a trusted principal, admits the name, constructs
state and asks the port for one atomic commit. Rename first reads/checks access
and the supplied version, then constructs a new value. The repository repeats
the version guard under its lock. That final check matters when two handlers
both read version 1: one can commit version 2 and the other must conflict.

The domain returns detached snapshots and internal facts without names. The
producer maps a fact into the versioned contract; it is infrastructure because
wire names/data and delivery metadata are external concerns. Mapping/capacity/
admission failures happen before either state or outbox changes. Delivery runs
later; an unavailable Audit consumer does not undo Account's committed write.

Conceptual example: two renames using ETag `"1"` produce one accepted transition,
one conflict and one rename message. `Idempotency-Key` is rejected rather than
pretending repeated creates have replay semantics. A lost HTTP response remains
an uncertain observation of a potentially committed write.

## Checks, limits and questions

[Store tests](../../../../internal/account/infra/memory/store_test.go) cover CAS,
state/outbox atomicity on mapping failure, forged ownership, cancellation,
detached payloads and retry exhaustion. [Module tests](../../../../internal/account/module_test.go)
consume [portable names](../../../../../../contracts/fixtures/account/names.json)
and check normalization versus strict restoration. File fixture admission stays
at the module-root test so domain test imports retain production restrictions.

[Actual HTTP tests](../../../../tests/account_audit_test.go) and process smoke
exercise admission, versions and event evidence. `make check test smoke` passes.
The store lock proves in-process atomicity; it does not survive a crash. Owner-only
access has no invitation/member/revocation workflow. Memory capacity is bounded
and delivered outbox records are retained. Next: preserve these port semantics
in a PostgreSQL transaction and define write receipt/reconciliation behavior.

Related: [behavior](../../../../../../contracts/behavior/account-audit.md),
[composition pattern](../../../patterns/ports-and-module-composition.md),
[Audit receiver](../audit/README.md) and [event runtime](../../pkg/events/README.md).

## PostgreSQL follow-up — 2026-10-13

Read [store](../../../../internal/account/infra/postgres/store.go),
[module selection](../../../../internal/account/postgres.go) and
[owned SQL](../../../../migrations/account/0001_accounts_outbox.sql).
The same Store port now commits state/outbox inside one Read Committed transaction.
Rename locks/restores the current row and repeats its version/immutable-field checks.
A duplicate event identity fails the outbox insert and rolls back the state write.

One store owns one worker. Its durable claim covers one record, expires after
five seconds and checks its unique owner during acknowledgment. Later pending
events for an account wait for earlier delivery; different accounts may progress.
Attempts/dead status survive restarts; invalid persisted envelopes are quarantined.
Assembly stops every worker before any module pool closes.

`make integration-test` exercises rollback, competing CAS, lease expiry/stale
owners, cancelled writes and forced API crash/recovery. These are distinct from
the earlier memory smoke. Pool failure remains a failure rather than absence.
No production identity, pruning, dead-letter administrative replay or idempotent
create receipt is implemented. See [transaction/lease pattern](../../../patterns/transactions-leases-and-committed-cursors.md).
Next: reconcile uncertain create/rename receipts when connecting native mutations.

## Personal bootstrap and discovery — 2026-10-13

Read [Bootstrap](../../../../internal/account/app/command/bootstrap.go),
[List](../../../../internal/account/app/query/list.go), then the Directory
implementations in [memory](../../../../internal/account/infra/memory/directory.go)
and [PostgreSQL](../../../../internal/account/infra/postgres/directory.go).
The narrower Directory port owns repeatable personal mapping and owner-filtered
discovery independently of the existing Store mutation/access surface.

Bootstrap admits a new candidate but commits only when no mapping exists. One
winner creates mapping/account/fact atomically; repeats return current mapped
state and do not rename, adopt an existing ordinary workspace or append a fact.
[Migration 0002](../../../../migrations/account/0002_personal_accounts.sql) adds
only owned relationships/indexes; retained accounts survive without inferred
defaults. A deferred account/owner FK allows claiming the owner key before the
aggregate insert, while rejecting cross-owner mappings at commit.

Conceptual example: twelve simultaneous valid bootstraps return one ID; after
renaming that account, another bootstrap returns its new name/version. Its
candidate ID remains absent. Listing then returns this personal account alongside
ordinary owner accounts. Choosing one uses the existing owner-guarded GET; no
principal-global selection or Identity table access is introduced.

[Conformance and HTTP checks](../../../../tests/account_directory_test.go) pass on
memory/PostgreSQL, with one creation fact, isolated UUID pages, cancellation,
foreign cursors and selection denial. [Database checks](../../../../tests/integration/postgres_test.go)
pass retained upgrade, mapping/account/outbox rollback and owned FK rejection.
Compiled Identity processes recover bootstrap/list/selection after an API crash.
The complete slice acceptance ran against local PostgreSQL 14.24. Native Go
adapters and generated backend readiness remain pending.

Related: [bootstrap/discovery pattern](../../../patterns/idempotent-bootstrap-and-owned-discovery.md)
and [behavior](../../../../../../contracts/behavior/account-bootstrap.md).
Native Go adapters now preserve the stored account through checkpointed renewal.
Next: remote account data and uncertain mutation receipt/reconciliation behavior.
