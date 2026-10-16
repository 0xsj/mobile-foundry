# Repository organization

Use one repository for the reusable native foundation, its working catalogs,
shared specifications, and related projects. Swift and Kotlin have independent
builds. Cross-platform agreement lives in contracts and fixtures.

## Reusable native implementations

`frontend/swift` owns Swift code and `frontend/kotlin` owns Kotlin code. Each
platform's catalog imports its real reusable modules. Put catalog-only samples
and controls for simulated failures in the app rather than the libraries.

The Swift layout grows from the first package:

```text
frontend/swift/
  apps/FoundryCatalog/          # Xcode iOS application
  apps/ProductApp/             # Clean Daily product entry and configuration
  packages/FoundryKernel/       # Outcomes and failure vocabulary
  packages/FoundryHTTP/         # Native transport and wire admission
  packages/FoundryServices/     # Domain ports and provider adapters
  packages/FoundryData/         # Native SQLite storage adapters
  packages/FoundryQuery/        # Pure query snapshots and mutation phases
  packages/FoundryUI/           # Tokens, themes, native components
  packages/FoundryGraphics/     # Native Metal surface, shaders and frame policies
  packages/FoundryPhysics/      # Pure fixed-step toss simulation and contacts
```

Use Swift Package Manager for reusable capability libraries and an Xcode
application for the catalog. Each package owns its `Package.swift`, `Sources`,
and `Tests`. Native app features and composition live in the Xcode app.

The Android layout grows inside one Gradle build:

```text
frontend/kotlin/project/
  app/                         # Compose catalog application
  product/                     # Clean Daily product application module
  core/kernel/                 # Initialized pure Kotlin library
  core/http/                   # Native transport and wire admission
  core/services/               # Domain ports and provider adapters
  core/data/                   # Android SQLite storage adapters
  core/query/                  # Pure Kotlin query and mutation state
  core/ui/                     # Android library with Compose components
  core/graphics/               # Native GL surface, shaders and frame policies
  core/physics/                # Pure Kotlin fixed-step toss simulation
  gradle/                      # Wrapper and shared version catalog
  settings.gradle.kts
  build.gradle.kts
```

The build is initialized in `project/`, and the app depends on `core/kernel`.
Add reusable Gradle modules to this build; keep common versions in its version
catalog and keep its wrapper versioned.

## UI directory families

Inside `FoundryUI/Sources/FoundryUI` and `core/ui/src/main/kotlin/dev/mobilefoundry/ui`,
use Bento's separation with native naming:

```text
Swift                            Kotlin
Styles/                          styles/
  Tokens/                          tokens/
    Primitives.swift                 Primitives.kt
    Semantic.swift                   Semantic.kt
    Space.swift                      Space.kt
    Typography.swift                 Typography.kt
    Shape.swift                      Shape.kt
    Motion.swift                     Motion.kt
    Material.swift                   Material.kt
  Presets/V1.swift                 presets/V1.kt
Theme/FoundryTheme.swift          theme/FoundryTheme.kt
Components/Feedback/Query/        components/feedback/query/
  QueryContent.swift               QueryContent.kt
  QueryCopy.swift                  QueryCopy.kt
Components/Feedback/Mutation/     components/feedback/mutation/
Components/Forms/TextField/        components/forms/textfield/
Components/Forms/SubmitButton/     components/forms/submitbutton/
Components/Layout/Surface/        components/layout/surface/
```

The [style guide](../STYLES.md) explains ownership and consumption. The
[component map](COMPONENTS.md) lists implemented mirrored component leaves
across Forms, Display, Layout, Feedback, Navigation, Overlays, Shells, Patterns
and Charts. The initial UI reservations are complete. TabBar uses native
TabView on Swift and a floating Material bar on Kotlin.
Keep variants/configuration beside their component;
do not create a build module per family. App-owned token and component galleries
live under `Sources/Tokens` on Swift and `ui/tokens` on Kotlin. Domain state,
service selection, and asynchronous orchestration remain outside these folders.

The five-tab prototype lives in Swift app `Sources/Shell` and Kotlin app
`ui/shell`. Studio opens the existing catalog separately; Account exposes the
app material choice. Route/presentation ownership stays in the app even though
shared AppShell supplies layout slots and TabBar supplies controlled native chrome.
The middle Camera feature lives in `Sources/Camera` and `ui/camera`. Its native
session and photo decoder stay app-owned; it shares the Image studio editor in
`Sources/Graphics` and `ui/graphics`. Only admitted CPU pixels enter the graphics
package. See [camera/photo behavior](../contracts/behavior/camera-photo.md).
PhotoLibraryImporter in each Camera folder adapts the native selected-photo
result into that same admitted image boundary.

## Capability names

| Responsibility | Swift package | Android module |
| --- | --- | --- |
| Outcomes and failures | FoundryKernel | core/kernel |
| HTTP transport | FoundryHTTP | core/http |
| Domain service ports and provider adapters | FoundryServices | core/services |
| Query snapshots, mutation phases and pure transitions | FoundryQuery | core/query |
| Local repositories and storage | FoundryData | core/data |
| Session lifecycle | FoundrySession | core/session |
| Synchronization | FoundrySync | core/sync |
| Components, tokens, and patterns | FoundryUI | core/ui |
| Graphics integration | FoundryGraphics | core/graphics |
| Bounded 2D physics | FoundryPhysics | core/physics |
| Fakes and fixture support | FoundryTestSupport | core/testing |

Kernel, HTTP, Services, Query, Data, Session, UI, Graphics and Physics are implemented.
Sync and TestSupport still reserve responsibilities. Create a package or module when
its first implementation is ready; source folders can organize smaller pieces
inside a module.

## Dependency direction

Kernel code stays independent of UI, HTTP, storage, and renderers. HTTP depends
on kernel contracts. Services use the transport interface and own response
decoding. Repositories own application data access. Session and sync define
the interfaces they consume and receive concrete adapters through composition.

FoundrySession/core:session own identity/account values, SessionAuthority and
CredentialVault ports, session controllers and native credential adapters.
They depend on Kernel. The catalog selects a labeled demo authority and shared
device vault; transient login/password drafts remain in its Session/ui/session
feature. The [account-access guide](blueprints/account-access.md) adds the HTTP
adapter in Session, account factories/lease-bound CRUD in Data and inline editors/
probes in the same app feature. Services stays independent of Session. Device
account data has a new namespace; the existing guest collection stays unchanged.

Services also expose domain ports that memory or SDK adapters can implement
without HTTP. The notes exemplar keeps its store/ViewModel and rendering in
the catalog's feature folder, with adapter selection in app composition.
FoundryQuery/core:query now owns the generic snapshot vocabulary and pure
transitions; it depends only on kernel. FoundryUI/core:ui renders that state
with native controls and content slots. Neither owns a service or request
lifetime. A shared query runtime/cache remains separate work.

The Forms feature composes NoteCreator separately from NotesService. Draft and
submission orchestration stay in app Forms/ui/forms; reusable fields and
mutation feedback stay in UI. Catalog scenarios and injected wire responses
belong in app composition, including simulated write failures.

UI components receive values and callbacks; app features connect components
to services and repositories. Graphics receives scene values and interaction
commands through an explicit boundary. Rendering events become durable
application operations at the feature boundary.

The GPU effects gallery lives in app `Sources/Graphics` / `ui/graphics`.
FoundryGraphics keeps policies at its source root, Metal integration in `Metal/`,
and bundled shader source in `Shaders/`. core/graphics mirrors this with root
policy classes, `gl/` adapters and `src/main/assets/foundry_graphics/` shaders.
Neither graphics library depends on UI tokens or services. Shared policy fixtures
live in `contracts/fixtures/graphics`; GPU handles never enter shared contracts.
Image studio and Product studio use those same app directories. PreviewValues
at the graphics source root holds owned image/mesh input and bounded editing
values; native preview adapters share one surface across the two consumers.
`assets/source` and `assets/manifests` own canonical content/provenance;
`make assets-sync` copies it to iOS Resources/Graphics and Android app assets/graphics.
`make assets-check` rejects mismatched hashes or copies. Runtime catalog decoders
stay in the app, so the reusable module has no bundled product-path assumptions.

Keep domain-specific service and feature folders named by capability, such as
`identity`, `account`, or `scene`. Avoid a general helpers folder that mixes
transport, storage, UI, and business rules.

The [physics playground](blueprints/physics-playground.md) lives in app
`Sources/Physics` / `ui/physics`. Its pure `FoundryPhysics` / `core/physics`
modules contain simulation values and contacts without UI or Graphics dependencies.
App-owned native Canvas drawing consumes world snapshots; the feature owns
gestures, frame loops and foreground/manual pause. It is not yet a CLI capability.

## Shared contracts and assets

`contracts/behavior` contains native behavior and backend boundary policies.
`contracts/fixtures` contains portable inputs and expected outcomes.
`contracts/http` contains public specifications for owned backends. Generated
Swift and Kotlin wire code stays within the relevant platform service boundary.
`contracts/capabilities` reserves synchronous module request/response contracts;
`contracts/events` integration facts; `contracts/realtime` client live protocols.
Account access/facts are implemented contracts; realtime remains reserved. See
[module boundaries](../contracts/behavior/module-boundaries.md).

`assets` owns shared graphics inputs and logical asset manifests. Platform shader
source belongs to the package or module that compiles it. Put transformed asset
output under the ignored `.cache` directory and keep conversion tooling in
`scripts`.

## Product projects and experiments

`projects/<name>` owns a product's features, application shells, backend
composition, contracts, and infrastructure. During foundry development,
products can reference local reusable libraries. A future generator should
produce an independent project with its selected foundation source and tooling.

`experiments/<name>` owns a focused investigation, its setup, and recorded
results. Promote a useful implementation into a reusable native module once
its contract and lifecycle behavior are established.

## Documentation and tooling

Keep repository direction in `README.md`, architectural boundaries in
`ARCHITECTURE.md`, and setup steps in `docs/SETUP.md`. Record decisions under
`docs/decisions` and construction specifications under `docs/blueprints`.

Keep learning in `frontend/swift/notes`, `frontend/kotlin/notes`, and the shared
root `notes` directory. Backend notebooks live in `backend/<profile>/notes`.
Module walkthroughs mirror paths from their owning
implementation root; transferable findings use Bento's lifespan categories.
[The notes workflow](NOTES.md) defines ownership and reading handoffs.

Use `scripts` for executable checks, fixture processing, and asset conversion.
Use `templates` for future generated-project overlays and setup material.
Reusable runtime code remains in its native implementation directory.

## Backend ownership

`backend/go` implements startup in `cmd/server`, listener/drain ownership in
`internal/server`, and reusable errors, clock, ID, env/config, logger, HTTP,
principal and module assembly packages under `pkg`. Health has `app/query`,
`transport/http/v1`, `module.go` and `build.go`. Business modules retain `domain`,
`app/command`, `app/query`, `infra/<adapter>` and versioned transports. Account and
Audit implement memory/PostgreSQL stores, event mapping/receiving and access
capabilities. `pkg/persistence/postgres` owns mechanics; each module's adapter owns
SQL, `migrations/<owner>` holds embedded deployment artifacts, `cmd/migrate`
applies/checks them and `infra/local` holds the owned development stack.
Identity now implements domain principals/sessions, application commands/queries,
security/store/event adapters, HTTP and credential capability transports.
Account adds personal bootstrap and owned discovery behind its Directory port;
native Go authority adapters remain separate. The Go module is initialized;
[runtime behavior](../contracts/behavior/go-runtime.md) and its notebook describe
the implemented slice.

The [capability catalog](../backend/go/CAPABILITIES.md) lists module ownership and
selection. Consumer-owned application ports are implemented by
`infra/capabilities`; provider admission belongs in `transport/capability/v1`.
`infra/events` maps internal facts and receives external integration messages.
Generic invocation, event delivery and live broadcast mechanics belong in `pkg`;
module-owned outbox/inbox storage belongs beside its repository adapter. These
placements are implemented by the Account/Audit memory/PostgreSQL slices.
Remote invocation and broadcast remain planned. See
[construction](blueprints/account-audit.md).

`backend/supabase` owns its self-hosted Compose snapshot and support files.
`backend/firebase` owns its emulator image, Compose configuration, rules, and
indexes. Each owns setup commands and its notebook. Provider-specific native
adapters belong in the corresponding Swift or Kotlin implementation.

Put infrastructure shared by profiles in `infra`. Keep profile-specific local
stacks beside their source so setup and configuration have one owner. Select
the first connected workflow before introducing business adapters or modules.

## Live read placement

NotesObserver/ObservableNotesRepository live in Services beside the domain ports.
NotesChanges and SQLite observation live in Data. Catalog LiveNotesStore/Reader
own query/subscription state; LiveNotesCatalogModel retains source bundles above
storage branches. The account example adds a lease-owned live projection. Kotlin's
CatalogPresentationModel retains the nested ViewModelStore across Activity
recreation and clears it on dismissal. Native learning step 44 mirrors these owners.
See [observation construction](blueprints/notes-observation.md).

## Product shell placement

Swift `apps/FoundryCatalog/Sources/Shell/Patterns` and Kotlin
`project/app/.../ui/shell/patterns` own five full-screen example compositions,
profile identities/actions and gallery state. Existing UI AppShell, DetailShell,
AuthShell, SplitPane and TabBar stay in their component directories. The examples
add no new shared UI family. Contracts/fixtures describe portable behavior;
native app tests and module notes follow the actual owners. Future generator
overlays belong in `templates`; source compositions remain app-owned until the
selected product dependencies and independent output are verified. See
[construction](blueprints/product-shells.md) and [readiness](STARTER-READINESS.md).

The clean Daily entries are `apps/ProductApp` and `project/product`. Each owns
product configuration, root composition, placeholder screens and native entry
tests. They import UI and its Query/Kernel closure, with no catalog dependency.
Swift build identity lives in `Config/Product.xcconfig`; Kotlin installed identity
in `product.properties`, label in product resources and source namespace stays
stable. Runtime theme defaults and tab metadata stay in product source.
The [inventory](../templates/profiles/native-daily.json) selects exact source/test/
fixture paths and a product-only Gradle settings overlay. `bin/mobile-foundry`
anchors the Python CLI to its checkout; `cli/mobile_foundry` owns registration,
validation, copying, identity edits, generated guides and tool diagnostics.
`cli/tests` covers safety; `cli/acceptance` holds temporary feature probes excluded
from normal output. Native output verification is recorded in
[readiness](STARTER-READINESS.md). Backend extraction remains future work.

`cli/mobile_foundry/capabilities.py` admits optional capabilities through explicit
registered combinations. The [local-data inventory](../templates/profiles/native-daily-local-data.json)
selects only local Services/Data source and portable tests/fixtures. The
[overlays](../templates/overlays/local-data) own product-specific Notes composition,
CRUD/reader models, Library presentation and reduced local module build files.
Foundation ports/SQLite/observation remain in native source. The validator reads
effective overlaid dependencies/settings, allows explicit partial source trees
and checks required capability files. Notes HTTP adapters live in their own
source files so local ports do not import transport. Other capability combinations
need explicit registration and independent native acceptance.

Workspace and Browse inventories select their own complete app source sets,
sharing only the product entry/configuration and UI/Query/Kernel closure. Their
navigation/state/screens/tests live in [shell overlays](../templates/README.md#workspace-and-browse),
then become editable app-owned files in generated output. Product target/module
names stay stable; the CLI profile chooses the composition. Shared SplitPane
owns width/text policy, while product owners admit identities and handle Back.
Read [shell starter behavior](../contracts/behavior/shell-starters.md). Canvas and
Setup remain catalog examples; local-data is registered only for Daily.

## Local data placement

NotesRepository and admitted CRUD commands live in Services alongside the domain
values. The mutable memory adapter also lives there; native SQLite adapters live
in FoundryData/core:data. App-owned LocalNotesComposition selects the private
file/source, and LocalNotesStore/ViewModel owns query/mutation/draft lifetime.
The existing read-only Notes and Forms galleries retain their injected providers.
See [local persistence](../contracts/behavior/local-notes.md) for the current
guest collection and schema/version policy; account partitions and native live
readers have their own contracts. Remote synchronization remains deferred.
