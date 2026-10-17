# PostgreSQL mechanics without a business catalog

A shared persistence package can own connection, transaction and migration
mechanics while each module keeps its SQL and application ports.

Origin: PostgreSQL slice, 2026-10-13. Read
[pool/transactions](../../../../../pkg/persistence/postgres/pool.go),
[migration mechanics](../../../../../pkg/persistence/postgres/migrations.go),
[unit checks](../../../../../pkg/persistence/postgres/pool_test.go) and
[database acceptance](../../../../../tests/integration/postgres_test.go).

## What and why

`Open` parses configuration, sets development bounds/UTC and actually pings the
database. pgxpool construction alone is lazy. One owning module receives one
pool and closes it only after HTTP and every worker have drained. No domain
query or module selection belongs in this package. `Config` formats as redacted;
typed failures preserve driver causes for diagnosis without copying them to
public HTTP/process output.

`Transaction` explicitly selects Read Committed, executes the supplied adapter
work and checks cancellation before attempting commit. A deferred rollback uses
a fresh two-second cleanup context, so a cancelled request does not prevent
cleanup. There is no generic automatic retry: a network failure at COMMIT may
mean the write succeeded. `ErrCommitUnknown` retains that uncertainty. An
acknowledged PostgreSQL rejection/aborted-transaction rollback is separate.

Migration `Schema` is supplied by composition rather than loaded from a shared
business inventory. `Load` admits a safe owner name and contiguous numbered SQL
files. `Apply` locks by schema, checks catalog state after lock acquisition and
commits each SQL file with its checksum. `Check` only reads history. Embedding
deployment artifacts in the process binaries avoids dependence on terminal CWD.

Excerpt from the migration API (the caller supplies an embedded filesystem):

```go
schema, err := postgres.Load(files, "account")
if err != nil { return err }
return schema.Check(ctx, pool)
```

## Evidence and limits

`make check test` checks import direction, classification/privacy, checksum input
admission and simulated uncertain commit outcomes. `make integration-test` owns
a disposable database and exercises concurrent migrators, checksum drift,
missing-schema refusal, rollback/cancellation and the two module adapters.
Database cases deliberately skip in the ordinary suite without the disposable
test URL/flag. Only the integration target establishes actual database evidence.
Docker PostgreSQL 18.6 passed, followed by a complete local PostgreSQL 14.24 run
including the held-lock regression after Docker became unable to create containers.

A repeat acceptance run caught concurrent fresh-schema bootstrap returning a
catalog unique violation. The path now explicitly selects isolation, consumes
the lock result and checks schema existence after acquisition; the focused
regression runs four migrators against each of 24 fresh schemas. The precise
cause of the earlier intermittent behavior is WORKING; do not infer a general
PostgreSQL/pgx defect from that observation.

Statement/lock waits are bounded development settings, not production tuning.
No automatic reset, down migration, failover/backup drill, SQL role separation,
or live network-cut-at-COMMIT test is supplied. See
[behavior](../../../../../../../contracts/behavior/postgres-persistence.md),
[transaction/lease pattern](../../../../patterns/transactions-leases-and-committed-cursors.md)
and [pgx substrate](../../../../substrate/pgx-and-postgresql.md).
Next: how should a write receipt reconcile an uncertain create without replaying it?

## Pool and restore follow-up — 2026-10-17

Claim: pool limits must be assessed across selected modules because each owns a
separate pool. Config now admits MaxConns 1..128, with zero selecting the default
eight for direct package callers. Runtime configuration rejects zero; idle
connections expire after thirty minutes. Existing connection/statement/lock
budgets remain. Eight pools at eight connections can consume sixty-four database
connections; a per-module maximum is not a process-wide cap.

The current disposable suite passes on Docker PostgreSQL 18.6. Portable operations
also verifies quiesced common-database/private-files backup and an unused-project
restore, separately from transaction conformance. Earlier unverified-backup limits
above refer to the original slice. Hosted failover, role isolation and live
network-cut-at-COMMIT testing remain separate. Read
[current execution](../../../../../../../docs/blueprints/backend-connected-foundations.md).
