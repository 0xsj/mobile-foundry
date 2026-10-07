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
  packages/FoundryKernel/       # First Swift package
  packages/FoundryHTTP/         # Added when transport is implemented
  packages/FoundryUI/           # Added with the UI catalog
```

Use Swift Package Manager for reusable capability libraries and an Xcode
application for the catalog. Each package owns its `Package.swift`, `Sources`,
and `Tests`. Native app features and composition live in the Xcode app.

The Android layout grows inside one Gradle build:

```text
frontend/kotlin/project/
  app/                         # Compose catalog application
  core/kernel/                 # Initialized pure Kotlin library
  core/http/                   # Transport library when implemented
  core/ui/                     # Android library with Compose components
  gradle/                      # Wrapper and shared version catalog
  settings.gradle.kts
  build.gradle.kts
```

The build is initialized in `project/`, and the app depends on `core/kernel`.
Add reusable Gradle modules to this build; keep common versions in its version
catalog and keep its wrapper versioned.

## Capability names

| Responsibility | Swift package | Android module |
| --- | --- | --- |
| Outcomes and failures | FoundryKernel | core/kernel |
| HTTP transport | FoundryHTTP | core/http |
| Typed remote services | FoundryServices | core/services |
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

UI components receive values and callbacks; app features connect components
to services and repositories. Graphics receives scene values and interaction
commands through an explicit boundary. Rendering events become durable
application operations at the feature boundary.

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
root `notes` directory. Module walkthroughs mirror paths from their owning
implementation root; transferable findings use Bento's lifespan categories.
[The notes workflow](NOTES.md) defines ownership and reading handoffs.

Use `scripts` for executable checks, fixture processing, and asset conversion.
Use `templates` for future generated-project overlays and setup material.
Reusable runtime code remains in its native implementation directory.

The placeholder backend directories reserve candidate profiles. Select a
profile against the first connected workflow before adding dependencies or
copying a backend implementation.
