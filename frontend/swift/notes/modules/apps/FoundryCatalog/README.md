# Swift catalog walkthrough

The catalog composes an HTTP health service with an injected wire transport and displays loading, admitted domain data, and public failures.

## Origin

Shell initialized 2026-10-07; HTTP example implemented and checked 2026-10-08
with Xcode 26.2, Swift 6.2.3, and an iPhone 17 Pro simulator on iOS 26.2.
This note mirrors `apps/FoundryCatalog/`. [HTTP and health contract](../../../../../../contracts/behavior/http.md) owns health behavior.

## Reading order

1. [project.yml](../../../../apps/FoundryCatalog/project.yml) registers the five local packages and application target.
2. [App entry](../../../../apps/FoundryCatalog/Sources/FoundryCatalogApp.swift) supplies CatalogView through FoundryTheme in the @main app's WindowGroup.
3. [CatalogView](../../../../apps/FoundryCatalog/Sources/CatalogView.swift) links Tokens, HTTP health, Notes service seam, and Async UI patterns from the Foundation section.
4. [HealthCatalogView](../../../../apps/FoundryCatalog/Sources/HealthCatalogView.swift) owns selected scenario, request generation, phase and composition.
5. [Services walkthrough](../../packages/FoundryServices/README.md) and [HTTP walkthrough](../../packages/FoundryHTTP/README.md) explain the reusable operations below the view.

## Walkthrough

CatalogView puts a List inside NavigationStack. NavigationLink supplies a
HealthCatalogView destination. Other foundation and graphics rows reserve
future work; they do not implement those capabilities.

The health view stores selection, a replay counter, and a phase with `@State`.
Its body renders loading, success(Health), or failure(Failure). Picker chooses
one of six injected responses. Run again increments the replay counter.
[Async ports](../../../language/swift-async-ports-and-continuations.md) explains the Swift types, and [View task lifetime](../../../substrate/urlsession-and-view-task-lifetime.md) explains `.task(id:)`.

Each view task sets Loading, creates the client with the selected injected
transport, and calls HealthService.ready. The transport waits briefly so loading
can be visible. The result is checked for cancellation before state publication.
Timeout uses a slow response and short budget. Replacement/removal cancellation
is silent; an unexpected exception reaches OSLog and deliberate generic copy.

Failure rendering calls publicInfo. Malformed JSON therefore shows An unexpected
error occurred without the internal message/code, while retaining request ID.
Validation displays admitted field strings; rate limiting displays timing without
automatically retrying. The health view uses standard SwiftUI controls. The later query/UI extraction
adds reusable async presentation for notes and the separate state gallery.

## Verification and limits

`make ios-generate` and `make ios-build` passed. Package HTTP/services tests
passed. The app was launched on a dedicated iPhone 17 Pro simulator: navigation,
Healthy with status/IDs/version, Malformed with redacted message, and Timeout
with deadline copy were observed through accessibility state and screenshots.
These are injected transport examples. They do not establish backend connectivity,
physical-device behavior, VoiceOver interaction, large text, GPU work or all
lifecycle races. Shared and native adapter tests cover request cancellation below
this view; [Verification techniques](../../../../../../notes/techniques/shared-fixtures-and-native-adapters.md) explains those evidence levels.

The 2026-10-08 seam review found that the view constructs the concrete client
and HealthService inside `load()`. A domain service protocol, injected store,
and app-level provider selection remain planned. See [Provider seams](../../../../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state).

## Notes feature and provider composition

Read [NotesComposition](../../../../apps/FoundryCatalog/Sources/Composition/NotesComposition.swift)
to see adapter selection and catalog-only delay/failure injection. Then follow
[NotesStore](../../../../apps/FoundryCatalog/Sources/Notes/NotesStore.swift),
[NotesScreen](../../../../apps/FoundryCatalog/Sources/Notes/NotesScreen.swift), and
[NotesCatalogView](../../../../apps/FoundryCatalog/Sources/Notes/NotesCatalogView.swift).
The screen receives state and callbacks; no provider choice or JSON enters it.

The observable main-actor store accepts `any NotesService`, retains the previous
successful snapshot during refresh/failure, and owns the active task handle.
Replacement or cancel advances a generation before canceling work. Only the
current generation can publish; parent cancellation is forwarded to the service
task. A changed catalog selection gets a new SwiftUI identity and store.
See [store mechanics](../../../language/swift-observable-stores-and-service-protocols.md).

[NotesStoreTests](../../../../apps/FoundryCatalog/Tests/NotesStoreTests.swift) passed
seven tests on iPhone 17 Pro/iOS 26.2 via `make ios-test`: both providers share
load/failure/recovery and empty-snapshot behavior; controlled continuations check
refresh retention, explicit/parent cancellation, late success/failure, and
current versus obsolete defect reporting. These gates deliberately ignore
cancellation, so generation admission is tested independently of cooperation.

The simulator accessibility state confirmed memory and HTTP content, plus HTTP
empty and unavailable/retry controls. Screenshot capture returned blank images
in this session; these checks establish semantics and navigation, not visual
layout or VoiceOver quality. The HTTP option uses injected responses, not a
running backend. The original health view still owns its inline state; the
notes store is the new domain seam exemplar.

## Reusable query and UI extraction

The 2026-10-08 extraction moves phase values and pure transformations into
[FoundryQuery](../../packages/FoundryQuery/README.md), and rendering into
[FoundryUI](../../packages/FoundryUI/README.md). NotesStore still owns generation,
cancellation and diagnostics. NotesScreen now supplies only copy, list emptiness,
and note rows; the store no longer duplicates its retained snapshot in a field.

[QueryCatalogView](../../../../apps/FoundryCatalog/Sources/Query/QueryCatalogView.swift)
adds Async UI patterns with a scalar string, manually selectable phases, and
Refresh/Retry/Cancel transitions. Seven store tests still pass. The UI walkthrough
records current simulator interactions and a usable layout screenshot, updating
the earlier session's screenshot limitation. Five local packages are now linked.

## Tokens and scoped previews

The 2026-10-08 slice installs the reusable theme at app entry and adds
[TokenCatalogView](../../../../apps/FoundryCatalog/Sources/Tokens/TokenCatalogView.swift).
It owns appearance/reduction controls and a nested theme around examples only.
The examples show sixteen roles, native typography, spacing, shape, action counts,
and a bounded position transition. Private section layout belongs to the catalog;
it does not establish a reusable card API.

The [UI walkthrough](../../packages/FoundryUI/README.md) links the actual token
families and three passing package tests. The final app builds and all seven
store regressions pass. Manual iOS checks confirm scoped dark rendering beneath
light controls, zero custom durations, and action/position state surviving
appearance changes. Broader accessibility and iOS touch measurement remain open.
Read [environment scope](../../../substrate/swiftui-token-environment.md) next.

The material extension adds a Glass surfaces binding owned by the app entry.
Tokens can inherit that style or preview Solid/Glass locally, independently of
appearance and reduced motion. Its floating scene keeps selection/position state
through material changes and exposes transparency reduction. See the
[UI material walkthrough](../../packages/FoundryUI/README.md#swappable-material-slice)
for source order, native fallbacks and observed verification limits.

## Forms and mutations

The 2026-10-08 slice adds [CreateNoteStore](../../../../apps/FoundryCatalog/Sources/Forms/CreateNoteStore.swift),
[CreateNoteScreen](../../../../apps/FoundryCatalog/Sources/Forms/CreateNoteScreen.swift),
[FormsCatalogView](../../../../apps/FoundryCatalog/Sources/Forms/FormsCatalogView.swift), and
[CreateNoteComposition](../../../../apps/FoundryCatalog/Sources/Composition/CreateNoteComposition.swift).
Trace raw draft → command factory → injected NoteCreator → admitted receipt.
The renderer handles native focus/Return with shared controls; the store owns
touched/error policy, synchronous busy admission, task and generation.

Provider/scenario bindings call use before changing their selection. Busy
changes are refused; idle replacement preserves draft/touched while clearing
server feedback. The catalog simulates six scenarios through memory and actual
HTTP service admission over an injected transport. Success locks input until
New note; missing confirmation keeps the draft and warns against blind replay.
onDisappear calls stop, invalidating admission before canceling work.

[CreateNoteStoreTests](../../../../apps/FoundryCatalog/Tests/CreateNoteStoreTests.swift)
add six passing iOS tests for validation ordering, both providers, retained
draft/server errors, duplicate/busy guards, reset, late success/refusal/defect,
current diagnostic identity and direct cancellation. Thirteen app tests pass
on iPhone 17 Pro/iOS 26.2 via `make ios-test`. The initial compiler crash from
the Binding setter method reference was resolved with a closure; see
[the framework note](../../../substrate/swiftui-form-focus-and-submit.md).
Read [shared mutation ownership](../../../../../../notes/patterns/forms-and-mutation-ownership.md)
for the distinction between local duplicate prevention and remote idempotency.

Manual iOS 26.2 checks confirmed required feedback/automatic field focus,
native Return submission,
receipt/disabled state/reset, draft preservation on Memory → HTTP, server title
feedback and recovery to HTTP success. A simulator CLI screenshot was inspected
for the populated error form: title/help/error, actions and panels fit without
overlap. CUA screenshots remained blank. Navigation reopening reset the abandoned
draft, and the form remained usable with the app Glass selection. This is one normal-size layout, not
an exhaustive Dynamic Type, keyboard, VoiceOver or device assessment.

The Glass follow-up on the same day corrects the form gallery's material usage:
both outer panels select the floating role, and a catalog-owned cobalt backdrop
makes the native glass visible. Explicit full-width layout constrains both
material variants equally. Input remains native and unblurred. The initial
always-content role had made the app toggle invisible here despite correct
environment propagation. Solid/Glass simulator CLI screenshots were inspected;
both show readable controls and the Glass capture shows the backdrop through
the outer panels. The final app build passes.

## GPU effects

The 2026-10-08 slice adds [GPUEffectsView](../../../../apps/FoundryCatalog/Sources/Graphics/GPUEffectsView.swift)
and links the optional [FoundryGraphics package](../../packages/FoundryGraphics/README.md).
The feature owns effect/quality/strength/point, Animate and reduction preview,
and running combines visibility, scenePhase, token motion and those choices.
Its native canvas persists across settings updates; retry alone changes identity.
Opaque controls sit below the renderer, independently of the app material style.
This does not claim that the renderer is captured by Glass.

`make ios-build` passes. All 14 app checks pass on iPhone 17 Pro/iOS 26.2,
including [GPUEffectsLayoutTests](../../../../apps/FoundryCatalog/Tests/GPUEffectsLayoutTests.swift).
The new hosted test checks actual native/texture and automatic-frame dimensions,
static quality updates, pause/resume and removal. An intermediate view-scale
assertion failed because contentScaleFactor varied with the manually reduced
drawable. Using independent screen nativeScale corrected the target. The earlier
offscreen shader test passed even while a hosted canvas stretched one pixel;
native layout now resizes before frame acquisition. Read
[MetalKit mechanisms](../../../substrate/metalkit-surface-and-shader-lifetime.md).

Simulator CLI screenshots show Ripple's field and a lit sphere/torus in Orbit.
Manual native controls select Orbit, pause, change strength, and keep Animate
paused when Reduce motion preview is enabled. These are separate from the real
offscreen Metal pixel comparisons in `make graphics-test`. CUA screenshots still
return blank output; the simulator CLI provides usable images. Touch behavior
on iOS, exhaustive orientations/accessibility and physical-device performance
need further checks; Android device tests exercise native touch separately.

## Product previews

[ImageStudioView](../../../../apps/FoundryCatalog/Sources/Graphics/ImageStudioView.swift)
and [ProductStudioView](../../../../apps/FoundryCatalog/Sources/Graphics/ProductStudioView.swift)
are two feature examples above the optional graphics package. [PreviewAssets](../../../../apps/FoundryCatalog/Sources/Graphics/PreviewAssets.swift)
decodes the bundled photograph and authored mesh in Task.detached, then the view
checks task cancellation before publishing. Expected missing/invalid assets use
kernel failures. Original GPU errors go to private OSLog, and public copy uses
Failure.publicInfo. Retrying changes surface identity only after failure.

Image tools own adjustment, comparison and viewport values. Product tools own
camera, finish and requested turntable; visible/scene/motion state gates running.
Neither view owns textures, buffers or shader source. The sample assets can be
replaced above the renderer without adding product IDs/services to its API.

[PreviewLayoutTests](../../../../apps/FoundryCatalog/Tests/PreviewLayoutTests.swift)
hosts both screens, checks drawable sizes and exercises static updates/removal.
All 15 app checks pass on iPhone 17 Pro / iOS 26.2. Simulator captures show the
upright original/edited photograph and a lit lamp mesh; the Metal package tests
verify actual filtering/material/camera pixels. Native drag/pinch and VoiceOver
behavior on physical iOS hardware are not established by these hosted checks.
Native simulator controls also changed the finish to Cobalt, rotated the camera
from 0.35 to 0.85 and zoomed distance from 4.50 to 3.75; a new capture showed the
larger blue lamp and retained target size. This confirms those control paths,
while the gesture/accessibility limit above still applies.
Read [preview substrate](../../../substrate/metal-preview-textures-and-meshes.md).

## Questions for the next session

- Why does the task ID contain a replay counter as well as scenario?
- Which data comes from the service, and which comes from presentation policy?
- Which UI patterns are now concrete enough to extract for another screen?
- What must move out of the view before a memory service can replace HTTP?
- Why are cancellation and generation admission both necessary after suspension?

## Related

[View protocols](../../../language/swift-protocols-and-opaque-return-types.md), [Swift reading order](../../../README.md), and [Setup](../../../../../../docs/SETUP.md).

## Expanded GPU use cases

The 2026-10-08 expansion adds Flow, Material, Liquid, Particles and Field to
the existing GPU effects destination. Read the updated EffectSettings and shader
before the catalog controls. Liquid takes supplied completion; Particles takes
a finite playhead; Field takes up to twelve owned point/weight/radius samples.
Field and particle canvases draw on input changes. Only the catalog owns Replay
event timing, with pause/reduction/lifecycle gates and a restart generation.
See [graphics inputs and event time](../../../../../../notes/patterns/graphics-inputs-and-event-time.md) for the cross-platform
reasoning, reusable product examples, upload budget and next questions.

Five graphics value/policy checks and two actual Metal execution tests pass.
The expanded effect test executes all seven branches, tests liquid endpoints,
particle playhead/time independence and changed/empty/zero-weight field data.
All fifteen iOS app regressions pass on iPhone 17 Pro/iOS 26.2 and the app builds.
Manual native checks confirm the seven-option menu, Liquid Empty/Half/Full,
Particles finite completion/reduction, and Field clear/add/trail (0/1/6 samples).
CLI screenshots show Flow, the material card, the half-full gauge and the trail.
An accessibility setValue attempt changed the exposed slider value without
driving SwiftUI state; it is not evidence of a successful iOS scrub interaction.
Actual fixed-playhead pixels are covered by Metal tests, and Android device
tests cover scrub cancellation. VoiceOver and physical-device cost remain open.

## Compositor studio

Claim: the catalog owns one transient composition and the renderer owns its
textures/passes; performance observations do not control rendering policy.

Added 2026-10-08. Read [the studio](../../../../apps/FoundryCatalog/Sources/Graphics/CompositorStudioView.swift) → [asset creation](../../../../apps/FoundryCatalog/Sources/Graphics/PreviewAssets.swift) → [graphics walkthrough](../../packages/FoundryGraphics/README.md). The existing opaque photograph loads off main; the small RGBA disc/ring is
code-authored locally and retained by feature identity. Layer/Mask/Compare tools
change placement or the divider, pinch scales, and labeled sliders provide native
alternatives for every edit. Reset restores composition defaults. Persistence,
import/export, arbitrary layers and product rules are outside this example.

Profile redraws defaults off. Reduced motion stops the repeated workload while
static controls still draw. Visibility/scene phase gate the iOS request.
The panel labels target dimensions, four passes, input/target payload, uploads,
individual target allocations, CPU encoding and available GPU timing separately.
The latest snapshot is not a statistical recorder or displayed FPS.

[Native integration checks](../../../../apps/FoundryCatalog/Tests/CompositorLayoutTests.swift) exercise retained canvas identity, static redraw, resizing, profile reporting,
continuous pause and removal. Hosted layout checks include the actual studio view; actual blend pixels run in the graphics package. Native slider/VoiceOver behavior still needs manual iOS coverage.
Read the platform index for final execution evidence; physical-device budgets
remain unmeasured. Follow [the profiling protocol](../../../../../../docs/GRAPHICS-PROFILING.md) and [the shared pattern](../../../../../../notes/patterns/premultiplied-compositing-and-render-passes.md). Next: how should a feature save a versioned
composition recipe without persisting native GPU handles?

## App icon asset

The 2026-10-08 icon slice fills the existing AppIcon asset-catalog slot with an
opaque 1024-square cobalt ribbon generated using the built-in imagegen tool.
The canonical original, derivative, full prompt and hashes live in shared assets.
Read [icon packaging](../../../substrate/swift-package-and-xcode-project-wiring.md#generated-app-icon)
and [the manifest](../../../../../../assets/manifests/mobile-foundry-icon.json).
The simulator build and asset checks pass; no new runtime code, signing or beta
upload is introduced. Next: complete the user's remaining pre-release slices,
then validate a signed device archive and App Store Connect distribution.

## Four-tab placeholder shell

Added 2026-10-08. Read
[AppShellView](../../../../apps/FoundryCatalog/Sources/Shell/AppShellView.swift),
then [app composition](../../../../apps/FoundryCatalog/Sources/FoundryCatalogApp.swift)
and [CatalogView](../../../../apps/FoundryCatalog/Sources/CatalogView.swift).
AppShellView owns a scene-stored String selection and an in-memory full-screen
presentation flag. Each destination has a native NavigationStack and empty
placeholder; Studio opens CatalogView, which keeps its own detail stack and
accepts an optional close callback. Closing returns to the selected Studio tab.
Account and catalog receive the same material Binding from app composition.

The app starts in Glass for visualization; FoundryTheme's default is still
Solid. Native TabView supplies tab semantics, safe areas and system glass,
independent of the Foundry surface switch. The static accent wash requires no
render loop. Read [framework mechanics](../../../substrate/swiftui-tab-selection-and-presentation.md)
and [shared ownership](../../../../../../notes/patterns/tabs-and-feature-lifetime.md).
The simulator build and all sixteen existing app/state-owner/native-layout
tests pass. Manual iPhone 17 Pro/iOS 26.2 execution covers all four tabs, both
material choices, Studio → catalog → Tokens → back → close, and restored
Studio selection after closing. A simulator CLI screenshot was inspected for
the empty Home layout and floating tab bar. Native CUA screenshot capture
became blank after modal dismissal; accessibility state and the CLI capture
provide the recorded evidence. Process/scene restoration, older iOS versions,
tablet adaptation and physical-device accessibility/performance are unverified.
Future destination features/stacks, deep links and authentication are absent.
