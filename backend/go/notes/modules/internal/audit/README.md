# Audit receiving and account access

Audit can own evidence and access orchestration without importing Account's
domain, application or repository.

Origin: 2026-10-13 implementation; source inspection and race-enabled execution
including concurrent replay and real HTTP. Evidence is memory-only.
The PostgreSQL follow-up below adds independently checked persistent storage.

## Read the two boundaries

1. [Receiving adapter](../../../../internal/audit/infra/events/consumer.go) admits
   Account integration data and maps it into Audit's own evidence input.
2. [Record command](../../../../internal/audit/app/command/record.go) constructs
   an [Entry](../../../../internal/audit/domain/entry.go).
3. [Store](../../../../internal/audit/infra/memory/store.go) commits the append
   and consumer/event marker together. Identical replay succeeds; changed
   evidence using the same identity is rejected.
4. [List handler](../../../../internal/audit/app/query/list.go) checks its own
   [AccountAccess port](../../../../internal/audit/app/ports.go) before reading.
5. [Contract client](../../../../internal/audit/infra/capabilities/account.go)
   implements that port with a generic invoker and local wire types.
6. [HTTP](../../../../internal/audit/transport/http/v1/handler.go) projects selected
   evidence and owns the account-bound opaque cursor.

One event can reach multiple consumers independently. Audit records `audit.account`
plus event ID in the same critical section as evidence. If evidence was committed
but the delivery acknowledgment was lost, replay observes the marker and does
not append again. Keeping delivery success alone would leave a crash/replay gap.

Auditing is interpretation: action is derived from an admitted message name;
names/credentials/arbitrary payloads are excluded. Source version/time and actor
remain distinct from Audit recording time and its own append sequence. The first
recording time survives replay, and append order is not global business-event order.

A capability response uses a pointer boolean so missing/null is distinguishable
from an admitted false. An access outage must not become denial. The List command
propagates that failure and never reads evidence in either failure/denial case.
Wire types stay in infrastructure; the application owns the behavior it needs.

## Evidence and limits

[Receiver tests](../../../../internal/audit/infra/events/consumer_test.go) cover
eight concurrent replays, mismatched/extra data, conflicting identity reuse and
account filtering. [Client tests](../../../../internal/audit/infra/capabilities/account_test.go)
cover missing/null decisions and unavailable providers; [query tests](../../../../internal/audit/app/query/list_test.go)
verify that failures remain failures and block storage access.
[HTTP tests](../../../../tests/account_audit_test.go) exercise owner isolation,
pagination and replay after lost acknowledgment. `make check test smoke` passes.

All inbox/evidence disappears on process exit. Owner-only policy currently has
no mutable memberships. Retention, persistent append/inbox transactions, richer
audited domains and production access policy remain future work. Next: inject a
crash after the durable Audit commit but before acknowledgment and verify one
append after restart.

Related: [event contracts](../../../../../../contracts/events/account-v1.md),
[access contract](../../../../../../contracts/capabilities/account-access-v1.md),
[Account](../account/README.md) and [capability runtime](../../pkg/capability/README.md).

## PostgreSQL follow-up — 2026-10-13

Read [adapter](../../../../internal/audit/infra/postgres/store.go),
[owned SQL](../../../../migrations/audit/0001_entries_inbox.sql) and
[selection](../../../../internal/audit/postgres.go). Evidence, inbox identity/
fingerprint and append-position allocation share one transaction. A matching
fingerprint succeeds without changing first recording time; changed evidence
using that identity fails. SQL stays within Audit; access still uses its own port.

An append-state row lock is held through commit. Using a database sequence alone
could expose a higher committed position while a lower one is still uncommitted,
letting a cursor skip late evidence. This foundation serializes appends instead.
Account-filtered reads restore domain evidence and use one lookahead row for Next.

Database acceptance observes concurrent replay, conflicting reuse, forced inbox
failure rolling back evidence, lost acknowledgment followed by durable replay,
scope/cursor isolation and append serialization. Compiled API checks force a
producer crash and observe evidence after recovery and another restart.
No production retention/access policy, multi-region ordering or throughput claim
is established. See [cursor pattern](../../../patterns/transactions-leases-and-committed-cursors.md).
Next: choose retention and administrative query policy before expanding audited domains.

## Identity follow-up — 2026-10-13

[Receiving](../../../../internal/audit/infra/events/identity.go) admits selected
Identity facts into an explicit principal scope; Account evidence still requires
its real account ID. [Personal queries](../../../../internal/audit/app/query/principal.go)
use only the authenticated subject and a subject-bound cursor. No Identity
implementation imports or credential/profile fields enter Audit.

Migration [0002](../../../../migrations/audit/0002_principal_evidence.sql) extends
the owned table while keeping applied 0001 immutable. New empty Evidence fields
are omitted from fingerprints so old Account replay identities survive; the
[compatibility check](../../../../internal/audit/infra/postgres/fingerprint_test.go)
compares the actual previous encoding. Receiver checks exercise scope/malformed
facts/replay; HTTP and PostgreSQL process checks observe safe scoped evidence
and recovered Identity facts. Account access tests continue to pass.
Read [scope distinction](../../../patterns/principal-session-and-account-boundaries.md).

## Identity lifecycle follow-up — 2026-10-17

Claim: extending domain evidence need not silently extend a closed wire schema.
The Identity receiver admits the new principal lifecycle v1 names and password
revocation v2. It maps v1/v2 revocations to the existing canonical Audit action,
but checks each schema's separate reason vocabulary. No contact, digest or hash
enters evidence. Migration 0004 extends owned evidence constraints without editing
applied SQL or changing old fingerprints. See [contract](../../../../../../contracts/events/identity-recovery-v1.md)
and [receiver compatibility tests](../../../../tests/identity_recovery_events_test.go).
Memory/PostgreSQL lifecycle tests verify required-fact rollback and safe evidence.
Next: retain schema-specific admission when another authentication method adds facts.
