# Transactions, leases and committed cursors

Durable delivery needs separate boundaries for producer commit, work ownership,
consumer commit and acknowledgment; none can replace the others.

Origin: memory Account/Audit ports and PostgreSQL implementation, 2026-10-13.
The package split was informed by Bento's
[pool](../../../../../bento/backend/go/pkg/persistence/postgres/pool.go) and
[transaction bounds](../../../../../bento/backend/go/pkg/persistence/postgres/transaction.go),
then implemented against this repository's own ports and acceptance cases.

## What and why

Account writes state and its stable event in one transaction. Publishing directly
from the command would let one succeed while the other fails. The worker claims
committed work with a lease, performs delivery outside the claim transaction and
acknowledges later. A crashed process cannot permanently own the record; a
successor waits for expiry and uses the original event identity.

Expiry is not proof that an old worker stopped. Two consumers may still observe
the same message if work overlaps after expiry. Audit therefore commits evidence
and its consumer/event inbox marker together. Repeating identical evidence is
success; reusing the identity for changed evidence is a failure. Stable identity
and atomic deduplication matter more than pretending transport is exactly once.

Conceptual example: Account commits rename/event → Audit commits evidence/inbox
→ Account crashes before acknowledgment → a new owner reclaims the original
event → Audit observes the marker → acknowledgment completes with one evidence
entry and its original recording time.

The account adapter uses one five-second claim per poll, covering the normal
two-second cooperative delivery budget. One store owns one dispatcher, while
different process stores have distinct owner IDs. Acknowledgment checks owner;
an earlier pending record blocks later records for the same account. Delivered
and exhausted records remain retained. The current throughput and retry policy
are foundations to measure and evolve, not a scalable queue claim.

## The cursor problem

A PostgreSQL sequence reserves numbers before transaction commit. Imagine one
transaction reserving 10, another committing 11, and a reader advancing past 11
before 10 becomes visible. An exclusive append cursor would skip the late entry.
Audit instead locks an append-state row through allocation and commit. Appends
serialize, readers observe committed prefixes and rollback leaves allocation
unchanged. This trades append throughput for straightforward cursor semantics.
It does not establish global business-event ordering.

The [Audit SQL](../../migrations/audit/0001_entries_inbox.sql) and
[adapter](../../internal/audit/infra/postgres/store.go) show this boundary;
[Account SQL](../../migrations/account/0001_accounts_outbox.sql) and
[adapter](../../internal/account/infra/postgres/store.go) show producer ownership.

## Checks, gotchas and actual use

[Database acceptance](../../tests/integration/postgres_test.go) observes state/
outbox and evidence/inbox rollback, two competing CAS writes, lease expiry,
stale-owner rejection, committed-evidence replay and serialized append/cancellation.
[Process acceptance](../../scripts/postgres_integration.py) forces SIGKILL after
Account writes while Audit recording is unavailable, then observes restored
state and pending delivery in a new API process. It also restarts after delivery
and verifies stable evidence plus other-principal denial.

No administrative replay/pruning/backoff policy or database crash/failover drill
is implemented. Five failed attempts can dead-letter a transient outage; readiness
reports that condition but does not repair it. Commit-response loss is explicitly
uncertain and writes have no automatic replay/idempotency receipts.

Related: [module ports](ports-and-module-composition.md),
[persistence walkthrough](../modules/pkg/persistence/postgres/README.md) and
[behavior](../../../../contracts/behavior/postgres-persistence.md).
Next: select retention, replay and idempotent receipt rules before extracting a
production worker or a generic synchronization engine.
