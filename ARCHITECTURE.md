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
| Query and mutation state | Generic snapshots, write phases and pure transitions; no request runtime or cache |
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

The first value-only slice is implemented under [the kernel contract](contracts/behavior/kernel.md)
and [construction specification](docs/blueprints/kernel.md): standard Swift
Result and a generic Kotlin Outcome, immutable failure payloads, retry timing,
and explicit public projection. The next implemented slice adds [HTTP and health](contracts/behavior/http.md):
URLSession/OkHttp transports, problem and JSON admission, metadata, owned
deadlines and cancellation, health services, and injected catalog scenarios.
Diagnostic observers preserve known dependency causes outside public failures;
full reporting infrastructure remains a separate capability.

The [notes/query slice](contracts/behavior/notes-query.md) implements a domain
service port with memory and HTTP adapters, injected feature state owners, and
provider-independent list rendering. It establishes refresh retention and
latest-result admission without adding a shared query cache or persistence.
The subsequent [query/UI extraction](contracts/behavior/query-ui.md) adds
FoundryQuery/core:query value state and FoundryUI/core:ui rendering. Notes and a
scalar state gallery consume both; request orchestration remains feature-owned.
The [token slice](contracts/behavior/ui-tokens.md) adds one native V1 preset,
semantic colors and scales, scoped system-aware themes, and a Tokens catalog.
[Styles](STYLES.md) separates token definitions, preset values, native theme
adaptation, and component families following Bento's organization. UI remains
independent of services and feature request lifetime.
The [everyday component batch](contracts/behavior/ui-components.md) adds unbranded
action, search, display, feedback, selection and page/settings APIs. Native slots
and caller state keep them reusable across verticals; the app-owned Components
gallery exercises composed patterns and native overlays. Display, loading,
grouped fields and collection composition keep image admission, validation,
selected IDs and transient notice lifetime with their caller. Contextual help
and navigation retain caller-owned presentation/routes; small adaptive layouts
measure native children without adding scroll or collection state. DetailShell
reserves header/body/action regions while screens supply scrolling and routes.
Compact choices, bounded quantities and disclosure keep committed drafts with
their caller; a floating ActionBar does not create operation state.
Rich input keeps native editing state separate from draft storage. Passive
requirements/progress project feature readiness; AuthShell and OnboardingPage
provide layout slots while the app owns focus, step transitions and submission.
Solid/Glass material styles are independent of light/dark colors. Content panels
remain opaque; floating surfaces use native glass or a bounded Compose backdrop
with an opaque fallback. Renderer frame ownership and external GPU integration
remain separate from theme resolution.

The [forms/mutation slice](contracts/behavior/forms-mutations.md) adds an admitted
CreateNote command, a separate NoteCreator write port, memory/HTTP adapters,
pure MutationState and native field/submit/feedback components. Features own
drafts, validation timing, write execution and result admission. Submitting is
set before dispatch to prevent concurrent local duplicates. A missing response
does not establish rollback or authorize replay; idempotency and reconciliation
remain data/backend capabilities.

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

The first [GPU slice](contracts/behavior/gpu-effects.md) implements optional
FoundryGraphics/core:graphics modules with bounded settings, pure resolution/time
policies and native surfaces. Metal/MTKView and OpenGL ES 2/GLSurfaceView render
Ripple and a ray-marched Orbit without external assets or an engine dependency.
The catalog owns controls; renderers own GPU handles, attachment/context lifetime
and frame submission. Shared policies, native shader pixels, Android lifecycle
and iOS hosted drawable checks are exercised. Physical-device performance remains
unmeasured, so the broader graphics proof below is still incomplete.

The [procedural expansion](contracts/behavior/gpu-effects.md#product-use-cases)
also exercises ambient flow, a reflective material card, supplied liquid progress,
a finite celebration playhead and a supplied scalar field. The feature owns
completion/data/event meaning; shader time owns only decorative phase. These
inputs reuse the existing native surface and bounded uniform upload.

The [preview follow-up](contracts/behavior/graphics-previews.md) adds two concrete
consumers: an image adjustment editor and a mesh product viewer. Owned opaque
sRGB pixels or validated triangles enter the renderer alongside bounded edit,
viewport, camera and finish values. Asset reference identity retains uploads
across ordinary edits; context recreation rebuilds from CPU input. Catalogs own
asset decoding and feature state. The internal triangle format is an exemplar,
not a universal engine schema or general importer. Persistence, import/export
and product-specific variant rules remain above this seam.

The [camera slice](contracts/behavior/camera-photo.md) supplies another input to
the same image editor. App-owned AVFoundation/CameraX adapters own permission,
session lifetime and still capture. Native decoding normalizes orientation,
flattens alpha and bounds dimensions before admitting RasterImage. The shell
holds only transient CPU pixels across tab changes; neither the camera session
nor platform image handles enter the shared graphics boundary.
System photo pickers supply a second source through app-owned import adapters.
Only the chosen asset is read, normalized and admitted; selected-library access
does not require camera or full-library permission. Picker/URI objects remain
above the CPU image boundary and are not persisted as a durable asset recipe.

The [compositor](contracts/behavior/compositor.md) extends the retained preview
surface with straight RGBA admission, linear premultiplied upload, masking, blend
modes and three offscreen targets for blur/glow. Renderers retain pass resources;
features retain editable values. Optional profiles separate CPU encoding, completed
Metal GPU intervals (when available), and estimated texture payload. GLES 2 GPU
timing remains unavailable. The [device protocol](docs/GRAPHICS-PROFILING.md)
defines future sustained measurements; no physical-device budget is established.

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
The first exemplar selects Metal and OpenGL ES 2; evaluate other renderers when
their scene or platform requirements apply. Record any shader,
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

Activity UI follows the same boundary: SectionHeader, AvatarGroup, TimelineItem,
ExpandableText, RefreshContainer and LoadMoreFooter receive supplied content,
state and actions. Native list/refresh gestures stay in UI; feed records, stable
IDs, refresh/page coordination, failures and task lifetime stay in the app.
The catalog uses a local paged fixture, not another service/query abstraction.

Collection editing UI receives selected flags, token drafts/content and row
actions. Filtering, admission, stable record identity, guarded mutations and undo
snapshots remain app-owned. Native swipe state is transient presentation; restoring
a screen must not replay a command from its old gesture position.

Communication UI receives draft, send eligibility, transfer phase/copy and native
content/action slots. File admission, operation identity, retry/cancellation,
delivery results and post-send clearing stay in the feature. The local conversation
fixture owns native keyboard placement and keeps transfer recovery in scrolling
content. It does not establish an upload service, message store or outbox.

Small chart UI receives admitted samples, stable category IDs, explicit bar maxima
and caller-formatted summaries/values. The feature owns grouping, units, periods,
empty-data admission and goal changes. Native Canvas/layout draws summary visuals;
the optional graphics renderer and analytics services remain independent seams.

Media UI also receives native presentation state and admitted artwork slots.
Carousel delegates native paging; per-record ratings/favorites and explicit
page commands remain above lazy pages in app composition. MediaOverlay hides
only passive art, while its meaning and actions remain independent controls.

Scheduling UI receives wall-clock readings, calendar dates, supplied day IDs
and agenda copy. Date ordering, availability, instant conversion and command
admission stay in the feature. Temporary native picker drafts are separate from
both feature drafts and applied sessions; reusable UI does not request calendar
permission or schedule notifications.

Adaptive workspace UI receives destination/path identities, selection and native
primary/detail slots. Local width and text size choose presentation; feature
selection, filtering, compact detail intent and back policy stay above layout
branches. The rail emits actions and breadcrumbs project a supplied path.
SplitPane is a bounded layout within an app-owned route; it does not own a router
or replace native navigation containers for application-level navigation.

Tables receive admitted small pages, stable row/column identities, supplied widths
and native header/cell slots. Sorting, tie-breakers, page size, totals, empty-data
projection and row action admission stay in the feature. Native horizontal scroll
preserves column alignment; page controls emit intent without fetching records.
A real cursor or numbered-page service should fit the existing service/query seams.

Account UI receives identity/context choices, session copy and permission
projections. Account switching, credential ownership and authenticated service
composition remain feature responsibilities. A destructive session intent must
carry its original account/device IDs and be revalidated at confirmation.
Device capabilities have their own scope: account changes do not imply a new OS
permission state. UI permission cards expose rationale/status/actions; platform
permission adapters and settings routing stay outside the reusable UI layer.

Discovery receives supplied literal text runs and action copy. Search matching,
ranking, filter drafts/application, recent-query policy, saved IDs and result
routing belong to the feature. Result open controls contain passive preview;
bookmarks and other native actions remain independent siblings. Keep selected
identity above its visible filtered projection. Real requests and cancellation
fit the existing service/query seams without entering reusable row components.

Commerce UI receives formatted amount copy and native slots. Product identity,
inventory limits, cart quantities, code admission, currency/rounding and totals
belong to a feature or authoritative service. A draft code and an applied discount
are separate values; a review snapshot is separate from a recalculated current
total. Inline field button/keyboard use one admission callback without performing
an effect. Real quotes, reservations and mutations fit the existing thin seams.

Notification UI receives supplied count/read/time copy, passive artwork and native
action slots. Stable identity, grouping, read-on-open, filter scope, archive/undo
and detail presentation belong to the feature. A filtered projection is separate
from stored read/archive identities. Push delivery, OS notification permissions,
device tokens and real inbox synchronization belong to platform/service adapters.

Plan UI receives feature availability, formatted prices and usage meaning. Draft
selection, billing choice, reviewed command and applied entitlement are distinct
feature values. A review application rechecks eligibility and the original plan/
cycle snapshot; changing a draft does not change allowance or erase usage.
Reusable cards/meters do not load products, verify purchases or authorize exports.
Real billing and entitlement services fit the existing query/mutation seams.

Files UI receives flattened row copy, nonnegative depth, selected state and
independent open/disclosure/action eligibility. The feature owns hierarchy
identity, ordered projection, expansion, ancestor search, favorites and retained
selection. FileTypeMark receives supplied format text; no file inference or OS
access enters reusable UI. The bounded acyclic Files fixture demonstrates this
boundary, while real provider adapters, child loading, paging and permissions
remain outside TreeRow.

Sharing UI receives passive identity and link copy plus native access/action
slots. The feature owns member identity, roles, invitation lookup/admission,
protected-owner policy, confirmation revision and explicit clipboard effects.
The local one-workspace preview guards revision-bound removals and retains
nonsecret values; a real service must admit workspace/account scope and current
version before applying membership or issuing/revoking an access token. No
provider, email sending or clipboard work enters MemberRow/ShareLinkCard.

Playback UI receives supplied identity/artwork, formatted time/state and independent
native transport/timeline/action slots. Selected identity, remembered position,
queue policy, speed, repeat, command admission and metadata belong to the feature.
The catalog uses a manually advanced local timeline; its playing flag owns no
audio resource. A real native player adapter must publish engine snapshots and
admit seek/transport commands at its own lifecycle boundary. Engine scheduling,
loading, interruptions and OS media sessions stay outside PlaybackControls and
NowPlayingCard.

Verification UI receives canonical code drafts, supplied destination copy and
independent status/action slots. CodeFormat owns bounded input mechanics; challenge
identity, attempt admission, delivery, expiry, resend and authoritative verification
belong to the feature/service. The local preview captures attempt ID plus challenge
generation/channel and rejects stale completion. Code/pending/result presentation
are transient on Android recreation. Real challenge time and identity must come
from an adapter; manual UI countdown values cannot authorize authentication.
