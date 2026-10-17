# Connected native starter construction

CLI 0.13.0 includes the explicitly selected Daily + Go connected composition and
[durable manual pending recovery](pending-notes.md). It
extracts the working catalog contracts into product-owned screens/state without
copying the catalog. Read [behavior](../../contracts/behavior/go-connected-starter.md)
and [readiness](../STARTER-READINESS.md) for current scope/evidence.

## Generate and run

From the directory where you want a new product:

```sh
mobile-foundry init canopy --platform both --with go-connected --backend go \
  --app-id com.example.canopy --display-name Canopy \
  --go-module github.com/your-org/canopy/backend
cd canopy
make go-dev
```

Use `swift` or `kotlin` instead of `both` for one platform. Generation still requires
only Python; native/Go toolchains are needed for subsequent builds. The API defaults
to memory/owned Identity on loopback 8080. In another terminal, register a development
user using the generated SETUP.md example, build/run the app, open Account and sign
in. Library is the connected Notes exemplar. No fixture login is seeded by generation.

`--api-base-url https://api.example.com/v1` selects a deployment. The public base is
written into each app's ConnectionConfiguration and provenance. For another local
port, select `http://127.0.0.1:PORT/v1`, set Go's HTTP_ADDR accordingly and use that
port for ADB reverse. Keep loopback HTTP in Debug. Release requires a reachable
HTTPS server and deliberate branding/signing. This does not provision a deployment.

`--backend go` alone retains the existing local shell. Workspace/Browse and
local-data + go-connected are not registered together. Source-copy inventories
are complete selections rather than arbitrary additive file merging.

## Read the implementation

1. [Registry](../../mobile-foundry.starters.json) →
   [connected inventory](../../templates/profiles/native-daily-go-connected.json).
2. [Connection admission](../../cli/mobile_foundry/connections.py) →
   [generation](../../cli/mobile_foundry/generation.py) →
   [portable setup/Make targets](../../cli/mobile_foundry/documents.py).
3. [Swift product composition/screens](../../templates/overlays/go-connected/swift)
   and [Kotlin product composition/screens](../../templates/overlays/go-connected/kotlin).
4. Original [Session](../../frontend/swift/packages/FoundrySession/Sources/FoundrySession),
   [HTTP Notes adapter](../../frontend/swift/packages/FoundryServices/Sources/FoundryServices/HTTPRemoteNotesRepository.swift)
   and their Kotlin counterparts remain the reusable port/adapter owners.
5. [Independent acceptance driver](../../scripts/connected_acceptance.py) and
   [CLI selection/refusal checks](../../cli/tests/test_connections.py).

Swift owns one observable connection in product state. Account controls session
actions; a lease change detaches then rebinds Notes/directory. A deployment-scoped
Keychain vault supplies restore. Kotlin keeps the same composition in an Activity
ViewModel with a Keystore vault, serial controller state collection and explicit
foreground binding. Neither product selects demo auth or an automatic retry worker.

Library's RemoteNotesStore is app-owned recovery/paging state around an injected
RemoteNotesRepository. It retains the exact command per original SessionScope.
Its five unit cases on each platform cover lost acknowledgements through renewal,
confirmed-write/read failure, late success after replacement, lost stage
acknowledgement and failed receipt persistence through new owner construction.
An injected PendingNotesStore now persists exact input and terminal results before
clearing pending; generated composition selects the narrow SQLite Data adapter. The reference
catalog keeps its own gallery owner; these overlays are portable product consumers.

## Independent verification

The generated project supplies these commands, without a source-checkout path:

```sh
make ios-build swift-test ios-test
make android-build android-test android-ui-test
make go-check go-test go-smoke
make connected-test CONNECTED_PLATFORM=host
make connected-test CONNECTED_PLATFORM=swift
make connected-test CONNECTED_PLATFORM=kotlin
```

Use only the selected native platform's targets and serialize jobs sharing derived
data/Gradle output. Connected acceptance requires an unused 127.0.0.1 HTTP endpoint
with /v1, a booted simulator/emulator and installed toolchains/caches. It starts its
own generated API and never resets the retained Compose stack. Source acceptance
does not establish live provider delivery or production deployment.

Observed 2026-10-17, final independent CLI 0.12.0 output:
`/private/tmp/mobile-connected-starter-z3ieecjs/launch-ready`, installed identity
`com.example.launchready`, module `github.com/example/launch-ready/backend`, public
Debug base `http://127.0.0.1:19879/v1`. This final copy was generated after the app,
recovery, test-driver and entry-lifetime corrections; its source files were not
patched after generation.

- The copied `make connected-test` passes end to end. Real URLSession/OkHttp session/
  account and Notes receipt/CAS checks use its own temporary compiled Go API.
- iPhone 17 Pro / iOS 26.2 executes one XCUITest without failure/skip: sign-in,
  Personal bootstrap, workspace selection, Notes create/update/delete, renewal,
  Keychain selected-workspace restore after termination/relaunch, account isolation
  and sign-out. Result: `.cache/connected-ios/Results/connected-1792247431909068000.xcresult`.
- Seven hosted iOS cases pass without failure/skip, including three Notes recovery
  cases, native tab/theme/branding, Library's public-snapshot presentation regression
  and built single-scene metadata. Result: `.cache/connected-ios/Logs/Test/Test-ProductApp-2026.10.17_17-34-25-+0300.xcresult`.
- Pixel_9_Pro / Android 15 executes one write and one restore instrumentation case,
  each without failure/skip. Reports are `.cache/connected-android/write-1792247548283792000.log`
  and `restore-1792247572552389000.log`. Two additional native entry/navigation/
  branding/permissions/single-task cases pass. Full unit reports contain 66 cases,
  zero failures/errors and two expected external-API skips in the ordinary run;
  the copied connected driver separately executes those real-API methods.
- The final generated Go API passes its own formatting/import policy/vet/build,
  full race suite and compiled process smoke, under its renamed module. No Go
  runtime/SQL changes were needed; earlier PostgreSQL migration/recovery evidence
  remains in the Go starter notes. This connected device run intentionally uses
  an isolated memory API and does not claim native PostgreSQL restart acceptance.
- All 32 CLI tests pass, including Swift-only/Kotlin-only/both selection, endpoint
  canonicalization/refusal, incompatible combinations, complete closure, source
  preservation and ordinary backend-only/co-located regressions. Starter inventory
  and documentation checks pass.

Investigations mattered: the first iOS form locator asserted an off-screen field,
then exposed Library reading only an observation-ignored lease. Reading the public
snapshot and adding the hosted regression fixed the actual presentation issue.
Android UTP explicitly uninstalled both APKs after each connected invocation;
installing once and using two raw instrumentation processes made vault restoration
measurable. A dialog button also requires a direct click rather than a scroll-to
operation intended for content. No timeout increases or weakened assertions were
used to obtain the final pass.

The registered Daily + go-connected profile is now **connected starter ready** on
Swift and Kotlin. This is selected local Debug evidence, using installed caches;
cold dependency installation, other OS/device coverage, production identity,
release archives/signing and cloud/provider delivery were not exercised.

## Learning and next scope

Read [composition versus co-location](../../notes/patterns/connected-products-and-composition.md),
then the platform ProductApp/product walkthroughs. The
[pending follow-up](pending-notes.md) now preserves command input across termination
without automatic replay. A read cache/offline launch, multiple ordered writes,
Files, inbox, Realtime and system push need their own explicit source closure and
acceptance before joining the registered product combination.

## CLI 0.13.0 pending recovery

The connected selection now stages exact Notes commands into native SQLite before
HTTP and restores them after process termination for explicit receipt lookup/retry.
The final independent `recovery-verified` copy passes both platform process/fault
scenarios against its own Go API; source files were not patched after generation.
Read [pending construction, test receipts and limits](pending-notes.md#observed-verification--2026-10-17).
This adds a bounded single-slot journal, not offline reads or automatic synchronization.
