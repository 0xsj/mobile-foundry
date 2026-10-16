# Go backend learning notes

Follow [the shared notes workflow](../../../docs/NOTES.md). Runtime/shared packages
and Identity/Account/Audit memory/PostgreSQL modules are implemented. Notes distinguish
source boundaries, verified execution, and future provider behavior.

Use `modules/<source-path>` for walkthroughs relative to the Go root, such as
`modules/pkg/errors/` or `modules/internal/identity/app/command/`. Put
transferable Go mechanics in `language`, architectural approaches in `patterns`,
domain distinctions in `concepts`, verification methods in `techniques`, and
versioned tool or dependency findings in `substrate`.

## Reading order

1. [Backend setup and layout](../README.md) and [runtime contract](../../../contracts/behavior/go-runtime.md).
2. [Process startup and drain](modules/cmd/server/README.md).
3. [Go interfaces, errors and contexts](language/interfaces-errors-and-contexts.md).
4. [Assembly and cleanup](modules/pkg/module/README.md), then
   [ports and composition](patterns/ports-and-module-composition.md).
   Its integration-events section traces the proposed Identity/Account → Audit
   seam against Bento's actual producer, receiving and outbox/inbox design.
   The client-realtime section separates durable delivery, ephemeral broadcast
   and a selectable WebSocket gateway, including native lifecycle limits.
   Its synchronous-capabilities section follows a consumer-owned port through
   local/remote routing and explains failure versus a negative answer. Read the
   [capability catalog](../CAPABILITIES.md) and
   [established boundary policy](../../../contracts/behavior/module-boundaries.md)
   alongside it; the implemented memory subset is described in the module notes below.
5. [Failure projection](modules/pkg/errors/README.md).
6. [HTTP admission and responses](modules/pkg/httpx/README.md), then
   [Health query/transport](modules/internal/health/README.md).
7. [Account values and atomic facts](modules/internal/account/README.md), then
   [Audit receiving/access](modules/internal/audit/README.md).
8. [Local capabilities](modules/pkg/capability/README.md) and
   [outbox delivery/worker lifetime](modules/pkg/events/README.md).
9. [PostgreSQL mechanics](modules/pkg/persistence/postgres/README.md),
   [explicit deployment](modules/cmd/migrate/README.md), then
   [transactions, leases and committed cursors](patterns/transactions-leases-and-committed-cursors.md).
10. [Toolchain history](substrate/go-runtime-toolchain.md) and
    [pgx/PostgreSQL execution](substrate/pgx-and-postgresql.md).
11. [Identity orchestration](modules/internal/identity/README.md),
    [principal/session/account distinction](patterns/principal-session-and-account-boundaries.md),
    then [Argon2/opaque credentials](substrate/argon2-and-opaque-session-credentials.md).
12. [Idempotent bootstrap and owned discovery](patterns/idempotent-bootstrap-and-owned-discovery.md),
    then Account's latest source walkthrough. Compare a stable personal mapping,
    live UUID listing and device-owned selection with generic write receipts.

13. Read [Notes commands/queries and receipts](modules/internal/notes/README.md),
then [shared acknowledgement recovery](../../../notes/patterns/command-receipts-and-uncertain-writes.md).
Memory/PostgreSQL conformance, atomic receipt-finalization rollback, CAS/replay
and killed API recovery now pass. Notes stays a replaceable exemplar; native
durable outboxes, Notes facts/live hints and backend extraction remain separate.

## Current coverage — 2026-10-13

Reading step 14: [Go source-copy verification](../../../notes/techniques/go-source-copy-verification.md)
and [CLI walkthrough](../../../notes/modules/cli/README.md). Independently generated
Go APIs now pass imported-module policy, memory/PostgreSQL and runner acceptance;
CLI 0.4.0 marks Go standalone ready. Native connected profiles remain separate.

Go 1.27.1: formatting, AST import checks, vet, build and race-enabled tests pass.
Runtime tests use real loopback HTTP, readiness failures, graceful active-request
draining and forced timeout. A compiled-process smoke check verifies live/ready,
metadata, HEAD, 404/405 problems, JSON logging and SIGTERM exit 0. Invalid log
configuration exits 1 before listening. Root `make notes-check` validates links
and mirrored paths separately.

Account/Audit checks additionally cover canonical names/restoration, atomic
memory state/outbox, concurrent CAS, private fact projection, capability admission/
caller/deadline policy, dependency failure versus denial, concurrent Audit replay,
scoped pagination and delivery acknowledgment loss. Worker checks cover bounded
deadline retries, cancellation and joining. `make check test smoke` passes;
compiled processes verify opt-in credentials, owner HTTP/evidence, stale versions,
private JSON logs, SIGTERM and expected memory loss after restart.

PostgreSQL execution now additionally checks explicit migration guards,
concurrent schema bootstrap, state/outbox and evidence/inbox rollback, leases,
stale acknowledgment, durable replay, cursor isolation, cancellation, poison
quarantine and dependency failure versus absence. Compiled processes verify
forced API crash/recovery, persisted evidence and other-principal denial.
`make integration-test` establishes database evidence separately from the normal
suite, which skips database cases without a disposable URL/flag.
PostgreSQL 18.6 Docker runs passed; the final full rerun used local PostgreSQL 14.24
because Docker's VM became read-only. Compose configuration validates separately.

Identity now implements real password/session credentials, a versioned resolver
and principal-scoped Audit. Security/domain/HTTP and memory/PostgreSQL conformance
checks cover expiry, rotation races, reuse revocation, stable absolute lifetime,
rollback/leases, scope/cursor admission and credential privacy. Compiled API checks
observe issued sessions after SIGKILL, durable renewal/revocation/sign-out, recovered
Audit facts and no raw credentials in owned rows/events/logs. This Identity batch
ran against local PostgreSQL 14.24; no new Identity 18.6 run is claimed.

Account scope now passes twelve-way bootstrap contention on both adapters,
one creation fact, no ordinary-account adoption/orphans, current renamed state,
owner-filtered UUID pagination and cancellation. HTTP verifies foreign cursors,
HEAD and guarded selection. PostgreSQL checks retained 0001 migration upgrade,
mapping/state/outbox rollback and account/owner FK rejection. Compiled processes
recover the personal mapping and list/select workspaces through actual Identity
credentials after SIGKILL. This full rerun used isolated local PostgreSQL 14.24.

Native Go adapters now compose the public Identity/Account contracts, with real
URLSession/OkHttp acceptance and separate catalog/native-vault observations.
Read [the cross-platform checkpoint pattern](../../../notes/patterns/deployment-authorities-and-rotation-checkpoints.md).
These new native checks use an owned memory API; they do not replace PostgreSQL
recovery evidence or establish a generated connected profile.

Remote routing, Realtime, remote Notes and backend CLI generation remain pending.
Next: remote account data/receipts and independently generated setup. Database failover, role isolation,
operational replay/retention and actual network-cut commit uncertainty remain
unverified. Documentation checks validate links and mirrored paths separately.
