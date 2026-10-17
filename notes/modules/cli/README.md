# CLI native and Go source-copy walkthrough

Claim: independent backend extraction needs its own registered closure,
identity rewrite and runtime evidence, while native/backend co-location remains
a separate selection from connected native behavior.

Origin: Go starter batch, 2026-10-13. Evidence: 28 CLI cases, three independently
generated module identities, race checks, real generated Make-runner API flows,
PostgreSQL migrations/crash recovery, and both co-located native builds pass.

## Backend foundation follow-up — 2026-10-17

Backend 0.14.0 follow-up: the explicit Go inventory adds Realtime, checkpointed
identity lifecycle, migrations and portable operations. It selects public
production examples individually instead of copying a configuration directory
that might hold private files. Generation also rejects private configuration
suffixes. The renamed output owns its Go module and default production Compose
project. Root wrappers forward to the copied backend commands. Read
[independent backend evidence](../../../docs/blueprints/backend-connected-foundations.md).
This selection does not add recovery forms or socket consumers to native apps.

## Durable recovery follow-up — 2026-10-17

CLI 0.13.0 adds the PendingNotesStore contract/shared cases and only the SQLite
journal source/tests from Data to go-connected. Its overlaid Data manifest drops
unselected local CRUD/Session dependencies; local-data and UI-only inventories
are unchanged. Generated setup documents bounds, retention, recovery and API
requirements. Read [pending construction/evidence](../../../docs/blueprints/pending-notes.md).
The copied driver owns a fault proxy and verifies exact original command input
through actual process restarts. Tests must pass outside the checkout before
promoting that added behavior; a complete inventory alone is insufficient.

## Source reading order

Connected follow-up, 2026-10-17: CLI 0.12.0 registers Daily + go-connected + Go,
selecting HTTP/Session and narrow remote Notes Services in both native products.
[Connection admission](../../../cli/mobile_foundry/connections.py) validates the
public endpoint before copying and writes app-owned constants/provenance. The
[copied driver](../../../scripts/connected_acceptance.py) exercises the actual
generated screens and native credential restoration against that product's API.
Go co-location and local-data behavior remain independent selections. Read
[construction/evidence](../../../docs/blueprints/go-connected-starter.md), then
[composition reasoning](../../patterns/connected-products-and-composition.md).

Installation follow-up, 2026-10-17: CLI 0.7.0 selects credential v2, compatible v1,
installation metadata, 0002 migration and registry tests. The independent device-api
passes its own race/smoke/PostgreSQL recovery and generated root runner's proof,
rebind and unregister flows. Native OS integration remains unselected. Read
[actual readiness](../../../docs/STARTER-READINESS.md).

Notifications follow-up, 2026-10-17: CLI 0.6.0 extends the explicit Go module/source/
migration/test closure with Notifications. No source domain import reaches another
module. The independent generated API passes its own memory/race, PostgreSQL 14.24
recovery and Make-runner upload → inbox → read → preference flow. Read
[the current evidence](../../../docs/STARTER-READINESS.md) and
[Notifications construction](../../../docs/blueprints/notifications.md).

1. [Entry and selection](../../../cli/mobile_foundry/cli.py): API-only versus native
   platform choices, backend/module admission and explicit scope in the handoff.
2. [Backend inventory](../../../templates/backends/go.json) and
   [validation](../../../cli/mobile_foundry/backends.py): source/module/toolchain,
   own imports, SQL embed and shared fixture closure.
3. [Generation](../../../cli/mobile_foundry/generation.py): preflight snapshots,
   source merge, output ownership, identity/import rewrite, cleanup and provenance.
4. [Portable guides](../../../cli/mobile_foundry/documents.py) and
   [Go overlays](../../../templates/overlays/go): runner defaults and environment.
5. [Safety tests](../../../cli/tests/test_backends.py) and
   [real output driver](../../../cli/acceptance/go/verify.py).

## What and why

The generator reuses the native regular-file/exclusion/symlink policy for Go,
then merges snapshots before creating output. Differing collisions are errors.
ProductInputs with an empty native platform tuple admits API-only scope and
refuses installed app/signing inputs. Metadata records null native profile/app ID
and the backend module list, rather than inventing a native shell.

The Go module is product identity; wire authority/capability/schema names remain
protocol identity. Source module imports and architecture-policy prefix change
together. An import block that previously placed github.com after fmt may need
example.com before fmt. Sorting each ordinary import group restores gofmt order
without a Go dependency in the generator. Unknown import syntax fails and removes
only owned output. This is a bounded reference-source transform, not a Go parser.

Example — Conceptual:

```text
registry → native inventory + Go inventory → admitted snapshots
  → exclusive new destination → product/module/Compose identity
  → portable commands + provenance → independent build/runtime acceptance
```

## Verification and gotchas

The backend-only and three native co-location selections preserve source and
secrets/cache exclusions. Fault injection confirms output cleanup and existing
destination refusal. Different module prefixes pass the copied architecture check.
The root Make runner's exported Identity default is exercised by a precompiled
server behind a narrow temporary go-run shim; the real copied API handles the
HTTP requests. A fresh isolated process group receives TERM at cleanup. This
checks runner environment and protocol behavior without reinstalling dependencies.
The normal generated go-build/check/test/smoke commands also execute independently.

PostgreSQL uses a disposable local cluster; the retained development stack is
never reset. Compose config is checked without starting Docker's read-only VM.
Native builds establish co-location, not provider UI integration. Cold dependencies,
release deployment, native connected device flows and module subsets are separate.
See [actual acceptance](../../../docs/blueprints/go-starter.md#acceptance-evidence).

Used in [CLI usage](../../../cli/README.md) and
[backend readiness](../../../docs/STARTER-READINESS.md).
Related: [Go source-copy verification](../../techniques/go-source-copy-verification.md),
[source ownership](../../techniques/local-cli-and-independent-source-copies.md).
Next: select a clean native session/account/remote-data entry and independently
exercise the generated product on both platforms before promoting connected readiness.


## Native lifecycle/live follow-up — 2026-10-17

CLI 0.15.0 adds security model/forms as explicit overlays and keeps Session ports
inside the selected package/module closure. A shared hint vector and local SMTP
fixture are required sources. The copied driver tunnels only its owned backend's
WebSocket upgrade and keeps write-fault injection separate. It tests mail codes
and external live writes through actual native transports/screens, preserving
prior pending recovery assertions. No production provider credential is copied.
Read [acceptance/evidence](../../../docs/blueprints/native-identity-live.md).
Next: keep new capabilities explicit rather than inferring them from co-located code.
