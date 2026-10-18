# SQLx transactions and pool lifetime

Claim: dropping a Rust database future ends local polling, while database cleanup
and commit certainty still need explicit ownership.

Origin/evidence: SQLx 0.8.6 on Rust 1.86, inspected and executed 2026-10-18 with
PostgreSQL 14.24. Primary references:
[Transaction](https://docs.rs/sqlx/0.8.6/sqlx/struct.Transaction.html) and
[Pool](https://docs.rs/sqlx/0.8.6/sqlx/struct.Pool.html).

A transaction owns a checked-out connection. `commit(self)` and `rollback(self)`
consume that owner, so Rust prevents accidentally using it afterward. Transaction
Drop starts rollback. PostgreSQL's SQLx implementation queues that work; it does
not synchronously finish it or cancel the server statement when a Tokio timeout
drops the future. Connection reuse must finish queued protocol work first.

Example (Conceptual): insert a record, await a slow statement, and time out before
calling COMMIT. The transaction drops with the future. A subsequent query through
a one-connection pool sees no inserted record once the queued rollback finishes.
This is different from timing out during COMMIT: the server may already have
committed, so readback is required before deciding whether to try another command.

Returned application errors can explicitly await rollback under an independent
cleanup timeout. Request cancellation must not cancel that cleanup budget too.
Transport loss at COMMIT is uncertain; a local error cannot manufacture evidence
that the database rolled back. SQLSTATE classes for acknowledged rejection can be
classified separately. No transparent transaction retry is introduced.

Pool clones refer to the same shared pool. Dropping a clone is not a graceful
shutdown protocol. `close().await` fences new acquires and waits for checked-out
connections. Stop and join every consumer/producer task first; otherwise closing
one pool can strand work that still needs another owner's pool.

Used in [PostgreSQL primitives](../modules/pkg/persistence/postgres/README.md)
and [the explicit migrator](../modules/cmd/migrate/README.md). Acceptance includes
one-connection reuse after a dropped transaction and rollback when a scope is
canceled before COMMIT. It does not sever a live COMMIT connection or prove HA
failover behavior. Related: [owned polling](../language/owned-polling-and-cancellation-safe-join.md).
