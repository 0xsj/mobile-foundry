# Process composition and owned server

Claim: the process selects configuration/adapters while the server owns the
bound listener, readiness and complete connection-task lifetime.

Origin/evidence: 2026-10-18 actual-listener/drain tests and compiled process runner.
Read [startup config](../../../../pkg/config/src/lib.rs),
[process composition](../../../../cmd/server/src/main.rs),
[server](../../../../internal/server/src/lib.rs), then
[listener tests](../../../../internal/server/src/tests.rs).

Config defaults only absent settings and rejects unimplemented adapter selections.
It exposes a redacted Debug view. Main installs the redacted panic hook and both
Unix signal handlers before binding; ordinary failure yields a nonzero exit with
only a safe classification. Server consumes self, preventing reuse by ownership.

Serving state begins false. A bound listener enters its accept loop; each Hyper
connection is owned by a JoinSet. A signal clears readiness, drops admission and
notifies those connections to drain. A separate timeout triggers abort-and-join.
A scope guard clears readiness if the entire serving future is dropped.
Server::with_ids selects the generator passed through module composition into
the real HTTP boundary. Default construction selects RandomIds; Health does not
own or read OS entropy itself.
Explicit Identity mode supplies raw module routes at process composition. The
server merges routes before installing one shared HTTP boundary and adds the
accepted socket IP to each connection's service. Identity closes its bounded KDF
work after server drain, including jobs whose request future was canceled.
Delivery now adds explicit assembly before admission: register capabilities and
subscriptions, freeze/verify requirements, start owned workers, then serve. Selected
worker/outbox probes extend serving readiness. On exit, all workers are fenced,
all joined, and then crypto closes. See [owned polling](../../../language/owned-polling-and-cancellation-safe-join.md).

The normal listener test reads the actual ready route and verifies listener
closure. The active-request test holds a handler, signals shutdown, observes
readiness false while work stays pending, then permits a complete response.
The forced test holds work indefinitely and observes both a timeout error and
its dropped handler guard after all tasks are joined. Shared process acceptance
additionally observes clean SIGTERM exit in Go and Rust.

PostgreSQL follow-up: [process composition](../../../../cmd/server/src/main.rs)
now checks migration histories and owns separate pools. Workers all fence/join,
then crypto drains, then every pool explicitly closes, including startup failure.
See [SQLx lifetime](../../../substrate/sqlx-transactions-and-pool-lifetime.md).

Recovery follow-up: configuration now selects a private SMTP file only in owned
Identity mode, with a redacted runtime view and NUL-path refusal. Module composition
starts one bounded reset worker. Its readiness joins the selected probes; shutdown
fences that queue before joining workers, drains KDF work and only then closes
pools. Startup failure follows the same resource order. Read
[recovery ownership](../identity/recovery.md) for ephemeral admission and mail limits.

Files follow-up: process composition owns a fifth Files pool/schema and an Objects
selection. Memory uses bounded ephemeral bytes; PostgreSQL uses the private
FILES_DIRECTORY. Its independent outbox joins readiness/delivery, while Audit
subscribes to File facts. After HTTP/workers/KDF drain, Directory fences admission
and joins retained blocking jobs before any pool closes, including startup failure.
Read [I/O lifetime](../../../language/owned-blocking-filesystem-jobs.md); filesystem
work may extend total shutdown beyond the HTTP-only deadline.

Notifications follow-up: the sixth pool owns in-app decisions/items/preferences.
Assembly registers its consumer before freezing the bus; existing Files workers
invoke it independently of Audit. No new timer/task owner is introduced, and all
delivery joins still precede pool closure. The six-database acceptance now writes
a file and observes its notification through a separately selected Notifications
pool. Read [the inbox walkthrough](../notifications/README.md).

Installation follow-up: the same concrete Notifications owner also supplies the
registry port. Assembly adds notifications.identity before freezing; existing
Identity outbox workers invoke its v1/v2 retirement consumer. The six-database
phase registers a target and observes retirement with Identity's tables in another
database. No foreign SQL or additional worker is selected. Read
[installation ownership](../notifications/installations.md).

Gotchas: delivery workers/pools are selected explicitly; no generic module
lifecycle registry exists. Interrupted COMMIT remains uncertain. Rust's network budgets
are explicitly narrower than Go's. Related:
[owned drain](../../../patterns/readiness-and-owned-drain.md),
[fixture technique](../../../techniques/shared-runtime-fixtures.md) and
[parity gaps](../../../../PARITY.md). Next: where should a future module's
startup failure transfer or retain resource ownership?
