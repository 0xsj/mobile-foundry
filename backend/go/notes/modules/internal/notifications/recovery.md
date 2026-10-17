# Owned job cancellation and replacement

Claim: recovery changes only owned execution state or inserts a new linked intent;
it never rewrites an earlier external outcome. Origin: recovery slice, 2026-10-17.

## Read the source

1. [Domain recovery](../../../../internal/notifications/domain/push_job_recovery.go)
   admits cancellation before sending and replacement only for eligible terminal states.
2. [Commands](../../../../internal/notifications/app/command/recover_push_job.go)
   validate current actors/target leases and retain same-ID replay semantics.
   [Lookup](../../../../internal/notifications/app/query/get_push_job_replacement.go)
   reads the owned direct child.
3. [Memory](../../../../internal/notifications/infra/memory/push_job_recovery.go)
   and [PostgreSQL](../../../../internal/notifications/infra/postgres/push_job_recovery.go)
   serialize cancellation with Begin and atomically admit one child per source.
   No Identity or provider call occurs inside those critical sections.
4. [HTTP](../../../../internal/notifications/transport/http/v1/push_job_recovery.go)
   proves current auth/installation proof, admits an explicit boolean and retains
   the existing eight-field public job projection. Private lineage/acknowledgement
   do not become extra fields in the prior status DTO.
5. [Migration](../../../../migrations/notifications/0004_push_job_recovery.sql)
   preserves old roots and adds a nullable self-reference, default-false flag and
   partial unique index. These are relationships inside Notifications only.

## Verification and mechanics

[Conformance](../../../../tests/push_job_recovery_test.go) parks a worker before
and after the marker, exercises twelve competing replacement IDs, foreign owners,
changed input, immutable source state and lost commit acknowledgements.
[HTTP cases](../../../../tests/push_job_recovery_http_test.go) use real Identity
with a fake provider and reject ambiguous acknowledgement JSON.
[Rollback/reopen checks](../../../../tests/push_job_recovery_postgres_test.go)
and [old-row upgrade](../../../../tests/push_job_recovery_migration_test.go)
separately verify storage behavior. The upgrade uses a second owned disposable
database with only migrations 0001–0003 before applying 0004; it does not downgrade
the active test schema or touch retained development data.

A unique partial index protects the source relationship even with concurrent
callers; the self-reference keeps a child attached to retained source evidence.
See primary PostgreSQL [partial-index](https://www.postgresql.org/docs/current/indexes-partial.html)
and [constraint](https://www.postgresql.org/docs/current/ddl-constraints.html) mechanics.
These choices mean a future pruning policy must account for related rows.

Read [replacement reasoning](../../../patterns/recovery-preserves-the-original-outcome.md),
[boolean presence](../../../language/explicit-json-boolean-presence.md) and
[setup/exact evidence](../../../../../../docs/blueprints/push-job-recovery.md).
Cross-session administration, erasure, live delivery and a native recovery screen
remain separate. What should an operator see after both source and child become uncertain?
