# Go runtime foundation

Established 2026-10-12 for runtime/shared packages; extended 2026-10-13 by the
[Account/Audit workflow](account-audit.md) and
[PostgreSQL persistence](postgres-persistence.md) and
[Identity/sessions](identity-sessions.md).
[HTTP v1 runtime](../http/runtime-v1.md) owns the baseline wire behavior.

The process validates one environment snapshot, constructs logging, clock/ID
primitives and explicitly selected module builders, then binds its listener.
Default memory selection needs no running services. PostgreSQL builders receive
explicit connection/migration configuration; business handlers never read the
environment or receive a dependency container.

Application code owns its ports; adapters implement them and module composition
selects implementations. AST checks protect shared-package isolation, module
ownership, and domain/application/adapter/transport import direction. They do
not establish runtime access, SQL isolation or provider conformance.

Go uses ordinary `(value, error)` and `error` returns. Classified errors keep
private diagnostics, preserve cause/condition identity through wrapping, and
expose public messages only when deliberately supplied. Projection strips
internal/unknown codes, text, fields and causes. Joined failures have no single
public condition when multiple failures are present; a single cleanup-wrapped
failure retains its safe projection. Mixed/unclassified joins become internal. Validation fields
are detached copies. Business conditions belong to their module.

`module.Assemble` builds in order; successful builders transfer ownership. A
failed builder cleans its own partial resources. Earlier units close in reverse
order on startup failure; cleanup executes once. Duplicate/invalid registrations
and route conflicts fail startup. Providers/subscriptions register and declared
capability requirements resolve before registries freeze and workers start.
A partly registered mux/runtime is discarded.
Readiness probes cooperate with their context; no configured dependencies means
there is nothing additional to probe.

The server runs once. Readiness starts false, becomes true when a bound listener
is handed to Serve, and becomes false before drain. Health readiness combines
that state with selected unit probes. Live means the request can be served;
ready means currently serving with passing probes. PostgreSQL units probe their
owned dependencies and Account also reports exhausted/quarantined delivery.

SIGINT/SIGTERM starts a ten-second graceful shutdown. Active requests retain
their context during drain. Module resources close after successful drain.
On drain timeout, HTTP connections close and the process returns failure;
adapter cleanup is skipped so resources are not closed under potentially running
handlers. Account's worker starts after module registration and is cancelled/
joined in the assembly Stop phase, after HTTP drains and before any resource Close.
Its context is separate from
the process signal context so in-flight handlers can still commit during drain.

Clock and ID generation are injectable. IDs are canonical lowercase nonzero
UUID strings; default generation uses cryptographic randomness with v4/variant
bits. Principal resolution is a port with a denying default and an explicitly
selected loopback development fixture. An ID alone does not authenticate or
authorize a caller. Explicit Identity mode resolves opaque access credentials
through its versioned capability and current session state; fixtures remain separate.

Tests cover import policy rejection, failure privacy/ownership, JSON/header
admission, readiness, real loopback requests and graceful/forced drain. Compiled
process smoke checks cover startup, responses and SIGTERM exit. Authentication,
native connection, deployment and generated backend starter verification remain
separate work. PostgreSQL acceptance adds migration/atomicity/replay and API
crash/restart evidence; database crash/failover and live uncertain commit drills
remain unverified.
