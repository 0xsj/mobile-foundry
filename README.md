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
      apps/          # iOS catalog and clean ProductApp entry
      packages/      # Reusable Swift packages
    kotlin/project/  # Android catalog, clean product and reusable modules
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
  bin/               # mobile-foundry CLI executable
  cli/               # Source-copy generator, checks and acceptance probes
  templates/         # Registered inventories and extraction overlays
  projects/          # Product projects consuming the foundation
  experiments/       # Focused technical and graphics investigations
```

[Organization](docs/ORGANIZATION.md) explains ownership and native module
placement. [Setup](docs/SETUP.md) provides commands and IDE steps for building
the Swift and Kotlin projects. [Styles](STYLES.md) explains the Bento-style token,
theme, and component directory splits. [Architecture](ARCHITECTURE.md) defines the
proposed behavioral boundaries.

[Starter readiness](docs/STARTER-READINESS.md) tracks when each native/backend
profile can seed a new product. Swift/Kotlin Daily is now **standalone starter
ready**: renamed Swift-only, Kotlin-only and combined outputs build/run outside
the checkout. The [local CLI](cli/README.md) copies those selections; Go
combinations remain planned.

```sh
make cli-install
export PATH="$HOME/.local/bin:$PATH"
mobile-foundry list
# Run from an existing directory outside this checkout:
mobile-foundry init canopy --platform both --app-id com.example.canopy
# Include the editable, persistent Notes example instead:
mobile-foundry init field-notes --platform both --with local-data --app-id com.example.fieldnotes
# Choose adaptive project navigation or browse/search/detail:
mobile-foundry init workbench --profile native-workspace --platform both --app-id com.example.workbench
mobile-foundry init collections --profile native-browse --platform swift --app-id com.example.collections
```

Clean Daily product entries now live in
[Swift ProductApp](frontend/swift/apps/ProductApp/README.md) and
[Kotlin product](frontend/kotlin/project/product/README.md). Each owns four tabs,
placeholder screens, branding inputs and Solid/Glass selection while importing
only UI and its Query/Kernel closure. Use `make product-ios-generate`,
`make product-ios-build` or `make product-android-build`; `make starter-check`
checks the [extraction inventory](templates/README.md).

The optional [local-data combination](contracts/behavior/local-data-starter.md)
adds a Library Notes form, SQLite CRUD and an independent live reader. It selects
local Services/Data plus the existing UI/Query/Kernel closure, with no backend or
auth provider. Product composition chooses the adapter; Notes is an example
domain you can replace. This combination is also **standalone starter ready**
for Swift, Kotlin and both, including observed process-relaunch persistence.
The generated setup guide includes its native checks.

Workspace and Browse are also **standalone starter ready** for Swift, Kotlin or
both. Workspace adapts between a project list/detail and expanded panes; Browse
keeps local search when returning from detail. Both use product-owned placeholder
screens, a Solid/Glass header and the UI/Query/Kernel closure. Daily remains the
CLI default; local-data is currently a Daily-only option. Native navigation,
recreation and Workspace phone/tablet checks passed in independent outputs.
Read [CLI usage](cli/README.md) and [shell behavior](contracts/behavior/shell-starters.md).

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
The [local persistence slice](contracts/behavior/local-notes.md) adds
FoundryData/core:data, a CRUD repository seam and actual SQLite storage. Open
**Local notes** to create, edit and confirm deletion using Device storage or
Memory. Saved device notes survive reopening; writes work without a backend.
Read the [construction guide](docs/blueprints/local-notes.md) and native learning
step 41 for storage/versioning and commit-versus-observation lifetime.
The [session foundation](contracts/behavior/session.md) adds swappable authority
and credential-storage ports, identity/account values, restoration, renewal,
expiry and sign-out with guards against old session results. Open **Identity
and session** for Alex/Sam demo accounts, Device/Memory storage and lifecycle
controls. Device credentials use native Keychain/Keystore. This is a local demo;
real authentication remains future integration work.
The [account-access slice](contracts/behavior/account-access.md) adds authenticated
HTTP and separate local Notes per account. In **Identity and session**, save a
note as Alex, switch to Sam, then return to Alex. Drafts close with the session;
Device records remain after sign-out and guest Local notes stay separate. The
HTTP probe uses labeled local responses and explicit renewal, with no backend.
Read [construction](docs/blueprints/account-access.md) and native learning step 43.
The [observation slice](contracts/behavior/notes-observation.md) adds native live
readers over Memory and SQLite. Open **Live notes**, create/edit/delete a note,
pause the second reader, then resume it to read current data. **Identity and session**
also shows an account live reader that closes with its lease. This is local
observation; backend integration remains separate. Read
[construction](docs/blueprints/notes-observation.md) and native learning step 44.

Open **Studio → Open catalog → Product shells** for five native starting
compositions: a daily tabbed app, adaptive workspace, browse/detail collection,
canvas editor and guided setup. Each has small working local interactions and
Solid/Glass selection, using the existing UI primitives. Read
[shell behavior](contracts/behavior/product-shells.md),
[construction](docs/blueprints/product-shells.md) and native learning step 45.
They are app-owned examples; Daily has verified CLI extraction, while the other
four compositions remain candidates for additional starter profiles.
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
profiles are [Go, Supabase, and Firebase](backend/README.md). Go has a runnable
runtime and an [Account/Audit workflow](contracts/behavior/account-audit.md)
with local capability/event seams and swappable memory/PostgreSQL stores.
Explicit migrations, API recovery and owned Identity/session authentication are
verified, including repeatable personal bootstrap and owned-account discovery.
[Native Go session adapters](contracts/behavior/go-native-session.md) now provide
real sign-in, account selection, renewal/restoration and sign-out in both catalogs.
Remote data and backend CLI extraction remain future work. Both provider stacks
have Compose setup.

The initial app deployment targets are iOS 17 and Android API 24. Review these
bootstrap baselines when selecting graphics capabilities.

## Native builds

The catalog's **Physics playground** adds drag-to-throw and alternate aim/launch
controls, hoop/landing-zone targets, gravity/bounce controls, pause and reset.
Pure native Physics modules own the actual 2D simulation. Read the
[construction guide](docs/blueprints/physics-playground.md) and
[learning pattern](notes/patterns/fixed-step-physics-and-presentation.md).

```sh
make kernel-build
make kernel-test
make http-test
make query-test
make ui-test
make graphics-test
make physics-test
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

The Go runtime needs no external services:

```sh
make -C backend/go dev
```

Open `http://127.0.0.1:8080/v1/health/ready`. Run `make go-check go-test` for
foundation checks. [Go setup and learning](backend/go/README.md) describes its
ports, configuration and limits. [Go starter generation](docs/blueprints/go-starter.md)
is now available through `--backend go`, including backend-only output with
`--platform none`. Go is standalone ready; connected native extraction follows.

Start the profile you want to explore from the repository root:

```sh
make -C backend/supabase up
make -C backend/firebase up
```

Run the corresponding `make -C backend/<profile> down` to stop it while keeping
data. Supabase setup generates private local credentials. Firebase runs Auth,
Firestore, and Storage emulators with a demo project. Each profile documents
its endpoints and native connection requirements.

Account-scoped [remote Notes and receipts](contracts/behavior/remote-notes.md) now
connect both catalogs to Go through native repository ports. Open **Go identity
and accounts** to create/edit/delete remote notes and explicitly recover
unconfirmed writes. Read [setup and ownership](docs/blueprints/remote-notes.md).
PostgreSQL stores receipt/data atomically; native pending commands currently
remain transient. Independently generated connected profiles are the next
starter milestone.

## Learning notes

Start at [the notes index](notes/README.md) for native and backend reading
orders. Each implementation follows Bento's split into modules, language, patterns,
concepts, techniques, and substrate. Module walkthroughs mirror source paths;
shared mobile findings live in root `notes`.

[The notes workflow](docs/NOTES.md) defines note shape, evidence, and how each
implementation slice leaves a reading handoff. [AGENTS.md](AGENTS.md) carries
that requirement into future sessions. Run `make notes-check` to check links,
module paths, and native example labels.

## Connected media example

The Go backend includes account-owned Files with memory/PostgreSQL metadata and
replaceable byte storage. Both native catalogs can upload a gallery photo and
open the downloaded file in the image editor through an authenticated session.
Read [setup and verification](docs/blueprints/files-media.md).

The Go backend also includes an account-scoped in-app inbox: completed uploads
produce an upload-ready notice with read state and media preferences. Read
[Notifications setup and limits](docs/blueprints/notifications.md). Swift and Kotlin
now show the connected Account inbox in Go identity and accounts, with unread
filtering, read acknowledgements and media preferences. System push remains a
separate integration step.

The [session-bound installation registry](docs/blueprints/installations.md) now has Swift/Kotlin ports, native secure identity vaults and catalog controls for registration, token replacement, explicit rebinding and unregister. Identity revocation retires matching server targets; uncertain writes require readback. Optional [APNs/FCM token adapters](docs/blueprints/push-tokens.md) add explicit collection and a separate Register collected token action. Provider setup and live cloud checks are required; push sending remains separate.

Both catalogs now include [System notifications](docs/blueprints/system-notifications.md):
explicit device permission/settings, a real local test notification and admitted
navigation on open. The preview remains separate from account inbox preferences
and installation registration; cloud push is a later capability.

The [Go push-dispatch foundation](docs/blueprints/push-dispatch.md) checks live
session eligibility, rechecks a versioned installation and calls a swappable
gateway once. Invalid-address cleanup preserves newer registrations. CLI 0.8.0
includes the independently verified source; the default API selects no sender.

Optional [APNs/FCM preview providers](docs/blueprints/push-providers.md) now add
real HTTP adapters and a guarded manual preview endpoint. CLI 0.9.0 includes
the source/example and private-key copy exclusions. Provider selection is off
by default; live credentials, device delivery and remote native opens need their
own acceptance.

The [native remote preview](docs/blueprints/remote-notifications.md) now admits
the provider payload, records foreground receipt and routes taps into notification
diagnostics. iOS simulator pushes and Android fixture/PendingIntent checks are
separate from live signed-device delivery. Learning notes follow the value,
transport and app-navigation owners on both platforms.

[Queued Go previews](docs/blueprints/push-jobs.md) now retain manual delivery
intents behind guarded enqueue/status routes. An opt-in worker retries preflight
failures and retains uncertain sends without automatic resend. CLI 0.10.0 includes
the owned migration and independently verified source; it enables no worker by
default and requires PostgreSQL plus private provider configuration when selected.

[Owned job recovery](docs/blueprints/push-job-recovery.md) now cancels work before
sending or creates an explicit linked replacement while retaining the source.
Uncertain replacement requires duplicate-risk acknowledgement. CLI 0.11.0 includes
the independently verified source and 0004 migration; default selection remains off.
