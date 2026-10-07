# Native foundation architecture

Status: Draft. Swift and Kotlin, the reusable component catalog, and preparation
for demanding graphics are established direction. The boundaries and construction
order below are proposed implementation guidance.

The foundation should support personal tools, connected applications, and
interactive graphics projects through a consistent application architecture.
Native kitchen sinks will exercise the reusable components and services, while
a graphics gallery will test rendering integration early.

## Ownership boundaries

| Boundary | Owns |
| --- | --- |
| Kernel | Outcomes, failure vocabulary, identifiers, clocks, diagnostic metadata |
| HTTP | One application request attempt, deadlines, cancellation, wire decoding, response metadata |
| Services | Endpoint contracts, validated responses, narrow domain values |
| Repositories | Application data, local observation, local and remote data coordination |
| Sync | Durable pending commands, retry policy, confirmations, incoming changes, conflict handling |
| Session | Credential restoration, renewal, expiry, revocation, current authentication scope |
| Features | Use cases, screen state, presentation, feature-specific rules |
| UI system | Semantic tokens, controls, composed patterns, accessibility, motion |
| App composition | Dependency selection, startup, navigation, deep links, lifecycle wiring |
| Graphics | Render surfaces, frame scheduling, transient interaction, GPU resource ownership |

Features depend on explicit service, repository, or rendering interfaces.
Concrete networking, storage, platform, and rendering implementations are
selected at composition boundaries. Create separate packages or build modules
where they protect a useful boundary; a responsibility alone does not require
another build target.

## Kernel and failure handling

Carry Bento's expected-failure policy across owned boundaries. Anticipated
validation, permission, conflict, and dependency failures are typed values.
Unexpected defects retain their diagnostic identity and reach an appropriate
reporting boundary.

Use Swift outcome types with typed failures and a Kotlin sealed outcome type
whose variants express the same behavior. Decide concrete type names and
signatures in the kernel construction specification.

Preserve stable failure categories and operation-specific codes. Validation
failures carry field information; throttling can carry retry timing. Public
presentation selects deliberate copy by code or category. Diagnostic causes
remain separate from public and persisted failure representations.

Cancellation must cooperate with structured concurrency. Swift task
cancellation and Kotlin coroutine cancellation stop owned work. Adapters must
preserve that behavior when classifying errors; mapping cancellation into a
public outcome occurs only at a boundary whose contract explicitly requires it.

## HTTP and application data

Transport owns wire behavior and response metadata, including request IDs,
ETags, and idempotency headers. Services decode responses and expose domain
values. UI and feature state use those values rather than generated wire models.

Application retries, credential renewal, and durable synchronization sit above
the transport. Their ordering and interaction require explicit contracts so
one failed operation cannot cause duplicate writes or competing refreshes.

Local persistence owns migrations and account-scoped records. For synchronized
features, repositories expose local observations; remote results update local
state before reaching observers. A feature may select local-only storage,
offline reads, queued writes, or richer synchronization according to its needs.

## Identity and account capabilities

Keep identity, authentication, session, account, and authorization distinct.
Identity represents the person; authentication proves identity; session owns
the current device's authenticated access; account defines data ownership;
authorization determines permitted actions.

Connected examples should exercise startup restoration, sign-in, renewal,
expired access, sign-out, and account switching. Guest use and team membership
are separate product capabilities rather than required behavior for every app.
Native authentication callbacks and secure credential storage belong to
platform adapters.

## Offline synchronization

For features supporting queued writes, save the local change and its pending
command atomically. Give the command a stable identity, original account scope,
and any version precondition needed by the operation. Confirmation updates
local state and settles pending work durably.

Retry policy distinguishes transient failures, denied access, permanent
refusals, conflicts, and cancellation. An uncertain response can be retried
safely only when the receiving backend supplies the required idempotency behavior.

Incoming changes must account for deletions and reconcile with pending local
edits. Conflict policy belongs to the affected domain. The sync specification
must define checkpoints, command ordering, retention, and recovery after app
termination before claiming multiple-device synchronization.

Account switching invalidates the previous scope's in-flight results. Pending
commands cannot replay under another identity. Sign-out explicitly stops
authenticated work and applies the chosen local retention policy.

Foreground reconciliation provides progress when the app runs. Background
scheduling is an additional platform capability whose execution timing is
controlled by the operating system. See [Apple background scheduling](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate)
and [Android offline architecture](https://developer.android.com/topic/architecture/data-layer/offline-first).

## Native UI catalogs

Build a reference application on each platform using the actual reusable
components. Catalog examples should use deterministic fixtures and injected
dependencies so loading, failures, and offline behavior are easy to exercise.

The initial catalog should cover:

- Semantic colors, typography, spacing, shape, and motion.
- Buttons, inputs, validation, pickers, selection, and search.
- Navigation, sheets, dialogs, menus, and contextual actions.
- Lists, details, settings, loading, empty, and error states.
- Authentication, account settings, offline status, pending writes, and conflicts.
- Keyboard and focus handling, gestures, haptics, and permission prompts.

Exercise larger text, screen-reader use, dark mode, reduced motion, and relevant
screen sizes. Shared semantics can produce platform-appropriate layouts and
interaction behavior.

## Graphics integration

Keep rendering integration optional for ordinary applications. Establish the
boundary early with an interactive scene rather than introducing a complete
engine abstraction before a renderer has been exercised.

The application sends commands or bounded state snapshots and receives meaningful
events. The renderer owns frame scheduling, GPU resources, and transient
interaction state. Frame-time work should avoid application database access,
networking, and allocations that have not been justified by measurement.

For example, an object drag updates its visible transform inside the rendering
interaction. Completing the drag emits a durable transform change that can
enter the repository and sync workflow. Persist scene values and asset
references; GPU handles remain renderer-owned resources.

Reusable support should grow around proven needs:

- Surface attachment, resize, pause, resume, and resource cleanup.
- Camera controls, coordinate conversion, hit testing, and selection.
- Asset loading, versioned references, caching, cancellation, and eviction.
- Renderer initialization and asset failures expressed at application boundaries.
- Quality selection, reduced motion, hardware capability checks, and fallbacks.
- Frame timing, CPU/GPU measurements, memory use, and sustained device behavior.

Explore effects, interactive 3D scenes, and custom rendering separately.
[Metal](https://developer.apple.com/documentation/metalkit/mtkview/),
[RealityKit](https://developer.apple.com/documentation/realitykit),
[AGSL](https://developer.android.com/develop/ui/views/graphics/agsl), and
[Filament](https://github.com/google/filament) are candidates to evaluate.
Select the first renderer from the exemplar's needs. Record any shader,
native-library, or asset-toolchain requirements separately from the Swift and
Kotlin application baseline.

## Backend profiles

The repository reserves three independent profiles: an owned Go API following
Bento's module conventions, Supabase, and Firebase. Go has architecture
placeholders; Supabase has a local self-hosted Compose stack; Firebase has a
Compose wrapper around its Local Emulator Suite. Choose which profile the
first native connected workflow exercises when that slice is specified.

Owned APIs can share HTTP contracts. Managed SDK adapters must satisfy the
application's session and repository behavior with their actual service
semantics. Authentication, idempotency, versioning, and sync behavior require
conformance scenarios for each profile.

Client rendering and backend infrastructure are independent choices. Introduce
server computation only when a product requires a server-owned operation.

## Construction order and evidence

| Increment | Evidence required |
| --- | --- |
| Kernel and HTTP contracts | Failure classification, decoding, deadlines, cancellation, version and command metadata |
| UI catalogs | Interactive components and realistic screen states on both platforms |
| Early graphics proof | Touch interaction, lifecycle transitions, cleanup, and recorded measurements on real devices |
| Local exemplar | Durable edits, observable state, restart recovery, and migrations |
| Session and first backend | Restoration, renewal, revoked access, sign-out, and account isolation |
| Sync exemplar | Duplicate delivery, lost responses, restart during replay, deletions, and a two-device conflict |
| Graphics with application data | A durable scene edit survives restart and reconciles across devices |
| Starter extraction | Independent generated applications build and exercise their selected capabilities |

Set device and performance targets before judging the graphics proof. Choose
minimum OS versions, UI toolkits, storage libraries, renderer, and backend as
the relevant increments become concrete. Use the repository's
[implementation blueprint protocol](../IMPLEMENTATION-BLUEPRINT-PROTOCOL.md)
for file and symbol specifications after those slice contracts are established.
