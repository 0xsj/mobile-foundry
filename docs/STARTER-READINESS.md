# Starter readiness

The user requested an explicit handoff when a project/profile becomes a good
foundation for a new product. Assess each selected native/backend profile
independently and report milestone changes during development handoffs.
Last assessment: 2026-10-13, after Go CLI extraction and independent backend acceptance. Native connected-profile readiness remains separate.

## Readiness levels

| Level | Meaning |
| --- | --- |
| Foundation candidate | Reusable boundaries and selected behaviors work in the reference application; extraction is worth starting. |
| Standalone starter ready | A clean, renamed product has been generated outside this checkout, builds/runs independently and passes the checks for its selected capabilities. |
| Connected starter ready | A standalone starter additionally exercises a real chosen backend's sign-in, renewal, account isolation and remote data behavior. |

A local-only product can reach standalone readiness without authentication,
remote synchronization, every catalog component or every backend. GPU capability
is optional; device performance claims require separate measured evidence.
Readiness applies to the selected profile and tested behavior, not a blanket
production guarantee for the whole catalog.

## Current assessment

| Profile | Current state | What remains before recommending generated products |
| --- | --- | --- |
| Swift local UI / Daily | **Standalone starter ready** through mobile-foundry 0.1.0; product-owned configuration/screens and UI → Query → Kernel closure. | Ready for local UI products. Add deliberate feature capabilities and release branding/signing as needed. |
| Kotlin local UI / Daily | **Standalone starter ready** through mobile-foundry 0.1.0; product-owned resources/screens and product-only module settings. | Ready for local UI products. Add deliberate feature capabilities and release branding/signing as needed. |
| Swift Daily + local-data | **Standalone starter ready** through mobile-foundry 0.2.0; local Services/Data and injected CRUD/live-reader exemplar. | Ready for local stored-data products. Replace the Notes domain and supply release branding/signing as needed. |
| Kotlin Daily + local-data | **Standalone starter ready** through mobile-foundry 0.2.0; local module overlays and native SQLite/observation. | Ready for local stored-data products. Remote/account/sync behavior needs separate capabilities. |
| Swift Workspace | **Standalone starter ready** through mobile-foundry 0.3.0; app-owned adaptive list/detail. | Ready for UI products; replace placeholder projects and add feature capabilities/signing deliberately. |
| Swift Browse | **Standalone starter ready** through mobile-foundry 0.3.0; retained local search and detail/back. | Ready for UI products; replace placeholder collections and add chosen providers later. |
| Kotlin Workspace | **Standalone starter ready** through mobile-foundry 0.3.0; native Back, saveable state and adaptive panes. | Ready for UI products; durable records and deep navigation need separate capabilities. |
| Kotlin Browse | **Standalone starter ready** through mobile-foundry 0.3.0; saveable search/detail and native Back. | Ready for UI products; collection persistence/provider operations are separate capabilities. |
| Swift/Kotlin Physics | **Catalog capability verified**: pure 2D modules, native court/control consumers, five numeric cases per platform and five total native interactions. | Register a deliberate CLI feature combination and independently verify generated output before recommending physics-enabled starters. |
| Swift/Kotlin Go session | **Catalog integration verified**: deployment-bound authority, real HTTP/account scope, rotation checkpoints, discovery/switching and actual native-vault workflows. | Independently generated connected products remain required; Android process relaunch is not claimed from route restoration. |
| Swift/Kotlin Go remote data | **Connected foundation candidate**: account-scoped Notes, CAS and receipt recovery verified against Go through URLSession/OkHttp; catalog owners/controls build and pass recovery tests; iOS Notes CRUD/Keychain process relaunch passes. | Select/extract a connected CLI combination and independently verify generated products. Durable native queues/sync remain optional future capabilities; Android Notes UI gestures are not established by host tests. |
| Go backend | **Standalone starter ready** through CLI 0.5.0: independently generated API, auth/account/Notes/Files flows, race checks and PostgreSQL migration/crash recovery. | Ready to start Go APIs. Generated native session/account/remote-data composition needs its own connected acceptance; auth recovery, roles and deployment remain deliberate product work. |
| Supabase integration | Local development stack present; native provider adapters/policies unconnected. | Real auth/account/remote Notes adapters and policies, then connected starter verification. |
| Firebase integration | Local emulator setup present; native provider adapters/rules unconnected. | Real auth/account/remote data adapters and rules, then connected starter verification. |

Native runtime evidence is recorded in the
[Swift handoff](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#product-shell-variations)
and [Kotlin handoff](../frontend/kotlin/notes/modules/project/app/README.md#product-shell-variations),
including references to the earlier local foundation slices.
Local provider infrastructure alone does not establish a connected mobile starter.
The [Daily inventory](../templates/profiles/native-daily.json) records source/test
closure, identity edit locations, exclusions and the product-only Kotlin settings
overlay. `make starter-check` shares the CLI's profile validator and remains
read-only. `make cli-test` checks generation safety. Independent native acceptance
provides the additional runtime evidence recorded below.

Clean-entry evidence, 2026-10-10: both native products build; two hosted iOS
checks and two actual Android Activity checks pass for Daily navigation, theme
retention and product-owned runtime metadata. `make starter-check` passes, plus
four negative inventory probes covering missing dependencies/fixtures and an
escaping path. See the [Swift product handoff](../frontend/swift/notes/modules/apps/ProductApp/README.md)
and [Kotlin product handoff](../frontend/kotlin/notes/modules/project/product/README.md).
Those initial runs used this checkout and did not establish standalone readiness.

## Files-enabled Go acceptance — 2026-10-17

CLI 0.5.0 includes Files in the full Go backend. The independent media-final
output under /private/tmp/mobile-files-starter-hk73dwex uses
`github.com/example/media-final/backend` and passes formatting/import/vet/build,
race/smoke, disposable PostgreSQL 14.24 and API crash recovery, plus its generated
Make-runner auth/Files upload/download/deletion/receipt workflow. The short-module
media-api output passes memory/race and runner checks too. Private local object
storage is ignored by generated git configuration and must be retained with the DB.

Both native catalogs build and their real HTTP Files adapters pass URLSession/
OkHttp receipt recovery; each platform's two owner tests pass. These catalog
screens are not part of generated native/Go auth/media composition. Cloud object
storage, background transfer, long-term cleanup and physical-device performance
remain separate work. Read [Files construction](blueprints/files-media.md).

## Go backend acceptance completed

CLI 0.4.0 registers Go. `--platform none --backend go` generates an API-only
project; adding Go to a native profile co-locates an independent API. This first
selection includes Health, Identity, Account, Audit and Notes. No native auth/data
integration or backend module subset is implicit.

Fresh outputs outside this checkout at `/private/tmp/go-starter-acceptance-q7d088qa`:

| Output | Identity | Observed evidence |
| --- | --- | --- |
| canopy-api | github.com/example/canopy-api/backend | Go formatting/import policy/vet/build/race and smoke; real generated Make-runner auth/account/Notes/CAS/receipts/renewal/sign-out; full disposable PostgreSQL suite and API crash recovery. |
| canopy-combined | a.example/canopy/backend; com.example.canopycombined | Both native local-data apps build; renamed Go formatting/import/vet/build/race checks pass alongside native output. |
| ready-api | example.com/ready-api/backend | Final registered inventory; Go checks/race/smoke and generated runner acceptance pass. |

28 CLI cases pass with zero failures, including six backend-focused cases.
The source/fixture/migration/import closure is copied, module/path policy rewritten,
source kept unchanged and output contains no required checkout references. Project
Compose names/volumes are independent; Compose configuration validates. PostgreSQL
execution used isolated local 14.24 because Docker's VM storage is read-only;
the pinned 18.6 Compose runtime was not started. Dependencies/toolchains used
installed caches; cold installation and release deployment remain separate.

The short a.example module exposed import-order drift after renaming. Sorting
ordinary import groups in the generator fixed formatting without requiring Go
at initialization. Co-located native builds do not establish auth/data wiring or
new native device behavior. Earlier standalone native local-data evidence is
unchanged. Read [Go construction](blueprints/go-starter.md) and
[verification reasoning](../notes/techniques/go-source-copy-verification.md).

## Workspace and Browse acceptance completed

CLI 0.3.0 adds `native-workspace` and `native-browse` for Swift, Kotlin or both.
Daily remains the default; local-data remains a Daily-only registered combination.
The new inventories select only their own product navigation/state/screens/tests
and UI/Query/Kernel. Go, session/HTTP and graphics selection remain future work.

Fresh outputs were generated outside the checkout under
`/private/var/folders/_t/3ghz_tk90d53wy21s4tb20h80000gn/T/mobile-shells-r1k8eu9q`.
Observed acceptance completed 2026-10-11:

| Profile / output | Identity | Native evidence |
| --- | --- | --- |
| Workspace / Swift-only | com.example.workspacereadyswift / Workspace Ready | Simulator build; three iOS cases on iPad Pro 11-inch (M5). |
| Workspace / Kotlin-only | com.example.workspacereadykotlin / Workspace Ready | Debug build; four actual Android cases and twenty shared unit cases. |
| Workspace / both | com.example.workspacereadyboth / Workspace Lab | Both native builds; three iPhone cases and four Android cases. |
| Browse / Swift-only | com.example.browsereadyswift / Browse Ready | Simulator build; three iPhone cases. |
| Browse / Kotlin-only | com.example.browsereadykotlin / Browse Ready | Debug build; three actual Android cases. |
| Browse / both | com.example.browsereadyboth / Browse Lab | Both native builds; three iPhone cases and three Android cases. |

All listed native results have zero failures/errors/skips. iOS has two hosted
identity/state cases plus one actual UI workflow per output. Workspace verifies
selection, compact Back, material retention, rotation and expanded iPad list/detail.
Android additionally checks Activity recreation, bounded 800→390dp reflow and
font scale 1.5 fallback without losing selected identity. Browse verifies native
search, filtering, empty results, clear, detail/back and retained search/material;
Android includes recreation and system Back. Product labels/IDs were configured
independently, and generated guides contain no source-checkout references.

Copied Swift Kernel/Query/UI checks pass 7/3/9 (nineteen total), and Kotlin checks
pass 9/3/8 (twenty total). Twenty-two CLI cases pass, including all six new shell/
platform copies, source isolation, required overlay drift, Daily default and
incompatible local-data refusal. Inspected Android list/detail captures and
exported iPhone/iPad navigation captures show the chosen products and layouts.
Artifacts/results/logs remain in the temporary acceptance parent above.

The first Android expanded assertion matched the project title in both panes;
scoping it to detail corrected that locator. SwiftUI exposes a full labeled switch
row; tapping its trailing control corrected a center hit. Container identifiers
also masked a Back identifier and were removed from layout wrappers. A host launch
wait stalled Android's shell and Xcode's temporary manifest before execution;
a sample showed dyld startup. Retried jobs with bounded tool PATH completed.
These observations do not establish a product performance budget.

Observed tools: Xcode 26.2 / Swift 6.2.3 / XcodeGen 2.46.0, iOS 26.2;
Gradle 9.1.0 / AGP 9.0.1 / Kotlin 2.3.20 / Compose BOM 2026.03.01,
launcher JDK 21.0.6 / compilation 17, API36_Test / Android 16. UI-only placeholders
do not claim durable records/settings, deep links, provider integration or release/
physical-device validation. The unchanged library closure retains the earlier
first-feature service/query/mutation evidence below. Read shared step 25/native
step 49 and [shell behavior](../contracts/behavior/shell-starters.md).

## Optional local-data acceptance completed

CLI 0.2.0 adds `--with local-data` to the existing native-daily profile, for Swift,
Kotlin or both. Omission retains UI-only scope. The registry selects a complete
[combination inventory](../templates/profiles/native-daily-local-data.json), not
an implicit overlay merge. Local manifests select the original guest Services/
Data code without HTTP/session/account dependencies. No backend is selected.

Generated outside the checkout under `/private/tmp/mobile-local-data-k1y9qusq`:

| Combination | Product identity | Observed checks |
| --- | --- | --- |
| Both | Field Ready / com.example.fieldready | Both native builds; eight iOS cases; 26 Android unit and eleven Android device cases. |
| Swift-only | Swift Ready / com.example.swiftready | Independent native build; all eight iOS cases pass. |
| Kotlin-only | Kotlin Ready / com.example.kotlinready | Independent native build; 26 unit and eleven device cases pass. |

iOS has seven hosted cases plus one actual UI workflow. It validates an empty
title, creates/edits, cancels deletion, terminates/relaunches the app, reads the
persisted record/current count and confirms deletion. Hosted cases cover Memory/
SQLite parity, guarded admission, late commits, post-commit read failure and
terminal owners. Android has three product UI cases and eight real Data cases
for SQLite CRUD, corruption/version refusal and cross-instance observation.
Product tests exercise validation, form actions, canceled/confirmed deletion,
live counts and Activity recreation. Results report zero failures/errors/skips.
UI form checks use unique records and preserve the existing collection.

Separate Android process evidence: a note was entered through native controls,
then the process was force-stopped; PID absence was verified before launching
the app again. Library displayed `Process rerun persisted` and its live count.
No reinstall/data clearing occurred between that write and relaunch. The inspected
capture is `android-process-relaunch.png` under the acceptance parent. iOS's
exported relaunch screenshot was inspected as well. The temporary emulator first
displayed a System UI timeout; its am_anr event identified com.android.systemui
startup at 22:36:58, not the product. Wait cleared it; the later app check passed.
This is emulator evidence, not a physical-device performance assessment.

Selected Swift package checks pass: Kernel 7 / Query 3 / UI 9 / Services 2 / Data 9
(30 total). Android library checks are Kernel 9 / Query 3 / UI 8 / Services 2,
plus four product state-owner checks (26 total). Full reference Services checks
also pass on both platforms, eleven each, after HTTP Notes source separation.
Nineteen CLI checks pass, including base/local platform selections, effective
overlay graph drift, required source/fixture exclusions, refusals and cleanup.
Source/configuration audits find no required checkout paths in all three outputs.
Built plist/APK labels/identifiers and native metadata checks confirm branding.

Observed tools remain Xcode 26.2 / Swift 6.2.3 / XcodeGen 2.46.0, Gradle 9.1.0 /
AGP 9.0.1 / Kotlin 2.3.20, launcher JDK 21 with compilation 17. Runtime is iOS
26.2 and Android 16/API36_Test. The tests use installed toolchains/dependency
caches; fully cold dependency installation, old OS/device coverage, accessibility,
release signing, account migration, backend and synchronization remain separate.
Read shared step 24/native step 48 for ownership and verification reasoning.

## Initial UI-only independent acceptance completed

CLI version 0.1.0, Python 3.14.8, 2026-10-10. Generated from an unrelated working
directory into `/private/tmp/mobile-foundry-cli.nQJrnN`; output has its own guides,
build files, wrapper, fixtures and source provenance, with no source-checkout links.

| Combination | Product | Observed output checks |
| --- | --- | --- |
| Swift-only | Swift Sprout / com.example.swiftsprout | Simulator build, two hosted native entry cases, installed launch and inspected capture. |
| Kotlin-only | Kotlin Sprout / com.example.kotlinsprout | Debug build, two real Activity cases, installed launch and inspected capture; fresh build with no task-cache reuse also passes. |
| Both | Orbit Lab / com.example.orbitlab | Both native builds, three hosted iOS cases and three real Android cases after adding the temporary service/query/mutation probe; both launches inspected. |

The combined output's selected library checks pass using copied fixtures: Swift
Kernel 7 / Query 3 / UI 9; Kotlin Kernel 9 / Query 3 / UI 8. Native results report
zero failures/skips; Android XML also reports zero errors. Built plist/APK identity
and label match CLI inputs. Source/configuration audit finds no required paths
back to the checkout. Gradle cache hits initially retained old source locations
in build reports; these are generated artifacts, not copied source/build inputs.
The Kotlin-only fresh build executed all 58 tasks with `--no-build-cache --rerun-tasks`.

The [feature probe](../cli/acceptance/README.md) is added only to temporary output:
an injected memory service returns Kernel results, a state owner settles Query/
Mutation snapshots and Today renders the counter. Android exercises real action
taps; Swift checks service sharing/isolation and separately hosts native entry
controls. Swift imports require explicit package link products; Kotlin uses an
explicit product.R import. No foundation library changes were required.

Thirteen CLI checks pass for combinations, branding, alternate source/cwd,
destination/source preservation, exclusions, refusals, failure/interrupt cleanup,
installation and doctor behavior. The actual command is installed in ~/.local/bin;
add that directory to PATH. Xcode 26.2 / Swift 6.2.3 / XcodeGen 2.46.0 and Android
Studio launcher JDK 21.0.6 / compilation toolchain 17 / Gradle 9.1.0 / API 36 are
the observed tools. iOS runtime is 26.2; Android runtime is 16. Older OS/device,
release archive/signing, full accessibility, real providers and GPU budgets are
outside this local UI gate. Canvas/Setup and other capabilities remain extraction candidates.
Read native learning step 47 and shared step 23 for implementation/evidence.

## Standalone acceptance

Before announcing a profile as ready, record an actual generated-product run:

1. Generate into a new temporary destination outside this repository. Include
   selected native modules plus their dependency closure, required resources,
   contracts/fixtures, wrappers and setup tooling. No references back to this
   checkout or its parent directories may be required by the resulting app.
2. Apply product display name, iOS bundle identifiers, Android application identity
   and product configuration. Signing/team settings and credential namespaces
   must be deliberate product inputs. Preserve the reusable component names.
3. Start with a small product shell. Keep kitchen-sink screens, Notes/session
   fixtures and graphics demos as explicit examples/capabilities rather than
   requiring their full gallery in every product.
4. Follow the generated setup guide, build and launch each selected native app,
   and run its capability checks using included fixtures. Add a small product
   feature through the existing service/query/mutation seams to verify usability.
5. Verify required dependency/resource selection, safe destination refusal,
   failure cleanup, source preservation and exclusion of caches, local secrets,
   developer signing files and machine-specific SDK settings. Record generator
   version, source provenance, selected profiles and actual evidence.

The first profile is local native UI with no backend; data/session/graphics can
be explicit later capabilities. Swift-only,
Kotlin-only and both-platform output can be admitted as each is verified.
Connected provider profiles come afterward; durable outbox/sync is a further
capability with its own acceptance scenarios.

## CLI direction

Bento provides the useful precedent: explicit starter registration, list/init
commands, independent source copies, product metadata and generated setup guides.
Its current native support must be designed separately: the existing Bento CLI
registers Node/Go starters, and its mobile profile is Expo rather than Swift/Kotlin.

The implemented [mobile-foundry CLI](../cli/README.md) uses Python standard library
and provides list/init/doctor. It selects Swift/Kotlin/both and product identity
from registered Daily/Workspace/Browse profiles. Backend none remains default;
Go is independently verified and available, including backend-only output.
Local-data is an optional registered
capability for Daily; other capabilities and Canvas/Setup require later extraction/acceptance.
Template overlays belong in `templates`;
reusable source stays in native modules. Connected native provider profiles follow
their own implementation and acceptance after the standalone Go extraction.

The [five shell variations](../contracts/behavior/product-shells.md) remain
app-owned examples. Daily now has separate clean entries:
[Swift](../frontend/swift/apps/ProductApp/README.md) and
[Kotlin](../frontend/kotlin/project/product/README.md). Swift-only, Kotlin-only
and combined Daily copies passed the independent gates above, with and without
local-data. Workspace/Browse select distinct UI-only overlays with their own
independent acceptance gate. Next candidates are Canvas/Setup or session/HTTP/graphics selection. See
[standalone verification reasoning](../notes/techniques/standalone-starter-verification.md)
and [template ownership](../templates/README.md).
