# Generate a Go starter

Status: standalone backend ready through CLI 0.12.0, connected follow-up 2026-10-17. Daily can now select [connected native sessions/accounts/Notes](go-connected-starter.md)
through --with go-connected. Backend co-location alone retains local shell behavior.

[Owned job recovery](push-job-recovery.md) now adds explicit cancellation before
sending and one linked replacement per eligible source. Independent CLI 0.11.0
output at `/private/tmp/mobile-push-recovery-starter-2QauZT/recovery-verified`
passes its own check/race/smoke, disposable PostgreSQL old-row upgrade and recovery,
and generated HTTP runner acceptance. Apply Notifications 0004 explicitly on an
existing deployment; source copying still selects no provider keys or active worker.

[Queued manual previews](push-jobs.md) now add opt-in retained intents and fenced
execution under Notifications. Independent CLI 0.10.0 output at
`/private/tmp/mobile-push-jobs-starter-SNkXAO/verified-api` passes its own
check/race/smoke, disposable PostgreSQL crash/rollback and HTTP runner acceptance.
PUSH_JOBS_ENABLED=true additionally requires PostgreSQL plus selected private
providers. Source copying enables no live worker and provisions no credentials.

The selected source includes [internal push-dispatch seams](push-dispatch.md) and
[optional APNs/FCM adapters](push-providers.md): versioned Identity eligibility,
exact installation rechecks, conditional invalid-address retirement and a guarded
manual preview. PUSH_CONFIG_FILE explicitly selects private provider routes/keys;
the ordinary generated runner leaves it disabled. Independent CLI 0.9.0 output at
`/private/tmp/mobile-provider-verified-9jtwolsv/push-api` passes check/race/smoke,
disposable PostgreSQL and generated runner acceptance. Local TLS protocol tests
are not live cloud/device-delivery evidence. The synchronous route has no durable
send receipt; the separate queued workflow above now retains execution state.

From an existing output parent outside this checkout:

```sh
mobile-foundry doctor --platform none --backend go
mobile-foundry init canopy-api --platform none --backend go \
  --go-module github.com/your-org/canopy/backend
cd canopy-api
make go-check go-test go-smoke
make go-dev
```

The installed command links this repository's bin/mobile-foundry; use that entry
directly or `make cli-install` if needed. Initialization requires Python 3.11+,
copies exclusively into a new directory and does not run Go/install dependencies.
Default module identity is `example.com/NAME/backend`; an explicit --go-module
uses a conservative lowercase domain/path grammar. Imported package and boundary
test prefixes are rewritten while wire authority/capability names stay stable.

Generated layout:

```text
backend/go/
  cmd/server, cmd/migrate
  internal/health, identity, account, audit, notes, server, architecture
  pkg/                         # reusable runtime primitives
  migrations/                  # checksummed owned schemas
  scripts/                     # temporary API/disposable database checks
  tests/                       # provider/HTTP/integration conformance
  infra/local/compose.yaml
contracts/fixtures/account/names.json
Makefile, README.md, SETUP.md, mobile-foundry.json
```

Notes is a replaceable domain exemplar, included in this first backend selection.
There is no module-selection flag yet. Each module retains domain → command/query
→ consumer ports → adapters → versioned transport and owned composition. The
generator omits reserved placeholders and repository notebooks; generated guides
contain no source-checkout links. Module dependencies are pinned in go.mod/go.sum.

The generated runner explicitly selects Identity and memory storage. Register
and sign in using the generated SETUP.md; memory records/sessions vanish on exit.
For durable development:

```sh
make go-db-check
make go-db-up go-migrate go-migrate-check
STORAGE=postgres make go-dev
```

Each output uses its own Compose project/retained volume. Set POSTGRES_PORT if
another project uses 58432; the default Make DATABASE_URL follows it. Explicit
DATABASE_URL/module overrides remain available. Startup checks migrations but
never applies them; go-db-down retains the volume. .env.example is documentation,
not automatically loaded credentials.

To include an independent Go API alongside a native shell:

```sh
mobile-foundry init canopy --platform both --with local-data --backend go \
  --go-module github.com/your-org/canopy/backend --app-id com.example.canopy
```

Native screens still use the selected local UI/data ports. The API's presence
does not connect those screens; generated session/account/remote-data composition
needs its own explicit profile and device acceptance. No source references,
shared credential store, external provisioning or Git initialization is implied.

## Acceptance evidence

Independent outputs remain at `/private/tmp/go-starter-acceptance-q7d088qa`:
canopy-api (github module), canopy-combined (short a.example module with both native
local-data apps), and ready-api (final registered example.com module).
Go formatting/import policy/vet/build/race checks pass across these identities;
API-only smoke and real Make-runner auth/account/Notes/CAS/recovery checks pass.
The generated PostgreSQL suite verifies explicit migration refusal/application,
atomic receipts/outbox/inbox, bootstrap, rotation/reuse and killed API recovery.
Both co-located native apps build with their configured product identity.

28 CLI cases pass, including six backend-focused checks, source preservation,
independent native/backend copies, required closure/exclusions and failed/interrupt
cleanup. Compose configuration validates; Docker VM storage was read-only, so
database execution used an isolated local PostgreSQL 14.24 server. Pinned Compose
18.6 execution, cold dependency installation, release deployment and generated
native auth/data UI were not run in this batch.

Reproduce runtime acceptance after generation:

```sh
python3 -B /path/to/mobile-foundry/cli/acceptance/go/verify.py /path/to/generated-project
cd /path/to/generated-project
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/path/to/postgres/bin make go-integration-test
```

The driver uses only the generated server/source/Make runner for behavior; its
location is an acceptance utility, not a dependency of the output. Read
[CLI source walkthrough](../../notes/modules/cli/README.md),
[verification reasoning](../../notes/techniques/go-source-copy-verification.md),
[behavior](../../contracts/behavior/go-starter.md), and
[readiness](../STARTER-READINESS.md).

Files follow-up: the full backend now includes Files, with memory/PostgreSQL
metadata and memory/private-directory object bytes. Read
[Files setup and current acceptance](files-media.md). Retain FILES_DIRECTORY with
the database; generated native shells still do not compose authenticated media UI.
