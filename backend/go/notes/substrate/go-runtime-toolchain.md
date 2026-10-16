# Go runtime toolchain and execution evidence

The standard library is sufficient for the first HTTP runtime, while process
execution and adapter conformance are separate forms of evidence.

Origin: 2026-10-12, macOS arm64, local `go version go1.27.1 darwin/arm64`.
The initial runtime used Go 1.27.1 without third-party dependencies; the
PostgreSQL follow-up adds [pgx v5.11.0](pgx-and-postgresql.md).
Primary references: [ServeMux](https://pkg.go.dev/net/http#ServeMux),
[Shutdown](https://pkg.go.dev/net/http#Server.Shutdown),
[structured logging](https://pkg.go.dev/log/slog).

## What and why

The server uses ServeMux, http.Server and slog. Shared Methods dispatches HTTP
methods so refused methods use the same problem projection. ServeMux still owns
path matching and canonicalization. Readiness is a query port rather than a
library-specific health framework or hard-coded database ping.

The API uses 5/10/20/60-second read-header/read/write/idle budgets, a cooperative
15-second handler context and ten-second graceful drain. These are initial
runtime settings, not measured device/network performance budgets. Shutdown
drains active requests; request handlers must still honor their contexts.

## Observed checks

`make check test`: formatting, architecture, vet, build and race-enabled tests
passed. Tests opened actual loopback listeners and exercised probe failure,
private problems, active-work drain and forced deadline.

A separately compiled server passed live/ready, correlation/request metadata,
HEAD body absence, 404/405 problem projection, JSON log parsing and SIGTERM exit 0.
Invalid LOG_FORMAT exited 1 before binding. The temporary binary was
`/private/tmp/mobile-go-runtime`; no server was left running. Python's local
smoke listener required sandbox approval; the first restricted bind was blocked,
then the approved loopback check passed. This is host permission behavior, not
an API failure.

Root `make notes-check` verifies local documentation links and mirrored notes;
it does not execute examples or validate external documentation. No database,
auth provider, native connection, CLI backend extraction or production deployment
was exercised. Revisit budgets and lifecycle when those resources are added.

Related: [process ownership](../modules/cmd/server/README.md) and
[HTTP helpers](../modules/pkg/httpx/README.md).

## Account/Audit execution — 2026-10-13

The same Go 1.27.1 toolchain builds the memory business modules without third-party
dependencies. `make check test` passes architecture/vet/build and all race-enabled
suites. Actual loopback tests cover owner scope, ETag conflicts, pagination,
capability failures and event replay after an acknowledgment loss.

`make smoke` executes [the reusable process probe](../../scripts/smoke.py), which
builds a temporary binary and starts/stops only its own loopback processes. It
passed denying-default auth, explicit fixture create/read/rename/Audit, metadata,
HEAD, stale conflict, secret/content-free JSON logs, SIGTERM exit 0, production
fixture refusal and expected absence of memory accounts after restart. No server
or temporary binary remains. No escalation was needed for this observed run.

This validates local process behavior, not durable recovery, a real Identity
authority, native integration or backend starter extraction. Read
[Account](../modules/internal/account/README.md),
[Audit](../modules/internal/audit/README.md) and
[delivery lifetime](../modules/pkg/events/README.md) for their ownership checks.
