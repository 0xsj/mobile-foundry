# Borrowing a transaction's connection

Claim: a transaction can retain commit ownership while lending its connection to
several awaited statements without exposing database handles to application ports.

Origin/evidence: Rust 1.86 and SQLx 0.8.6, 2026-10-18; workspace Clippy/build and
owned PostgreSQL transaction acceptance.

`Transaction` dereferences to its underlying connection. With an owned local
`tx`, `&mut *tx` explicitly borrows that connection for one SQLx call. SQLx's
executor implementation accepts the mutable connection reference. Helpers can
instead accept `&mut PgConnection`; Rust deref coercion lets callers pass
`&mut tx`. Each awaited borrow finishes before the next statement or COMMIT takes
ownership. This is a sequential protocol, not independent concurrent queries on
the same connection.

Example (Excerpt):

```rust
// Excerpt: lend the connection, then consume the transaction.
let mut tx = pool.begin().await?;
sqlx::query("SELECT 1").execute(&mut *tx).await?;
tx.commit().await?;
```

This excerpt shows borrowing syntax, not the complete write policy. A real
adapter checks scope, validates domain snapshots, explicitly chooses isolation,
attempts rollback on returned pre-commit failure and preserves uncertain COMMIT.
Application traits return domain values through owned async port futures; they
never accept `PgConnection` or `Transaction`.

Gotchas: `&mut tx` and `&mut *tx` select different types for trait resolution.
Trying to use the transaction after consuming COMMIT is a compile-time ownership
error. A dropped future ends the borrow but does not instantly cancel server SQL.
Used in [shared mechanics](../modules/pkg/persistence/postgres/README.md) and
[Identity](../modules/internal/identity/README.md). Related:
[SQLx lifetime](../substrate/sqlx-transactions-and-pool-lifetime.md).
