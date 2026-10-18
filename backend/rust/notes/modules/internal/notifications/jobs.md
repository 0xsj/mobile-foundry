# Retained intent before a non-repeatable effect

Claim: a durable send marker can make crash recovery conservative without pretending
that a database transaction and an external provider are atomic.

Origin/evidence: 2026-10-18 Rust 1.86, Tokio 1.53, SQLx 0.8.6 and disposable
PostgreSQL 14.24. The [Go jobs](../../../../../go/notes/modules/internal/notifications/jobs.md)
and [recovery](../../../../../go/notes/modules/internal/notifications/recovery.md)
walkthroughs are the reference. Read [scope/limits](../../../../../../contracts/behavior/rust-push-jobs.md), then:

1. [Domain](../../../../internal/notifications/src/domain/push_job.rs): private
   PushJob plus admitted Snapshot/Intent, typed status/code vocabularies and pure
   claim/Begin/finish/cancel/replacement transitions. State and intent formatting
   are redacted; no provider token is retained in the job.
2. [Ports](../../../../internal/notifications/src/app/push_jobs.rs),
   [enqueue](../../../../internal/notifications/src/app/command/queue_push_preview.rs),
   [recovery](../../../../internal/notifications/src/app/command/recover_push_job.rs)
   and [queries](../../../../internal/notifications/src/app/query/push_job.rs):
   immutable replay precedes a fresh target check, with scope and lineage retained.
3. [Execution](../../../../internal/notifications/src/app/command/run_push_job.rs):
   claim an original intent, decorate the existing send port with a committed
   marker, classify only owned outcomes and settle under an independent budget.
4. [Memory](../../../../internal/notifications/src/infra/memory/push_jobs.rs)
   and [PostgreSQL](../../../../internal/notifications/src/infra/postgres/push_jobs.rs):
   one admission guard for replay/capacity/child insertion, separate short row locks
   for claims/settlement/cancel and unchanged Go-compatible persisted columns.
5. [HTTP](../../../../internal/notifications/src/transport/http/v1/installations/jobs.rs),
   [facade](../../../../internal/notifications/src/lib.rs) and
   [process](../../../../cmd/server/src/main.rs): explicit routes and separately
   selected workers with Identity/proof/revision admission and health/join ownership.
6. [Generic polling](../../pkg/jobs/README.md): scheduling/lifetime mechanics without
   business SQL, status codes, credentials or retry policy.

Example: worker A commits sending, the provider processes its request, then A dies
before settlement. After the thirty-second claim expires, worker B commits
uncertain/worker_lost without another send. The user can explicitly acknowledge
possible duplication and create a linked child. B cannot infer “unsent” from the
absence of a result, and it cannot safely rewind the source to queued.

Rust's outer Result still means the store acknowledgement may be unknown.
The gateway decorator sets its AtomicBool only after Begin returns success. If
Begin committed but its acknowledgement was lost, no provider call is allowed;
the attempted preflight settlement is fenced out by the persisted sending state.
Recovery later retains uncertainty even if this conservative choice meant no alert
was ever attempted. This trades availability for avoiding an implicit duplicate.

Typed Code and Status make unknown persisted strings fail restoration. They do
not by themselves enforce valid combinations; restore additionally checks claim/
lease/attempt/time/code relationships. Pure transitions return a new admitted value,
and adapters commit it under the appropriate owner guard. No lock crosses Identity
eligibility or provider I/O. PostgreSQL SKIP LOCKED is queue contention behavior,
not a general consistent read; see [SELECT](https://www.postgresql.org/docs/14/sql-select.html)
and [row locking](https://www.postgresql.org/docs/14/explicit-locking.html).

Verification: [57 shared transitions](../../../../internal/notifications/tests/job_transitions.rs),
[common store tests](../../../../internal/notifications/tests/support/jobs.rs),
[marker/result fault tests](../../../../internal/notifications/tests/job_execution.rs)
and [HTTP without a started worker](../../../../cmd/server/tests/jobs_http.rs) run in memory.
Common store checks also run with PostgreSQL, including twelve concurrent claims,
one-child replacement races, cancellation/Begin races and exhaustion. The
[database/process owner](../../../../cmd/server/tests/support/jobs_postgres.rs)
rejects marker, result and child writes, tests retained capacity and kills its own
child after the marker plus a visible simulated file effect. A fresh connection
recovers without another effect. The
[compiled API runner](../../../../../../scripts/push_jobs_parity.py) tests 21 shared
HTTP guards and Rust→Go→Rust job/lineage handoff with actual workers. Unsupported
APNs encoding refuses locally; that test never contacts Apple or proves delivery.

Gotchas: terminal evidence counts toward the ten-thousand-row cap. No pruning or
cross-session administration is selected. Cancel cannot retract an in-flight send.
Same-ID replay is not provider deduplication, and a linked replacement is a new
intent that may duplicate an uncertain alert. Scope cancellation does not undo a
commit or effect. Rust CLI/native queue selection remains unregistered. Related:
[send markers/replacement](../../../patterns/durable-send-markers-and-explicit-replacement.md),
[known rejection](../../../patterns/known-rejection-and-unconfirmed-cleanup.md),
[providers](providers.md). Next: how should recoverable live hints notify a client
of changed job state without becoming its durable source of truth?
