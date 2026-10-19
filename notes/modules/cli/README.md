# CLI native and backend source-copy walkthrough

Claim: independent backend extraction needs its own registered closure,
identity rewrite and runtime evidence, while native/backend co-location remains
a separate selection from connected native behavior.

Origin: Go starter batch, 2026-10-13. Evidence: 28 CLI cases, three independently
generated module identities, race checks, real generated Make-runner API flows,
PostgreSQL migrations/crash recovery, and both co-located native builds pass.

## Rust native connection — 2026-10-19

CLI 0.20.0 adds [rust-connected inventory](../../../templates/profiles/native-daily-rust-connected.json).
It shares the exact native closure and overlays with Go-connected, while
[connection admission](../../../cli/mobile_foundry/connections.py) requires the
matching backend before copying. Generation records rust-connected/rustPackage;
[portable guides](../../../cli/mobile_foundry/rust_documents.py) expose Rust tasks
and connected scope. Tests compare native source snapshots, check all platform
outputs and refuse mixed backend/capability/configuration selections.

The [copied driver](../../../scripts/connected_acceptance.py) now chooses Go or Rust
from product metadata. The Rust branch compares Cargo package identity and builds
--locked in the output's own target directory; it never runs Go. Both native host
transport suites pass against that real Rust process. Actual screens and private
storage across process restarts provide a separate gate, now passed by 36 iOS
tests and ten Android screen stages/seven native Data tests. The capability was
promoted only after that generated execution. Follow
[current execution evidence](../../../docs/blueprints/rust-connected-starter.md),
[copy/runner checks](../../../cli/tests/test_rust_connections.py) and
[the composition pattern](../../patterns/connected-products-and-composition.md).
The important lesson is to test backend substitution at the product boundary,
without copying its lifecycle policy into a second set of screens.

## Optional account inbox — 2026-10-19

CLI 0.21.0 explicitly registers inbox with either Go-connected or Rust-connected
Daily. The [Go](../../../templates/profiles/native-daily-go-connected-inbox.json)
and [Rust](../../../templates/profiles/native-daily-rust-connected-inbox.json)
inventories select the same [inbox overlays](../../../templates/overlays/inbox) and add only
NotificationInbox, HTTPNotificationInboxRepository, RemoteServiceAdmission and
their malformed-response fixtures/tests to Services. Base connection, local-data
and other shells retain their own closure. No Files or provider SDK is inferred.

The first independent compile found RemoteServiceAdmission missing from the
narrow inventory: full catalog builds had included that same-module helper
implicitly. Required-file declarations now keep it visible. The package graph
validator cannot establish that every selected Swift/Kotlin declaration resolves;
compile the actual copied source set. [Execution evidence](../../../docs/blueprints/connected-inbox.md)
records that correction and clean generation separately from investigations.

Read [selection/copy checks](../../../cli/tests/test_inbox.py), then
[the copied driver](../../../scripts/connected_acceptance.py). Its proxy now
forwards PUT for byte ingestion/settings and records committed lost-reply
attempts by owned inbox scope. Real Files completions feed Notifications; actual
apps must use readback once and keep mutations at one attempt through restoration.
This proves a selected product interaction, rather than rendering fixture rows.
Final independent Go inbox and Rust full-connected acceptance both pass on Swift
and Kotlin. The 48 CLI tests and source-content digests also pass. The capability
was promoted after actual generated execution; see the linked construction guide
for precise counts, retained artifact paths and local-only limits.

## Rust backend extraction — 2026-10-18

Rust follow-up, 2026-10-18: CLI 0.19.0 registers an independently verified Rust
backend. Read [the inventory](../../../templates/backends/rust.json),
[effective Cargo validation/identity](../../../cli/mobile_foundry/rust_backend.py),
[portable documents](../../../cli/mobile_foundry/rust_documents.py),
[safety/copy tests](../../../cli/tests/test_rust_backends.py) and
[the actual Make-runner probe](../../../cli/acceptance/rust/verify.py).
The 335-file closure retains 24 crates, 30 shared fixtures, immutable migration
bytes and independent drivers. Package identity and one lock entry change while
the explicitly named binary/reusable crates remain stable. init never invokes
Cargo. Doctor disables Rustup auto-install. Missing/escaping dependencies/includes,
collisions and private state refuse before publication; owned rewrite failures
clean up only that output. Real renamed output passes locked checks/tests/smoke,
PostgreSQL rollback/SIGKILL/restart and its freshly built Docker/Caddy/mail/backup
flow. See [evidence](../../../docs/blueprints/rust-starter.md) and
[the transferable technique](../../techniques/cargo-closure-and-independent-starters.md).
Native co-location stays unconnected; Go's explicit connected selection is unchanged.

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

## Optional Files extraction (CLI 0.22.0)

[Files inventories](../../../templates/profiles/native-daily-go-connected-files.json)
select the same native overlays for either backend. Their test dependency closure
includes Notifications admission for the existing binary integration check; it
selects no inbox screen or graphics renderer. [Copy tests](../../../cli/tests/test_files.py)
cover six independent native/backend selections, refusal and closure drift.
[Construction](../../../docs/blueprints/connected-files.md) records runtime evidence;
static copying alone is not readiness. Pending transfers are owner-lifetime only.

## Files + Inbox combination (CLI 0.23.0)

The [combined Go inventory](../../../templates/profiles/native-daily-go-connected-files-inbox.json)
and matching Rust inventory select both feature owners unchanged. Only session
composition, navigation refresh and the joined workflow tests are new overlays.
[Copy tests](../../../cli/tests/test_media.py) cover six outputs, source equality,
required closures and unsupported combinations. The copied driver calculates
native owner/test counts from selected features and adds a media scenario.
Read [construction and observed execution](../../../docs/blueprints/connected-media.md)
for readiness; source-copy checks do not prove consumer delivery or native restore.

## Files recovery review — CLI 0.24.0

The existing Files-only and Files + Inbox manifests continue selecting the same canonical feature owners and UI across Go/Rust. No capability or backend route is added. Generation documents review/Keep/Forget and its ephemeral limits; copy checks ensure the Files owner stays verbatim. The copied acceptance boundary now commits a competing delete under a different key before forwarding the original delete, checks its real 404, and verifies no automatic replay while the user reviews and forgets. The existing lost-completion and same-delete cleanup checks still run. Read [the abandonment lesson](../../patterns/explicit-abandonment-is-not-settlement.md) and [independent native evidence](../../../docs/blueprints/connected-files.md).

## Durable Files selection — CLI 0.25.0

The four explicit Files-only/combined Go/Rust inventories add PendingFiles admission/ports/tests, the separate SQLite adapter/tests and shared fixtures. Base/local-data/inbox-only inventories stay narrow. Existing canonical feature sources are reused; composition injects disk without adding a backend route. Generated setup now explains process-restored manual recovery, private storage and failure gates. Read [the staging lesson](../../patterns/atomic-byte-staging-before-transport.md) and [generated execution](../../../docs/blueprints/connected-files.md). The driver adds exact unsent-content fault counts and six Android/two iOS Files workflows, rather than treating page restoration as recovery proof. Startup is bounded by 30 seconds and preserves pre-fixture failure logs. CONNECTED_SWIFT_DISABLE_SANDBOX is an explicit host test opt-in; normal SwiftPM manifest sandboxing remains the default.

## Scoped inbox destinations — 2026-10-19

CLI 0.26.0 adds three app-owned files per native platform to the explicit Files + Inbox inventories: detail owner, screen and owner tests. Files-only/Inbox-only source closures do not acquire those files. [CLI copy tests](../../../cli/tests/test_media.py) exercise all six native/backend copies, source equality and missing owner/screen/test refusal on both platforms. The copied acceptance driver selects four added owner cases, adapter admission and the expanded real-media UI workflow.

The media proxy commits an independent authorized target delete before the second metadata GET. The product then sees its actual selected backend's 404. Exactly two metadata opens, one successful verified preview, unchanged unread count on each open and the existing explicit read/preference attempt guards test navigation without inventing write semantics. Read [execution evidence](../../../docs/blueprints/connected-media.md) and [the pattern](../../patterns/notice-targets-and-current-authority.md). OS push and deep-link admission remain separate.
