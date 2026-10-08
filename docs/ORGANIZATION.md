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
  packages/FoundryKernel/       # Outcomes and failure vocabulary
  packages/FoundryHTTP/         # Native transport and wire admission
  packages/FoundryServices/     # Domain ports and provider adapters
  packages/FoundryQuery/        # Pure query snapshots and mutation phases
  packages/FoundryUI/           # Tokens, themes, native components
  packages/FoundryGraphics/     # Native Metal surface, shaders and frame policies
```

Use Swift Package Manager for reusable capability libraries and an Xcode
application for the catalog. Each package owns its `Package.swift`, `Sources`,
and `Tests`. Native app features and composition live in the Xcode app.

The Android layout grows inside one Gradle build:

```text
frontend/kotlin/project/
  app/                         # Compose catalog application
  core/kernel/                 # Initialized pure Kotlin library
  core/http/                   # Native transport and wire admission
  core/services/               # Domain ports and provider adapters
  core/query/                  # Pure Kotlin query and mutation state
  core/ui/                     # Android library with Compose components
  core/graphics/               # Native GL surface, shaders and frame policies
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
[component map](COMPONENTS.md) now reserves mirrored empty component leaves
with .gitkeep across Forms, Display, Layout, Feedback, Navigation, Overlays,
Shells and Patterns. Reserved directories have no implementation. Kotlin's
Navigation/TabBar counterpart is implemented; Swift uses native app TabView.
Keep variants/configuration beside their component;
do not create a build module per family. App-owned token and component galleries
live under `Sources/Tokens` on Swift and `ui/tokens` on Kotlin. Domain state,
service selection, and asynchronous orchestration remain outside these folders.

The four-tab prototype lives in Swift app `Sources/Shell` and Kotlin app
`ui/shell`. Studio opens the existing catalog separately; Account exposes the
app material choice. Route/presentation ownership stays in the app even though
the future reusable AppShell component folder is reserved.

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
| Fakes and fixture support | FoundryTestSupport | core/testing |

These names reserve responsibilities. Create a package or Gradle module when
its first implementation is ready; source folders can organize smaller pieces
inside a module.

## Dependency direction

Kernel code stays independent of UI, HTTP, storage, and renderers. HTTP depends
on kernel contracts. Services use the transport interface and own response
decoding. Repositories own application data access. Session and sync define
the interfaces they consume and receive concrete adapters through composition.

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

## Shared contracts and assets

`contracts/behavior` contains behavior that both platforms must implement.
`contracts/fixtures` contains portable inputs and expected outcomes.
`contracts/http` contains public specifications for owned backends. Generated
Swift and Kotlin wire code stays within the relevant platform service boundary.

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

`backend/go` reserves Bento's `cmd/server`, reusable `pkg` packages, and
`internal/<module>` layers: `domain`, `app/command`, `app/query`, adapters under
`infra`, and versioned transports. Identity, Account, and Audit have placeholder
directories; the user initializes `go.mod` when ready.

`backend/supabase` owns its self-hosted Compose snapshot and support files.
`backend/firebase` owns its emulator image, Compose configuration, rules, and
indexes. Each owns setup commands and its notebook. Provider-specific native
adapters belong in the corresponding Swift or Kotlin implementation.

Put infrastructure shared by profiles in `infra`. Keep profile-specific local
stacks beside their source so setup and configuration have one owner. Select
the first connected workflow before introducing application adapters or backend
business code.
