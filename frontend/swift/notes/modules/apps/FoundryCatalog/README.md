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

## Media gallery

Added 2026-10-09: Media is the fourteenth family, with 57 building blocks.
[Catalog ownership](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift) retains values through family/route changes.
[MediaExamples](../../../../apps/FoundryCatalog/Sources/Components/MediaExamples.swift) composes native paging, passive position, supplied
artwork, overlay favorites and per-study ratings. MediaTile shows the selected
study's metadata and an explicit Use action that increments a local count.
ComponentExamples keeps MediaValues above the family switch and native link.
The selected record ID, rating dictionary, favorite set and local use count
remain caller-owned. Previous/Next change the binding using the token motion;
page position and per-record values do not live inside lazy page content.

Enable media controls gates gestures/rating/favorites/use. Clear resets only the
current rating. The procedural artwork needs no asset loader or new UI
dependency, and is not an image editing or GPU performance feature.

Read [UI mechanics](../../packages/FoundryUI/README.md#media-browsing-and-actions),
[native paging](../../../substrate/swiftui-media-paging-and-overlays.md)
and [shared ownership](../../../../../../notes/patterns/media-selection-and-passive-artwork.md).
[Native checks](../../../../apps/FoundryCatalog/Tests/MediaComponentTests.swift) link actual code and assertions.

Final evidence, 2026-10-09: All 29 iOS app checks pass, including two new MediaComponentTests. Hosted
native checks change selected IDs and measure the selected page aligned to the
actual viewport, then measure metadata/rating copy growth at accessibility3.
Manual simulator checks execute rating/favorite actions, Previous/Next with
per-study retention, Back/reopen retention and light/dark layouts/toolbar. A
CUA drag did not change the page and is not counted as swipe evidence. These
checks establish native position/binding/layout behavior; comprehensive swipe,
VoiceOver, localization, macOS runtime and physical-device audits remain separate.
The first app build corrected a ToggleField call-site argument from checked to
its native isOn label.

Both native consumers build; four UI token/material unit checks per platform
pass. Notes validation passes. Next: choose
an admitted media record contract and a selection fallback before adding a loader.

## Communication gallery

Claim: a caller-owned conversation fixture can exercise draft and transfer
recovery independently of shared component rendering and backend transport.

Added 2026-10-09: Communication is the fifteenth family, with 63 building blocks.
[CommunicationExamples](../../../../apps/FoundryCatalog/Sources/Components/CommunicationExamples.swift) composes all six new APIs; the Design room row
opens a destination with a persistent composer.

ComponentExamples owns CommunicationValues above the family switch and native
destination. A binding shares draft, file/transfer fixture and sent previews.
Swift current-view state is retained across Back/theme/family changes; no durable
storage is implied. Sent preview messages receive UUID identities.

The explicit fixture controls admit waiting → transferring → paused/failed/
complete, with Resume/Retry returning to transferring. There is no timer or
network request. Adding an incomplete attachment blocks send; failing/retrying
preserves text, and cancellation removes only the file. A nonblank text or a
completed attachment can send. Admission appends one local message, then clears
the draft/file. Disabled callbacks guard transitions. Inspect increments a local
counter; typing presence is a toggleable fixture. No filesystem reads or messages
to another person occur.

ConversationPreviewView puts transcript/recovery controls in a native ScrollView
and the composer in a bottom safeAreaInset. Its focus binding belongs to the
composer host. Interactive keyboard dismissal is screen policy. The native
software keyboard was explicitly shown for manual verification; the composer
remained above it with Send accessible.

Read [UI mechanics](../../packages/FoundryUI/README.md#communication-and-attachments),
[native framework behavior](../../../substrate/swiftui-composer-and-safe-area.md)
and [shared draft/transfer ownership](../../../../../../notes/patterns/composer-drafts-and-transfer-ownership.md).
[Native checks](../../../../apps/FoundryCatalog/Tests/CommunicationComponentTests.swift) link actual assertions.

Final evidence, 2026-10-09: All 32 iOS app checks pass, including two new state-policy cases and one hosted
narrow/larger-text geometry case. The state checks exercise pending-file send
blocking, failure/retry, pause/resume, one-time local send, cancellation,
attachment-only admission and disabled callbacks. Hosted geometry verifies
conversation copy growth at accessibility3 and a minimum-height attachment action
inside the message bubble. Four UI-package unit checks also pass.

Manual iOS 26.2 observation opened Design room, focused the editor, explicitly
showed the software keyboard, added a file, failed/retried/completed its transfer
and sent one local preview. Back → Dark → Design room retained that message.
Light keyboard and dark conversation images were inspected. A CUA typeText call
delivered only part of its multiline payload; complete input automation is not
claimed. Swift owner checks and Android instrumentation separately exercise full
multiline text.

Both consumers build. Full VoiceOver/TalkBack, localization, every keyboard,
rotation/iPad, physical-device performance and real service delivery remain
outside this slice. Keep pinned slots small and supply bounded screen layout.
Next: introduce an actual message/attachment service through existing thin seams
with operation identity and draft-revision-aware success handling.

## Editing gallery

Claim: a local library can show identity-preserving selection/removal/undo without
turning shared UI components into a collection service.

Added 2026-10-09: Editing is the sixteenth family, with 68 building blocks.
[EditingExamples](../../../../apps/FoundryCatalog/Sources/Components/EditingExamples.swift) composes all five APIs; Open library editor opens
the native destination with six stable fixture records.

ComponentExamples holds EditingValues above the family switch. A binding shares
draft/tags, filter, selection/archive/removal sets and the latest undo snapshot
with the destination. These are current-view values, not durable storage.
LibraryEditingPreview receives appearance/style and creates its scoped theme;
the native List owns gestures/scrolling and a bottom safeAreaInset hosts actions.

The feature trims one tag, rejects case-insensitive duplicates, caps tags at six
and preserves rejected drafts. Accepted admission clears only that draft; removing
a tag preserves it. Filtering derives visible records without changing selected
IDs. Select visible items changes only that projection; the bottom summary names
hidden selection and bulk actions operate on all selected IDs. Removal guards
valid/nonremoved identity and captures the latest IDs plus prior selected subset.
Undo restores those same records/selection while retaining archive flags and source
ordering. Another removal replaces the snapshot; dismiss discards recovery.
Disabled commands are guarded at the owner too. No files or backend records change.

Read [UI mechanics](../../packages/FoundryUI/README.md#selection-tokens-and-row-editing),
[native framework behavior](../../../substrate/swiftui-wrapping-and-list-actions.md)
and [shared identity/undo](../../../../../../notes/patterns/selection-identity-and-undo.md).
[Checks](../../../../apps/FoundryCatalog/Tests/EditingComponentTests.swift) link actual assertions.

Final evidence, 2026-10-09: All 35 iOS app checks pass. Three new cases exercise hidden selection, visible
aggregation, archived/removal identity, latest undo, duplicate/cap rejection,
draft retention and disabled callbacks, plus native wrapping geometry at widths
400/220, mixed heights and RTL. Four UI-package unit checks pass.

Manual iOS 26.2 execution selected Orbit, invoked its native accessibility Archive
action, removed it through the visible row menu, and used Undo. The restored row
retained selected/archived values. Back → Dark/Solid → editor retained values;
light/glass and dark/solid screenshots were inspected. A CUA drag did not produce
a gesture outcome, so it does not establish full physical swipe behavior. Native
List actions were exposed and the action/menu paths executed separately.

Both consumers build. Full VoiceOver/TalkBack, localization, all keyboard/window
sizes, macOS runtime, physical-device gestures/performance and persistent undo
remain outside this slice. A real library command needs receipts/revisions and
failure projection through existing result/mutation seams.
Next: how should undo admit a record that changed after its removal?

## Insights gallery

Claim: a caller-owned dashboard can compare periods and empty data without
overwriting an independently owned goal or hiding chart values in decoration.

Added 2026-10-09: Insights is the seventeenth family, with 74 building blocks.
[InsightsExamples](../../../../apps/FoundryCatalog/Sources/Components/InsightsExamples.swift) composes six APIs. Open insights dashboard
opens the scrolling native destination.

ComponentExamples holds InsightsValues above the family switch. Bindings carry
period, empty-data flag, enabled goal controls and completed count into the
destination. Appearance/style are captured for its theme and navigation chrome.
Current view state is retained across Back/family/theme, not process termination.
The exact-values disclosure is local presentation, not a persisted preference.

The immutable fixture provides seven daily Week samples or four weekly Month
samples. Focus totals are 210/420 minutes and Design/Reading/Practice categories
sum to the same total. Both periods use a 200-minute bar maximum; sparklines
explicitly describe their relative scale and have a native exact-values disclosure.
Comparison copy/direction is fixture policy. Empty mode replaces focus plots while
keeping the independent session goal. Its 0..20 commands reject disabled/out-of-
range changes; Reset returns to 14. Drawing components never aggregate, format a
unit/date, select a period or perform analytics collection. No service is invoked.

Read [UI mechanics](../../packages/FoundryUI/README.md#insights-and-small-charts),
[native drawing](../../../substrate/swiftui-chart-drawing-and-summaries.md)
and [chart meaning/scales](../../../../../../notes/patterns/chart-meaning-and-scales.md).
[Checks](../../../../apps/FoundryCatalog/Tests/InsightsComponentTests.swift) link executed assertions.

Final evidence, 2026-10-09: All 37 iOS app checks pass after the final chart accessibility projection.
The new owner case verifies Week=210/Month=420 minutes, category totals, independent
goal/empty state, bounded changes and disabled/reset admission. Hosted geometry
at 240 points/accessibility3 verifies plot/action bounds and growing copy, with
a fixed-height sparkline and a minimum-height native action. Six UI-package checks
pass, including finite extrema and empty/single/constant normalization.

Manual iOS 26.2 observation opened the light/glass dashboard, inspected the real
sparkline and category/ring drawing, revealed exact values and incremented the goal
to 15 of 20. The initial bar group exposed only its title in the automation tree;
the final implementation explicitly supplies all category/value pairs. Final
native compilation/regressions pass, but a later CUA attempt to reopen the final
preview failed with windowNotFoundAtPosition and blank screenshot output, so no
post-fix full assistive-technology observation is claimed.

Both consumers build. Full VoiceOver/TalkBack, all locales/keyboards/window sizes,
macOS runtime, dense data, time axes, physical-device rendering/performance and
real analytics providers remain outside this slice. Next: introduce an admitted
metrics service through existing thin seams before adding dashboard orchestration.

## Scheduling gallery

Claim: a session draft, date availability and an applied session are distinct
feature values above family/route/theme changes.
Origin, 2026-10-09: [Scheduling source](../../../../apps/FoundryCatalog/Sources/Components/SchedulingExamples.swift) and
[consumer checks](../../../../apps/FoundryCatalog/Tests/SchedulingComponentTests.swift), linked from the root component catalog.

Open Scheduling → Open schedule planner. The UTC week fixture has an unavailable
Sunday and an initially selected Monday. Tuesday has an empty agenda until Apply
copies a chosen time/day into one local session. Use time changes only the feature
draft; Keep time changes nothing. Date endpoints commit independently. Reversed
dates disable all bounded days/Apply without repairing the selected ID or changing
the already applied session. Reset dates restores the week. Empty projection and
disabled controls retain feature values; Android saves primitives above routes.

Evidence: both native consumers build; 39 iOS app checks pass, including admission
and 240-point accessibility-size geometry. Five Android scheduling checks pass,
including native hour/minute cancel/commit, disabled modal closure, 240-dp font
scale 2 layout, range admission, route/theme changes and saved-state restoration.
An existing Android date-picker regression also passes after allowing the
native Today prefix in its full-date selector; exact UTC timestamp assertions
remain. The UI package check passes seven Swift and six Kotlin checks. See the native
substrate for what each check establishes. A full VoiceOver/TalkBack, all locales,
real-zone DST, device calendar, reminders and physical-device audit remains open.

Read [UI APIs](../../packages/FoundryUI/README.md#dates-and-agendas), [native mechanics](../../../substrate/swiftui-time-and-date-drafts.md) and
[calendar meaning](../../../../../../notes/patterns/calendar-dates-and-clock-readings.md).
Next: define a real availability/scheduling port and reject stale revisions
without discarding a user's uncommitted time/date draft.

Manual final iOS observation, 2026-10-09: the light/glass planner shows native
day choices and agenda copy. Changing the wheel to 10:30 then Keep time retains
09:30; reopening starts at 09:30. Use time changes the feature draft to 10:30
with zero applied sessions; Apply creates the 10:30 agenda row and count one.
Back and dark/solid theme changes retain those values; reopening the native
wheel shows 10:30 in the dark theme. The visual check corrected a cramped
duplicate picker label by hiding it under the existing sheet heading. Simulator
AX did not expose modal descendants and some coordinate calls failed after
window movement; screenshots/visible controls supplied the time-modal evidence.
No iOS date-modal interaction or complete accessibility audit is claimed.

## Workspace gallery

Claim: selected project identity, collection projection and compact detail intent
are separate feature values above adaptive layout and catalog routes.
Origin, 2026-10-09: [Workspace source](../../../../apps/FoundryCatalog/Sources/Components/WorkspaceExamples.swift) and
[consumer checks](../../../../apps/FoundryCatalog/Tests/WorkspaceComponentTests.swift), reached through Components → Workspace.

Open adaptive workspace, choose Orbit study, star it and return to projects.
Selection/stars remain. Wider local bounds show a rail beside the project list
and detail; Single pane preview requests the compact flow. Archived hides Orbit
without selecting Field notes; the wide detail uses an unselected placeholder.
Returning to Starred makes Orbit visible again. The disabled Shared rail item
does nothing. Breadcrumb ancestors close compact detail or return to All projects;
the final crumb stays passive. Current copy follows browse intent rather than a
retained hidden or inactive selection. The preview's outer Back exits to Components.

The owner retains values across theme/family/route changes. Android additionally
saves primitives and route; no new Swift process-restoration claim is made.
Compact Android system Back closes detail before exiting the preview. Reflow
does not change selected identity or replay project actions. These are local
fixture projects, with no storage, service or navigation-library integration.

Verification, 2026-10-09: both native consumers build; all 41 iOS app
checks, five focused Android Workspace UI checks and seven Swift/six
Kotlin UI package checks pass. `make notes-check` validates links and
example labels, not native behavior.
The hosted iOS probe observes 820/280-point bounds, logical RTL placement and accessibility-size collapse. Feature checks cover hidden IDs, guarded selection and current-path changes. Manual simulator observations are recorded below.
Wide hosts are synthetic native geometry on phone simulators; physical tablet,
foldable hinge, predictive-back animation, keyboard/focus, localization and full
VoiceOver/TalkBack coverage remain open. Scroll offsets are slot-local and are
not promised across reflow. Read [UI APIs](../../packages/FoundryUI/README.md#adaptive-workspaces),
[native mechanics](../../../substrate/swiftui-bounded-panes-and-navigation.md)
and [adaptive ownership](../../../../../../notes/patterns/adaptive-layout-and-navigation-state.md).
Next: connect a real routed workspace and admit deep-link selection before
choosing its compact pane.

Manual iOS evidence, 2026-10-09: the final simulator build shows aligned breadcrumb
copy in Light/Glass. Open Orbit, Star this project, Back to projects and the
Starred collection retain Orbit's selected marker and star. Returning to the
list changes current-location narration to the collection. Outer Back, Dark
preview and Solid surfaces preserve the active Orbit detail and starred value
on reopen; its native navigation chrome follows the preview theme. The rail's
selected/disabled accessibility tree was observed; full rail gesture, VoiceOver,
physical iPad and keyboard traversal were not manually audited. A first visual
pass prompted baseline alignment and current-path corrections before final tests.

## Tables gallery

Claim: full ordering, page projection and inspected identity are distinct feature
values above table slots and catalog routes.
Origin, 2026-10-09: [ledger source](../../../../apps/FoundryCatalog/Sources/Components/TableExamples.swift) and
[consumer checks](../../../../apps/FoundryCatalog/Tests/TableComponentTests.swift), reached from Components → Tables → Open project ledger.

The nine-record fixture has Project/Sessions/Status sort keys and three records
per page. Clicking a new key chooses ascending; clicking it again reverses the
primary order. Ascending ID breaks ties in either direction. Sorting preserves
the current page. Inspect accepts only a visible row, retains its ID and increments
a local count. The DataTable cell has one native action, independent of other
cells. Values are above family/route/theme changes; Android saves primitives.

Example: sort Sessions ascending and open page 2 to inspect Field notes. Empty
records displays no rows and disabled Page 1 of 1, retaining actual page 2,
sort and inspected ID. Turning it off restores page 2. Disabling actions blocks
sort/page/inspection without discarding values. These are local fixture actions;
there is no inspection route, service request, durable storage or server cursor.

Verification, 2026-10-09: both native consumers build; all 43 iOS app checks,
four focused Android Tables UI checks, seven Swift and six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
Two iOS checks exercise feature admission/tie-breakers and actual hosted 240-point
header/cell alignment, public native scroll movement, larger-text row growth and
RTL order. The first consumer compile caught Foundation.SortOrder ambiguity;
final helper names preserve native APIs. The first hosted lookup stopped at the
outer UIScrollView, so it was corrected to recurse through descendants. A public
contentOffset change proves layout movement, not a finger gesture. Manual
simulator observations are recorded separately below.

Manual iOS evidence, 2026-10-09: the ledger opens in Light/Glass with aligned
headers and rows. Sorting Sessions ascending shows 2/4/6 on page 1 and 8/10/12
on page 2. The native horizontal Scroll Right action reveals Status and Inspect;
clicking the first cell action records Field notes and increments Inspections
to 1. Empty mode shows Page 1 of 1 with both page actions disabled, retains Field
notes, and restores page 2 when turned off. Returning to the gallery, selecting
Dark/Solid and reopening preserves page, sort and inspection values; native
navigation chrome and table surfaces follow that preview. The visible Sessions
header initially wrapped at the default text size, so the fixture column was
widened to 180 points/dp on both platforms and native checks rerun. The simulator
accessibility bridge exposed the table as a scrollable group without its cell
children; these pointer/native-scroll observations do not establish VoiceOver
header associations or traversal.
No full VoiceOver/TalkBack, all locales, physical-device performance, desktop
keyboard traversal, virtualization or remote response admission is established.
Read [UI APIs](../../packages/FoundryUI/README.md#tables-and-pagination),
[native mechanics](../../../substrate/swiftui-table-columns-and-scrolling.md) and
[shared policy](../../../../../../notes/patterns/table-sorting-and-page-ownership.md).
Next: define stable ordering and page/cursor revisions for a real data source,
then prevent a late response from replacing a newer user's sort/page intent.

## Commerce gallery

Claim: cart commands own quantity/code admission and totals, while review
presentation displays a retained moment-in-time snapshot.

Origin, 2026-10-09: [Commerce source](../../../../apps/FoundryCatalog/Sources/Components/CommerceExamples.swift)
and [consumer checks](../../../../apps/FoundryCatalog/Tests/CommerceComponentTests.swift),
reached from Components → Commerce → Open cart preview (95 blocks/23 families).

CommerceValues holds bounded kit/notebook quantities, delivery choice, code draft,
applied discount/error, enabled/busy state and reviewed total/items/count. Native
ValueStepper callbacks guard stable product IDs and their limits; unavailable
Travel case never enters the cart. Integer cents derive subtotal, applied ten
percent discount, delivery and total for fixed local USD fixtures only.

Code editing clears feedback without applying or removing the discount. Apply
admits trimmed case-insensitive STUDIO10, while invalid attempts retain an applied
discount and project an error. Busy code state blocks edit/apply/remove and review;
global disabled blocks commands. Clear cart empties quantities while preserving
delivery/draft/discount; Restore changes only sample quantities.

Review records current total/item count, increments a count and opens a local
sheet. The sheet is disposable; losing review availability dismisses it. A later
quantity change recalculates current totals without rewriting the recorded review.
Values remain above family/theme/NavigationLink destinations. Code FocusState and
review presentation live locally; the caller explicitly dismisses keyboard focus.

Example: max Studio kits and add a notebook, select Pick up, apply STUDIO10 and
review $89.10. Clear then restore gives $26.10 while keeping that earlier review.
No quote service, tax, durable cart, currency domain, stock reservation or purchase
effect is implemented. Read [UI walkthrough](../../packages/FoundryUI/README.md#products-and-order-composition),
[native mechanism](../../../substrate/swiftui-inline-fields-and-order-composition.md)
and [shared ownership](../../../../../../notes/patterns/price-copy-and-committed-cart-values.md).

Verification, 2026-10-09: both consumers build; all 49 iOS app checks, four
focused Android Commerce UI and seven Swift/six Kotlin UI package checks pass.
The owner case checks arithmetic/admission/retained snapshots, while a real
240-point host measures larger-text growth, RTL and action bounds. Full native
keyboard/VoiceOver/localization/device behavior remains outside those observations.
Next: define authoritative quote and mutation contracts through existing seams.

## Discovery gallery

Claim: stable search/bookmark values live above catalog routes, while a filter
sheet is a disposable editing session around applied facets.

Origin, 2026-10-09: [Discovery source](../../../../apps/FoundryCatalog/Sources/Components/DiscoveryExamples.swift)
and [consumer checks](../../../../apps/FoundryCatalog/Tests/DiscoveryComponentTests.swift),
reached from Components → Discovery → Open search workspace (91 blocks/22 families).

DiscoveryValues stores query, applied topic/archive facets, enabled state, saved
IDs, recent queries and last-opened identity/count. A computed results projection
filters six local records. Commands guard enabled and current visible membership;
hidden IDs remain saved. Suggestions/submit/open remember trimmed nonblank terms,
case-insensitively deduplicated to three. Opening and saving have separate intents.

DiscoveryContent owns only filter presentation/draft. Opening copies applied
filters; Reset affects draft, Apply commits and any dismissal discards. Applied
chips remove their facet. Disable closes the sheet; values remain above the
family/theme/NavigationLink destination. Highlighting styles one native String
range in an excerpt and supplies complete runs, never shared numeric offsets.

Example: save Motion study, apply Writing while searching motion and observe
empty results with its saved identity retained. Discard a different filter draft
and compare applied copy. Dark/Glass use the existing scoped theme. No service,
record route, debounce, durable history or full-text ranking is implemented.

Verification, 2026-10-09: both consumers build; all 47 iOS app checks, four
focused Android Discovery UI and seven Swift/six Kotlin UI package checks pass.
The owner case checks history/projection/guards/text preservation; real hosted
240-point geometry checks larger text, RTL and separate save bounds. Native
manual interaction evidence is recorded in the substrate note; these checks do
not establish VoiceOver, localization, physical-device or backend behavior.
Read [UI walkthrough](../../packages/FoundryUI/README.md#search-and-discovery),
[native mechanism](../../../substrate/swiftui-attributed-text-and-search-actions.md)
and [projection pattern](../../../../../../notes/patterns/search-projection-and-filter-drafts.md).
Next: attach a real query owner and define privacy/account scope for history.

## Account gallery

Claim: captured account-qualified intent and device-scoped capability projection
need distinct feature ownership above reusable account UI.
Origin, 2026-10-09: [account source](../../../../apps/FoundryCatalog/Sources/Components/AccountExamples.swift) and
[consumer checks](../../../../apps/FoundryCatalog/Tests/AccountComponentTests.swift), reached from Components → Account → Open account center.

Personal and Studio team are available; Invited workspace is disabled. Each
begins with three fixture devices. Removal keys include account/device IDs;
current iPhone is protected. Cancel keeps all devices, and confirm rechecks
enabled state, captured account and eligible device before changing the list.
Switching context or disabling actions closes outstanding prompts. A stale
captured context is rejected even if its callback runs later.

Example: remove Personal's desktop, switch to Studio team (three devices), then
return to Personal (two). Allow the local photo preview and both contexts show
Allowed. A Denied scenario offers Preview settings and increments a count while
retaining Denied. Profile action also increments a preview count. No OS photo
prompt, settings app, real device removal or authenticated service is invoked.

Verification, 2026-10-09: both native consumers build; all 45 iOS app checks,
four focused Android Account UI checks and seven Swift/six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
Two added iOS checks cover captured-context guards and actual hosted 240-point
composition growth, independent action bounds and RTL placement. Swift retains
feature values above family/route/theme changes; prompts remain view-local.

The feature distinguishes dialog presentation from pending intent: dismissing
the wrapper cannot erase the value needed by its confirm callback. Context and
eligibility are checked at the feature boundary; a disabled control is not an
authorization mechanism. Real principal/workspace/session relationships remain
an explicit future domain decision rather than a guarantee of this fixture.
Read [UI APIs](../../packages/FoundryUI/README.md#accounts-and-access), [native mechanics](../../../substrate/swiftui-account-menus-and-action-slots.md)
and [shared scope](../../../../../../notes/patterns/account-context-and-device-capabilities.md).
No real auth/capability work, full VoiceOver/TalkBack, all locales, physical-device
profiling or secure-storage behavior is established. Next: define context scope,
then connect session commands and foreground permission refresh through adapters.

Manual iOS evidence, 2026-10-09: the Light/Glass screenshot shows the floating
account controls, opaque profile/permission cards, readable identity copy and
outline artwork. The native account menu shows Personal checked, Studio team
available and Invited workspace disabled. Choosing Studio team updates its value
and Team member copy. Native accessibility actions execute photo preview cancel
(Ask retained), Allow preview (Allowed), and confirmed MacBook removal (Devices:
2, current iPhone removal disabled). Switching to Personal shows three devices
and retains Allowed, establishing distinct fixture scopes. After the first menu
selection, simulator screenshot capture returned blank images; subsequent action
results were observed through the native accessibility tree, so that pass does
not establish dark-surface contrast, scrolled device visuals or VoiceOver traversal.

## Notifications gallery

Claim: read/archive identities outlive filtered visibility, while native detail
presentation remains a disposable local value.

Origin/evidence, 2026-10-09:
[NotificationExamples](../../../../apps/FoundryCatalog/Sources/Components/NotificationExamples.swift)
owns a four-update inbox model; the existing
[component route](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
retains its @State above family/theme changes. Stable IDs drive read/archive/
undo/opened state. Today/Earlier groups reuse SectionHeader and Card. The model
guards known active identity and enabled state at each command boundary.

Example: choose Unread and open Review. It is marked read and leaves the filter,
but its sheet stays available. Archive from details closes the sheet; Undo restores
that ID with its existing read state. Mark visible as read snapshots current active
IDs; archived updates remain unchanged. Undo restores only the latest archive,
not all historical archives. Reset restores fixtures but retains the open count.
Counts cover active-inbox unread and visible-filter meaning separately. English
plural fixtures and supplied relative times are not production localization/time policy.

Verification: both consumers build; 51 iOS app cases, four focused Android inbox
cases and seven Swift/six Kotlin UI package cases pass.
[Native checks](../../../../apps/FoundryCatalog/Tests/NotificationComponentTests.swift)
cover unknown/archived/disabled command admission, filtered bulk scope, stable
read identity and hosted narrow/large-text/RTL action bounds. The iOS detail flow
is source/build-checked; the Android consumer tests exercise actual presentation,
restoration and dismissal. No full VoiceOver or physical-device audit is established.
Read [UI walkthrough](../../packages/FoundryUI/README.md#notifications-and-inbox),
[native mechanics](../../../substrate/swiftui-notification-actions-and-narration.md)
and [shared pattern](../../../../../../notes/patterns/inbox-projection-and-read-identity.md).
Next: define account receipt scope and offline/server conflict policy before
connecting a real inbox query/mutation seam.

## Plans gallery

Claim: considering a plan or billing cycle changes a draft; only an admitted
review application changes current allowance, while usage remains retained.

Origin/evidence, 2026-10-09:
[PlanExamples](../../../../apps/FoundryCatalog/Sources/Components/PlanExamples.swift)
owns a bounded Starter/Studio/Team fixture in the
[component route](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift).
The @State model lives above family/theme changes. Plan and cycle are typed enums;
current and selected values are separate. Reviewed plan/cycle are a captured
choice, not a live projection of the draft. Commands recheck eligibility.

Example: with three exports on Starter, select Studio/Yearly. Current quota stays
five until Review and Apply preview change. Studio then allows fifty and retains
three used. Reach fifty, then apply Starter: usage stays fifty and exceeds five.
Reach current limit cannot lower existing usage; Reset usage is explicit.
Team choice, disabled/pending actions, changed review drafts and repeated
application are rejected. Price labels describe monthly-equivalent and billed
amount separately. Fixed USD values are example copy, not fetched products.

Verification, 2026-10-09: both consumers build; 53 iOS app cases, four focused
Android Plans cases and seven Swift/six Kotlin UI package cases pass.
[Hosted/owner checks](../../../../apps/FoundryCatalog/Tests/PlanComponentTests.swift)
exercise stale/pending/disabled admission, unchanged current allowance before
application, preserved usage and actual narrow/larger-text/RTL bounds. Review UI
uses a transient @State flag that closes when eligibility becomes false; its iOS
interaction is source/build-checked. Android tests exercise native review/restore/
application. No full assistive, physical-device or store-purchase audit is supplied.
Read [UI walkthrough](../../packages/FoundryUI/README.md#plans-and-usage),
[native mechanics](../../../substrate/swiftui-plan-slots-and-usage-bars.md) and
[shared pattern](../../../../../../notes/patterns/plan-choice-and-applied-allowance.md).
Next: receipt/quote identity, applied capability scope and real usage-period policy.

## Files gallery

Claim: expansion, search projection, favorites and selected identity stay owned
above native row controls, while inspector presence remains transient.

Origin/evidence, 2026-10-09:
[FileExamples](../../../../apps/FoundryCatalog/Sources/Components/FileExamples.swift)
contains eight fixed acyclic BrowserItem records and FileBrowserValues.
[Catalog wiring](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
retains values above family selection and binds them into the native Files route.
Read the depth-first projection, trimmed matching-plus-ancestors search, guards
and native inspector before studying the row implementation.

Example: collapse all, search field, favorite/open the deeply nested image, then
clear search. The view returns to saved expansion but keeps last-opened identity
and favorite. Disclosure is disabled while searching. Empty/disabled state closes
the sheet without clearing selection; reset clears query/selection and restores
default expansion while preserving favorites/open count. Guarded commands reject
unknown, unavailable, hidden-open and ineligible IDs. An inspector can retain
meaning for a selected identity hidden by a filter.

Verification, 2026-10-09: both apps build; 55 iOS app checks, four focused Android
Files cases and seven Swift/six Kotlin UI package cases pass.
[Owner/hosted cases](../../../../apps/FoundryCatalog/Tests/FileComponentTests.swift)
execute projection, identity retention, command rejection and narrow large-text/
RTL geometry. They do not drive an iOS sheet interaction or actual file access.
The @State inspector flag closes on eligibility changes; it does not define
persisted provider state.
Read [UI walkthrough](../../packages/FoundryUI/README.md#files-and-hierarchy),
[native mechanics](../../../substrate/swiftui-tree-actions-and-indentation.md),
[shared pattern](../../../../../../notes/patterns/tree-projection-and-retained-selection.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#files-and-hierarchy).
Next: service/provider identity and child loading before using real file records.

## Sharing gallery

Claim: local membership values and revision-bound confirmations stay above UI
compositions, while copying is an explicit app-owned effect.

Origin/evidence, 2026-10-09:
[SharingExamples](../../../../apps/FoundryCatalog/Sources/Components/SharingExamples.swift)
owns known SharingContact fixtures and SharingValues. The
[catalog](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
retains values above family choice and binds them into Sharing preview.
Read invite lookup/error preservation, protected-owner admission, revision changes
and removal rechecks before reading the MemberRow/ShareLinkCard builders.

Example: invite River as Editor, request Jamie's removal, then change membership
before confirming. The captured revision becomes stale and removal rejects it.
Owner removal is never admitted. Reset restores members/roles, invalidates requests
and retains counts/link choice/invite draft. Disabled or pending state rejects
commands and closes removal presentation. These fixture rules send no invitation
and do not authorize real access.

Copy button admission returns the example URL and records a request, then invokes
copyText. The default writes UIPasteboard.general.string; no effect runs from a
view render or theme/role choice. Off hides the link and disables that button but
cannot erase previous clipboard text. Displayed native Text remains manually
selectable independently of the feature's disabled controls.

Verification, 2026-10-09: both apps build; 57 iOS app cases, four focused Android
Sharing cases and seven Swift/six Kotlin UI package cases pass.
[Owner/hosted cases](../../../../apps/FoundryCatalog/Tests/SharingComponentTests.swift)
execute known/duplicate/unavailable invite admission, stale/protected/disabled/
pending commands, reset preservation and narrow large-text/RTL geometry. They do
not drive an iOS confirmation, native selection menu or clipboard presentation.
Transient dialog @State closes on eligibility/revision change; persisted provider
versions/account scope remain future service work.
Read [UI walkthrough](../../packages/FoundryUI/README.md#sharing-and-access),
[native mechanics](../../../substrate/swiftui-member-slots-and-selectable-links.md),
[shared pattern](../../../../../../notes/patterns/membership-identity-and-confirmed-revisions.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#sharing-and-access).
Next: real service-admitted membership versions and localized native traversal.

## Playback gallery

Claim: a bounded manually advanced timeline can exercise media UI policy while
leaving real engine state and scheduling to a later adapter.

Origin/evidence, 2026-10-09:
[PlaybackExamples](../../../../apps/FoundryCatalog/Sources/Components/PlaybackExamples.swift)
owns fixtures and guarded PlaybackValues commands.
[Catalog wiring](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
owns the binding above family/theme/preview navigation. Follow selected ID,
per-track positions and separate favorite eligibility into the card's slots.

Example: seek Coastline to 45, switch to Orbit, play and Advance 10 seconds at
1.5×, then return to Coastline. Orbit retains 15 and Coastline retains 45; switching
pauses. Repeat wraps a manual step, while seeking to the end pauses regardless.
A paused end replays from zero. Nonfinite/overflowed targets, unavailable/unknown
IDs and ineligible commands leave values/counts unchanged.

Buffering/Failed pause and block timeline/transport/queue changes while favorite
remains independent. Retry returns Ready without resuming or clearing metadata.
Disabled/empty states pause and retain positions. Reset clears position map and
transport choices, recovers Ready/nonempty and retains favorites/counts. The
visible local-timeline copy is essential: Play only enables manual stepping.

Verification, 2026-10-09: both consumers build; 59 iOS app cases, four focused
Android Playback cases and seven Swift/six Kotlin UI package cases pass.
[Owner/hosted checks](../../../../apps/FoundryCatalog/Tests/PlaybackComponentTests.swift)
execute bounded seek/replay/repeat/speed/admission and narrow large-text/RTL card
geometry. They do not drive iOS transport taps, slider gestures or full VoiceOver.
There is no audio session, automatic clock, media loading or real player recovery.
Read [UI walkthrough](../../packages/FoundryUI/README.md#playback-and-timeline),
[native mechanics](../../../substrate/swiftui-playback-slots-and-native-transport.md),
[shared pattern](../../../../../../notes/patterns/media-timeline-and-transport-admission.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#playback-and-timeline).
Next: reconcile requested/observed engine state and scope asynchronous seek results
to the selected media identity.

## Verification gallery

Claim: attempt identity and a challenge generation let a local preview reject
stale responses while code input remains separate from verification authority.

Origin/evidence, 2026-10-09:
[VerificationExamples](../../../../apps/FoundryCatalog/Sources/Components/VerificationExamples.swift)
owns guarded VerificationValues and fixed channel/response fixtures.
[Catalog wiring](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift)
owns the binding above family/theme/preview navigation. Follow canEdit/canSubmit/
canResend, captured attempt ID/generation/channel/code and explicit local completion.

Example: enter 123-456, begin, cancel and begin again. An old attempt cannot finish
the newer request. Incorrect/unavailable responses retain draft; editing clears
the error. Local success clears code. Advance time manually to expire a pending
check: expiry clears request/draft, and a late result cannot verify. Resend or a
different channel creates a new generation and resets 30/120-second timers.

Disabling invalidates pending but retains draft/choices/times. Reset starts a
fresh Email challenge with normal response and retains attempt/resend counts.
Changing a completed channel also starts fresh. Code and local state remain
in-memory values; there is no delivery, automatic deadline or auth service here.
The visible fixture notice explains that no message is sent or account verified.

Verification, 2026-10-09: both consumers build; 61 iOS app cases, four focused
Android Verification cases and nine Swift/eight Kotlin UI package cases pass.
[Owner/hosted cases](../../../../apps/FoundryCatalog/Tests/VerificationComponentTests.swift)
execute stale/canceled/disabled/expired/duplicate admission, manual timer bounds,
native UITextField hints and 240-point large-text/RTL geometry. They do not drive
iOS verification taps, keyboard/paste/AutoFill UI or full VoiceOver.
Read [UI walkthrough](../../packages/FoundryUI/README.md#verification-and-code-entry),
[native mechanics](../../../substrate/swiftui-code-entry-and-content-hints.md),
[shared pattern](../../../../../../notes/patterns/challenge-drafts-and-attempt-identity.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#verification-and-code-entry).
Next: authoritative provider challenges/deadlines and scoped mutation outcomes.

## Shell primitives gallery

Claim: keeping page values above tab content and chrome branches makes layout
customization independent of feature state.

Origin/evidence: reserved UI completion, 2026-10-09. Components now includes
Shells (112 building blocks, 30 families). Both native consumers build and all
64 iOS app cases pass. Hosted checks exercise native tabs and shell geometry;
Android additionally observes full gallery route/recreation retention. These
are distinct evidence, not a claim of manual iOS gallery interaction.

What/why: [ShellExamples](../../../../apps/FoundryCatalog/Sources/Components/ShellExamples.swift)
owns a small ShellValues fixture through a binding from the
[component gallery](../../../../apps/FoundryCatalog/Sources/Components/ComponentCatalogView.swift).
Known different selection increments one counter; per-destination markers live
above native tab content. The preview embeds TabBar in AppShell's content slot;
its hidden-navigation branch keeps the same value owner. Controls change spacing,
visibility and selected destination. ScrollView remains app-owned.

The existing [five-tab shell](../../../../apps/FoundryCatalog/Sources/Shell/AppShellView.swift)
now consumes shared AppShell and TabBar. SceneStorage selection, catalog
presentation, camera selection/activity and photo state remain in app composition.
The extraction adds no route or feature-owner policy to the UI package.

Example: open Shells → Open shell preview, add Overview markers, switch Activity,
add markers, hide/show the bar and revisit Overview. Theme/family changes preserve
the fixture through the higher gallery owner.

Gotchas: markers are local demo values, not persisted data. Swift @State survives
recomposition while its owner remains; it is not disk/process persistence. Native
tab safe areas stay inside the content slot. Do not put another bottom bar/inset
around TabView or rely on inactive page composition to stop GPU/camera resources.

Actual checks: [ShellComponentTests](../../../../apps/FoundryCatalog/Tests/ShellComponentTests.swift)
test fixture admission/page values, default/explicit spacing, RTL, bounded shell
slots and native labels/images/programmatic selection. `make ios-build`,
`make ios-test` and `make ui-test` pass; logs are `.cache/components-shells-*`.
The xcresult contains 64 passes, zero skips/failures. iOS manual tab gestures,
VoiceOver, older OS execution and physical-device performance remain unmeasured.

Related: [UI primitives](../../packages/FoundryUI/README.md#stacks-separators-and-shells),
[native substrate](../../../substrate/swiftui-native-tabs-and-shell-slots.md),
[shared ownership](../../../../../../notes/patterns/shell-chrome-and-feature-lifetime.md).
Next: what should a closed catalog preserve, and how should a deep link select
a tab before entering its feature stack?
