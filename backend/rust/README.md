# Rust backend

An independent implementation of the Go backend's contracts, starting with the
runtime, Health, Identity/Account, independent Audit, receipt-backed Notes and
Files/Media, in-app Notifications and session-bound installations in memory or
PostgreSQL. Go remains the reference implementation.
Read the
[parity inventory](PARITY.md) for the exact implemented scope and remaining gaps.
This is a registered **standalone backend starter** through CLI 0.19.0.
Identity/Account run through versioned credential capabilities and durable events.
[Independent generated acceptance](../../docs/blueprints/rust-starter.md) covers
locked build/tests, memory APIs, PostgreSQL recovery and fresh Docker operations.

CLI 0.20.0 adds [explicit Rust-connected Daily](../../docs/blueprints/rust-connected-starter.md)
for Swift/Kotlin. It reuses native session/accounts/Notes ports and has its own
generated screen/process acceptance. Backend co-location alone stays unconnected.

```sh
mobile-foundry init canopy --platform none --backend rust
cd canopy
make rust-check rust-test rust-smoke
make rust-dev
```

The optional --rust-package changes the API package; binary/reusable crate names
stay stable. Native co-location is independent; rust-connected has separate
generated acceptance above. Earlier slice-local CLI/native-pending limits below
are superseded by the corresponding registered profile's evidence; public hosting
and full Go parity remain separate.

Requires Rust/Cargo 1.86 or later on macOS/Linux. The workspace uses edition
2024, resolver 3 and an included dependency lock. No database or external service
is needed for the default memory profile. PostgreSQL is an explicit selection.

From the repository root:

```sh
make rust-check
make rust-test
make backend-parity-test
make backend-identity-parity-test
make backend-account-parity-test
make backend-audit-parity-test
make backend-notes-parity-test
make backend-recovery-parity-test
make backend-files-parity-test
make backend-notifications-parity-test
make backend-installations-parity-test
make backend-push-parity-test
make backend-push-providers-parity-test
make backend-push-jobs-parity-test
make backend-realtime-parity-test
make backend-operations-parity-test
HTTP_ADDR=127.0.0.1:8081 make rust-dev
```

```sh
curl -i http://127.0.0.1:8081/v1/health/live
curl -i http://127.0.0.1:8081/v1/health/ready
```

Stop with Ctrl-C. SIGINT/SIGTERM closes readiness before draining active HTTP
connections for up to ten seconds. The server owns and joins its connection
tasks; a forced drain returns failure. Identity additionally waits for admitted
KDF calculations. Delivery/reset workers stop/join, crypto and tracked object I/O
drain, then all selected PostgreSQL pools close. Blocking disk work can outlast
the HTTP-only drain budget.
A generic module lifecycle registry is not selected.

## Layout

```text
cmd/server/          # Process configuration, signal wiring and composition
cmd/migrate/         # Explicit apply/read-only check command
migrations/          # Embedded owner histories, compatible core Go checksums
pkg/persistence/     # PostgreSQL pool/transaction/migration/lease mechanics
pkg/kernel/          # Failures, business clock and canonical/fallible ID ports
pkg/config/          # One validated startup snapshot
pkg/httpx/           # Problems, metadata, bounded JSON, version/key admission and safe logs
pkg/capability/      # Immutable assembly, callers, bounded versioned invocation
pkg/principal/       # Shared trusted credential edge port/adapter
pkg/events/          # Envelopes, independent bus, outbox port and owned workers
pkg/jobs/            # Cooperative serial polling, health and join ownership
pkg/telemetry/       # Bounded method/status observations and private metric routes
pkg/broadcast/       # Bounded one-slot dirty subscriptions; no durable history
internal/health/     # Application query/port and transport/http/v1 adapter
internal/identity/   # Domain, commands/query, ports, memory/security and HTTP v1
internal/account/    # Domain, commands/query, owned state/outbox and access provider
internal/audit/      # Selected evidence, inbox, scoped queries and receiving adapters
internal/notes/      # Account-scoped records, command receipts and memory/PostgreSQL stores
internal/files/      # Lifecycle/receipts/outbox and immutable memory/private-directory bytes
internal/notifications/ # In-app delivery decisions, scoped inbox, read state and preferences
internal/realtime/   # Account-authorized Feed/query and owned native WebSockets
internal/server/     # Listener, serving state and owned connection drain
notes/               # Source walkthroughs and Rust learning notes
```

Business modules will use `domain`, `app/command`, `app/query`, consumer-owned
ports, `infra/<adapter>` and `transport/<protocol>/<version>` inside their own
crate. Keep synchronous capabilities, durable integration facts and recoverable
live hints separate, following the existing
[module boundary policy](../../contracts/behavior/module-boundaries.md).
Identity, Account, Audit, Notes, Files and Notifications exercise that layout. Synchronous questions use
Invoker; committed facts use Publisher/outbox/inbox. Shared packages never import
business crates, and business crates never import each other.

The supported startup subset matches Go defaults: `APP_ENV=development`,
`HTTP_ADDR=127.0.0.1:8080`, `LOG_FORMAT=console`, `LOG_LEVEL=info`,
`STORAGE=memory`, `AUTH_MODE=deny`. Environment accepts development/test/production;
logs accept console/json and debug/info/warn/error. Addresses currently require a
literal IPv4 or bracketed IPv6 address and a nonzero port. Present empty required
settings fail validation. Development fixture credentials remain unsupported.
Private metrics and trusted proxies are explicit selections described below.
Manual providers and durable jobs have the explicit selections below.
`AUTH_MODE=deny` does not supply an Identity implementation;
the default Rust API exposes public Health and mounts Account/Audit/Notes with deny authentication.

Select the memory Identity core explicitly:

```sh
AUTH_MODE=identity HTTP_ADDR=127.0.0.1:8081 make rust-dev
```

It supports `/v1/identity/register`, `sign-in`, `renew`, `sign-out` and `me` with
the existing [Identity wire contract](../../contracts/http/identity-sessions-v1.md).
Registration returns a profile, then sign-in explicitly issues a grant; no account
is created. In memory mode credentials disappear on restart; PostgreSQL preserves them.
Password hashing uses Go-compatible Argon2id parameters; access/refresh tokens
are opaque random secrets with stored digests. CPU work is bounded and drained
after HTTP shutdown. Recovery is described below; native Rust profile and CLI
selection are not included. Read the
[implemented scope](../../contracts/behavior/rust-identity.md) and
[Identity learning notes](notes/modules/internal/identity/README.md).

### Recovery and private mail

Identity also supports verified email contacts, password reset and authenticated
password change through the [recovery contract](../../contracts/behavior/rust-recovery.md).
Password mutations atomically consume challenges, revoke sessions and commit
Audit facts. Anonymous reset returns generic queue admission; mail delivery is
ephemeral and asynchronous. Mail defaults to disabled. To select SMTP, set
`IDENTITY_MAIL_CONFIG` to a private JSON file outside the repository:

```json
{"Address":"smtp.example.com:587","From":"security@example.com","Username":"smtp-user","Password":"replace-privately","Mode":"starttls"}
```

Use `tls` for implicit TLS or `starttls` for an explicit upgrade. Both validate
the server certificate and hostname through the system trust store. Plaintext
`development` mode is refused in production; use it only with a private local
capture server. Lowercase field aliases are accepted; arbitrary Go-style field
case folding is not part of this configuration subset. Never commit this file
or copy it into generated products. Selection requires `AUTH_MODE=identity`.
Run `make backend-recovery-parity-test` for local SMTP and Go/Rust HTTP checks;
read [the recovery walkthrough](notes/modules/internal/identity/recovery.md).
Real provider delivery, contact replacement and periodic challenge pruning
remain separate work.

After sign-in, explicitly call `POST /v1/accounts/bootstrap` with a name. Account
also supports ordinary creation, owner-only GET/HEAD listing/selection and PATCH
rename with strong If-Match. Personal bootstrap is repeatable; ordinary create
and rename have no command receipts or automatic retry. Account consumes a
shared edge PrincipalResolver backed by `identity.credential.resolve.v2`,
with no Identity crate/store dependency. v1 remains available unchanged. Run
`make backend-account-parity-test`; read [scope/evidence](../../contracts/behavior/rust-account.md)
and [Account notes](notes/modules/internal/account/README.md).

Audit exposes GET/HEAD `/v1/accounts/{id}/audit` and `/v1/principals/me/audit`.
Account/Identity state commits include required outbox envelopes; independent
workers deliver to Audit's evidence/inbox. Reads can initially lag a successful
write. The memory profile preserves replay/deduplication and bounded retries,
including readiness failure on exhaustion, but nothing survives process exit.
Run `make backend-audit-parity-test`; read [scope/evidence](../../contracts/behavior/rust-audit.md)
and [Audit notes](notes/modules/internal/audit/README.md).

Select PostgreSQL explicitly from `backend/rust` (provide an owned development
database URL through your environment):

```sh
export STORAGE=postgres
export AUTH_MODE=identity
export DATABASE_URL=postgres://mobile_foundry@127.0.0.1:5432/mobile_foundry?sslmode=disable
make migrate
make migrate-check
HTTP_ADDR=127.0.0.1:8081 make dev
```

Account/Audit/Identity/Notes/Files/Notifications each use a separate pool and schema. Override the common
URL with `ACCOUNT_DATABASE_URL`, `AUDIT_DATABASE_URL`, `IDENTITY_DATABASE_URL`, `NOTES_DATABASE_URL`, `FILES_DATABASE_URL`, `NOTIFICATIONS_DATABASE_URL`
when needed; an explicitly empty override is invalid. Pool maximum defaults to
eight per module (`POSTGRES_POOL_MAX_CONNS`, 1–128). Startup checks migration
history without applying SQL. Required outbox/inbox state survives restart.
Read [PostgreSQL behavior/limits](../../contracts/behavior/rust-postgres.md).

Repository acceptance owns a disposable database, exercises failed transactions
and forced restarts, then checks Go↔Rust core state and Audit fingerprint replay:

```sh
# Docker disposable database (default)
make backend-postgres-parity-test  # run from the repository root
# Local PostgreSQL tools, fresh temporary cluster
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin make backend-postgres-parity-test
```

The observed local run used PostgreSQL 14.24; the inherited Docker runner selects
18.6-alpine and was not executed for this slice. Neither mode uses an existing
Compose volume. Cross-language acceptance includes core records/events, Notes
receipts, recovery challenges and Files metadata/bytes consumed across Go/Rust
restarts. Notifications adds retained source decisions/read/settings and
installation proof/target/retirement marker handoff. External push and generated
Rust products remain outside this profile.

Notes exposes account-scoped list/get/create/update/delete and receipt lookup.
Writes require a caller-generated command UUID; update/delete also require the
original strong ETag. Matching replay returns the historical receipt before
current-state checks, including after deletion. Notes owns records/receipts and
authorizes through account.access.v1; it has no Account/Identity store dependency.
In PostgreSQL mode run the explicit migrator before startup to apply the new
Notes history. The process selects local lossy Changes/Realtime adapters. Read
[Notes scope](../../contracts/behavior/rust-notes.md) and
[the walkthrough](notes/modules/internal/notes/README.md); run
`make backend-notes-parity-test` for shared Go/Rust HTTP/receipt acceptance.

Native bearer WebSockets are mounted at `/v1/accounts/{account}/realtime`.
Supply Authorization headers from an owned Identity session; Origin/Cookie and
nonempty queries are refused. Subscribe, receive ready, then refetch Notes over
HTTP. Four-field ready/changed messages contain no record data. The local hub
coalesces one dirty signal per watcher, bounded to 256 total/eight per principal.
Confirmed Notes writes/replays publish; failed writes do not. Renewal/revocation
or account refusal closes the original connection; reconnect with the new lease.
Sockets have independent ten-minute lifetimes, periodic authorization/Pong checks,
and are explicitly closed/joined before pools. Run
`make backend-realtime-parity-test`; PostgreSQL acceptance has its own runner.
Read [scope/limits](../../contracts/behavior/rust-realtime.md) and
[the walkthrough](notes/modules/internal/realtime/README.md). This is single-process
recoverable invalidation; public TLS/deployment, cross-process fanout and generated
Rust/native selection remain separate.

Files exposes account-scoped upload requests, immutable PUT content, completion,
download, deletion, listing and historical receipt lookup. Account authorization
uses its own `account.access.v1` adapter; Audit consumes Files facts independently.
PostgreSQL selects private `FILES_DIRECTORY` (default `.data/files`) for bytes.
Retain it alongside the database. Memory bytes disappear on restart. Deletion
commits inaccessibility before cleanup; retry the original key/input after cleanup
uncertainty. Receipt GET alone does not delete bytes. Run
`make backend-files-parity-test`; read [Files scope](../../contracts/behavior/rust-files.md)
and [learning notes](notes/modules/internal/files/README.md). Cloud storage,
media processing, orphan sweeping and Rust native/CLI selection remain separate.

Notifications independently consumes upload-completion facts into the source
actor's account inbox. List/HEAD, first-read acknowledgements and media preference
GET/HEAD/PUT use current Account authorization. Muted events still commit a source
decision; replay after unmuting cannot resurrect them. Read/settings have no
receipt/CAS: read current state after an unconfirmed write. Run
`make backend-notifications-parity-test`; read [scope](../../contracts/behavior/rust-notifications.md)
and [learning notes](notes/modules/internal/notifications/README.md).
PostgreSQL uses its own pool/history. No extra Notifications worker is selected.

Installations adds POST `/v1/installations` and GET/HEAD/PUT/DELETE item routes at
`/v1/installations/{id}`.
Use a current Identity access token and a separate canonical installation proof;
only its digest is retained. Explicit rebind/rotation requires the current ETag
and a new registration ID. Independent Identity v1/v2 revocation clears matching
targets and commits tombstones/source markers atomically; late old events leave
new bindings intact. Read current state after an uncertain mutation. Run
`make backend-installations-parity-test`; read
[scope](../../contracts/behavior/rust-installations.md) and
[learning notes](notes/modules/internal/notifications/installations.md).
Private tokens are plaintext at rest. Provider-issued tokens and native/CLI Rust
selection need separate acceptance.

Optional internal dispatch now supplies PushTargets, SessionEligibility and
PushGateway ports plus SendPushPreview. Compose through new_push_preview with an
explicit gateway and frozen Identity capability registry. It reads the exact
target twice around the authority check and conditionally retires only a rejected
owner/revision. Known rejection retains a separate fallible cleanup outcome;
uncertainty never triggers a resend. Default configuration adds no send route,
cloud gateway or worker. Run `make backend-push-parity-test`; read
[scope](../../contracts/behavior/rust-push-dispatch.md) and
[learning notes](notes/modules/internal/notifications/push.md).

### Optional APNs/FCM manual preview

Select `AUTH_MODE=identity` and `PUSH_CONFIG_FILE=/private/path/push.json` to mount
`POST /v1/installations/{id}/push-preview`. Keep that file and its provider keys
outside the repository and generated copies. Example configuration (replace the
route/key identifiers with your own; credential paths resolve beside this file):

```json
{"routes":[{"provider":"apns","application":"dev.example.app","environment":"sandbox","team_id":"TEAMID0001","key_id":"KEYID00001","key_file":"apns.p8"},{"provider":"fcm","application":"dev.example.app","environment":"production","project_id":"example-project","service_account_file":"service-account.json"}]}
```

APNs requires PKCS8 P-256 PEM; FCM requires a matching Google service account with
an admitted PKCS8 RSA key and standard OAuth token URI. Endpoints and TLS trust
cannot be overridden. Configuration loads without contacting providers. An empty
or absent setting leaves the route off. Durable jobs require the additional selection below.

Use current Identity bearer credentials, one X-Installation-Key and the current
strong If-Match. The body is `{"preview_id":"<fresh-canonical-uuid>"}`. Responses
contain preview_id, disposition and retired. There are no receipts: submitting
the same ID again can send again. Uncertainty needs reconciliation, never automatic
resend. Only definitive invalid-address evidence permits exact-version retirement.

Run `make backend-push-providers-parity-test` for local verified TLS/protocol,
shared HTTP and compiled selection checks, and `make backend-postgres-parity-test`
for separate-database HTTP acceptance. No live cloud/device delivery is claimed.
Read [scope and configuration limits](../../contracts/behavior/rust-push-providers.md),
[provider walkthrough](notes/modules/internal/notifications/providers.md) and
[HTTP library behavior](notes/substrate/reqwest-provider-boundaries.md).

### Optional retained jobs and explicit recovery

Add `PUSH_JOBS_ENABLED=true` to the PostgreSQL/Identity/private-provider selection
to mount job enqueue/status/cancel/replacement routes and start the serial worker.
Provider selection alone leaves jobs off. Apply/check the existing Notifications
0003/0004 histories explicitly before startup; no migration bytes change here.

POST `/v1/installations/{id}/push-jobs` with current bearer credentials,
X-Installation-Key, strong If-Match and `{"job_id":"<canonical-uuid>"}`. GET the
`/{job_id}` resource to read retained status. POST `/{job_id}/cancel` without a
body or If-Match to cancel queued/leased work. POST `/{job_id}/replacement` with
If-Match and `{"job_id":"<new-uuid>","accept_possible_duplicate":true}` only when
explicitly accepting an uncertain source's duplicate risk. Known-unsent terminal
sources instead require false. GET that replacement path to recover its direct
child after an unconfirmed request.

Exact intent replay returns retained state. Pre-marker failures have bounded
backoff; committed sending/uncertain outcomes never automatically resend. Jobs
expire for execution after fifteen minutes but terminal rows remain retained,
with a ten-thousand-row admission cap. There is no default pruning or admin
redrive policy. Cancellation cannot retract sending; replacement is a new intent.

Run `make backend-push-jobs-parity-test` and `make backend-postgres-parity-test`.
Read [behavior/limits](../../contracts/behavior/rust-push-jobs.md),
[module walkthrough](notes/modules/internal/notifications/jobs.md) and
[polling ownership](notes/modules/pkg/jobs/README.md). Local crash/handoff evidence
does not establish live cloud delivery or a generated Rust starter.

### Portable operations

Optional `METRICS_ADDR=127.0.0.1:9090` enables a separate private GET /metrics
listener with bounded method/status classes, active handlers and duration sum/count.
The API has no metrics route. Bind failure refuses startup; monitor failure drains
the API, and both owners join on shutdown. Rust's 101 completes the HTTP observation;
separately owned socket lifetime is not an HTTP active/duration sample.

`TRUSTED_PROXY_CIDRS` defaults empty. Only matching accepted peers may supply one
IP in X-Forwarded-For. Lists, duplicates, missing values, ports and hostnames are
refused for trusted peers; untrusted claims are ignored. Identity gates use the
admitted address. Keep the API private behind an edge that overwrites the header.

[Dockerfile](Dockerfile) builds four release binaries under Rust 1.86.0, with a
Debian bookworm-slim/non-root runtime and system TLS roots.
[The Compose profile](infra/production/compose.yaml) selects read-only root,
bounded tmpfs, private PostgreSQL/files/metrics and one API replica behind Caddy.
Copy [the env example](infra/production/.env.example) and supply a private database
password, actual domain and external private SMTP configuration. Keep an absolute
PROD_ENV_FILE outside tracked source. If changing addresses, update PRIVATE_SUBNET,
PRIVATE_IP_RANGE, EDGE_IP and TRUSTED_PROXY_CIDRS together; the fixed edge must
stay outside automatic allocation.

From backend/rust, after configuring that private file:

```sh
make prod-config prod-build prod-db prod-migrate prod-up
make prod-maintenance
```

Pass `PROD_ENV_FILE=/absolute/private/rust.env` to each invocation and choose a
distinct PROD_PROJECT when needed. Migrations remain explicit; startup checks
histories. Maintenance prunes expired Identity challenges only, with bounded
--limit/deadline and without starting API/mail/crypto workers.
`make prod-backup BACKUP_DIRECTORY=/absolute/private/backup` quiesces the API and
captures common database/private objects. `make prod-restore` also requires an
unused RESTORE_PROJECT and leaves its API stopped. Protect bundles as credential-
bearing data; restore only trusted owned backups.

`make operations-test` owns a fresh image and disposable Docker projects. For an
already built owned image, run
`python3 -B scripts/operations_acceptance.py --image <owned-image>`.
Observed acceptance exercises actual loopback Caddy/WebSockets, local SMTP,
metrics, paired backup/restore, retained state and explicit maintenance. Public
DNS/certificates, external delivery, split-database/cloud backups and Rust CLI/
native products remain separate. Read [scope](../../contracts/behavior/rust-operations.md)
and [the walkthrough](notes/modules/infra/production/README.md).

Start learning at [the Rust reading order](notes/README.md). The reusable failure
type uses ordinary `Result<T, Failure>`; HTTP serialization and runtime ownership
stay outside the kernel. Public errors match the shared vocabulary, while Rust
cause ownership and formatting remain idiomatic.

HTTP consumers can use `decode_json`, `required_version`, `idempotency_key` and
`json_response` from `httpx::admission`. Body admission is bounded to 16 KiB and
the owned request deadline. Version accepts only strong canonical integers
1..9007199254740991; key parsing is optional and does not implement receipts.
`AdmissionError` owns transport-only status overrides. Domain failures still
project by kind, so a copied error code cannot impersonate a 413/415/428 condition.
Business DTOs require their own compatibility cases for null, casing, duplicate
types and numeric behavior; the shared corpus is intentionally narrower than a
complete Go JSON decoder replacement.

`kernel::clock::Clock` provides business wall time, independently of monotonic
HTTP timers. `kernel::id::{Id, Generator, RandomIds}` provides canonical identifiers
and fallible OS entropy. `Server::with_ids` and `BoundaryState::new` select an ID
adapter; callers cannot construct malformed Id values. An allocation failure
returns a redacted 500 before the handler runs, with no fabricated request ID.
