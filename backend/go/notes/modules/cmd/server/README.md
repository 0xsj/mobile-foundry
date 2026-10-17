# Process startup and drain

The process owns configuration, selected modules and HTTP lifetime; handlers
receive ports rather than process state.

Origin: runtime-only batch, 2026-10-12. Source inspection and executed Go checks
support [runtime behavior](../../../../../../contracts/behavior/go-runtime.md).

## Reading order

1. [main.go](../../../../cmd/server/main.go): signal context, validated config,
   concrete primitives and explicit builder list.
2. [configuration](../../../../pkg/config/config.go): injected environment access.
3. [logger](../../../../pkg/logger/logger.go): standard slog handler selection.
4. [server](../../../../internal/server/server.go): routes, listener and drain.
5. [runtime tests](../../../../internal/server/server_test.go): actual HTTP and
   active work during shutdown.

`run` validates configuration before binding. As of 2026-10-13 its explicit
builder list selects Account and Audit; Health is assembled by server composition
with a process readiness probe. Runtime Clock/IDs, capability/event registries
and principal resolver support composition rather than arbitrary service lookup.
Business handlers receive narrow ports, not Runtime.

A signal cancels the process context. Serve clears readiness, then uses a new
bounded shutdown context so cancellation does not instantly abort active work.
Successful drain closes units in reverse build order. A timed-out drain forces
HTTP connection closure, returns failure and leaves adapter cleanup untouched;
closing a database under active handlers would violate ownership. The executable
then exits. Account owns a polling worker, started after registration and cancelled/
joined during module cleanup. Audit registers its independent event subscription.
The process signal does not cancel the worker before active HTTP requests drain.

## Verification and limits

`make check test` passes. Tests cover startup-not-ready, probe failure, real
loopback requests, draining an active handler before cleanup, and forced deadline.
A compiled executable separately passed health/metadata/HEAD/404/405, JSON logs,
SIGTERM exit 0 and invalid configuration exit 1. The business smoke additionally
checks opt-in fixtures, Account/Audit HTTP, private logs, production refusal and
memory loss after restart. PostgreSQL follow-up: `STORAGE` selects memory or
module-owned pools through explicit builders. Startup checks embedded migrations;
database acceptance checks durable writes, forced API crash/recovery, replay and
other-principal denial. All workers stop before any pool closes. Process failure
output uses public projection so driver causes/URLs stay private. No native
connection, real Identity, deployment or CLI extraction was exercised.

Read [language mechanics](../../../language/interfaces-errors-and-contexts.md)
and [toolchain evidence](../../../substrate/go-runtime-toolchain.md). Next: which
resources would a database module own, and when may its cleanup run?

Identity follow-up, 2026-10-13: `AUTH_MODE` selects deny, development fixture or
owned Identity. Identity mode composes its versioned Resolver before registry
assembly, selects its module/store and checks the API-owned required capability
before listening. It cannot mix fixture credentials with real auth. Each selected
module retains its own pool/lifecycle. Compiled PostgreSQL probes verify sessions,
rotation/reuse and revocation across process exits without raw-secret logs.
Native authorities now connect through the established Account contract; see
[integration](../../../../../../contracts/behavior/go-native-session.md).
Next: remote data and independently generated backend/native acceptance.

## Portable operations follow-up — 2026-10-17

Claim: startup must own every listener and background resource, including those
outside the ordinary HTTP request lifecycle. The builder list now also includes
Notes, Files, Notifications and Realtime. The process supplies a local Signals
hub and optional private metrics listener. A metrics bind failure refuses startup;
later listener failure cancels the process. Realtime Stop joins hijacked sockets
before pools close. The SMTP reset worker has the same ownership boundary.

The explicit trusted-proxy wrapper accepts one forwarded client address only
from configured CIDRs; untrusted peers cannot choose the rate-admission key.
The portable Caddy edge overwrites that header. Configuration admits an explicit
per-module PostgreSQL pool bound, default eight connections per module, rather
than letting every module use an unreviewed driver default.

Source checks, private metrics/proxy tests and owned container acceptance support
these claims. Read [operations and evidence](../../../../../../docs/blueprints/backend-connected-foundations.md).
Public certificates, actual host sizing and external monitoring remain deployment
work; a local acceptance run does not establish those outcomes.
