# pgx pools and PostgreSQL execution

Pool creation, live connection checks and transaction cleanup are distinct
responsibilities that must be explicit at the module boundary.

Origin: 2026-10-13, macOS arm64, Go 1.27.1, pgx v5.11.0 and the locally available
`postgres:18.6-alpine` image. The acceptance database reports PostgreSQL 18.6.
Dependencies are pinned in [go.mod](../../go.mod)/[go.sum](../../go.sum).
The final acceptance run used installed Homebrew PostgreSQL 14.24 in an isolated
temporary cluster after Docker's VM became read-only. Both versions were observed;
this is not a claim about untested server versions.

Primary references: [pgxpool](https://pkg.go.dev/github.com/jackc/pgx/v5/pgxpool),
[transaction API](https://pkg.go.dev/github.com/jackc/pgx/v5#Tx),
[PostgreSQL locking clauses](https://www.postgresql.org/docs/18/sql-select.html#SQL-FOR-UPDATE-SHARE),
[advisory locks](https://www.postgresql.org/docs/18/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS).

## What and why

`NewWithConfig` may return before opening a connection. The shared adapter calls
`Ping` immediately so the server can refuse an unavailable dependency before
listening. Pool connections use UTC and bounded connection/statement/lock waits.
Each module owns eight possible connections; modules sharing a database do not
silently share ownership of one pool.

pgx transactions do not automatically roll back because a context was cancelled.
The transaction helper schedules rollback with a fresh bounded cleanup context.
The driver closes a connection when a real rollback fails, and pool release
destroys connections that are busy or outside the idle transaction state. Read
the helper alongside the driver contract rather than inferring cleanup from
`defer` alone. Live connection failure at commit can still be ambiguous.

`SKIP LOCKED` is suited to claiming queue records; it is not a consistent view
for ordinary account queries. Account claims under a transaction and releases
the row lock before delivery, retaining a durable lease. Migrations use a schema
advisory lock and checksums instead. Audit uses a normal row lock to serialize
committed append positions. These locks protect different resources.

Runnable acceptance command, from `backend/go` with Docker available:

```sh
make integration-test
```

## Actual use and limits

The harness creates a uniquely named PostgreSQL container with temporary storage
and a randomly published loopback port. It runs race-enabled adapter checks and
compiled API processes, and removes only its own container in `finally`.
The normal local [Compose stack](../../infra/local/compose.yaml) uses a retained
volume and fixed loopback default port; the harness never resets that volume.
Set `POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin` for the alternate
mode. It creates/stops/removes only its own cluster, with temporary trust auth and
a randomly chosen loopback port. No Docker daemon restart or sibling-stack repair
was performed. The final Docker rerun was blocked by the VM's read-only filesystem;
the same complete suite passed locally instead.

Executed checks cover migration application/drift, transactional rollback,
version contention, inbox replay, cursor scope and API SIGKILL/restart delivery.
The Account scope follow-up used PostgreSQL 14.24 to check retained schema upgrade,
deferred account/owner constraints, conflict-winner visibility, mapping/outbox
rollback and recovered personal account/listing after API crash. See
[bootstrap mechanics](../patterns/idempotent-bootstrap-and-owned-discovery.md).
`make db-check` validates Compose separately. These do not measure throughput,
power use, database failover or production role permissions. The commit-unknown
unit test injects a failed response rather than disrupting a real server commit.

Related: [toolchain history](go-runtime-toolchain.md),
[pool mechanics](../modules/pkg/persistence/postgres/README.md) and
[ownership pattern](../patterns/transactions-leases-and-committed-cursors.md).
Next: measure contention and define operational recovery before changing bounds.
