# Rust backend

An independent implementation of the Go backend's contracts, starting with the
runtime, Health, Identity/Account, independent Audit, receipt-backed Notes and
Files/Media, in-app Notifications and session-bound installations in memory or
PostgreSQL. Go remains the reference implementation.
Read the
[parity inventory](PARITY.md) for the exact implemented scope and remaining gaps.
This profile is a foundation candidate. Identity/Account run through versioned
credential capabilities and durable event delivery. CLI generation remains future work.

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
internal/health/     # Application query/port and transport/http/v1 adapter
internal/identity/   # Domain, commands/query, ports, memory/security and HTTP v1
internal/account/    # Domain, commands/query, owned state/outbox and access provider
internal/audit/      # Selected evidence, inbox, scoped queries and receiving adapters
internal/notes/      # Account-scoped records, command receipts and memory/PostgreSQL stores
internal/files/      # Lifecycle/receipts/outbox and immutable memory/private-directory bytes
internal/notifications/ # In-app delivery decisions, scoped inbox, read state and preferences
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
settings fail validation. Development fixture credentials, metrics,
trusted proxies and push selections fail startup rather than selecting an
unimplemented adapter. `AUTH_MODE=deny` does not supply an Identity implementation;
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
Notes history. The process selects no Notes broadcast/socket adapter. Read
[Notes scope](../../contracts/behavior/rust-notes.md) and
[the walkthrough](notes/modules/internal/notes/README.md); run
`make backend-notes-parity-test` for shared Go/Rust HTTP/receipt acceptance.

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
Private tokens are plaintext at rest. No provider-issued token, send eligibility,
external push/jobs or native/CLI Rust profile is selected.

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
