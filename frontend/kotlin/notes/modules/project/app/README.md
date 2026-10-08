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
form panels and hosts an explicit FoundryBackdrop around the screen's decorative
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
