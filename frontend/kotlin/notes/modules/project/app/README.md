# Android catalog walkthrough

The app now navigates to an injected health example while retaining its generated starter data flow.

## Origin

Android starter initialized 2026-10-07; HTTP example added 2026-10-08 with
Kotlin 2.3.20, Compose BOM 2026.03.01 and API 36. This note mirrors `project/app/`.
[HTTP and health contract](../../../../../../contracts/behavior/http.md) controls the new service behavior; generated starter policy is separate.

## Reading order

1. [App build](../../../../project/app/build.gradle.kts) registers kernel/services and the AndroidX instrumented test runner.
2. [MainActivity](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/MainActivity.kt) supplies theme, surface and MainNavigation.
3. [Navigation](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/Navigation.kt) and [Navigation keys](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/NavigationKeys.kt) define Main and HealthCatalog entries.
4. [Main screen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/main/MainScreen.kt) renders an always-available HTTP health button and starter data.
5. [HealthCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/health/HealthCatalogScreen.kt) owns selection, effect lifetime, phase and service composition.
6. [Services walkthrough](../core/services/README.md) and [HTTP walkthrough](../core/http/README.md) explain the reusable operations.
7. [Health UI tests](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/health/HealthCatalogScreenTest.kt) exercises health scenarios and request replacement on a device.

## Walkthrough

The serializable HealthCatalog NavKey enters the Navigation3 back stack.
The entry supplies a back callback and safe-drawing padding. The health screen
remembers scenario, replay counter and phase; the selected keys own its
LaunchedEffect coroutine. [Suspending ports](../../../language/kotlin-suspending-ports-and-cancellation.md) explains suspend/cancellation syntax and
[Compose effect lifetime](../../../substrate/okhttp-and-compose-effect-lifetime.md) explains composition lifetime.

Loading is followed by Success(Health) or Failed(Failure). The screen's injected
transport creates six wire scenarios; the real client and health service still
admit them. State publication checks coroutine activity. Cancellation is
rethrown. Unexpected exceptions go to Log.e and a deliberate generic failure.
Expected failure rendering uses publicInfo, category copy, admitted validation
fields and optional retry timing. Standard Material controls are used; screen
layout scrolls vertically and scenario chips scroll horizontally.

## Retained starter

The Main screen's generated repository still emits `Android` in memory. Its
view model maps Flow data to Success, catches upstream Throwable into Error,
and uses WhileSubscribed(5000) with Loading. [Starter sharing configuration](../../../substrate/gradle-and-android-bootstrap.md) explains that subscription
policy. Success renders greetings; its error message remains generated starter
behavior. The kernel and health workflow do not adopt that arbitrary Throwable
presentation. Main's navigation callback is now used by the health button.

## Verification and limits

`make android-build` and `make android-test` passed: debug APK, core modules,
and host tests. The two existing starter tests still assert only Loading;
they do not establish a later repository Success transition. The health
instrumented tests require a local emulator/device and use the configured
AndroidJUnitRunner. `make android-ui-test` passed three instrumented tests on API36_Test (Android
16): the starter greeting test and two health checks covering healthy and all
failure scenarios plus replacement of an in-flight request. These device
checks also rebuilt the final app/test consumers.

The injected health example does not establish persistence, native provider
sessions, production network policy, background work, accessibility or graphics
performance. Kernel and adapter host tests provide independent evidence below
Compose; [Verification techniques](../../../../../../notes/techniques/shared-fixtures-and-native-adapters.md) explains the distinction.

The 2026-10-08 seam review found that HealthCatalogScreen constructs the concrete
client and HealthService inside `LaunchedEffect`. A domain service interface,
injected health view model, and app-level provider selection remain planned.
The starter DataRepository interface does not supply that seam for health.
See [Provider seams](../../../../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state).

## Notes feature and provider composition

Read [NotesComposition](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/composition/NotesComposition.kt),
then [NotesViewModel](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesViewModel.kt),
[NotesScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesScreen.kt), and
[NotesCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesCatalogScreen.kt).
The renderer sees state and callbacks only; composition selects the domain port.

The ViewModel exposes read-only StateFlow and owns loads in viewModelScope.
Refresh retains a previous successful list, including an empty list. Generation
checks and coroutine activity guard result admission. A navigation entry owner
retains at most eight keyed provider/scenario ViewModels; disposal cancels the
outgoing read and popping the entry clears them. Returning to a selection
refreshes its retained snapshot. See [interface/StateFlow mechanics](../../../language/kotlin-service-interfaces-and-stateflow.md).

[NotesViewModelTest](../../../../project/app/src/test/java/dev/mobilefoundry/catalog/ui/notes/NotesViewModelTest.kt)
passed eight host tests: both providers share state behavior, previous content
and empty snapshots survive failed refresh, late success/failure/defects are
ignored, cancellation restores state, current defects reach diagnostics, and
clearing a ViewModelStore cancels its read. The two starter tests also passed.
Coroutines tests use a controlled Main dispatcher; production uses the native
main dispatcher.

[NotesCatalogScreenTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/notes/NotesCatalogScreenTest.kt)
adds three passing device tests covering both providers' content/empty/failure,
retry, cancel, provider replacement, and popping/reopening the navigation entry
during an active read. All six instrumented tests passed on API36_Test/Android 16,
including the three existing catalog tests. These checks
use injected responses and establish neither a live backend nor offline storage.
The original health composable still owns inline state; notes demonstrates the
new service/ViewModel seam.

## Reusable query and UI extraction

The 2026-10-08 extraction moves phase values and pure transformations into
[core/query](../core/query/README.md), and rendering into
[core/ui](../core/ui/README.md). NotesViewModel still owns generation, coroutine
activity and diagnostics; its redundant retained-snapshot field is removed.
NotesScreen now supplies operation copy, list emptiness, and note rows.

[QueryCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/query/QueryCatalogScreen.kt)
adds Async UI patterns with manually selected scalar state and no service.
Eight notes ViewModel regressions and two starter tests still pass. All ten
instrumented tests passed on API36_Test/Android 16, including four new generic
presentation/callback/navigation checks linked from the UI module walkthrough.

## Tokens and scoped previews

The 2026-10-08 slice makes [the catalog theme](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/theme/Theme.kt)
a thin delegate to core/ui FoundryTheme, replacing generated purple/type defaults.
Navigation adds Tokens and
[TokenCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/tokens/TokenCatalogScreen.kt).
It keeps selection controls outside the nested preview theme, and owns private
section layout and sample action/position state. Shared theme code owns the
palette and Material adaptation; app code owns the gallery.

The [UI walkthrough](../core/ui/README.md) links token families and checks.
All fourteen instrumented tests pass on API36_Test/Android 16, including four
new scope, font/touch, control, and state-preservation checks. The eight notes
ViewModel and two starter host tests remain passing. Read
[theme/touch mechanics](../../../substrate/compose-token-theme-and-touch-bounds.md)
for why visible button height is not its touch-area measurement, then
[backdrop layer mechanics](../../../substrate/compose-backdrop-layers.md).

The material extension's FoundryCatalogRoot hoists a saveable session choice
above navigation. MainScreen forwards the Glass surfaces switch callback.
Tokens inherits the app choice until a local Solid/Glass preview is selected;
MaterialExample owns its independent selection and scene position. See the
[UI material walkthrough](../core/ui/README.md#swappable-material-slice) for
source order, layer ownership, native checks and limits.

## Forms and mutations

The 2026-10-08 slice adds [CreateNoteViewModel](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/forms/CreateNoteViewModel.kt),
[CreateNoteScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/forms/CreateNoteScreen.kt),
[FormsCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/forms/FormsCatalogScreen.kt), and
[CreateNoteComposition](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/composition/CreateNoteComposition.kt).
The navigation entry owns the ViewModel; the catalog collects its StateFlow
and stops observation in DisposableEffect. Composition supplies the provider
and six scenarios; the screen uses native fields/IME over reusable components.

Submit admits the command and marks busy before launch. Field editing, reset,
another submit and service replacement refuse while busy. Failure retains the
draft and maps known field keys; success locks the receipt until New note.
Generation/activity checks precede settlement and diagnostics. Stop invalidates
before canceling; cancellation cleanup cannot publish over a new attempt.
See [native focus and concurrency mechanics](../../../substrate/compose-form-focus-and-ime.md)
and [shared mutation ownership](../../../../../../notes/patterns/forms-and-mutation-ownership.md).

[CreateNoteViewModelTest](../../../../project/app/src/test/java/dev/mobilefoundry/catalog/ui/forms/CreateNoteViewModelTest.kt)
adds seven passing host tests, including both providers, busy guards, preserved
drafts, unknown fields, old completions/defects, original diagnostic identity,
direct cancellation and clearing the navigation ViewModelStore. Seventeen app
host tests pass in `make android-test`.
[FormsCatalogScreenTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/forms/FormsCatalogScreenTest.kt)
adds native input/IME, provider/failure/recovery, disabled-control and feedback
checks. The first device run passed nineteen of twenty-one checks; two new
assertions used SetText to find a disabled field, which intentionally removes
that action. The corrected matcher uses EditableText. Final evidence is recorded
with the completed run below; thresholds and unrelated tests were unchanged.

The complete device rerun passed all twenty-one checks on API36_Test/Android 16
in 50 seconds, including all four new form/feedback tests. Both provider paths
show local/server validation, retained draft, missing confirmation and recovery;
IME and busy-control checks use one controlled pending write. Reset and public
internal-copy projection pass. This does not establish server delivery, draft
process restoration, TalkBack speech or every font/keyboard configuration.

The Glass follow-up on the same day selects floating surfaces for both outer
form panels and hosts an explicit Backdrop around the screen's decorative
background. Without that host Android would fall back to solid even with a
floating role. Foreground controls are excluded from the capture. A fifth form
device check samples actual form pixels across Solid → Glass → reduced
transparency → Solid, retaining the title and confirmed receipt. All twenty-two
device checks pass on API36_Test/Android 16 in 61 seconds; the app build passes.

## GPU effects

The 2026-10-08 slice adds [GPUEffectsScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/GPUEffectsScreen.kt)
and the optional [core/graphics module](../core/graphics/README.md). A serializable
GPU navigation entry supplies safe padding and Back. The feature remembers
saveable control values; the native surface owns transient resources and counters.
UI tokens supply native typography/spacing and reduced-motion preference, while
the graphics module has no UI dependency. Controls remain opaque below the
external surface; existing glass capture does not include its GL output.

[GPUEffectsScreenTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/graphics/GPUEffectsScreenTest.kt)
adds three checks: real framebuffer differences across touch/effect with motion
paused and actual Economy dimensions; foreground pause/context recreation and
no events after disposal; navigation reopening and native motion controls.
All 25 device checks pass on API36_Test/Android 16 in 65 seconds, preserving all
22 earlier checks. Both app build and unit regression commands pass. See
[GL/Compose mechanics](../../../substrate/glsurfaceview-and-compose-lifetime.md)
for queueEvent snapshots, native context ownership and why PixelCopy is needed.
This is emulator evidence, not physical-device frame time, battery/thermal
measurement, exhaustive GLES compatibility or TalkBack verification.

## Product previews

[ImageStudioScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/ImageStudioScreen.kt)
and [ProductStudioScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/ProductStudioScreen.kt)
are independent feature examples over GPUPreviewSurface. [PreviewAssets](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/PreviewAssets.kt)
decodes assets on IO; feature publication stays on main. Cancellation is rethrown
and unexpected exceptions keep their original diagnostic identity. GPU failures
use public kernel projection. The loader's catalog paths never enter core/graphics.

Editable primitives use rememberSaveable; GPU assets and counters are transient.
Gesture callbacks construct the next camera/viewport from current primitives,
rather than a value captured by the previous composition. This matters when
native events arrive faster than recomposition. Product turntable defaults off
and the native adapter gates it against the navigation entry's lifecycle.

[PreviewStudioTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/graphics/PreviewStudioTest.kt)
uses PixelCopy for real pixels while exercising image tools, camera/materials,
pinch, motion reduction, resolution, context rebuilding and navigation reopening.
Read [asset/gesture substrate](../../../substrate/gles-preview-assets-and-gestures.md)
for the observed first-buffer, dispatcher and incremental-delta corrections.
All 29 device checks pass, including the existing 25; the four new preview checks
also pass in a final focused run. Android host regressions pass as well.
Final emulator captures of the installed catalog show the upright before/after
photograph and the lit lamp matching the intended framing. They supplement the
PixelCopy interaction checks; they do not establish device frame-time budgets.
The sample saves no durable edit recipe and imports/exports no user assets.

## Questions for the next session

- Why is composition lifetime different from viewModelScope?
- Which layout/state behavior is ready to extract into a reusable UI component?
- Why should generated raw Throwable rendering remain outside real dependencies?
- What must move out of the composable before a memory service can replace HTTP?
- Why should an empty prior snapshot survive a refresh failure?

## Related

[State types](../../../language/kotlin-sealed-ui-states-and-data-classes.md), [Kotlin reading order](../../../README.md), and [Setup](../../../../../../docs/SETUP.md).

## Expanded GPU use cases

The 2026-10-08 expansion adds Flow, Material, Liquid, Particles and Field to
the existing GPU effects destination. Read the updated EffectSettings and shader
before the catalog controls. Liquid takes supplied completion; Particles takes
a finite playhead; Field takes up to twelve owned point/weight/radius samples.
Field and particle canvases draw on input changes. Only the catalog owns Replay
event timing, with pause/reduction/lifecycle gates and a restart generation.
See [graphics inputs and event time](../../../../../../notes/patterns/graphics-inputs-and-event-time.md) for the cross-platform
reasoning, reusable product examples, upload budget and next questions.

Five graphics host checks pass, including new content admission/owned snapshots.
The app builds and host regressions pass. The first device run passed thirty
of thirty-one checks: a queued Replay frame overwrote a just-scrubbed input.
Checking current playback/generation before publication corrected that race.
All five focused GPU tests and the final complete thirty-one-check device suite
pass on API36_Test/Android 16. New checks use actual PixelCopy output for the
five additions and retain the same surface, then exercise progress/data controls,
reduced motion, finite completion/restart, pause/background and scrub cancellation.
The input-driven canvas caption avoids displaying the ambient scheduler's zero
rate as if it measured Replay redraws. Physical-device profiling remains open.

## Compositor studio

Claim: the catalog owns one transient composition and the renderer owns its
textures/passes; performance observations do not control rendering policy.

Added 2026-10-08. Read [the studio](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/CompositorStudioScreen.kt) → [asset creation](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/PreviewAssets.kt) → [graphics walkthrough](../core/graphics/README.md). The existing opaque photograph loads off main; the small RGBA disc/ring is
code-authored locally and retained by feature identity. Layer/Mask/Compare tools
change placement or the divider, pinch scales, and labeled sliders provide native
alternatives for every edit. Reset restores composition defaults. Persistence,
import/export, arbitrary layers and product rules are outside this example.

Profile redraws defaults off. Reduced motion stops the repeated workload while
static controls still draw. The native attachment gates RESUMED lifecycle and removal.
The panel labels target dimensions, four passes, input/target payload, uploads,
individual target allocations, CPU encoding and available GPU timing separately.
The latest snapshot is not a statistical recorder or displayed FPS.

[Native integration checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/graphics/CompositorStudioTest.kt) exercise retained canvas identity, static redraw, resizing, profile reporting,
continuous pause and removal. Device UI checks additionally exercise blend/blur controls, layer drag, mask placement, pinch, reset, redraw/reduction and navigation reopening using PixelCopy.
Read the platform index for final execution evidence; physical-device budgets
remain unmeasured. Follow [the profiling protocol](../../../../../../docs/GRAPHICS-PROFILING.md) and [the shared pattern](../../../../../../notes/patterns/premultiplied-compositing-and-render-passes.md). Next: how should a feature save a versioned
composition recipe without persisting native GPU handles?

## Four-tab placeholder shell

Added 2026-10-08. Read
[AppShell](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/shell/AppShell.kt),
then [FoundryCatalogRoot](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/MainActivity.kt),
[MainNavigation](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/Navigation.kt)
and [MainScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/main/MainScreen.kt).
The app saves tab ID and presentation flag and composes only the selected
placeholder. Studio opens the catalog; its local ViewModelStoreOwner ends
with the presentation. This also clears its models on Activity recreation;
this prototype promises saved navigation, not retained feature drafts.

The app starts in Glass for visualization; FoundryTheme still defaults to
Solid. Account and catalog share the root material callback. TabBar
accepts item values and callbacks while the app owns routes, insets and the
static backdrop. MainNavigation guards its last entry and maps root Back to
close when an exit callback is supplied. MainScreen scrolls so every catalog
entry remains reachable with the new close action.

Read [native mechanics](../../../substrate/compose-tabs-and-presentation-owners.md)
and [shared ownership](../../../../../../notes/patterns/tabs-and-feature-lifetime.md).
[AppShellTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/shell/AppShellTest.kt)
exercises four selected-state targets, saved selection/material choice and
catalog detail Back, root Back and close. The native build and Android host
regressions pass. The first complete device run passed 35 of 36 tests, including
both shell checks; an existing token test still expected the old catalog root
and Solid default. After updating its entry through Account/Studio, all eight
focused shell/theme checks pass on API36_Test/Android 16. Other implementation
code was unchanged after the full run. A final emulator screenshot was inspected
for the Home placeholder, floating bar, selected label and system insets.
Saved-state testing exercises rememberSaveable restoration, not an actual
process-kill scenario. Deep links, adaptive tablet navigation, physical-device
performance and retained catalog drafts remain unverified or planned.

## Camera and shared photo editor

Claim: CameraX use cases end at the app boundary; an admitted photograph then
uses the existing editor and GPU preview independently of those use cases.

Added 2026-10-08. [AppShell](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/shell/AppShell.kt)
inserts Camera between Library and Studio and remembers its transient RasterImage.
Read [CameraScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/camera/CameraScreen.kt) →
[CameraCaptureController](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/camera/CameraCaptureController.kt) →
[PhotoDecoder](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/camera/PhotoDecoder.kt) →
[ImageEditorContent](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/ImageEditorContent.kt).
[ImageStudioScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/ImageStudioScreen.kt)
loads the bundled asset and hosts that same extracted editor. The caller owns
scrolling; GPU resource ownership remains in core/graphics.

The manifest declares CAMERA with optional camera hardware. Enable camera
requests runtime permission; RESUMED lifecycle, permission and viewfinder state
gate binding. The screen's DisposableEffect unbinds only its owned Preview and
ImageCapture on exit. CameraState.OPEN admits shutter actions. Switching lenses
replaces the controller and disposes the old use cases. Capture copies bounded
compressed bytes, closes ImageProxy in finally and decodes off main. Generation
checks reject native/decode results from an earlier binding. CameraX rotation
metadata is applied before opaque RGBA admission, with a maximum 2048 edge.

[PhotoFilter](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/PhotoFilter.kt)
defines Natural/Mono/Vivid/Soft as existing adjustment values. The camera begins
neutral; Image studio retains its previous initial edits and comparison. Retake
clears the photograph and recreates the viewfinder. Ordinary tab changes retain
shell-owned pixels; process restoration and durable edits remain separate work.

[CameraScreenTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/camera/CameraScreenTest.kt)
checks decoding, sample → real GPU filters/controls → navigation → retake, and
actual synthetic CameraX capture followed by closed camera states after leaving
the tab. Its virtual-capture test is explicitly emulator-only. The first run's
Mono assertion included the unfiltered tinted letterbox; sampling the central
photograph fixed the test measurement without changing shader behavior.
The final focused nine-check camera/shell/preview run passes on API36_Test /
Android 16. It includes the updated five-target/middle-position assertions in
[AppShellTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/shell/AppShellTest.kt)
and all four original preview interaction checks. The APK and Android host
regressions pass. This slice did not rerun the complete unrelated device suite.

Read [CameraX substrate](../../../substrate/camerax-capture-and-photo-admission.md)
and [shared capture ownership](../../../../../../notes/patterns/capture-assets-and-preview-lifetime.md).
Physical camera quality, sensor rotation, interruptions, denied permission UI and
device performance need further execution. Next: how should an export command
own output size and storage permission while the preview remains transient?

## Gallery input and outline tabs

The 2026-10-08 follow-up replaces Sample photo with Choose photo. The system
PickVisualMedia(ImageOnly) result enters
[PhotoLibraryImporter](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/camera/PhotoLibraryImporter.kt),
which reads only the selected URI, bounds compressed data to 32 MiB and decodes
off main. PhotoDecoder.decodeLibrary reads EXIF rotation/reflection separately
from the CameraX callback's rotation. Permission to use a camera is unnecessary
for this path. CameraScreen unbinds capture during picker presentation/import,
leaves canceled selection unchanged and shows recoverable import failure copy.
The original catalog's Image studio still has its bundled photograph.

All seven focused camera/shell checks pass, including the existing virtual camera
and navigation checks. A test ActivityResultRegistry supplies selected/canceled
results, while URI reads, conversion, editor state and real GPU pixels execute.
Additional checks cover malformed input, missing URI access and EXIF rotation/
transverse dimensions. The updated APK packages and Android host regressions pass.
This result does not establish system-picker UI, reflected pixel placement, cloud
providers or a complete unrelated device-suite rerun. Initial AAPT2 startup
failures disappeared in a fresh Gradle process limited to two workers; no source
workaround was required. The existing five tab vectors are already stroked outlines.
Read [picker/EXIF substrate](../../../substrate/camerax-capture-and-photo-admission.md#selected-library-input--2026-10-08-follow-up).

## Everyday component gallery

Added 2026-10-08. Studio → Open catalog → Components opens
[ComponentCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt).
The app owns saveable counters, search, busy state, settings, quality selection,
current family and native presentation. A scoped theme and bounded Backdrop
let Light/Dark and Solid/Glass change without replacing those owners. Native
ModalBottomSheet and AlertDialog examples compose the same controls rather than
introducing shared overlay wrappers. Read
[the UI walkthrough](../core/ui/README.md#everyday-component-batch-and-naming)
and [slot ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md).

Both native builds pass. The focused Android run passes eleven tests: four new
[component checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogTest.kt),
five existing forms checks and two shell checks. Gallery coverage includes
disabled/busy activation, confirmation/cancel, search empty/clear recovery,
quality/settings retained across preview changes and saved-state restoration,
native sheet open/close and progress-range semantics. The existing form and
shell consumers also compile and execute with the unbranded API names.

This focused suite does not rerun camera/GPU/unrelated feature tests. Broad
TalkBack, localization, keyboard, larger fonts/screens and physical-device
checks remain open. Next: which repeated composition warrants extracting a
shared overlay or collection toolbar instead of another native app example?

## Controls and overlays gallery

Added 2026-10-08. The parent
[ComponentCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
now offers five families. Committed toggles, child selections, export format,
destination, intensity and review date use rememberSaveable above the family
switch. Preview themes, changing families and saved-instance restoration retain
these values; this is not durable application storage.

[ControlExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ControlExamples.kt)
receives values/callbacks and derives the aggregate checkbox. Mixed activation
selects all, a disabled toggle stays inactive, and a three-step slider supplies
five positions. The review date is seeded with October 8, 2026 UTC midnight.
[OverlayExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/OverlayExamples.kt)
requests caller-owned sheet/reset presentation and duplication; disabled Share
and cancel cannot perform operations. Existing details/removal examples now
reuse SheetPanel and ConfirmationDialog.

Read [the UI walkthrough](../core/ui/README.md#selection-controls-and-native-overlays)
and [native mechanics](../../../substrate/compose-selection-and-modal-drafts.md).
Interaction checks live in
[ComponentCatalogTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogTest.kt)
and [DateFieldTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DateFieldTest.kt).
The latter changes the default zone to America/Los_Angeles and verifies date
formatting, cancel and confirm using the actual native picker. Final run outcomes
and limitations are recorded below. Next: which multi-field draft needs a
feature-owned save/cancel policy instead of a single temporary picker state?

Final verification: the focused API 36 emulator run passes all fourteen checks:
six component-gallery checks, one native date-picker check, five forms checks
and two shell checks. Both apps build, and four UI unit tests pass per platform.
New checks cover mixed/off/on semantics, disabled toggle, radio/menu selection,
actual slider range/steps, family/theme/saved-state retention, native sheet close,
disabled Share, menu duplication and reset cancel/confirm. The date check proves
October 8 remains October 8 in a western zone, discard keeps it, and confirm
commits October 9. Early test selectors assumed numeric day text; inspection
showed this Material version exposes full date text in its day button semantics.
The final selector uses that observed native text.

This focused run does not rerun unrelated camera/GPU tests or establish broad
TalkBack, localization, large-font, rotation, sheet gesture or hardware coverage.

## Display feedback and collections gallery

Added 2026-10-08. The parent
[ComponentCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
adds three families and hoists their values above its family switch.
[DisplayExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/DisplayExamples.kt)
composes avatar fallbacks/artwork and supplied stats.
[FeedbackExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/FeedbackExamples.kt)
uses saveable loading/reduction/counters, but a remember-only notice. Family
changes clear it and saved-instance restoration does not replay recovery UI.
Undo/retry callbacks change local counters only after explicit activation.

[CollectionExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/CollectionExamples.kt)
projects three app-owned records through search/favorites/sort while retaining
selected IDs. Select visible adds matching IDs, Clear selection clears all, and
empty matches disable Select visible while offering filter reset.
[FieldGroupExample](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/FieldGroupExample.kt)
keeps native text editing and caller validation separate from group layout.
Read [the UI walkthrough](../core/ui/README.md#display-feedback-and-collection-components),
[native mechanics](../../../substrate/compose-loading-and-passive-content.md),
and [shared reasoning](../../../../../../notes/patterns/collection-projections-and-feedback-lifetime.md).

The focused API 36 emulator run passes nineteen checks: ten
[ComponentCatalogTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogTest.kt)
checks, one [DateFieldTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DateFieldTest.kt),
one [SkeletonMotionTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SkeletonMotionTest.kt),
five forms checks and two shell checks. New coverage includes one accessible
avatar identity with fallback/artwork, supplied stats, loading/content switching,
explicit notice dismissal/retry/undo and no notice replay, grouped native editing
and help/error precedence, real recency ordering, hidden selection retention,
visible bulk selection, empty/reset recovery and saved-state retention. The
motion check controls the native Compose test clock, captures actual pixels,
observes pulse changes and verifies stable output after changing reduction.

Both apps compile, eighteen existing iOS app checks and four UI token/material
checks per platform pass. This focused run does not rerun unrelated GPU/camera
checks or establish complete TalkBack/announcement, keyboard, large-font,
rotation or hardware performance coverage. Next: which bulk command should
include hidden IDs, and what would make a real Undo transaction durable?

## Context and layout gallery

Added 2026-10-08. [ComponentCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
adds Context and Layout, for ten families and 33 building blocks. Context popup
flags use remember only; choices, narrow preview and picked item use
rememberSaveable. Family changes clear popup flags. The app owns a detail flag
and SaveableStateHolder around gallery content, preserving saveable values when
the detail route replaces it. BackHandler and the visible back button return to
the gallery. This local route is an example, not a new reusable router.

[ContextExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ContextExamples.kt)
uses short help and storage option panels. Close/back dismiss without selecting;
only Choose local storage increments the local count. NavLink opens a separate
placeholder screen using shared ContentContainer and Card.
[LayoutExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/LayoutExamples.kt)
keys three cards, supplies accessible abstract 4:3 artwork and keeps picked values
above the family switch. Narrow preview changes only the readable width to 240dp.

Read [the UI walkthrough](../core/ui/README.md#context-navigation-and-adaptive-layouts),
[native mechanisms](../../../substrate/compose-layout-and-contextual-presentation.md),
and [shared reasoning](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08).
[ContextCatalogTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ContextCatalogTest.kt)
executes help close, menu back/close without commit, explicit choice, no popup
replay after restoration, navigation return and retained narrow/picked/theme
values. [AdaptiveLayoutTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/AdaptiveLayoutTest.kt)
checks native bounds under width/font/RTL changes, remembered child state,
readable insets and actual media ratio.

All fourteen focused API 36 emulator checks pass: these four new checks plus ten
existing ComponentCatalogTest regressions. Both apps build and four UI unit tests
per platform pass. This does not rerun unrelated GPU/camera tests or establish
complete TalkBack/keyboard/localization/hardware coverage. Next: which real route
needs an entry-owned ViewModel and which collection needs a lazy data pipeline?

## Details and delivery preview gallery

Added 2026-10-08. [ComponentCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
adds Details, for eleven families and 39 building blocks. Format, copies, note,
expansion, enabled and applied count are saveable primitives above route
replacement. DeliveryValues is an immutable snapshot whose callback updates
those fields; rememberSaveable does not automatically save a custom data class.
[DetailsExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/DetailsExamples.kt)
composes chips, bounded quantity, disclosure editing and passive detail rows.
The feature clears focus on collapse or Done editing note.

[DeliveryPreviewScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/DeliveryPreviewScreen.kt)
composes DetailShell with an explicitly scrolling body and ActionBar outside it.
BackHandler and visible back return to the existing gallery. Apply requires a
positive quantity and increments only a local count; Reset restores draft
defaults while retaining the count. No export/storage behavior is implied.
Read [UI mechanics](../core/ui/README.md#choices-disclosure-and-detail-composition),
[native disclosure/layout](../../../substrate/compose-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08),
and [bounded arithmetic](../../../language/kotlin-widen-before-integer-arithmetic.md).

The focused API 36 emulator run passes nineteen checks: three
[DetailsCatalogTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DetailsCatalogTest.kt)
checks, one [ValueStepperTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ValueStepperTest.kt),
one [DetailShellLayoutTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DetailShellLayoutTest.kt),
and fourteen prior component/context/layout regressions. New checks execute
disabled/selected chips, both step endpoints and disabled quantity controls,
real note editing with collapse/reopen, family/theme/saved-state retention,
navigation return, Apply/Reset and fixed footer bounds after body scrolling.
The extreme-integer check activates native buttons across Int.MAX_VALUE and
Int.MIN_VALUE. The shell check changes fontScale to two and measures actual
nonoverlapping header/body/footer geometry and larger rows.

Both native consumers build; 21 iOS app checks and four UI token/material checks
per platform pass. The Swift walkthrough records separate manual navigation and
dark-toolbar evidence. This focused Android run does not rerun unrelated
camera/GPU suites or establish comprehensive TalkBack, IME/keyboard avoidance,
localization, rotation or hardware coverage. Next: which real screen needs a
route-owned ViewModel and a draft that survives beyond saved-instance restoration?

## Journeys gallery

Added 2026-10-08. [ComponentCatalogScreen](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
adds Journeys, for twelve families and 45 building blocks. It holds password
TextFieldState with plain remember, profile TextFieldState with
rememberTextFieldState, and progress/preferences/counters with rememberSaveable,
all above route replacement. Route/family restoration does not deliberately save
the password. [JourneyExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/JourneyExamples.kt)
reads the native draft to project passive requirement/step rows and owns all
local readiness and navigation actions.

The account destination uses AuthShell and increments only a local preview
counter. OnboardingPage shares the profile note through three steps; the first
requires a nonblank note. Previous/Restart retain edits/preferences. Explicit
Finish increments once and disables until navigation/restart. A keyed page resets
native scrolling, while the draft stays above that key. Focus clears before
step changes. BackHandler and visible Back return to the gallery.

Read [the UI walkthrough](../core/ui/README.md#rich-input-and-journey-pages),
[native mechanics](../../../substrate/compose-rich-input-and-journey-pages.md)
and [shared ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08).
[JourneyCatalogTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/JourneyCatalogTest.kt)
adds three interaction/restoration cases, while
[JourneyFieldTest](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/JourneyFieldTest.kt)
executes long multiline input and error/help precedence. These do not implement
sessions, credential storage or a durable profile. Next: which entry-owned state
holder should coordinate a real form mutation and server-side readiness?

Final evidence, 2026-10-08: all nineteen focused API 36 emulator checks pass:
three JourneyCatalogTest cases, one JourneyFieldTest case, three DetailsCatalogTest
cases, two ContextCatalogTest cases and ten ComponentCatalogTest regressions.
New checks cover native password semantics, real multiline editing, passive
requirement states, error/help precedence, disabled inputs, first-step gating,
Previous/Restart retention, explicit finish count, body scrolling with fixed
actions, family/theme changes and saved-state restoration. Restoration retains
note/progress/counts while clearing the ephemeral password and disabling the
account action. The first run's disabled-field selector incorrectly required
SetText; a label/disabled assertion corrects that test assumption.

Both apps build; all 22 iOS app checks and four UI token/material checks per
platform pass. This focused run does not rerun unrelated GPU/camera suites or
establish full TalkBack, software keyboard/inset, localization, rotation,
provider autofill or physical-device coverage. Swift's walkthrough records
separate native geometry and manual input/navigation evidence.

## Activity gallery

Added 2026-10-09. Activity is the thirteenth family, with 51 building blocks.
[Catalog ownership](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt) retains preview values through family/route
changes. [ActivityExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ActivityExamples.kt) contains passive collaborator artwork,
an expandable summary and the separate bounded native List/LazyColumn screen.
The app model is remembered above route replacement in ComponentCatalogScreen.
A request generation drives a destination LaunchedEffect; delay is cancellable,
finally restores busy/loading, and disposal also clears requests not yet started.
BackHandler and visible Back return to the gallery without discarding feed values.

The local fixture starts with three rows, admits pages of three up to nine,
retains rows/expansion on failure, consumes Fail next page once and retries only
on action. Refresh resets to the first page and retains surviving expanded IDs.
Refresh/page work cannot overlap. Its 450 ms delay is a visibility aid, not
network evidence. No persistence, cache or domain paging port is added.

Read [UI mechanics](../core/ui/README.md#activity-and-paged-collections),
[native lifetime](../../../substrate/compose-refresh-and-lazy-activity.md)
and [shared reasoning](../../../../../../notes/patterns/refresh-and-pagination-ownership.md).
[Native activity checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ActivityCatalogTest.kt) cover the actual fixture boundaries.

Final evidence, 2026-10-09: all four ActivityCatalogTest checks pass in the final
focused API 36 emulator run. They execute a native pull gesture, duplicate
admission, failed-page retry, retained rows/expansion, exhaustion, refresh,
destination disposal/retry and Back/family/theme retention. The broader run
passes nineteen existing ComponentCatalogTest, ContextCatalogTest,
DetailsCatalogTest, JourneyCatalogTest and JourneyFieldTest regressions.

The broader run's only failure was the new retention test waiting for an
unrealized footer after a page moved it offscreen. The corrected check scrolls
the list to its status and waits for the updated count before navigating. The
four activity tests then pass; no implementation change followed that regression
run. An earlier helper also needed performScrollToNode rather than finding
unrealized lazy descendants directly. These are test assumptions, not pagination
or state-retention failures.

Both native consumers build; all 27 iOS app checks and four UI token/material
unit checks per platform pass. Notes validation passes. This focused coverage
does not rerun unrelated GPU/camera checks or establish comprehensive TalkBack,
localization, process-restored feeds, real-network or physical-device behavior.
Next: choose a domain paging contract and route-owned state holder for a real feed.

## Media gallery

Added 2026-10-09: Media is the fourteenth family, with 57 building blocks.
[Catalog ownership](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt) retains values through family/route changes.
[MediaExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/MediaExamples.kt) composes native paging, passive position, supplied
artwork, overlay favorites and per-study ratings. MediaTile shows the selected
study's metadata and an explicit Use action that increments a local count.
ComponentCatalogScreen keeps native PagerState, a saveable rating map keyed by
study ID, favorite-ID list, enabled flag and local use count above family/route
replacement. MediaValues is a passed snapshot whose callback updates those
owners. The fixed study order maps currentPage to a record; rating identity is
the record ID. Previous/Next use an app-owned coroutine scope and token motion.

Enable media controls gates gestures/rating/favorites/use. Clear resets only the
current rating. The procedural artwork needs no asset loader or new UI
dependency, and is not an image editing or GPU performance feature.

Read [UI mechanics](../core/ui/README.md#media-browsing-and-actions),
[native paging](../../../substrate/compose-media-paging-and-overlays.md)
and [shared ownership](../../../../../../notes/patterns/media-selection-and-passive-artwork.md).
[Native checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/MediaCatalogTest.kt) link actual code and assertions.

Final evidence, 2026-10-09: Four final media checks pass on the temporary API 36 emulator: three
MediaCatalogTest checks plus one MediaComponentTest check. They execute native
swiping, explicit paging, independent favorite/rating actions, per-record
retention, selected/disabled choices, Clear, use counts, Back/family/theme changes
and saved-state restoration of values/native page position. The font-scale-two
240 dp composition checks target bounds and proves passive artwork/icon text and
position do not absorb an overlay action. The prior broader run passes all
eighteen checks (four media plus fourteen existing component/activity regressions).
A final scoped rerun verifies the rating map keyed by study IDs after moving
from the initial fixed-order rating list; no source change followed it.

Initial attempts could compile but had no connected device. A temporary read-only
instance of API36_Test supplied execution without deleting its locked saved data.
No comprehensive TalkBack, localization, arbitrary live-record replacement,
physical-device performance or durable review-storage guarantee is claimed.

Both native consumers build; four UI token/material unit checks per platform
pass. Notes validation passes. Next: choose
an admitted media record contract and a selection fallback before adding a loader.

## Communication gallery

Claim: a caller-owned conversation fixture can exercise draft and transfer
recovery independently of shared component rendering and backend transport.

Added 2026-10-09: Communication is the fifteenth family, with 63 building blocks.
[CommunicationExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/CommunicationExamples.kt) composes all six new APIs; the Design room row
opens a destination with a persistent composer.

ComponentCatalogScreen keeps rememberTextFieldState plus saveable primitive
flags, transfer enum, append-only sent-text/attachment lists and counters above
route replacement. CommunicationValues is a passed snapshot; its callback updates
those owners. The fixture never deletes/reorders history, so its append-only
positions remain stable. Real messages need domain IDs and persistence policy.

The explicit fixture controls admit waiting → transferring → paused/failed/
complete, with Resume/Retry returning to transferring. There is no timer or
network request. Adding an incomplete attachment blocks send; failing/retrying
preserves text, and cancellation removes only the file. A nonblank text or a
completed attachment can send. Admission appends one local message, then clears
the draft/file. Disabled callbacks guard transitions. Inspect increments a local
counter; typing presence is a toggleable fixture. No filesystem reads or messages
to another person occur.

ConversationPreviewScreen uses a bounded Column with app-owned imePadding,
a weighted scrolling transcript/recovery body and a separate composer. Native
Back clears focus before replacing the route. This is source-level inset setup;
the executed semantic checks do not establish all real IME occlusion/animations.

Read [UI mechanics](../core/ui/README.md#communication-and-attachments),
[native framework behavior](../../../substrate/compose-composer-and-ime.md)
and [shared draft/transfer ownership](../../../../../../notes/patterns/composer-drafts-and-transfer-ownership.md).
[Native checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/CommunicationCatalogTest.kt) link actual assertions.

Final evidence, 2026-10-09: Four final communication checks pass on the isolated read-only API 36 emulator:
three CommunicationCatalogTest cases and one large-text CommunicationComponentTest.
They execute native multiline editing, pending-file send blocking, failure/retry,
pause/resume, completion, independent attachment inspection, draft clearing,
cancellation, disabled input/actions, route/family/theme retention and saved-state
restoration. The narrow font-scale-two case checks passive artwork/typing semantics
and busy input/slot/send gating while retaining text. Four UI unit checks pass.

The first 17-case run passed thirteen existing component/media regressions but
failed the new harness: disabled fields omit SetText, and pinned controls have no
scroll ancestor. Retained-label selectors and appropriate action placement fixed
those checks. One later empty-field assertion mixed help/label copy with text;
asserting native EditableText separately fixed it. The final four-case run passes.
The temporary read-only emulator was used without resetting saved AVD data.

Both consumers build. Full VoiceOver/TalkBack, localization, every keyboard,
rotation/iPad, physical-device performance and real service delivery remain
outside this slice. Keep pinned slots small and supply bounded screen layout.
Next: introduce an actual message/attachment service through existing thin seams
with operation identity and draft-revision-aware success handling.

## Editing gallery

Claim: a local library can show identity-preserving selection/removal/undo without
turning shared UI components into a collection service.

Added 2026-10-09: Editing is the sixteenth family, with 68 building blocks.
[EditingExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/EditingExamples.kt) composes all five APIs; Open library editor opens
the native destination with six stable fixture records.

ComponentCatalogScreen saves small primitive strings, lists and flags above
route replacement. EditingValues is an immutable passed snapshot; callbacks
update those owners. LibraryEditingScreen uses stable LazyColumn item keys,
screen-owned imePadding and a separate bottom notice/action region. Swipe state
is intentionally not saved; restoring domain values cannot replay the gesture.

The feature trims one tag, rejects case-insensitive duplicates, caps tags at six
and preserves rejected drafts. Accepted admission clears only that draft; removing
a tag preserves it. Filtering derives visible records without changing selected
IDs. Select visible items changes only that projection; the bottom summary names
hidden selection and bulk actions operate on all selected IDs. Removal guards
valid/nonremoved identity and captures the latest IDs plus prior selected subset.
Undo restores those same records/selection while retaining archive flags and source
ordering. Another removal replaces the snapshot; dismiss discards recovery.
Disabled commands are guarded at the owner too. No files or backend records change.

Read [UI mechanics](../core/ui/README.md#selection-tokens-and-row-editing),
[native framework behavior](../../../substrate/compose-wrapping-and-swipe-actions.md)
and [shared identity/undo](../../../../../../notes/patterns/selection-identity-and-undo.md).
[Checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/EditingCatalogTest.kt) link actual assertions.

Final evidence, 2026-10-09: Five final editing checks pass on the isolated read-only API 36 emulator.
They exercise hidden selection and bulk archive/remove/undo, native right/left
swipes, reset and subsequent unarchive, saved-instance undo/restoration without
command replay, duplicate rejection/native Done, accepted tag clearing, removal
with draft retention, disabled controls and Back/family/Dark/Glass retention.
The narrow font-scale-1.5 check measures wrapped native 48dp removal targets and
native selection state; another case invokes custom accessibility actions and
checks disabled swipe/restoration cannot dispatch. Four UI unit checks pass.

The first compile attempt treated CustomActions as a callback action; it is a
semantics property list. Text-edit methods return Unit and cannot chain into
performImeAction. Reading custom action values on the UI thread and separating
input/submission corrected the harness. The first 18-case execution passed four
editing cases and thirteen existing component/communication regressions; one
assertion expected supporting copy without its visible Error prefix. Asserting
native Error semantics plus retained draft fixed that selector. The final five-
case run passes. Saved AVD data was not reset.

Both consumers build. Full VoiceOver/TalkBack, localization, all keyboard/window
sizes, macOS runtime, physical-device gestures/performance and persistent undo
remain outside this slice. A real library command needs receipts/revisions and
failure projection through existing result/mutation seams.
Next: how should undo admit a record that changed after its removal?

## Insights gallery

Claim: a caller-owned dashboard can compare periods and empty data without
overwriting an independently owned goal or hiding chart values in decoration.

Added 2026-10-09: Insights is the seventeenth family, with 74 building blocks.
[InsightsExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/InsightsExamples.kt) composes six APIs. Open insights dashboard
opens the scrolling native destination.

ComponentCatalogScreen saves primitive period, completed count, enabled/empty
flags and destination state above route replacement. InsightsValues is an immutable
snapshot; callbacks update those owners. InsightsPreviewScreen owns a bounded
vertical scroller, with period tabs' native horizontal scroll inside it. Disclosure
state is local presentation; it is not part of a durable analytics model.

The immutable fixture provides seven daily Week samples or four weekly Month
samples. Focus totals are 210/420 minutes and Design/Reading/Practice categories
sum to the same total. Both periods use a 200-minute bar maximum; sparklines
explicitly describe their relative scale and have a native exact-values disclosure.
Comparison copy/direction is fixture policy. Empty mode replaces focus plots while
keeping the independent session goal. Its 0..20 commands reject disabled/out-of-
range changes; Reset returns to 14. Drawing components never aggregate, format a
unit/date, select a period or perform analytics collection. No service is invoked.

Read [UI mechanics](../core/ui/README.md#insights-and-small-charts),
[native drawing](../../../substrate/compose-chart-drawing-and-semantics.md)
and [chart meaning/scales](../../../../../../notes/patterns/chart-meaning-and-scales.md).
[Checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/InsightsCatalogTest.kt) link executed assertions.

Final evidence, 2026-10-09: Four final Insights checks pass on the isolated read-only API 36 emulator.
They exercise period/empty/goal independence, sample disclosure, category copy,
disabled goal controls, reset, Back/family/Dark/Glass retention and saved-state
restoration. The drawing case captures pixels for finite signed extremes, a flat
sequence and empty input. A narrow font-scale-two panel checks merged category/value
semantics, bounded/clamped progress copy and an independently activated/disabled
minimum-height footer action. Six UI unit checks pass.

The initial nine-case execution passed three Insights checks plus all five editing
regressions. One test clicked Month after scrolling down to the goal, but generic
performScrollTo followed the tab's nested horizontal scope without restoring the
outer viewport. Two further selector/placement attempts confirmed Month remained
offscreen. Scrolling the outer insight-scroll viewport back to its header, then
asserting visibility and clicking the native tab, fixed the harness. The final
four-case run passes; actual category values are asserted after scrolling their
panel into view. The temporary read-only emulator did not reset saved AVD data.

Both consumers build. Full VoiceOver/TalkBack, all locales/keyboards/window sizes,
macOS runtime, dense data, time axes, physical-device rendering/performance and
real analytics providers remain outside this slice. Next: introduce an admitted
metrics service through existing thin seams before adding dashboard orchestration.

## Scheduling gallery

Claim: a session draft, date availability and an applied session are distinct
feature values above family/route/theme changes.
Origin, 2026-10-09: [Scheduling source](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/SchedulingExamples.kt) and
[consumer checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SchedulingCatalogTest.kt), linked from the root component catalog.

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
One existing Android date-picker regression passes as well. The UI package check
passes seven Swift and six Kotlin checks. See the native
substrate for what each check establishes. A full VoiceOver/TalkBack, all locales,
real-zone DST, device calendar, reminders and physical-device audit remains open.

Read [UI APIs](../core/ui/README.md#dates-and-agendas), [native mechanics](../../../substrate/compose-time-and-date-drafts.md) and
[calendar meaning](../../../../../../notes/patterns/calendar-dates-and-clock-readings.md).
Next: define a real availability/scheduling port and reject stale revisions
without discarding a user's uncommitted time/date draft.

Final Android evidence, 2026-10-09: the six-check scheduling/date run passes
after correcting the existing test selector. The native calendar labeled
October 9 as `Today, Friday, October 9, 2026`, so its exact full-date selector
failed; a full-date substring allows the Today prefix and retains exact UTC
timestamp/cancel/commit assertions. The fifth scheduling check covers independent
date endpoints and disabling an open draft. An isolated read-only emulator was
used; its initial crash-report helper handshake stalled, so that instance was
restarted with crash reporting disabled. This changed test setup only.

## Workspace gallery

Claim: selected project identity, collection projection and compact detail intent
are separate feature values above adaptive layout and catalog routes.
Origin, 2026-10-09: [Workspace source](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/WorkspaceExamples.kt) and
[consumer checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/WorkspaceCatalogTest.kt), reached through Components → Workspace.

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
Four component checks exercise native selected/disabled actions, passive current copy, 240-dp font-scale-two wrapping, scrollable rail items, width/RTL/text reflow and selection across hidden collections. One catalog check covers native system Back, saved-state restoration and family/theme/route changes.
Wide hosts are synthetic native geometry on phone simulators; physical tablet,
foldable hinge, predictive-back animation, keyboard/focus, localization and full
VoiceOver/TalkBack coverage remain open. Scroll offsets are slot-local and are
not promised across reflow. Read [UI APIs](../core/ui/README.md#adaptive-workspaces),
[native mechanics](../../../substrate/compose-bounded-panes-and-navigation.md)
and [adaptive ownership](../../../../../../notes/patterns/adaptive-layout-and-navigation-state.md).
Next: connect a real routed workspace and admit deep-link selection before
choosing its compact pane.

## Tables gallery

Claim: full ordering, page projection and inspected identity are distinct feature
values above table slots and catalog routes.
Origin, 2026-10-09: [ledger source](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/TableExamples.kt) and
[consumer checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/TableCatalogTest.kt), reached from Components → Tables → Open project ledger.

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
Four Android UI checks exercise native sort/endpoint/global disabled actions,
scrolling to an independent cell action, alignment, 240-dp font-scale-two growth,
RTL, sort/page/inspection, empty projection and saved-state/family/theme/route
retention. The first movement assertion incorrectly treated clipped semantic
bounds as a translation; the corrected check observes native ScrollState movement,
visible action activation and aligned header/cell bounds. The isolated read-only
emulator does not reset or mutate the saved AVD baseline.
No full VoiceOver/TalkBack, all locales, physical-device performance, desktop
keyboard traversal, virtualization or remote response admission is established.
Read [UI APIs](../core/ui/README.md#tables-and-pagination),
[native mechanics](../../../substrate/compose-table-columns-and-scrolling.md) and
[shared policy](../../../../../../notes/patterns/table-sorting-and-page-ownership.md).
Next: define stable ordering and page/cursor revisions for a real data source,
then prevent a late response from replacing a newer user's sort/page intent.

## Commerce gallery

Claim: saveable cart values and review snapshots outlive catalog routes/themes,
while a review sheet remains ephemeral presentation.

Origin, 2026-10-09: [Commerce source](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/CommerceExamples.kt)
and [consumer checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/CommerceCatalogTest.kt),
reached from Components → Commerce → Open cart preview (95 blocks/23 families).

CommerceValues holds bounded product quantities, delivery, code draft, applied
discount/error, enabled/busy state and recorded review total/items/count. The
feature admits IDs/quantity limits before deriving integer-cent subtotal,
ten-percent item discount, delivery and total. cartMoney is fixed USD display
copy for this fixture, not a general monetary formatter.

Text editing does not change an applied discount. Button/Done admission trims and
normalizes the ASCII sample code; invalid attempts project an error and retain
prior valid discount. Busy blocks code operations and review while retaining
draft/cart. Clear empties quantities, not delivery/draft/discount; Restore changes
only sample quantities. Global disabled blocks all cart operations.

ComponentCatalogScreen hoists saveable primitives/route before preview branches.
CommerceContent remembers review visibility keyed by availability. Review records
a total/item snapshot immediately and shows a disposable native sheet. Restoring
the route keeps its recorded values while discarding the sheet. Quantity edits
recalculate current totals without rewriting the snapshot. The host clears native
focus before code application/review and bounds review content scrolling.

Example: three kits and one notebook with Pick up and applied STUDIO10 review
at $89.10. Clear then restore shows $26.10 but preserves that review. Read
[UI walkthrough](../core/ui/README.md#products-and-order-composition),
[native mechanism](../../../substrate/compose-inline-fields-and-order-composition.md)
and [shared ownership](../../../../../../notes/patterns/price-copy-and-committed-cart-values.md).

Verification, 2026-10-09: both consumers build; 49 iOS app checks, four focused
Android Commerce UI and seven Swift/six Kotlin UI package checks pass. The
consumer cases exercise quantity endpoints/unavailability, keyboard code
application, errors, totals, busy/disabled controls, discarded review sheets,
routes/themes and restoration. Component cases measure narrow large-text/RTL
bounds, independent actions and retained draft after width changes. No full
TalkBack/localization/device audit, authoritative quote or purchase is established.
Next: introduce actual quote identity and guarded mutations through a service seam.

## Discovery gallery

Claim: saveable feature values outlive catalog routes and themes while filter
draft/presentation remain temporary native UI mechanics.

Origin, 2026-10-09: [Discovery source](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/DiscoveryExamples.kt)
and [consumer checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DiscoveryCatalogTest.kt),
reached from Components → Discovery → Open search workspace (91 blocks/22 families).

DiscoveryValues projects six stable local records from a trimmed literal query
and applied topic/archive facets. Enabled/visible guards admit opening and saving
independently. Hidden saved IDs and opened identity are retained. Explicit search
submission, suggestions and opening remember trimmed, case-insensitively unique
queries, bounded to three; clearing text leaves recent queries intact.

ComponentCatalogScreen hoists saveable primitives and route above its preview
branch. DiscoveryContent remembers only filter draft/visibility, copies applied
values when opening and commits solely on Apply. Reset changes draft; native
dismissal, availability loss and restoration discard the sheet. Applied chips
remove only their facet. The sheet bounds a vertical scroller and the host clears
focus before opening it or submitting search. Matching offsets stay Kotlin-local;
HighlightedText receives complete literal runs.

Example: save/open Motion study, filter it out, discard a new draft and restore
the route. Saved/open/applied values remain, while the pending sheet disappears.
Switch families and Dark/Glass without replacing feature values. No service,
record destination, durable history, debounce or multilingual ranking exists.

Verification, 2026-10-09: both consumers build; 47 iOS app checks, four focused
Android Discovery UI and seven Swift/six Kotlin UI package checks pass. Catalog
checks exercise filter cancel/apply/reset, restoration, hidden bookmarks,
history, independent actions, routes, theme changes and disabled controls.
Component checks measure 240-dp font-scale-two/RTL and action bounds. This is not
a TalkBack/localization/device/backend audit. An overlapping instrumentation run
was discarded; the final sequential four-case run passed completely.
Read [UI walkthrough](../core/ui/README.md#search-and-discovery),
[native mechanism](../../../substrate/compose-annotated-text-and-search-actions.md)
and [projection pattern](../../../../../../notes/patterns/search-projection-and-filter-drafts.md).
Next: connect actual query lifetime and account-scoped history through services.

## Account gallery

Claim: captured account-qualified intent and device-scoped capability projection
need distinct feature ownership above reusable account UI.
Origin, 2026-10-09: [account source](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/AccountExamples.kt) and
[consumer checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/AccountCatalogTest.kt), reached from Components → Account → Open account center.

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
Four focused Android checks cover native selected/disabled/current choices,
unknown selection, closing unavailable menus, 240-dp font-scale-two growth, RTL,
independent actions, cancelled/confirmed removal, protected current device and
saved-state/family/theme/route retention. Nonsecret primitives are saveable above
preview branches; prompts use remember keyed by context/availability. Saved-state
recreation discards outstanding confirmations without removing a device.

The feature distinguishes dialog presentation from pending intent: dismissing
the wrapper cannot erase the value needed by its confirm callback. Context and
eligibility are checked at the feature boundary; a disabled control is not an
authorization mechanism. Real principal/workspace/session relationships remain
an explicit future domain decision rather than a guarantee of this fixture.
Read [UI APIs](../core/ui/README.md#accounts-and-access), [native mechanics](../../../substrate/compose-account-menus-and-action-slots.md)
and [shared scope](../../../../../../notes/patterns/account-context-and-device-capabilities.md).
No real auth/capability work, full VoiceOver/TalkBack, all locales, physical-device
profiling or secure-storage behavior is established. Next: define context scope,
then connect session commands and foreground permission refresh through adapters.

## Notifications gallery

Claim: saved inbox identities survive changes of presentation while transient
native details are discarded on saved-state recreation.

Origin/evidence, 2026-10-09:
[NotificationExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/NotificationExamples.kt)
projects the same four-update fixture as Swift. The
[component route](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
hoists nonsecret filter/read/archive/undo/opened/count primitives. The new inbox
preview branch follows earlier family owners so they remain composed while it is
open. Its local sheet flag uses remember; LaunchedEffect closes unavailable details.

Example: choose Unread, open Review (which marks it read), archive Invitation and
mark visible updates read. Undo restores Invitation unread, even after state
recreation. Theme/family/route changes retain feature choices and earlier cart
quantities. Active-inbox count and current visible count have different scope.
Details remain available after their record leaves Unread, then close when
archived or globally disabled. Reset restores fixtures without erasing the open
operation count. All model commands reject unknown/archived/disabled targets.

Verification: both consumers build; 51 iOS app cases, four focused Android inbox
cases and seven Swift/six Kotlin UI package cases pass.
[Component cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/NotificationComponentTest.kt)
cover native semantic roles, independent actions and large-text/RTL bounds.
[Consumer cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/NotificationCatalogTest.kt)
exercise sheet presentation/dismissal, filtered bulk scope, archive undo,
saved-state recreation, route/theme/family retention and retained cart values.
Family tabs have a horizontal scroll inside the vertical gallery: tests bring
the tab container into the vertical viewport before targeting its horizontal tab.
No TalkBack, all-locales or device performance audit is established.
Read [UI walkthrough](../core/ui/README.md#notifications-and-inbox),
[native mechanics](../../../substrate/compose-notification-actions-and-narration.md)
and [shared pattern](../../../../../../notes/patterns/inbox-projection-and-read-identity.md).
Next: define real receipt/undo conflicts and account scope before service integration.

## Plans gallery

Claim: saved plan/current/usage values retain their meaning independently of
transient review presentation and catalog routes.

Origin/evidence, 2026-10-09:
[PlanExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/PlanExamples.kt)
owns the same Starter/Studio/Team fixture as Swift. The
[component route](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
hoists nonsecret selected/current/cycle/usage/review primitives above preview
branches. The Plans branch follows earlier owners so their remembered state is
not removed. Sheet presence uses remember; LaunchedEffect dismisses an invalid review.

Example: select Studio/Yearly while current Starter remains three of five. Review
stores this plan/cycle and application rechecks it before switching allowance to
fifty. Recreating state retains review metadata but closes its sheet and applies
nothing. Reach fifty and apply Starter: fifty used remains, the bar clamps full
and explicit copy reports exceeded allowance. Pending/disabled/unavailable and
stale review commands reject application or export. Reset usage is explicit.

Verification, 2026-10-09: both consumers build; 53 iOS app cases, four focused
Android Plans cases and seven Swift/six Kotlin UI package cases pass.
[Component cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlanComponentTest.kt)
cover native selection/eligibility and large-text bounds;
[consumer cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlanCatalogTest.kt)
exercise review/application, retained usage, current/draft distinction, quotas,
saved-state recreation, theme/family/route retention and earlier cart quantities.
The initial price assertion matched the same narration in card and sheet; the
final check scopes it to the review container, rather than changing that copy.
Nested catalog scroll tests bring the family container into the vertical viewport.

Passive complete labels must include price/availability/overflow meaning.
Selected UI is not entitlement authorization; a real product/quote/receipt service
needs explicit identity and validity. No TalkBack, all-locales, billing service or
physical-device audit is established.
Read [UI walkthrough](../core/ui/README.md#plans-and-usage),
[native mechanics](../../../substrate/compose-plan-slots-and-usage-bars.md) and
[shared pattern](../../../../../../notes/patterns/plan-choice-and-applied-allowance.md).
Next: actual service-owned capabilities and usage periods before store integration.

## Files gallery

Claim: saveable feature primitives retain file choices across destinations while
the inspector's transient presence can be safely discarded during recreation.

Origin/evidence, 2026-10-09:
[FileExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/FileExamples.kt)
contains the fixed acyclic BrowserItem tree and FileBrowserValues projection.
[Catalog wiring](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
owns saveable query/expansion/favorites/selection/open count/eligibility values
above route returns. The Files return follows earlier family owners, so visiting
it does not remove their state from composition.

Example: collapse all, search field, favorite/open the nested image and recreate
saved state. The sheet is discarded while selected identity and favorite survive.
Clear search restores manual expansion. Expand the path to reveal the selected
row, collapse its parent, and switch families/themes; state remains. Reset clears
query/selection and restores initial expansion without losing favorites or opens.
Guarded opening requires visibility; a retained selected item can still be
inspected/favorited while hidden. Empty/disabled state closes the sheet.

Verification, 2026-10-09: both consumers build; 55 iOS app checks, four focused
Android Files cases and seven Swift/six Kotlin UI package cases pass.
[Gallery cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/FileCatalogTest.kt)
execute the actual native search, disclosures, favorite/inspector, saved-state
recreation, themes/routes and earlier cart-state retention, plus disabled/empty/
unknown/unavailable/hidden command rejection. UI checks are over local records;
they establish no provider identity, file loading, OS permission or full TalkBack
traversal. Search IME dismissal and pre-inspector focus clearing are feature actions.

Read [UI walkthrough](../core/ui/README.md#files-and-hierarchy),
[native mechanics](../../../substrate/compose-tree-actions-and-indentation.md),
[shared pattern](../../../../../../notes/patterns/tree-projection-and-retained-selection.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#files-and-hierarchy).
Next: asynchronous loaded-child projection and provider reconciliation outside
the reusable rows.

## Sharing gallery

Claim: saveable membership values retain role/link choices while a transient
revision-bound removal request is safely discarded during recreation.

Origin/evidence, 2026-10-09:
[SharingExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/SharingExamples.kt)
owns known contacts, guarded SharingValues commands and native clipboard dispatch.
[Catalog wiring](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
owns saveable IDs/role map/draft/error/link choice/revision/counts above route
returns. The Sharing return follows earlier family owners and keeps their state
in composition.

Example: invite River as Editor, change Jamie to Viewer and turn link access Off.
Open removal, then recreate saved state. Members/roles/link choice survive and
the confirmation is discarded. A fresh explicit request may remove River; Cancel
changes nothing. Protected, unknown, stale, disabled and pending commands reject.
The fixed address lookup is not generic email validation and sends no message.

Explicit copy checks current eligibility, then invokes a supplied copyText callback
or the native ClipboardManager/ClipData adapter and records admission. Off hides
the link and disables the app copy button; previous clipboard text remains.
The callback is the test seam, while native manual link selection is separate.

Verification, 2026-10-09: both consumers build; 57 iOS app cases, four focused
Android Sharing cases and seven Swift/six Kotlin UI package cases pass.
[Gallery cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SharingCatalogTest.kt)
execute actual role menus, button/IME invitations, errors, cancel/confirm,
invalidation, saved-state restoration, themes/routes and retained prior cart state.
An injected copy callback receives the example URL once; rejected requests do not
dispatch. These checks do not exercise the OS selection toolbar/clipboard feedback,
full TalkBack or actual backend permissions.

Capture the rendered removal request before ConfirmationDialog: it dismisses
before calling confirmation. Reading mutable removal state in that callback would
find null. The captured ID/revision is still rechecked against feature admission.
Read [UI walkthrough](../core/ui/README.md#sharing-and-access),
[native mechanics](../../../substrate/compose-member-slots-and-selectable-links.md),
[shared pattern](../../../../../../notes/patterns/membership-identity-and-confirmed-revisions.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#sharing-and-access).
Next: actual provider concurrency, account scope and admitted mutation results.

## Playback gallery

Claim: saveable per-track values retain local timeline choices while a real
engine remains responsible for actual playback and lifecycle reconciliation.

Origin/evidence, 2026-10-09:
[PlaybackExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/PlaybackExamples.kt)
owns fixtures, guarded immutable PlaybackValues and content.
[Catalog wiring](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
owns selected ID, Double position map, favorite IDs, playing/speed/repeat/scenario,
flags and counts in rememberSaveable above route returns. The Playback return
follows earlier family owners so it does not remove their state from composition.

Example: seek Coastline to 45, switch to Orbit, play and advance at 1.5×, then
return. Each track retains its own position and selection pauses. Buffering pauses
transport but keeps favorite eligible. Recreate saved state: position map, speed,
repeat, favorites and scenario restore. This is local UI data, not a running audio
resource. Switching families/themes/routes retains it and the earlier cart.

Seek clamps finite input and pauses at the end. Playing at the end resets to zero.
Manual steps require playing and a finite positive delta/target; repeat wraps,
otherwise the end clamps and pauses. Unknown/unavailable/invalid/ineligible
commands reject without changing counts. Retry restores Ready without resuming.
Disabled/empty state retains positions; Reset clears positions/transport choices
and recovers Ready/nonempty while preserving favorites/counts.

Verification, 2026-10-09: both consumers build; 59 iOS app cases, four focused
Android Playback cases and seven Swift/six Kotlin UI package cases pass.
[Gallery checks](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlaybackCatalogTest.kt)
execute native slider SetProgress, transport, menus, repeat/favorite, recovery,
saved-state recreation, themes/routes and earlier cart retention. These checks
do not simulate a physical drag, full TalkBack, engine restoration, media loading
or playback performance. No timer or audio session exists in this fixture.
Read [UI walkthrough](../core/ui/README.md#playback-and-timeline),
[native mechanics](../../../substrate/compose-playback-slots-and-native-transport.md),
[shared pattern](../../../../../../notes/patterns/media-timeline-and-transport-admission.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#playback-and-timeline).
Next: engine command admission and identity-scoped seek completion before adding
OS transport/background playback.

## Verification gallery

Claim: nonsecret saved choices and transient codes/attempts give verification UI
clear restoration behavior without replaying a command or claiming authentication.

Origin/evidence, 2026-10-09:
[VerificationExamples](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/VerificationExamples.kt)
owns guarded immutable VerificationValues.
[Catalog wiring](../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/components/ComponentCatalogScreen.kt)
owns all values above route returns. Channel/response/generation/times/enabled/
counters use rememberSaveable; draft/request/error/verified use transient remember.
Earlier family owners remain composed during the destination.

Example: paste 123-456, submit and advance 30 seconds while pending, then recreate.
Channel/timing/counters restore; draft and pending check disappear. No check
replays. Resend starts a new generation; a wrong local code produces an error,
editing clears it and Done begins the same guarded command as Verify.
The matching fixture result clears draft and presents local success.

Each request captures attempt ID plus challenge generation/channel/code. A
canceled or disabled request cannot apply later, and a new begin has a distinct ID.
Manual expiry discards pending/draft; resend/channel/reset create fresh challenges.
Reset retains counters. These UI guards do not supply real code delivery, rate
limits, durable deadlines, server validation or auth state.

Verification, 2026-10-09: both consumers build; 61 iOS app cases, four focused
Android Verification cases and nine Swift/eight Kotlin UI package cases pass.
[Gallery cases](../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/VerificationCatalogTest.kt)
execute actual field replacement/Done, native buttons/menus, incorrect/unavailable
recovery, cancellation/expiry/stale/disabled/duplicate rejection, recreation,
theme/family/routes and earlier cart retention. They assert that code/pending are
discarded while counters/channel/timing survive. They do not drive physical paste
menus, OS Autofill delivery, full TalkBack or actual authentication.

Read [UI walkthrough](../core/ui/README.md#verification-and-code-entry),
[native mechanics](../../../substrate/compose-code-entry-and-autofill-hints.md),
[shared pattern](../../../../../../notes/patterns/challenge-drafts-and-attempt-identity.md)
and [behavior](../../../../../../contracts/behavior/ui-components.md#verification-and-code-entry).
Next: provider challenge identity and deadline reconciliation at the service seam.
