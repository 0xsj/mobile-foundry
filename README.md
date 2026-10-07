# Mobile Foundry

Mobile Foundry will provide reusable native application foundations in Swift
for iOS and Kotlin for Android, with common UI patterns, application services,
offline behavior, and support for demanding graphics interactions.

The foundation follows Bento's approach: explicit kernel and HTTP contracts,
expected failures as values, injected services, a substantial component
catalog, and working examples of common application behavior. Swift and
Kotlin implementations share behavioral specifications and fixtures while
remaining idiomatic to their platforms.

## Established direction

- Swift and Kotlin are the application languages.
- Build reusable components and common UI patterns ahead of individual products.
- Include native kitchen sinks that exercise components and complete states.
- Establish identity, authentication, session, and account boundaries.
- Design local persistence and offline synchronization as explicit capabilities.
- Prepare for GPU effects and interactive 3D through rendering and lifecycle boundaries.
- Support a small set of backend profiles suited to different product needs.

## Repository layout

```text
mobile-foundry/
  frontend/
    swift/
      apps/          # iOS catalog and graphics gallery app
      packages/      # Reusable Swift packages
    kotlin/project/  # Android catalog and reusable Gradle modules
  backend/
    go/              # Candidate owned API profile
    managed/         # Candidate managed service configuration
  contracts/
    http/            # Owned API wire specifications
    behavior/        # Shared native behavioral specifications
    fixtures/        # Portable test scenarios and expected outcomes
  assets/
    source/          # Original graphics assets
    fixtures/        # Small assets used by examples and tests
    manifests/       # Asset identity, versions, and platform variants
  infra/
    local/           # Development dependencies
    testing/         # Integration and performance tooling
    deployments/     # Backend deployment profiles
  docs/
    decisions/       # Architecture decisions
    blueprints/      # Construction specifications for individual slices
  scripts/           # Shared repository tooling
  templates/         # Future project generation overlays
  projects/          # Product projects consuming the foundation
  experiments/       # Focused technical and graphics investigations
```

[Organization](docs/ORGANIZATION.md) explains ownership and native module
placement. [Setup](docs/SETUP.md) provides commands and IDE steps for building
the Swift and Kotlin projects. [Architecture](ARCHITECTURE.md) defines the
proposed behavioral boundaries.

The iOS SwiftUI catalog and Android Compose application are initialized, along
with the Swift kernel package and Kotlin/JVM kernel module. Kernel behavior and
the full component catalogs are the next implementation slices. Graphics
engines, storage libraries, and backend profiles remain open decisions.

The initial app deployment targets are iOS 17 and Android API 24. Review these
bootstrap baselines when selecting graphics capabilities.

## Native builds

```sh
make kernel-build
make ios-build
make android-build
make android-test
```

Run `make ios-generate` after editing the iOS application's `project.yml`.
Use [Setup](docs/SETUP.md) for IDE paths and individual build commands.

## Learning notes

Start at [the notes index](notes/README.md) for the Swift and Kotlin reading
orders. Each platform follows Bento's split into modules, language, patterns,
concepts, techniques, and substrate. Module walkthroughs mirror source paths;
shared mobile findings live in root `notes`.

[The notes workflow](docs/NOTES.md) defines note shape, evidence, and how each
implementation slice leaves a reading handoff. [AGENTS.md](AGENTS.md) carries
that requirement into future sessions. Run `make notes-check` to check links,
module paths, and native example labels.

## Repository references

- [Bento frontend service conventions](../bento/frontend/SERVICE-QUERY-CONVENTION.md)
- [Bento kernel](../bento/frontend/next/lib/kernel/)
- [Bento HTTP transport](../bento/frontend/next/lib/http/)
- [Bento component catalog](../bento/frontend/next/app/kitchen-sink/)
- [Bento semantic style tokens](../bento/frontend/STYLES.md)
- [Bento backend boundaries](../bento/backend/CONVENTIONS.md)
- [Implementation blueprint protocol](../IMPLEMENTATION-BLUEPRINT-PROTOCOL.md)

Write construction specifications for individual slices once their ownership
and behavioral contracts are established. Extract starter generation after
the reference applications demonstrate those contracts.
