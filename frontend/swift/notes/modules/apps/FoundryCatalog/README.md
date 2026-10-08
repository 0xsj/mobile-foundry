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

## Camera and shared photo editor

Claim: Camera owns native capture while its admitted CPU photograph can feed
the existing GPU editor without retaining the camera session.

Added 2026-10-08. The fifth destination is inserted between Library and Studio.
[AppShellView](../../../../apps/FoundryCatalog/Sources/Shell/AppShellView.swift)
holds an optional RasterImage across ordinary tab changes. Read
[CameraView](../../../../apps/FoundryCatalog/Sources/Camera/CameraView.swift) →
[controller and engine](../../../../apps/FoundryCatalog/Sources/Camera/CameraCapture.swift) →
[PhotoDecoder](../../../../apps/FoundryCatalog/Sources/Camera/PhotoDecoder.swift) →
[ImageEditorView](../../../../apps/FoundryCatalog/Sources/Graphics/ImageEditorView.swift).
Camera is app-owned; the reusable graphics package still receives only owned
pixels and bounded adjustments. [ImageStudioView](../../../../apps/FoundryCatalog/Sources/Graphics/ImageStudioView.swift)
now loads the bundled asset and hosts that same editor with its previous defaults.

Enable camera explicitly requests permission. Selected tab, active scene and
viewfinder state gate session work; entering the editor stops capture. Flip
reconfigures the native input. The engine confines synchronous AVFoundation work
to a serial queue, correlates capture IDs, and tags events with a generation.
The controller rejects obsolete session and photo completions. Native rotation
coordinators handle preview and capture compensation separately. Decode applies
metadata orientation, bounds the thumbnail to 2048 and flattens it onto white
before admission. The project source declares the camera usage description.

Natural/Mono/Vivid/Soft set existing exposure/saturation/vignette values from
[PhotoFilter](../../../../apps/FoundryCatalog/Sources/Graphics/PhotoFilter.swift).
The camera editor begins neutral; the original catalog keeps its initial
comparison and sample adjustments. Retake clears pixels and returns to the
viewfinder. No save/export or durable draft is implied. Tab changes can recreate
editor controls, even though the photo itself remains in memory.

[PhotoDecoderTests](../../../../apps/FoundryCatalog/Tests/PhotoDecoderTests.swift)
uses a transparent 3000×1500 image with orientation metadata to check upright,
bounded opaque admission, plus malformed bytes. The complete eighteen-check
iOS app suite passes on iPhone 17 Pro/iOS 26.2, including the existing hosted
editor/renderer regressions. Simulator and unsigned generic-device builds pass;
the latter compiles the physical permission branch. Read
[AVFoundation substrate](../../../substrate/avfoundation-capture-and-photo-admission.md)
for manual Sendable confinement and native API limits.

Manual simulator checks confirm Sample photo, Mono/Vivid selection, retained
photograph after Home → Camera, and Retake returning to the disabled shutter.
CLI screenshots were inspected for native layout and the actual grayscale photo.
CUA's native screenshot remains blank; simulator CLI capture supplies the pixels.
A label on the horizontal filter ScrollView hid its individual buttons in the
observed accessibility tree, even with explicit child containment. Removing that
container label leaves four individually labeled, selected-state buttons. The
final adjustment builds and its buttons respond through native accessibility;
this is not a complete VoiceOver audit.

Physical capture, lens switching, OS interruption and device orientation still
need a real phone. Sample-photo editing is a separate simulator check; it cannot
establish those hardware properties. Next: how should export own encoding,
resolution and photo-library permission without changing preview ownership?

## Gallery input and outline tabs

The 2026-10-08 follow-up replaces Camera's Sample photo action with Photos
(accessible name Choose photo). [PhotoLibraryImporter](../../../../apps/FoundryCatalog/Sources/Camera/PhotoLibraryImporter.swift)
loads the chosen PhotosPickerItem, applies a 32 MiB encoded-size limit and reuses
PhotoDecoder off main. CameraView pauses capture during presentation/import,
shows loading/failure state and rejects canceled or obsolete results. Retake
still returns to the viewfinder; the original Image studio keeps its bundled
asset. No full-library authorization or original-photo write is introduced.

The eighteen-check app suite passes. Manual simulator execution opened the real
system picker, selected a repository photograph seeded into Photos, and reached
the existing GPU editor. CLI screenshots show the picker, selected photograph
and outline tabs. The final compact Photos label and outline person icon were
visually inspected; canceling the system picker returned to the usable Camera
screen with its shutter still disabled and Choose photo available.
The earlier sample checks above describe the initial camera slice, not a remaining
sample button. Cloud downloads, denied URI equivalents and a complete VoiceOver
audit remain unverified. Read [PhotosUI mechanics](../../../substrate/avfoundation-capture-and-photo-admission.md#photosui-input--2026-10-08-follow-up)
and [symbol variants](../../../substrate/swiftui-tab-selection-and-presentation.md#outlined-symbols--2026-10-08-follow-up).

## Orientation metadata and archive validation

The 2026-10-08 distribution follow-up fixes an App Store Connect rejection for
missing supported orientations in this universal app. Read the orientation
build settings in [project.yml](../../../../apps/FoundryCatalog/project.yml),
then [generated plist mechanics](../../../substrate/swift-package-and-xcode-project-wiring.md#generated-orientation-metadata).
The generic array declares portrait and both landscapes; the iPad-specific
array includes portrait upside down as required by the reported multitasking
validation. The locally selected development team is now preserved in the same
generator specification instead of being lost when the project is regenerated.

Observed verification: `make ios-generate` and `make ios-build` pass. An unsigned
Release archive built with `xcodebuild`, destination `generic/platform=iOS`,
`CODE_SIGNING_ALLOWED=NO`, and archive path
`.cache/archives/FoundryCatalog-orientations.xcarchive` also passes. Python
`plistlib` inspected both packaged plists and asserted the exact orientation
sets and device families `[1, 2]`; the Release bundle retains identifier
`dev.mobilefoundry.catalog`, version `0.1.0`, build `1`.

This is local packaging evidence, not successful App Store Connect validation.
The user must create a new signed archive before distributing the fix, since
the previously rejected archive is unchanged. Screen rotation, iPad layout and
camera orientation on hardware remain separate runtime checks. Next: which
phone and iPad sizes should form the pre-TestFlight rotation check?

## Everyday component gallery

Added 2026-10-08. Studio → Open catalog → Components opens
[ComponentCatalogView](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift).
The app owns counters, search text, busy state, settings, quality selection and
presentation state. Actions, Content and Patterns organize the examples. Theme
controls scope Light/Dark and Solid/Glass without replacing the state owner.
Native sheet and confirmation examples compose the same components; they do not
implement the reserved shared overlay wrappers. Read
[the UI walkthrough](../../packages/FoundryUI/README.md#everyday-component-batch-and-naming)
and [slot ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md).

Observed verification: the regenerated project builds and all eighteen existing
iOS app checks pass on iPhone 17 Pro/iOS 26.2. Manual native interactions and
CLI screenshots verify the light action layout, dark busy button, retained
action count of one after changing appearance, and Detailed selection retained
after changing Glass to Solid. Screenshots show the actual settings/selection
composition and readable native text. Simulator accessibility snapshots can lag
the visible state; screenshots supplied the current visual evidence.

Android has independent automated gallery interaction checks. This iOS slice
does not claim equivalent automated widget activation or a complete VoiceOver,
Dynamic Type, iPad/rotation, localization or keyboard audit. Next: which repeated
row/accessory or overlay pattern deserves its own public API?

## Controls and overlays gallery

Added 2026-10-08. The parent
[ComponentCatalogView](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
now offers five families; native Tabs uses a menu for this many choices.
[ControlExamples](../../../../apps/FoundryCatalog/Sources/Components/ControlExamples.swift)
receives a binding to values retained above the family switch. It demonstrates
mixed aggregate selection, disabled toggle, radio/menu choice, a five-position
slider and date draft confirmation. Review date begins at October 8, 2026 in the
local calendar, not at an arbitrary backend timestamp.

[OverlayExamples](../../../../apps/FoundryCatalog/Sources/Components/OverlayExamples.swift)
receives caller-owned presentation/counter values. A disabled Share action, menu
duplication, native sheet and reset confirmation show the difference between
request, dismissal and commit. The existing details/removal examples also use
the shared wrappers, replacing their earlier inline native implementations.
Read [the UI walkthrough](../../packages/FoundryUI/README.md#selection-controls-and-native-overlays)
and [native mechanics](../../../substrate/swiftui-selection-and-modal-drafts.md).

The regenerated project builds and all eighteen existing iOS app checks pass.
The package also compiles on macOS and passes four token/material tests. Manual
simulator checks and CLI screenshots provide widget/layout evidence separately;
the final observations are recorded below. Full VoiceOver, accessibility sizes,
iPad/rotation, hardware and TestFlight checks for this batch remain open.

Observed iPhone 17 Pro/iOS 26.2 interactions: Include everything advances mixed
to all included; Compact becomes selected. Changing a date draft then discarding
keeps October 8; confirming October 9 updates the committed review copy. Menu
Share is exposed disabled, Duplicate creates one copy, Keep copies preserves it,
and Confirm reset clears it. The shared sheet opens and closes in the dark
preview. CLI screenshots verify the native calendar, selected October 9 draft,
dark overlay gallery and the final full-sheet background. An initial sheet
capture exposed a dark content rectangle within a light intrinsic-height host;
the convenience wrapper now fills available bounds and aligns content at the
top. The corrected build and fresh screenshot confirm the repair.

Android's final fourteen-check suite independently covers native activation,
slider semantics, family/theme/restoration retention and date transactions. The
iOS checks above are manual observations, not added automated widget tests.

## Display feedback and collections gallery

Added 2026-10-08. The parent
[ComponentCatalogView](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
adds Display, Feedback and Collections and hoists their values above the family
switch. [DisplayExamples](../../../../apps/FoundryCatalog/Sources/Components/DisplayExamples.swift)
switches between fallback/artwork avatars and supplies illustrative stat copy.
[FeedbackExamples](../../../../apps/FoundryCatalog/Sources/Components/FeedbackExamples.swift)
separates loading/reduction controls, temporary notice presence and explicit
undo/retry counters. Changing families clears notice presence without resetting
committed preview values or counters.

[CollectionExamples](../../../../apps/FoundryCatalog/Sources/Components/CollectionExamples.swift)
filters/sorts three app-owned records and stores selected stable IDs separately.
Select visible unions only matches; Clear selection removes all. Filtering can
hide selected IDs, and the summary continues to count them. Empty projections
disable Select visible and offer filter reset. No service or shared query engine
is introduced. [FieldGroupExample](../../../../apps/FoundryCatalog/Sources/Components/FieldGroupExample.swift)
adds native fields to Controls, with app-owned validation and group error/help.

Read [the UI walkthrough](../../packages/FoundryUI/README.md#display-feedback-and-collection-components),
[native loading mechanics](../../../substrate/swiftui-loading-and-passive-content.md),
and [projection/lifetime reasoning](../../../../../../notes/patterns/collection-projections-and-feedback-lifetime.md).
The regenerated app compiles and passes all eighteen existing iOS app checks;
the macOS UI package compiles and its four tests pass. Manual simulator checks
and CLI captures supply actual widget/layout evidence recorded below. Full
VoiceOver announcements, Dynamic Type, iPad/rotation and physical-device checks
for these APIs remain separate.

Observed iPhone 17 Pro/iOS 26.2 interactions and CLI captures: fallback avatars
use one identity each, switching artwork retains the Studio emblem identity,
and combined stat semantics include supplied values/trend/units. Feedback exposes
real loading copy with no skeleton elements, changes to static preview copy under
reduction, and explicit Retry replaces the failed notice with a saved notice and
increments only its local counter. Native Undo and collection navigation were
also exercised. Selecting visible favorites yields two selected IDs; turning the
filter off reveals three records while retaining two selections. Native Newest
choice orders Orbit, Atlas, Field with the same selected IDs. Dark/Glass changes
retain selection, and screenshots show the actual avatar/stat, skeleton/banner
and floating toolbar composition.

The loading preview was adjusted to pass animated: false to each Swift skeleton
when its local reduction toggle is selected. This avoids installing a nested
theme background as a visible rectangle inside the Card; inherited/system
reduction still applies inside Skeleton itself. The final app compiles after
this gallery-only adjustment; a fresh launch and final CLI screenshot verify
static placeholders directly on the Card background. Android separately exercises live theme reduction
in its native pulse pixel test.

## Context and layout gallery

Added 2026-10-08. [ComponentCatalogView](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
now offers ten families and a 33-building-block badge. It keeps ContextValues and
LayoutValues above the family switch. Changing families clears popup presence
without clearing committed choice counts or picked items.
[ContextExamples](../../../../apps/FoundryCatalog/Sources/Components/ContextExamples.swift)
demonstrates short tap help, explicit/native dismissal and an explicit local
storage choice. NavLink opens ComponentDetailView in the existing NavigationStack;
this destination is a placeholder, not implemented storage. Its content uses the
shared readable container and native scrolling.

[LayoutExamples](../../../../apps/FoundryCatalog/Sources/Components/LayoutExamples.swift)
uses three stable ForEach identities, abstract native 4:3 artwork and caller-owned
picked copy. Narrow preview caps the content at 240 points; the same grid reflows
rather than switching to a different hierarchy. Theme changes do not own selection.
No GPU renderer or new image admission is needed for these decorative previews.

Read [the UI walkthrough](../../packages/FoundryUI/README.md#context-navigation-and-adaptive-layouts),
[native mechanisms](../../../substrate/swiftui-layout-and-contextual-presentation.md),
and [shared reasoning](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08).
[ComponentLayoutTests](../../../../apps/FoundryCatalog/Tests/ComponentLayoutTests.swift)
adds actual native geometry checks, not a copied column formula. They host views,
record CGRect preferences and change width, Dynamic Type and reading direction;
a separate case checks padded readable width and media ratio. Popover/nav
activation evidence is manual and recorded separately below.
Next: which detail destination needs real data and its own independent navigation
state, and which help content is substantial enough to deserve a sheet/page?

Final verification: all twenty iOS app checks pass on iPhone 17 Pro/iOS 26.2,
including the two new hosted layout checks. The first run exposed an explicit
RTL reversal that canceled SwiftUI's automatic mirroring. Removing it makes
native first-column geometry move to the right; the full suite passes afterward.
An attempted targeted command without Swift Testing's function parentheses
selected zero tests and is not counted as evidence. The final full-suite result
has twenty actual tests and zero failures.

Manual execution confirms short help opens/closes, closing storage options keeps
zero choices, explicit selection increments to one, and detail navigation/back
returns to that count. Pick Atlas updates caller copy. Narrow preview becomes
one centered column; Dark preview retains the picked value. CLI screenshots were
inspected for the actual anchored native help, two-column cards with differing
copy heights, and the centered dark one-column layout. The generic layout test
also exercises accessibility3 and RTL; this does not establish a complete gallery
VoiceOver, localization, keyboard, iPad/rotation or physical-device audit.
Android independently passes fourteen focused component/geometry checks. Both
native consumers build and four UI package tests pass per platform.

## Details and delivery preview gallery

Added 2026-10-08. [ComponentCatalogView](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
adds Details, for eleven families and 39 building blocks. Its DeliveryValues
binding stays above the family switch and native destination.
[DetailsExamples](../../../../apps/FoundryCatalog/Sources/Components/DetailsExamples.swift)
composes format chips, a bounded copy count, disclosure editing and passive
detail rows. The disabled Print choice is illustrative; the controls update a
local preview. A feature-owned FocusState clears when the note collapses or
Done editing note is activated.

[DeliveryPreviewView](../../../../apps/FoundryCatalog/Sources/Components/DeliveryPreviewView.swift)
uses DetailShell with a concise header, an explicitly scrolling body and an
ActionBar outside the scroll. Apply requires copies greater than zero and only
increments a local count. Reset restores the draft while retaining that count.
The route shares the same binding, so collapsing a note or returning to the
gallery does not intentionally discard the draft. No export or persistence is
implemented by these examples.

Read [UI mechanics](../../packages/FoundryUI/README.md#choices-disclosure-and-detail-composition),
[native disclosure/layout](../../../substrate/swiftui-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08),
and [bounded arithmetic](../../../language/swift-bounded-integer-arithmetic.md).
[DetailShellLayoutTests](../../../../apps/FoundryCatalog/Tests/DetailShellLayoutTests.swift)
adds one native hosted geometry check: scrolling moves body records while the
action region stays fixed, and accessibility3 grows rows without overlapping
header/footer. All 21 iOS app checks pass on iPhone 17 Pro/iOS 26.2; four macOS
UI token/material checks pass. Android independently passes nineteen focused
component checks, including actual edited-note retention and extreme integers.

Manual iOS execution confirms selected/disabled chips, 0 → 2 → 4 → 5 clamping
with More disabled at five, disclosure expansion/collapse, detail navigation,
body scrolling with visible footer, Apply increment and Reset retaining the
count while disabling Apply at zero. This did not include manual typed-note or
extreme-Int execution on iOS. A CLI screenshot initially exposed black native
navigation/status text above the dark themed body. The destination now sets
native toolbar background/visibility/color scheme explicitly. A fresh build,
install and inspected CLI capture confirm readable light navigation/status
text on the dark destination. This app-only styling correction follows the
21-check run; those tests measure the shell rather than toolbar appearance.

These observations do not establish complete VoiceOver, keyboard avoidance,
iPad/rotation or physical-device coverage. Next: how should a real delivery
command validate copies and own pending/confirmed state beyond this local preview?

## Journeys gallery

Added 2026-10-08. [ComponentCatalogView](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
adds Journeys, for twelve families and 45 building blocks.
[JourneyExamples](../../../../apps/FoundryCatalog/Sources/Components/JourneyExamples.swift)
hoists password, introduction, enabled/preference values, step/completion and
local counts above the family/destination. Password and note fields are native;
checklist values derive from nonempty password/nonblank note, without claiming
real account validation. Continue account preview only increments a local count.

The account destination uses AuthShell, with native navigation appearance scoped
to the preview theme. OnboardingPage displays three caller-projected progress
rows and profile/preferences/review content. A blank note blocks Next at step
zero. Previous/Restart preserve draft/preferences; explicit Finish increments
once and disables itself until navigation/restart. Focus clears before changing
steps. The page id changes with the step to reset body scrolling, while values
remain above that identity. Native Back returns to the gallery.

Read [the UI walkthrough](../../packages/FoundryUI/README.md#rich-input-and-journey-pages),
[native mechanics](../../../substrate/swiftui-rich-input-and-journey-pages.md)
and [shared ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08).
[JourneyFieldTests](../../../../apps/FoundryCatalog/Tests/JourneyFieldTests.swift)
adds hosted native-input/geometry evidence; Android independently executes
keyboard editing, step actions and saved-state behavior. This gallery does not
implement credential storage, sessions or a durable profile. Next: which real
mutation should govern advancement beyond local readiness?

Final evidence, 2026-10-08/09: both consumers build, all 22 iOS app checks and
nineteen focused Android component checks pass, with four UI token/material
checks per platform. The new Swift probe initially reported zero height because
its preference reduction overwrote the measurement with default contributions.
Using max retains the one measured height. The passing case inspects a native
secure UITextField, observes growth from two to four visible lines, verifies a
30-line draft keeps a capped viewport, and observes larger accessibility3 text.
This correction changes the test probe, not production input behavior.

Manual iPhone 17 Pro/iOS 26.2 checks confirm obscured example input and passive
requirement changes, a three-line native draft retained across route changes,
Previous/Restart retaining values, update preference retention, explicit Finish
with disabled repeat, returned finish count one, and account count one after
Continue. CLI screenshots were inspected for dark native navigation, native
obscured input, readable account composition and onboarding actions outside the
scrolling body. Hardware Return/Option-Return and clipboard/AX limitations are
recorded in the substrate note. Full VoiceOver, software-keyboard/IME avoidance,
iPad/rotation, provider autofill and physical-device coverage remain open.

## Activity gallery

Added 2026-10-09. Activity is the thirteenth family, with 51 building blocks.
[Catalog ownership](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift) retains preview values through family/route
changes. [ActivityExamples](../../../../apps/FoundryCatalog/Sources/Components/ActivityExamples.swift) contains passive collaborator artwork,
an expandable summary and the separate bounded native List/LazyColumn screen.
The observable app model stays above NavigationLink in ComponentExamples.
A page request generation drives a native view task; refresh awaits the model.
Cancellation checks and a revision guard prevent stale completion after leaving.
An explicit refresh button owns a second view task with the same admission.

The local fixture starts with three rows, admits pages of three up to nine,
retains rows/expansion on failure, consumes Fail next page once and retries only
on action. Refresh resets to the first page and retains surviving expanded IDs.
Refresh/page work cannot overlap. Its 450 ms delay is a visibility aid, not
network evidence. No persistence, cache or domain paging port is added.

Read [UI mechanics](../../packages/FoundryUI/README.md#activity-and-paged-collections),
[native lifetime](../../../substrate/swiftui-refresh-and-lazy-activity.md)
and [shared reasoning](../../../../../../notes/patterns/refresh-and-pagination-ownership.md).
[Native activity checks](../../../../apps/FoundryCatalog/Tests/ActivityPreviewTests.swift) cover the actual fixture boundaries.

Final evidence, 2026-10-09: all 27 iOS app checks pass, including five new
ActivityPreviewTests. They execute failure/retry/exhaustion, duplicate admission,
refresh expansion retention, cancelled refresh/page work and late completion
invalidation. A hosted native Text check measures expansion/collapse and
accessibility3 geometry. A hosted UIRefreshControl check holds the async action
at a gate, verifies the spinner remains active, releases work and verifies the
spinner stops. This is programmatic native control activation, not a pull-gesture
assertion. The first test build needed the app module's testable import.

Manual simulator inspection confirms light/dark feed layout and dark toolbar,
full collapsed-copy accessibility text, one collaborator summary, explicit
refresh count, expansion and Back/reopen retention. CUA drag attempts did not
advance refresh and are not counted as gesture evidence. The native control
lifetime test passes; physical pull behavior remains outside the Swift manual
observation. Android separately executes a pull gesture on its native adapter.

Both native consumers build. Four UI token/material unit checks per platform
pass, and the notes checker passes. No complete VoiceOver, localization,
physical-device, real-network or macOS runtime audit is claimed. Next: choose a
real feed's cursor/merge and cached-window contract before introducing a provider.
