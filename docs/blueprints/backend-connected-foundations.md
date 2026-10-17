# Realtime, core identity lifecycle and portable operations

This batch extends the Go starter through CLI 0.14.0. Read the contracts for
[Realtime](../../contracts/behavior/realtime.md),
[identity recovery](../../contracts/behavior/identity-recovery.md) and
[operations](../../contracts/behavior/backend-operations.md).
Existing native sign-in/grant/Notes contracts remain compatible. The generated
mobile product does not yet select socket consumers or recovery/email forms.

## Read the implementation

1. [Realtime module](../../backend/go/internal/realtime/module.go) selects consumer-owned
   access/feed ports, a local capability client and the WebSocket edge. The
   [generic hub](../../backend/go/pkg/broadcast/hub.go) carries only coalesced dirty
   signals; Notes publishes through its application Changes port after confirmed
   store success. New/feed adapters can replace the local composition.
2. [Identity commands](../../backend/go/internal/identity/app/command) own verification,
   reset and password change. [Recovery ports](../../backend/go/internal/identity/app/recovery.go)
   keep checkpoint/consumption/revocation atomicity explicit. Memory/PostgreSQL
   implement the same behavior; app SignIn uses checked credential issuance.
3. [SMTP and reset queue](../../backend/go/internal/identity/infra/email) keep provider
   configuration private and existence lookup out of public response timing.
4. [Server telemetry](../../backend/go/pkg/telemetry/http.go),
   [trusted proxy admission](../../backend/go/pkg/httpx/proxy.go),
   [container](../../backend/go/Dockerfile) and
   [deployment profile](../../backend/go/infra/production/compose.yaml) provide the
   portable operational boundary.
5. [Backup/restore](../../backend/go/scripts/backup.py) and
   [owned acceptance](../../backend/go/scripts/operations_acceptance.py) exercise
   concrete recovery, rather than treating a successful dump as restore evidence.
6. [CLI inventory](../../templates/backends/go.json) includes Realtime, recovery
   migrations, tests and operational sources. It explicitly selects public SMTP
   examples, never private configuration or host credentials.

## Run

From backend/go, memory exploration still needs no external services:

```sh
AUTH_MODE=identity make dev
```

Select a private IDENTITY_MAIL_CONFIG for the lifecycle mail adapter. Example:
Address smtp.example.com:587, From security@example.com, Username/Password and Mode
starttls. Production also permits implicit tls. Explicit development mode can use
local SMTP capture. No mail sender is selected without this file.

Apply Identity 0002 and Audit 0004 with the existing explicit migrator before
starting retained PostgreSQL. Never edit an already-applied migration.

For a portable deployment, put SMTP configuration under a private parent directory
(mode 0700), keep its file readable by container UID 65532 (for Compose file secrets,
a read-only 0444 file under that private parent is portable), and configure a private
production env from infra/production/.env.example. Supply an alphanumeric DB password,
real public domain and deliberate image tag. Set PRIVATE_SUBNET, EDGE_IP and the exact
TRUSTED_PROXY_CIDRS together if the default network conflicts with another stack.

```sh
make prod-config prod-build prod-db prod-migrate
make prod-up
make prod-maintenance
make prod-backup BACKUP_DIRECTORY=/private/backups/first
```

For restore, create another private env with a separate network and unused project
namespace; set PROD_ENV_FILE to it. Restore leaves the API stopped for review.

```sh
make prod-restore PROD_ENV_FILE=/private/restore.env \
  BACKUP_DIRECTORY=/private/backups/first RESTORE_PROJECT=review-copy
```

Keep backup bundles private and encrypt off-host copies. The portable command
backs up one common PostgreSQL service plus its private files volume, not arbitrary
external/split stores. Retention is conservative: only expired challenge rows are
pruned. Account erasure, broad history pruning and cloud object-provider selection
need product policy and separate owner-specific contracts.

## Verification

```sh
make check test smoke
make integration-test
make operations-test
```

The source race suite exercises actual WebSocket frames and socket lifetime beyond
HTTP's fifteen-second budget, revocation and shutdown. Recovery conformance covers
single-use/expiry/replacement, stale sign-in, session invalidation, redacted events
and required-fact rollback in memory/PostgreSQL. v2 revocation tests retain v1's
closed reason contract. SMTP/queue checks cover production encryption admission,
redaction and bounded response-independent work.

The operations driver owns its API image, PostgreSQL/Mailpit services, local ports,
backup files and restore project. It never resets retained infrastructure and
cleans up its own containers/volumes/networks. It proves local SMTP and container
recovery, not public DNS/TLS issuance, live external delivery or physical-device behavior.

## Observed verification — 2026-10-17

CLI 0.14.0 independently generated
`/private/tmp/mobile-backend-complete-mjdo0i5l/service-ready`, Go module
`github.com/example/service-ready/backend`, using `--platform none --backend go`.
The copied project was not patched after generation. It has no checkout imports
or replace directives and owns its default `service-ready-prod` Compose namespace.

Its own root commands pass:

- `make go-check`: formatting, architecture policy, vet and build.
- `make go-test go-smoke`: race-enabled suite, real WebSocket lifetime/revocation/
  shutdown, recovery and versioned-fact cases, and compiled-process HTTP smoke.
- `make go-integration-test`: disposable PostgreSQL 18.6, recovery conformance and
  forced rollback, migrations, concurrency, leases and process-kill recovery.
- `make go-operations-test`: freshly built container, exact non-root/read-only
  settings, Mailpit verification/reset, private metrics, quiesced database/object
  backup, refusal of an existing restore target, retained receipt/private bytes/
  contact/challenge and post-reset credential refusal. The cloned API is started
  only by acceptance after restore review; all owned projects are removed.

The first parallel race run timed out in an existing registration HTTP case while
container build and the database suite were running. With those resource-heavy
checks complete, the full race suite passes in isolation, including the previously
failing case, followed by smoke. No timeout or runtime behavior was weakened.
The source checkout also passed its full check/race/smoke and PostgreSQL suite;
32 CLI cases pass. Documentation/inventory checks validate links and selected
closure separately from execution.

Go remains **standalone starter ready**, now including this backend scope. Existing
Daily native readiness remains based on its separate 0.13.0 screen/process
acceptance. This batch does not claim new native socket/recovery UI, external SMTP
mailbox delivery, signed-device push, public certificate issuance, distributed
fanout, multi-item sync or broad retention/erasure policy.


Native follow-up: CLI 0.15.0 now selects these backend contracts through native
recovery forms and account live hints in Daily go-connected. Its distinct
[construction/acceptance](native-identity-live.md) extends the earlier backend-only
evidence above; it does not retroactively turn that run into native UI acceptance.
