# Go backend foundation

Recovery follow-up, 2026-10-17: [cancel/replace setup](../../docs/blueprints/push-job-recovery.md)
adds owner-scoped cancellation, explicit replacement and direct-child readback.
Apply Notifications 0004 before an existing API starts. The source outcome remains
intact, and uncertain replacement requires explicit duplicate-risk acknowledgement.
CLI 0.11.0 includes migration/source/setup; queue selection remains opt-in.

Queue follow-up, 2026-10-17: [queued push-preview setup](../../docs/blueprints/push-jobs.md)
adds retained intents, fenced claims, bounded preflight backoff and conservative
crash recovery. Apply Notifications 0003 explicitly before an existing PostgreSQL
API starts. PUSH_JOBS_ENABLED=true additionally requires selected private provider
configuration, owned Identity and PostgreSQL; the default still starts no sender
worker. CLI 0.10.0 includes source/migration/setup, with readiness evidence in that guide.

Provider follow-up, 2026-10-17: [APNs/FCM preview setup](../../docs/blueprints/push-providers.md)
documents real outbound adapters and the optional authenticated/proof/version
preview route. Explicit private configuration is required; default startup sends
nothing. Local protocol/runtime and CLI 0.9.0 extraction checks are independent
of live cloud/device acceptance and native remote opens.

Current push follow-up, 2026-10-17: [dispatch foundation](../../docs/blueprints/push-dispatch.md)
adds a trusted internal preview use case, Identity session eligibility and
conditional invalid-target retirement through swappable ports. Source and
independent CLI 0.8.0 check/race/smoke/PostgreSQL/runner acceptance pass. The
default API selects no gateway, send endpoint or delivery worker.

A runnable runtime, reusable packages and an Account → Audit workflow with
swappable memory/PostgreSQL stores. Memory needs no running services; PostgreSQL
uses pgx v5.11.0. Identity adds owned password/session authentication through
explicit `AUTH_MODE=identity`. The module is initialized as
`github.com/0xsj/mobile-foundry/backend`; retain that import path until deliberately
renaming the backend. Go 1.27.1 is the checked toolchain.

## Run

From this directory:

```sh
make dev
```

The server defaults to `127.0.0.1:8080`. In another terminal:

```sh
curl -i http://127.0.0.1:8080/v1/health/live
curl -i http://127.0.0.1:8080/v1/health/ready
```

Both return `{"status":"ok"}` while the process is serving and selected
readiness probes succeed. Ctrl-C or SIGTERM starts a graceful drain. The native
HealthService can use `http://127.0.0.1:8080/v1` as its base; catalog composition
still selects its existing fixtures.

Configuration is read once at startup. `.env.example` documents the defaults;
`make dev` does not load `.env` implicitly. Export settings or load a trusted
local file in your shell:

```sh
HTTP_ADDR=127.0.0.1:8081 LOG_FORMAT=json make dev
```

`APP_ENV` accepts development/test/production; `LOG_FORMAT` console/json;
`LOG_LEVEL` debug/info/warn/error. Invalid values fail startup. Production mode
only selects a configuration label; deployment policy is separate from storage
and the explicitly selected authentication mode.

## Exercise Account and Audit

The default credential resolver denies authentication. To select the explicit
single-principal development fixture:

```sh
DEV_AUTH_TOKEN=local-example-token \
DEV_PRINCIPAL_ID=00000000-0000-4000-8000-000000000001 make dev
```

In another terminal:

```sh
curl -i http://127.0.0.1:8080/v1/accounts/bootstrap \
  -H 'Authorization: Bearer local-example-token' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Personal workspace"}'
curl -i 'http://127.0.0.1:8080/v1/accounts?limit=20' \
  -H 'Authorization: Bearer local-example-token'
```

Use the returned `id` in the paths below:

```sh
curl -i http://127.0.0.1:8080/v1/accounts/ACCOUNT_ID \
  -H 'Authorization: Bearer local-example-token'
curl -i -X PATCH http://127.0.0.1:8080/v1/accounts/ACCOUNT_ID \
  -H 'Authorization: Bearer local-example-token' \
  -H 'Content-Type: application/json' -H 'If-Match: "1"' \
  -d '{"name":"Renamed workspace"}'
curl -i http://127.0.0.1:8080/v1/accounts/ACCOUNT_ID/audit \
  -H 'Authorization: Bearer local-example-token'
```

Bootstrap returns 201 for the first personal account and 200 for repeats, even
after rename; it never overwrites the current name. `POST /v1/accounts` creates
additional owner workspaces independently. Use the guarded GET by ID to validate
a selected account. Selection stays on each device, with no server-global switch.
Read [account scope behavior](../../contracts/behavior/account-bootstrap.md).

Account writes commit independently of Audit delivery, which normally catches
up on the next 100 ms poll. A stale version returns 409. The fixture has no
sign-in/expiry/renewal; production use and non-loopback binding are refused.
With default memory storage, all accounts, evidence and delivery markers are
lost on process exit. PostgreSQL retains committed records and recovers pending delivery.
Read [workflow behavior](../../contracts/behavior/account-audit.md) and
[HTTP v1](../../contracts/http/account-audit-v1.md) for limits and admission.

## Use PostgreSQL

From `backend/go`:

```sh
make db-up
make migrate
make migrate-check
STORAGE=postgres DEV_AUTH_TOKEN=local-example-token \
DEV_PRINCIPAL_ID=00000000-0000-4000-8000-000000000001 make dev
```

Use the same Account/Audit requests above. The local stack binds PostgreSQL to
`127.0.0.1:58432`; Make exports its development-only `DATABASE_URL` default.
Shell settings override it. Outside Make, supply the URL explicitly. Optional
`ACCOUNT_DATABASE_URL`/`AUDIT_DATABASE_URL`/`IDENTITY_DATABASE_URL`/`NOTES_DATABASE_URL`
override it per module. Empty explicit
overrides fail admission. Server startup checks embedded migration checksums;
only `make migrate` applies them. Add numbered files rather than editing applied SQL.

`make db-down` stops the owned stack and retains its volume. Neither setup nor
tests reset existing databases. `make integration-test` uses a separate disposable
container, exercises atomicity/replay/CAS and forced API crash recovery, then
removes only that container. Docker must be available for these targets.
With installed PostgreSQL tools, run the same isolated tests without Docker:

```sh
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin make integration-test
```

Omit `POSTGRES_BIN_DIR` when `initdb`, `pg_ctl` and `psql` are on PATH. This creates
and removes its own temporary cluster, uses a random loopback port and never
connects to an existing local cluster. Trust authentication is confined to that
temporary test fixture. PostgreSQL 18.6 Docker and local 14.24 were observed.
Read [persistence semantics](../../contracts/behavior/postgres-persistence.md)
for leases, committed cursors, cancellation and uncertain write outcomes.

## Use Identity

For a memory auth workflow from this directory:

```sh
AUTH_MODE=identity make dev
```

For durable auth, apply the current migrations, then run:

```sh
make migrate
AUTH_MODE=identity STORAGE=postgres make dev
```

With the API running, register and sign in:

```sh
curl -i http://127.0.0.1:8080/v1/identity/register \
  -H 'Content-Type: application/json' \
  -d '{"login":"alex","display_name":"Alex","password":"a long local passphrase"}'
curl -i http://127.0.0.1:8080/v1/identity/sign-in \
  -H 'Content-Type: application/json' \
  -d '{"login":"alex","password":"a long local passphrase"}'
```

Use the returned access token as Bearer authorization for Account requests,
`GET /v1/identity/me` and `GET /v1/principals/me/audit`. Renew through
`POST /v1/identity/renew` with `{"refresh_token":"RETURNED_REFRESH_TOKEN"}`;
sign out through `/v1/identity/sign-out` with that same body shape. Renewal
rotates both tokens; reuse of a consumed refresh token revokes its session.
Do not blindly retry a renewal after losing its response.

Registration creates a principal only. After sign-in, use the returned access
token to call `POST /v1/accounts/bootstrap` with `{"name":"Personal"}`, or list
and validate an owned account using the requests above. Grants contain no selected
account; [native Go authorities](../../contracts/behavior/go-native-session.md) now
compose the account scope separately. On restoration/renewal they checkpoint the
rotation, then validate the stored account rather than silently choose a new default.
`IDENTITY_DATABASE_URL` overrides the common URL. The migrator includes Identity
when that URL is configured, and API startup checks the selected histories.

Default auth remains deny. Paired DEV settings retain the fixture workflow when
AUTH_MODE is omitted; explicit fixture selection is `AUTH_MODE=development`.
Fixture credentials cannot be mixed with Identity mode. Read
[Identity behavior](../../contracts/behavior/identity-sessions.md) and
[HTTP v1](../../contracts/http/identity-sessions-v1.md) for exact input, expiry,
revocation, admission and write-uncertainty policies. This is not a complete
production identity service or OAuth/OIDC server.

## Implemented layout

```text
cmd/server/                     # Signals, configuration, selected module builders
cmd/migrate/                    # Explicit migration application/read-only check
migrations/<module>/            # Embedded SQL and independent checksum histories
infra/local/                    # Owned local PostgreSQL Compose stack
internal/server/                # Listener, HTTP composition, readiness/drain lifecycle
internal/health/
  app/query/                    # Readiness query and its injected probe port
  transport/http/v1/            # Public live/ready endpoints
  module.go                     # Handler wiring
  build.go                      # Process probe → application port adapter
internal/architecture/          # AST import rules and policy regression checks
pkg/
  errors/                       # Typed conditions, wrapping, public projection
  clock/                        # Clock port and system implementation
  id/                           # Canonical UUID values and random generator port
  env/                          # Injectable environment source
  config/                       # Validated startup settings
  logger/                       # Standard slog console/JSON configuration
  httpx/                        # JSON/problem responses, input/header admission
  principal/                    # Credential resolver port; fail-closed default
  module/                       # Runtime, unit builders, registration/readiness/cleanup
  persistence/postgres/         # Pool, transaction and migration mechanics
```

Account and Audit implement the business-module shape:

```text
internal/<module>/
  domain/                       # Values, rules and transitions
  app/command/                  # Write use cases
  app/query/                    # Read use cases
  app/                          # Application-owned ports and conditions
  infra/<adapter>/              # Memory, PostgreSQL or external port implementation
  transport/http/v1/            # Wire admission and projection
  module.go                     # Inject ports into handlers
  build.go                      # Select concrete adapters
```

Dependency direction is transport/infra → application → domain. The process
selects `module.Builder` functions; their `module.Unit` values register routes,
probe readiness and release owned resources. A handler receives a narrow port,
not a provider SDK or a dependency container. Health is a runtime exemplar with
no business aggregate or write command.

Account implements create/read/rename and owner access, repeatable personal
bootstrap, owner-filtered listing, an atomic state/outbox store and a capability
provider. Audit implements admitted event
consumption, atomic evidence/inbox deduplication and scoped paginated
reads through its own AccountAccess port. `pkg/capability` supplies local routing;
`pkg/events` supplies independent memory delivery and an owned polling worker.

Each owner has memory/PostgreSQL stores selected in `build.go`/`postgres.go`.
The process supplies module-specific connection settings and migration artifacts.
Identity implements principals, Argon2id credential verification, opaque sessions,
rotation/reuse revocation, a credential capability and selected Audit facts.
Notes implements scoped paged reads, version-guarded CRUD and atomically committed
command receipts through consumer-owned AccountAccess. See
[the contract](../../contracts/behavior/remote-notes.md) and
[native setup](../../docs/blueprints/remote-notes.md). PostgreSQL receipts survive
API restart/deletion; native pending input is currently transient.
Membership management and sockets remain planned. CLI backend extraction is
implemented and independently verified; see [Go starter](../../docs/blueprints/go-starter.md).
Account rename enforces CAS and rejects Idempotency-Key; Notes requires an
admitted command key for writes. `principal.Deny` authenticates nobody.

## Planned capability catalog

The [capability catalog](CAPABILITIES.md) lists selectable Identity, Account,
Audit and optional connected/commercial modules, their data ownership and
construction order. The [module boundary policy](../../contracts/behavior/module-boundaries.md)
establishes application-owned ports, versioned synchronous capabilities,
committed integration facts and recoverable realtime hints. Start in-process;
select remote adapters when their contracts and authentication are verified.
The memory/PostgreSQL Account/Audit subset is implemented; the larger catalog, remote
adapters and CLI combinations remain planned.

## Verify and learn

```sh
make check
make test
make smoke
make db-check
make integration-test
```

`check` verifies formatting, architecture, vet and build. `test` runs the suite
with the race detector, including real loopback HTTP requests, readiness failure,
active-request draining, forced shutdown, private failure projection and the
Account/Audit workflow. `smoke` builds temporary binaries, starts only its own
loopback processes and checks HTTP/evidence, logs, restart loss and shutdown.
`integration-test` additionally runs the real disposable database suite and
compiled-process persistence/recovery checks; `test` alone skips database cases.
Identity checks cover the same store scenarios on memory/PostgreSQL, session
expiry/rotation races, rollback/leases, real HTTP scope, API crash restoration,
durable revocation/sign-out and raw-credential absence from stored rows/events/logs.
The last verification used local mode after Docker's VM filesystem became
read-only; Docker needs recovery before `db-up` can run on this host.

Read the [runtime contract](../../contracts/behavior/go-runtime.md),
[HTTP contract](../../contracts/http/runtime-v1.md), and
[Go learning order](notes/README.md). The runtime is implemented in this checkout;
the independently generated Go backend is standalone ready through CLI 0.4.0.
Native catalog integration is verified; generated native auth/data composition
remains tracked in [starter readiness](../../docs/STARTER-READINESS.md).

## Files and media

Files adds owned pending/available/deleted metadata, immutable content and atomic
command receipts behind application ports. Memory mode needs no services; with
PostgreSQL, FILES_DATABASE_URL inherits DATABASE_URL and FILES_DIRECTORY defaults
to .data/files. Retain that private directory with the database across restarts.
Migrations remain explicit. Cloud object storage and long-term cleanup are separate.

Run AUTH_MODE=identity make dev, then open Go identity and accounts → Account files
in a native catalog. Choose a gallery photo up to 10 MiB, upload it, download it
into the existing image editor or delete it. The editor displays a local preview;
it does not yet upload an edited render. Pending retry drafts last for this catalog
owner only. Use root make go-native-test for real URLSession/OkHttp file acceptance.

Read [Files construction](../../docs/blueprints/files-media.md),
[behavior](../../contracts/behavior/files-media.md) and
[HTTP v1](../../contracts/http/files-v1.md). DELETE commits inaccessibility before
object cleanup; a cleanup failure can follow a committed receipt. Retry the same
key/target/version. Receipt lookup alone does not remove bytes. Background upload,
folders/trash, processing, quotas, scanning and orphan retention are not implemented.

Notifications adds an account/principal in-app inbox, stable first-read time
and a media preference. It consumes committed Files completion independently of
Audit; disabled deliveries still retain dedup markers. Memory/PostgreSQL are
selected with the other modules. Run make migrate before starting an existing
PostgreSQL stack after this upgrade; NOTIFICATIONS_DATABASE_URL inherits DATABASE_URL.
Read [setup](../../docs/blueprints/notifications.md) and
[HTTP routes](../../contracts/http/notifications-v1.md). Native Swift/Kotlin catalogs now connect an Account inbox through exact-lease
repositories. Push registration/providers, generated native composition and
retention remain separate slices.

The [installation registry](../../docs/blueprints/installations.md) adds POST/GET/PUT/DELETE metadata routes under /v1/installations. It needs an owned Identity session plus an independent installation key. Credential v2 carries session evidence while v1 stays compatible. Run make migrate before upgrading an existing PostgreSQL API to 0002_installations.sql. Native OS token hooks, provider delivery, retention and encryption-at-rest key management remain separate.
