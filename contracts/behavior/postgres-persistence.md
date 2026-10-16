# Go PostgreSQL persistence

Implemented 2026-10-13 behind the existing Account Store/outbox and Audit Store
ports. [Account/Audit](account-audit.md) owns domain/HTTP/access behavior;
this contract adds the durable adapter's semantics. Identity and backend CLI
extraction remain separate capabilities.

## Selection and ownership

`STORAGE=memory` is the default and needs no external service. `STORAGE=postgres`
requires `DATABASE_URL`, or both `ACCOUNT_DATABASE_URL` and `AUDIT_DATABASE_URL`.
Module URLs override the common URL. Each module owns a separate pool, schema
and migration history, even when both connect to the same database. Account SQL
never reads Audit tables and Audit SQL never reads Account tables. There are no
cross-module foreign keys. Independent URLs support separate databases; this
slice's acceptance suite exercises a shared database, not separate database roles.

Pools validate live connections at startup, use UTC, at most eight connections
each, a five-second connection/statement timeout and two-second lock timeout.
Caller cancellation still applies. These are initial development bounds, not
measured production settings. Generic pool/transaction/migration mechanics live
in `pkg/persistence/postgres`; business SQL stays with each owning adapter.
URLs and development tokens are redacted when configuration is formatted.
Driver causes stay private; process and HTTP boundaries project safe failures.
Transactions select Read Committed explicitly rather than inheriting a connection default.

## Migrations

Ordered SQL files live under `backend/go/migrations/<owner>` and are embedded
into server/migrator binaries. Only process composition imports this deployment
artifact catalog. `cmd/migrate` applies migrations explicitly; `--check` is
read-only. API startup refuses missing, changed or unsupported migration history.
It never applies migrations or resets data automatically.

Migrators serialize by schema using a transaction advisory lock. Each file and
its SHA-256 checksum commit together. A previously applied file cannot change;
add a new numbered file. If a later file fails, earlier completed files remain
applied. Account and Audit migrations also commit independently. No automatic
down migration, destructive reset or cross-module deployment transaction exists.

## Account writes and delivery

Create/rename commits Account state and one stable integration envelope in the
same transaction. A failed outbox append rolls back the state change. Rename
locks the current row and repeats the expected-version and immutable-field
checks at write time. Two competing renames admit one transition and one conflict.

The PostgreSQL outbox atomically claims one eligible record per poll using
`FOR UPDATE SKIP LOCKED`. A five-second lease belongs to the store's unique
process-local owner; one store owns one dispatcher. A different process cannot
claim it before expiry, and an obsolete owner cannot acknowledge its successor's
claim. A crashed worker's lease expires without resetting state. A pending
earlier event blocks later events for the same account; independent accounts
may progress concurrently. Audit append order is still not global business order.

Delivery stays in-process through the existing Publisher port. The 100 ms poll,
two-second cooperative attempt budget and five-failure limit remain development
policies. Failed delivery releases its lease and persists attempts/safe failure
code. Dead records remain retained and fail Account readiness. Invalid persisted
envelopes are quarantined as dead records. An expired lease permits replay,
including after consumer commit but before acknowledgment; it cannot promise
exactly-once transport. Audit's inbox supplies duplicate suppression.

## Audit commits and cursors

Evidence, the `(consumer,event_id)` inbox marker and a committed append position
share one transaction. Identical evidence replay succeeds and retains the first
recording time; changed evidence with the same consumer/event identity fails.
The inbox stores a canonical evidence fingerprint and its evidence reference.

Audit locks one append-state row through commit before allocating the position.
This intentionally serializes appends: a later visible position cannot leap
over an earlier uncommitted append and make an exclusive cursor skip it. Failed
transactions roll back position allocation along with evidence/inbox changes.
Reads stay account-filtered, ascending and bounded to 1–100 plus a lookahead row.
Persisted values are restored through domain validation; corrupt state fails.

## Cancellation, shutdown and uncertainty

Cancellation before the commit attempt rolls back using a separate bounded
cleanup context. Losing the COMMIT response returns `postgres.commit_unknown`;
the helper never automatically retries writes or claims that they rolled back.
An acknowledged PostgreSQL rejection is classified separately. Reconcile current
state after an uncertain rename. Create still has no idempotent write receipt,
so a lost response cannot safely be resolved by blind resubmission.

After HTTP drains, assembly stops and joins **all** module workers before closing
**any** module resources. Each module then closes its pool. Pending records need
not flush at exit. API SIGKILL loses in-flight process work, while committed state
and delivery records remain in PostgreSQL for recovery.

## Evidence and limits

From `backend/go`, `make integration-test` creates its own disposable PostgreSQL
18.6 container and cleans it up. It checks concurrent migration application,
checksum drift/unmigrated startup refusal, state/outbox rollback, evidence/inbox
rollback, CAS, lease recovery/stale acknowledgment, durable replay, account cursor
isolation, cancellation, dependency failure versus absence, poison quarantine,
forced API crash/restart delivery, persisted evidence and other-principal denial.
`make test` alone skips database tests unless an explicitly disposable test URL is
supplied. Memory process smoke remains a separate `make smoke` check.
`POSTGRES_TEST_MODE=local` runs the same suite against its own temporary cluster
using installed tools (`POSTGRES_BIN_DIR` or PATH). It uses temporary trust auth
and a random loopback port, stops/removes that cluster, and never reuses existing
data. Local PostgreSQL 14.24 also passed; that observation is separate from the
18.6 Docker runs. The final rerun used local mode because Docker's VM turned read-only.

The local Compose volume persists across `make db-down`/`db-up`. Tests never
reset it or touch sibling stacks. Backups, database crash/failover drills, separate
roles, retention/pruning, administrative dead-letter replay, backoff, broker
delivery and generated backend acceptance remain future work. The subsequent
[Identity/session slice](identity-sessions.md) implements owned authentication,
its schema/outbox and principal-scoped Audit migration separately.
Commit uncertainty classification has a fault-injected unit test; the suite does
not sever a live database connection at COMMIT.
