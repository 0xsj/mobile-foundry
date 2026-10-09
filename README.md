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
    go/              # Owned API using Bento's Go architecture
    supabase/        # Local self-hosted Supabase stack
    firebase/        # Local Firebase Emulator Suite
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
the Swift and Kotlin projects. [Styles](STYLES.md) explains the Bento-style token,
theme, and component directory splits. [Architecture](ARCHITECTURE.md) defines the
proposed behavioral boundaries.

The iOS SwiftUI catalog and Android Compose application are initialized, along
with the Swift kernel package and Kotlin/JVM kernel module. The first kernel
slice implements typed outcomes, failure payloads, and public projection, with
[shared behavior and fixtures](contracts/behavior/kernel.md). The
[HTTP and health slice](contracts/behavior/http.md) adds URLSession/OkHttp,
problem decoding, metadata, deadlines and live/ready services. Open **HTTP health**
in either catalog for six injected scenarios without a backend.
The [notes/query slice](contracts/behavior/notes-query.md) adds interchangeable
memory/HTTP services and native state owners. Open **Notes service seam** in
either catalog to exercise content, empty, failure, refresh and cancellation.
The [query/UI slice](contracts/behavior/query-ui.md) extracts reusable generic
state and async presentation into FoundryQuery/FoundryUI and core:query/core:ui.
Open **Async UI patterns** to select states with a scalar payload; the notes
example uses the same components. The [token slice](contracts/behavior/ui-tokens.md)
adds the Foundry Studio V1 light/dark preset, native typography, spacing, shape and motion,
plus scoped themes. Open **Tokens** to inspect the roles and try appearance,
reduced motion, and native action samples. The home **Glass surfaces** switch
selects a material theme; Tokens also offers local Solid/Glass selection,
transparency reduction, and floating controls over a movable scene.
Read [surface themes](STYLES.md#swappable-surface-themes) and the
[learning handoff](notes/patterns/material-themes-and-backdrops.md).
The [forms/mutation slice](contracts/behavior/forms-mutations.md) adds native
fields, local/server validation, guarded submission and public write feedback.
Open **Forms and mutations** to create a note through memory or injected HTTP,
exercise failures, and reset after confirmation. The note is an exemplar;
persistence and shared read/write coordination remain separate capabilities.
The [GPU slice](contracts/behavior/gpu-effects.md) adds an optional FoundryGraphics/
core:graphics boundary using Metal on iOS and OpenGL ES on Android. Open **GPU
effects** to explore Ripple, Orbit, ambient Flow, a reflective Material card,
Liquid progress, replayable Particles and a supplied density Field. Change
strength/quality, pause and try reduced motion. The renderer owns GPU scheduling and resource lifetime;
[graphics notes](notes/patterns/renderer-frame-ownership.md) explain the seam and
native execution evidence. Physical-device performance remains unmeasured.
The [expanded use cases](notes/patterns/graphics-inputs-and-event-time.md) explain
how feature-owned progress, finite event playheads and bounded data enter the
same native drawing seam.
Open **Image studio** for before/after exposure, saturation, vignette and zoom/pan;
open **Product studio** for an actual lamp mesh with orbit/pinch, three finishes
and an optional turntable. These [preview foundations](contracts/behavior/graphics-previews.md)
share admitted assets and bounded values through a native preview surface.
Read [editable values and GPU resources](notes/patterns/editable-values-and-renderer-resources.md)
to adapt them to other verticals. Import/export, general model loading and saved
edits remain separate work.
**Compositor studio** extends the same preview surface with a transparent layer,
movable feathered mask, Normal/Multiply/Screen blending, blur/glow and comparison.
Its performance panel separates texture payload, CPU encoding and available Metal
GPU timing; GLES 2 timing is unavailable. Start with [the compositor notes](notes/patterns/premultiplied-compositing-and-render-passes.md)
and [physical-device profiling protocol](docs/GRAPHICS-PROFILING.md).

Open **Components** for 112 reusable building blocks across 30 families.
**Shells** demonstrates token-based stacks, decorative dividers and a reusable
app-shell layout with native tabs. Try spacing overrides, hide/show navigation
and per-page markers; the existing five-tab app also consumes these primitives.
**Verification** adds native one-time-code entry and a customizable verification
card. Open **Verification preview** to try incomplete/incorrect codes, pending
checks, cancellation, resend cooldowns and expiry with local fixture responses.
**Playback** adds transport controls and a now-playing card. Open **Playback
preview** to try seeking, remembered track positions, speed, repeat and recovery
with a manually advanced local timeline; no audio is played.
**Sharing** adds member rows and share-link cards. Open **Sharing preview** to
try local invitations, role changes, protected owners, removal and link access.
**Files** adds expandable tree rows and file-format marks. Open **Files preview**
to explore folders, ancestor-aware search, independent favorites and an inspector.
**Plans** adds feature rows, selectable plan cards and usage meters. Open
**Plans preview** to compare billing choices, review a local plan change and
try available, exhausted and exceeded allowances.
**Notifications** adds accessible count badges and notification rows with
independent actions. Open **Inbox preview** to try unread filters, grouped
updates, mark-visible-read, archive and undo.
**Commerce** adds price labels, product rows, an order summary and an inline
action field. Open **Cart preview** to try quantities, applied codes, delivery
choices, busy/empty states and a retained review snapshot.
**Discovery** adds suggested-search rows, result rows and literal highlighted
text. Open **Search workspace** to try recent queries, independent bookmarks,
applied versus draft filters, and empty or disabled states.
**Account** adds a profile header, account switcher, device-session rows and
permission cards; open **Account center** to try scoped device removal,
cancelled confirmations and shared photo-access preview states.
**Tables** adds sortable column headers, a scrollable data table and pagination;
open **Project ledger** to try sorting, empty records and guarded row actions.
**Workspace** adds a destination rail, breadcrumbs and a bounded split-pane
layout. Open **Adaptive workspace** to browse projects and keep selection/stars
while moving between one and two panes.
**Scheduling** adds time input, date ranges, day choices and agenda rows; open
**Schedule planner** to edit a draft, try invalid/unavailable dates and apply a
local session. **Insights**
adds sparklines, category bars, progress rings, trends and legends; open **Insights
dashboard** to try period selection, empty data and a local session goal. **Editing**
adds wrapping tags, row selection, native swipe actions and a library editor with
undo. **Communication** includes conversation rows, message bubbles, a glass
composer, attachments and transfer recovery. Open **Design room** to try them
together. **Media** includes native paging, per-study ratings/favorites, icon
actions and artwork overlays; preview values stay caller-owned. Complete graphics
engines, native storage libraries, and identity/sync implementations remain
future work. The backend
profiles are [Go, Supabase, and Firebase](backend/README.md); the Go module is
reserved for manual initialization, and both provider stacks have Compose setup.

The initial app deployment targets are iOS 17 and Android API 24. Review these
bootstrap baselines when selecting graphics capabilities.

## Native builds

```sh
make kernel-build
make kernel-test
make http-test
make query-test
make ui-test
make graphics-test
make assets-check
make ios-build
make ios-test
make android-build
make android-test
make android-ui-test
```

Run `make ios-generate` after editing the iOS application's `project.yml`.
Use [Setup](docs/SETUP.md) for IDE paths and individual build commands.

## Local backends

Start the profile you want to explore from the repository root:

```sh
make -C backend/supabase up
make -C backend/firebase up
```

Run the corresponding `make -C backend/<profile> down` to stop it while keeping
data. Supabase setup generates private local credentials. Firebase runs Auth,
Firestore, and Storage emulators with a demo project. Each profile documents
its endpoints and native connection requirements.

## Learning notes

Start at [the notes index](notes/README.md) for native and backend reading
orders. Each implementation follows Bento's split into modules, language, patterns,
concepts, techniques, and substrate. Module walkthroughs mirror source paths;
shared mobile findings live in root `notes`.

[The notes workflow](docs/NOTES.md) defines note shape, evidence, and how each
implementation slice leaves a reading handoff. [AGENTS.md](AGENTS.md) carries
that requirement into future sessions. Run `make notes-check` to check links,
module paths, and native example labels.
