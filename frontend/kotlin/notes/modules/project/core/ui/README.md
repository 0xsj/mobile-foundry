# Native UI module walkthrough

The Android UI library renders generic query state with Material controls,
caller copy and a composable domain content slot. It also owns V1 semantic
tokens and a scoped provider that adapts MaterialTheme.

## Origin and reading order

Extracted 2026-10-08 with AGP 9.0.1, Kotlin 2.3.20, and Compose BOM 2026.03.01.
Read [the contract](../../../../../../../contracts/behavior/query-ui.md),
[build file](../../../../../project/core/ui/build.gradle.kts), then
[QueryContent](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/query/QueryContent.kt).
Compare [NotesScreen](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesScreen.kt)
and [QueryCatalogScreen](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/query/QueryCatalogScreen.kt).

## Walkthrough and gotchas

The library uses the Android library and Compose compiler plugins, with AGP's
built-in Kotlin support. It depends on query/kernel and native UI libraries,
not services or app code. The host supplies MaterialTheme and scrolling.

QueryContent switches on QueryState, labels indeterminate progress, renders
retained content or empty copy, projects failures through publicInfo, and
forwards button callbacks. QueryCopy can receive localized application strings.
The content slot is composable; it receives a typed payload, not a service.
See [slot mechanics](../../../../language/kotlin-covariant-query-state-and-content-slots.md).

## Verification and limits

`make android-build` assembled the library and catalog. `make android-ui-test`
passed all ten app instrumented tests on API36_Test/Android 16. Four new
[QueryContentTest checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/query/QueryContentTest.kt)
cover the nine-state presentation matrix, progress semantics, exact callback
counts, internal failure copy, and gallery navigation with empty refresh/cancel.
The six existing catalog tests still pass, including both notes providers.

The installed Compose test API requires a range argument for
hasProgressBarRangeInfo; the tests explicitly match Indeterminate. Semantic
checks do not establish TalkBack usability or large-text/dark-mode layout.
The token slice below adds font/appearance checks; forms and further controls
remain subsequent capabilities.

## Token families and Material adaptation

The 2026-10-08 token slice follows [Styles](../../../../../../../STYLES.md) and
[the token contract](../../../../../../../contracts/behavior/ui-tokens.md).
Read the actual source in this order:

1. [Primitives](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Primitives.kt)
   owns sRGB/alpha values and internal palette steps.
2. [Semantic](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Semantic.kt)
   defines role types and the resolved bundle.
3. [Space](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Space.kt),
   [Typography](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Typography.kt),
   [Shape](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Shape.kt), and
   [Motion](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Motion.kt)
   supply native values. Collections exposed as immutable are backed by
   unmodifiable lists rather than mutable lists behind read-only interfaces.
4. [V1](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/presets/V1.kt)
   maps the canonical light/dark palettes.
5. [FoundryTheme](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/theme/FoundryTheme.kt)
   resolves inherited/system inputs, observes animator reduction, and maps Material.
6. [Token unit tests](../../../../../project/core/ui/src/test/kotlin/dev/mobilefoundry/ui/TokensTest.kt)
   cover fixtures, contrast, and reduction without composing a screen.
7. [TokenCatalogScreen](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/tokens/TokenCatalogScreen.kt)
   and [TokenThemeTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/tokens/TokenThemeTest.kt)
   exercise the real provider and native controls.

The app theme is now a thin delegate; generated purple colors and type defaults
were removed. Material3 and animation-core are API dependencies because public
token values expose their types. The nullable composition-local default lets
a nested theme inherit the actual preview appearance. Reduction combines parent,
system, and explicit requests. The observer is registered in DisposableEffect
and removed on disposal; no settings are mutated. See
[Compose theme and touch mechanics](../../../../substrate/compose-token-theme-and-touch-bounds.md).

QueryContent and [QueryCopy](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/query/QueryCopy.kt)
now live in components/feedback/query. Imports follow that package. Rendering
uses theme stack spacing and semantic feedback colors, with no service or
coroutine ownership added.

Three token unit tests pass; module/APK builds, eight notes ViewModel and two
starter regressions pass. All fourteen device tests pass on API36_Test/Android 16.
The four new tests cover nested scope/reduction and Material background mapping,
text height at fontScale 1/2 and at least 48 dp touch bounds, preview controls,
and action state surviving appearance changes. The initial visible-height
assertion was corrected to measure touch bounds; the substrate note explains why.
Live OS-setting changes, TalkBack, every large-text layout, and physical devices
are not established by these tests.

## Foundry Studio revision

Revised 2026-10-08 to establish an independent native/graphics identity: porcelain
and blue-graphite surfaces, cobalt actions, and sky/ochre/vermilion status colors.
Space now follows a four-point rhythm, panels use radius 16, native headings are
semibold, and standard custom motion decelerates over 200 ms. Palette primitives
and preset mappings retain their existing ownership.

Dark primary fills pair pale cobalt with dark ink. Material inversePrimary uses
the opposite appearance's accent so action text remains readable on an inverse
surface. The three revised unit tests pass, checking shared values, muted-text
and inverse-accent contrast, and reduction. The nested theme probe now checks
Material primary/onPrimary and inversePrimary alongside background mapping.
See [the shared pattern](../../../../../../../notes/patterns/semantic-tokens-and-native-themes.md#visual-identity-and-semantic-stability)
for the visual-identity reasoning.
The first revised device run passed thirteen of fourteen checks, including all
four token/theme tests. Notes navigation timed out waiting for Cancel loading
after selecting Slow; that scenario lasts 1.5 seconds. The same check passed in
isolation without code changes. This suggests a transient-state timing limit;
it does not establish a deterministic cause. No feature logic or wait threshold
was changed to obtain the isolated pass.
The final full rerun passed all fourteen checks on emulator-5554. It also logged
an initial device-property fetch timeout and took 5m 54s; the underlying timing
sensitivity remains a test limitation rather than a proven palette regression.

## Swappable material slice

Added 2026-10-08. Read [Material](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/styles/tokens/Material.kt),
the updated provider and V1, then [FoundryBackdrop](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/surface/FoundryBackdrop.kt)
and [FoundrySurface](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/surface/FoundrySurface.kt).
The material family owns blur/tint/highlight parameters as well as semantic
role selection. Content panels always remain solid. Floating surfaces replay
the bounded host's separately blurred background, tint it, then draw controls.

The nullable style override inherits the parent by default. Reduction combines
parent and explicit flags without claiming an Android-wide system preference.
MainActivity's catalog root owns a saveable session choice; the home switch
forwards its callback. TokenCatalogScreen owns local overrides and
[MaterialExample](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/tokens/MaterialExample.kt)
owns geometric scene state. FlowRow wraps actions at narrower widths. The source
is a Compose background; external SurfaceView/GL frames are not captured.
Read [layer mechanics](../../../../substrate/compose-backdrop-layers.md) and
[shared material ownership](../../../../../../../notes/patterns/material-themes-and-backdrops.md).

Four UI unit tests pass, including material fixtures, unsupported API/missing
backdrop fallback, and palette/motion stability. Both native apps build. The first
full device run passed sixteen of seventeen checks, including every new material
check. Existing HTTP health presentation timed out waiting for malformed-response
copy; the same test passed in isolation without changes to feature logic or waits.
This is a timing observation, not an established root cause.

[TokenThemeTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/tokens/TokenThemeTest.kt)
covers app/local choice, inherited reduction, callbacks and preserved scene state.
[GlassBackdropTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/tokens/GlassBackdropTest.kt)
samples a translated source, its update, and opaque fallback with an interactive
control. Its final extension also checks smoothing across a hard source edge.
The final full `make android-ui-test` run passed all seventeen checks, including
that edge-smoothing assertion, on emulator-5554/API36_Test. It took 5m 29s and
logged a device-property timeout; the existing query gallery test consumed about
188s. No wait thresholds or feature logic were changed to obtain the pass.
These targeted pixel assertions do not establish a complete visual audit,
large-text behavior for all new controls, rotated/scaled ancestor transforms,
external renderer capture, battery cost, or physical-device frame pacing.

## Native forms and write feedback

The 2026-10-08 forms slice adds
[FoundryTextField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/textfield/FoundryTextField.kt),
[FoundrySubmitButton](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/submitbutton/FoundrySubmitButton.kt), and
[MutationFeedback](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/mutation/MutationFeedback.kt).
OutlinedTextField keeps native editing/labels and adds caller help/error semantics.
The button disables while busy; feedback supplies a polite live region, public
failure projection and a composable success slot. Focus, validation, viewport
and execution remain caller-owned. Read [focus and IME mechanics](../../../../substrate/compose-form-focus-and-ime.md).

Components occupy Forms/TextField, Forms/SubmitButton and Feedback/Mutation
families, translated to lowercase packages. MaterialTheme consumes existing
tokens; no form-specific raw palette or service dependency is introduced.
The [device tests](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/forms/FormsCatalogScreenTest.kt)
exercise actual input, IME, disabled semantics and feedback. A disabled native
field omits SetText semantics, so tests identify it by EditableText instead;
the first run exposed that test-matcher mistake. See the app walkthrough for
final run evidence and limits. TalkBack speech and every large-text/keyboard
combination remain unmeasured.

The form gallery's material follow-up demonstrates the caller's remaining
responsibility: choose the floating role for its outer panels and provide a
backdrop host. The reusable surface and theme required no change. The actual
form pixel test now checks visible material selection, opaque reduction and
retained confirmation rather than only reading the selected theme enum.

## Questions and related reading

Why is emptiness supplied by the feature? Why can Refresh stay available while
loading without the component owning a coroutine? Read
[query ownership](../../../../../../../notes/patterns/query-state-and-rendering.md)
and [Compose lifetime](../../../../substrate/okhttp-and-compose-effect-lifetime.md).
Primary references: [AGP 9 built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes#built-in-kotlin)
and [native progress indicators](https://developer.android.com/develop/ui/compose/components/progress).
How should a renderer expose a source while retaining frame ownership? What
device evidence would justify applying backdrop effects beyond bounded panels?

## Reserved component catalog and tab bar

The 2026-10-08 [component map](../../../../../../../docs/COMPONENTS.md) reserves
.gitkeep-only planned leaf directories grouped like Bento. These are not
implemented APIs. The new
[FoundryTabBar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/tabbar/FoundryTabBar.kt)
is implemented: it receives item IDs, labels, icon slots, selected ID and a
selection callback. A transparent Material NavigationBar renders over the
floating FoundrySurface. The app supplies a bounded backdrop and safe-area
padding; the internal bar adds zero insets. No routes, screens or feature models
enter core/ui. See [the app shell](../../app/README.md#four-tab-placeholder-shell)
and [backdrop mechanics](../../../../substrate/compose-backdrop-layers.md).
