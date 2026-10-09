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
the updated provider and V1, then [Backdrop](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/surface/Backdrop.kt)
and [Surface](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/surface/Surface.kt).
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
[LabeledTextField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/textfield/LabeledTextField.kt),
[SubmitButton](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/submitbutton/SubmitButton.kt), and
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
[TabBar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/tabbar/TabBar.kt)
is implemented: it receives item IDs, labels, icon slots, selected ID and a
selection callback. A transparent Material NavigationBar renders over the
floating Surface. The app supplies a bounded backdrop and safe-area
padding; the internal bar adds zero insets. No routes, screens or feature models
enter core/ui. See [the app shell](../../app/README.md#four-tab-placeholder-shell)
and [backdrop mechanics](../../../../substrate/compose-backdrop-layers.md).

## Everyday component batch and naming

Added 2026-10-08. Read [the API/usage guide](../../../../../../../docs/blueprints/ui-components.md),
then [ActionButton](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/button/ActionButton.kt),
[Card](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/card/Card.kt),
[ListRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/listrow/ListRow.kt),
and [SelectionCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/selectioncard/SelectionCard.kt).
Twelve formerly reserved leaves now contain implementation. All component APIs
and helper types use unbranded names. Package/theme/token names retain their
identity. Explicit imports distinguish Card and Surface from Material controls.

ActionButton selects native Button/OutlinedButton/TextButton for its variant
and disables activation while busy. SubmitButton delegates to it. Card composes
Surface, and SettingsSection/SelectionCard compose Card. ColumnScope and RowScope
content receivers expose appropriate native layout customization. There is no
extra layout model, service dependency or coroutine ownership. Read
[slot mechanics](../../../../language/kotlin-covariant-query-state-and-content-slots.md)
and [shared state ownership](../../../../../../../notes/patterns/component-slots-and-caller-owned-state.md).

ListRow puts trailing content below copy at fontScale >= 1.5. Tabs uses native
scrollable tabs; unique, nonempty options must contain the current value. A
SelectionCard has one selectable radio-option action and a passive RadioButton;
its content slot cannot contain nested controls. EmptyState hides decorative
artwork semantics while leaving its title/message/actions available.

Both native builds and the focused eleven-check Android run pass, including
four new ComponentCatalogTest checks and existing forms/shell regressions.
These exercise busy/disabled actions, explicit removal confirmation, search
empty/clear recovery, selected/settings state across themes and saved state,
native sheet dismissal and actual progress-range semantics. These are emulator
checks, not a complete TalkBack, large-font, localization, keyboard, sheet-gesture
or physical-device audit.

## Selection controls and native overlays

Added 2026-10-08. Read
[ToggleField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/toggle/ToggleField.kt),
[Checkbox](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/checkbox/Checkbox.kt),
[RadioGroup](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/radiogroup/RadioGroup.kt),
[SelectField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/select/SelectField.kt),
[ValueSlider](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/slider/ValueSlider.kt),
and [DateField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/datepicker/DateField.kt).
Whole-row choice modifiers carry the native switch/checkbox/radio role; their
native visual indicators have null callbacks. Mixed-state transitions, formatted
slider copy and committed values remain caller policy. Slider steps count
intermediate stops; a three-step 0..1 range has five positions.

DateField uses the nullable millisecond picker API, supported at minimum API 24.
Its conditional remembered picker state is a temporary draft. Confirm commits
one nonnull value; dismissal removes the draft. Formatting uses UTC because
native picker dates are UTC-midnight calendar labels. Read
[Compose selection and modal drafts](../../../../substrate/compose-selection-and-modal-drafts.md)
for the LocalDate API-level alternative and timezone gotcha.

[SheetPanel](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/overlays/sheet/SheetPanel.kt),
[ConfirmationDialog](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/overlays/dialog/ConfirmationDialog.kt),
and [ActionMenu](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/overlays/menu/ActionMenu.kt)
wrap native modal sheets, alerts and menu items. The caller owns sheet/dialog
presence and real actions; menus own expansion only. Empty menus disable their
trigger, disabled entries cannot dispatch, and destructive entries can request
confirmation. Large sheet slots need caller scrolling.

The Android app builds and all four existing UI token/material unit tests pass.
Actual control/overlay interactions and saved-state checks are recorded in the
app walkthrough. Next: when should a multi-field draft move above its modal,
and which domain date encoding should cross the service seam?

The final focused emulator suite passes fourteen checks, including six gallery
checks and one real date-picker transaction/time-zone check; the remaining seven
are existing forms/shell regressions. This establishes exercised native behavior,
not comprehensive TalkBack, date constraints or physical-device coverage.

## Display feedback and collection components

Added 2026-10-08. Read
[Avatar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/avatar/Avatar.kt),
[StatCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/stat/StatCard.kt),
[Skeleton](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/skeleton/Skeleton.kt),
[ToastBanner](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/toast/ToastBanner.kt),
[FieldGroup](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/fieldgroup/FieldGroup.kt),
and [CollectionToolbar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/collectiontoolbar/CollectionToolbar.kt).
They fill six reserved leaves using the existing UI module and native composition.

Avatar receives fallback text or nullable BoxScope artwork; it clears child
semantics and supplies one identity. StatCard combines supplied value/trend copy,
so domain units and good/bad meaning remain caller policy. Skeleton creates its
native infinite transition only while animated and not reduced, and exposes no
fake loading content. The host supplies a real label. The theme's system/parent
reduction already flows to this branch.

ToastBanner has optional ToastAction and separate dismissal, with a polite
message live region and independent native buttons. It has no queue, timer or
operation runtime. FieldGroup establishes traversal grouping without flattening
native fields; error takes precedence over help but per-field association remains
caller policy. CollectionToolbar exposes ColumnScope filter/action slots rather
than an item, filter or selected-ID model.

Read [native mechanics](../../../../substrate/compose-loading-and-passive-content.md)
and [shared projection/lifetime](../../../../../../../notes/patterns/collection-projections-and-feedback-lifetime.md).
The app builds and four UI token/material unit checks pass. All nineteen focused
emulator checks pass, including a native pixel test demonstrating pulse changes
and static output after changing reduction while composed. This is exercised
emulator output, not complete TalkBack, large-font or hardware performance
coverage. Next: which notice needs explicit event identity and timeout ownership?

## Context navigation and adaptive layouts

Added 2026-10-08. The fourth batch fills six leaves, bringing the component
batches to 33 building blocks. Read
[NavLink](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/navlink/NavLink.kt),
[PopoverPanel](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/overlays/popover/PopoverPanel.kt),
[HelpTooltip](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/overlays/tooltip/HelpTooltip.kt),
[ContentContainer](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/container/ContentContainer.kt),
[AdaptiveGrid](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/grid/AdaptiveGrid.kt),
and [MediaFrame](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/aspectratio/MediaFrame.kt).
They use the existing module, descriptive unprefixed names and native slots.

NavLink invokes a caller route callback from a full-row native clickable target;
its chevron is decorative and directional. PopoverPanel composes an anchor and
focusable native DropdownMenu. Native outside/back dismissal and explicit close
invoke only presentation callbacks. The native menu supplies vertical scrolling;
do not nest another unbounded scrolling list inside. HelpTooltip is persistent
tap help, not native timed/hover TooltipBox behavior.

ContentContainer centers a capped padded Column. MediaFrame supplies a ratio and
clipping while the caller supplies crop/semantics. AdaptiveGrid uses Layout with
bounded width and natural child heights. It measures each child once, grows the
minimum width with fontScale and places relative to reading direction. Maximum
row heights prevent longer content overlapping later rows. Its child composition
paths stay stable across reflow; consumer key calls preserve item identity. The
eager grid has no scroll or record model, and is intended for small compositions.

Read [native mechanisms](../../../../substrate/compose-layout-and-contextual-presentation.md),
[shared ownership](../../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08),
and [usage](../../../../../../../docs/blueprints/ui-components.md#small-responsive-compositions).
[AdaptiveLayoutTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/AdaptiveLayoutTest.kt)
measures actual bounds after width/font/RTL changes, retains a remembered child
counter and checks container insets/media ratio. Four existing UI token/material
unit checks pass. App interaction results and limits live in its walkthrough.
Next: which consumer needs native lazy-grid ownership instead of eager layout?

## Choices disclosure and detail composition

Added 2026-10-08. Six APIs bring the five component batches to 39 building blocks.
Read [ChoiceChip](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/chip/ChoiceChip.kt),
[ValueStepper](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/stepper/ValueStepper.kt),
[DisclosureSection](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/disclosure/DisclosureSection.kt),
[KeyValueRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/keyvalue/KeyValueRow.kt),
[ActionBar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/actionbar/ActionBar.kt),
and [DetailShell](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/shells/detailshell/DetailShell.kt).
All names remain unprefixed; slots and modifiers support native composition.

ChoiceChip delegates selection/disabled behavior to Material FilterChip. Its
caller decides single or multiple selection. ValueStepper receives a value and
callback, validates the range/current value/positive step, widens to Long before
arithmetic, clamps and only then narrows. Native minus/plus buttons have supplied
accessible labels. Read [widening before arithmetic](../../../../language/kotlin-widen-before-integer-arithmetic.md).
The endpoint policy allows 0, 2, 4, 5 in 0..5 with step two; it is not a domain
quantity model or a promise of equal integer widths across platforms.

DisclosureSection uses a clickable button-role header and AnimatedVisibility,
or a direct conditional branch under reduction. Removed content cannot own a
durable draft. KeyValueRow combines passive copy, using FlowRow or a large-font
Column. ActionBar supplies a floating surface, not a sticky-layout mechanism.
DetailShell's bounded Column gives its body remaining height via weight; the
consumer installs scrolling inside that body and owns system insets/navigation.
Read [native mechanisms](../../../../substrate/compose-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08),
[shared ownership](../../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#collapsed-drafts-and-detail-viewports--2026-10-08),
and [usage](../../../../../../../docs/blueprints/ui-components.md#choices-disclosure-and-detail-screens).

[ValueStepperTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ValueStepperTest.kt)
executes actual native buttons across Int bounds; four accepted changes reach
both endpoints without wrapping. [DetailShellLayoutTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DetailShellLayoutTest.kt)
measures actual scrolling/header/footer bounds and changes fontScale to two.
All nineteen focused emulator checks and four existing UI unit checks pass.
The app walkthrough records the interaction coverage and limits. Next: which
feature needs repeating presses, a fixed step lattice or entry-owned draft state?

## Rich input and journey pages

Added 2026-10-08. Six components bring the catalog batches to 45 building blocks.
Read [PasswordField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/password/PasswordField.kt),
[MultilineField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/multiline/MultilineField.kt),
[ValidationChecklist](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/validationchecklist/ValidationChecklist.kt),
[StepIndicator](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/stepindicator/StepIndicator.kt),
[OnboardingPage](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/onboardingpage/OnboardingPage.kt)
and [AuthShell](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/shells/authshell/AuthShell.kt).

PasswordField wraps native OutlinedSecureTextField with hidden text and autofill
purpose; the wrapper contains its experimental Material opt-in. MultilineField
uses state-based OutlinedTextField with native TextFieldLineLimits. Both receive
caller-owned TextFieldState rather than creating a mirrored String internally.
The feature chooses save/restore and validates state.text. Error replaces help;
keyboard and modifier-based focus policy remain native caller customization.

Requirement/progress models carry unique IDs and supplied accessible states.
The rows are passive, with hidden symbol semantics. Step state is not a route.
AuthShell scrolls its footer with the form; OnboardingPage composes DetailShell
with body-only scrolling and a separate action slot. Neither supplies an identity
provider, saved draft store or reusable router. Read
[native input and identity](../../../../substrate/compose-rich-input-and-journey-pages.md),
[shared ownership](../../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08)
and [usage](../../../../../../../docs/blueprints/ui-components.md#rich-input-and-onboarding).

[JourneyFieldTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/JourneyFieldTest.kt)
executes native long multiline editing without truncation and checks error/help
precedence and input error semantics. The catalog checks separately exercise
secure-input semantics, state ownership and flow actions. Final evidence and
limits are in the app walkthrough. Next: which input needs a native transformation,
and which draft belongs in an entry-owned ViewModel?

The final focused consumer run passes all nineteen emulator checks, including
four new journey/field cases and fifteen existing component regressions. All
four UI token/material unit checks pass. The app walkthrough distinguishes
native input/restoration evidence from remaining keyboard/accessibility and
provider-autofill coverage.

## Activity and paged collections

Added 2026-10-09 for the seventh batch. New component leaves:

- [SectionHeader](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/sectionheader/SectionHeader.kt).
- [AvatarGroup](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/avatargroup/AvatarGroup.kt).
- [TimelineItem](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/timelineitem/TimelineItem.kt).
- [ExpandableText](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/expandabletext/ExpandableText.kt).
- [RefreshContainer](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/refreshcontainer/RefreshContainer.kt).
- [LoadMoreFooter](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/loadmore/LoadMoreFooter.kt).

SectionHeader supplies compact reading structure and independent actions.
AvatarGroup receives stable identities and passive slots, limits visible artwork
and exposes one complete supplied summary. TimelineItem draws only a decorative
marker/connector; supplied content can contain native controls. ExpandableText
receives the expanded flag and explicit labels for known long copy. It changes
native text limits without guessing overflow or storing important row state.
LoadMoreFooter projects idle/loading/failed/exhausted phases; only enabled
idle/failed actions dispatch. It never loads on appearance.

RefreshContainer wraps Material3 PullToRefreshBox; its refreshing flag and
callback are caller-owned. It creates no coroutine. The catalog supplies a
bounded LazyColumn; native gesture state stays inside the native adapter.

Read [native mechanics](../../../../substrate/compose-refresh-and-lazy-activity.md),
[shared ownership](../../../../../../../notes/patterns/refresh-and-pagination-ownership.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#activity-feeds-and-pagination).
[Native consumer checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ActivityCatalogTest.kt) exercise the operation/lifetime and disclosure
boundaries; the app walkthrough records final evidence and limits. Next: what
real feed needs a domain paging contract rather than more UI abstraction?

Final evidence for this batch, 2026-10-09: both consumers build, 27 iOS app
checks pass, four new Android activity checks pass, and nineteen existing Android
component regressions pass. Four UI unit checks per platform and notes validation
pass. The app walkthrough separates native refresh-control/gesture evidence,
manual visual observations, test-helper corrections and coverage limits.

## Media browsing and actions

Added 2026-10-09 for the eighth batch. New component leaves:

- [IconAction](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/iconaction/IconAction.kt).
- [RatingField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/rating/RatingField.kt).
- [PageIndicator](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/pageindicator/PageIndicator.kt).
- [Carousel](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/carousel/Carousel.kt).
- [MediaTile](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/mediatile/MediaTile.kt).
- [MediaOverlay](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/mediaoverlay/MediaOverlay.kt).

IconAction reuses native ActionButton target/styling, hides only passive icon
content and supplies localized action meaning. RatingField receives bounded
integer state and separate option/value copy; the exact selected choice remains
a native action. Zero is unrated and clearing belongs to the host. Its adaptive
layout preserves touch targets. PageIndicator is passive, with a small bounded
count and one supplied summary replacing decorative dots.

MediaTile supplies a ratio frame, metadata and separate action slot without an
implied whole-card tap. MediaOverlay hides decorative art/scrim semantics while
keeping overlay content independent. The caller provides bounds, clipping,
meaningful media copy and readable action surfaces; image admission stays outside
these components.

Carousel delegates to native HorizontalPager with a caller PagerState and
stable key builder. It admits nonempty pages and exposes userScrollEnabled. The
app owns rememberPagerState and any command coroutine; the component creates no
scope, request or autoplay job.

Read [native mechanics](../../../../substrate/compose-media-paging-and-overlays.md),
[shared ownership](../../../../../../../notes/patterns/media-selection-and-passive-artwork.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#media-browsing-and-actions).
[Native consumer checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/MediaComponentTest.kt) cover actual native layout/semantics rather
than a second UI model. Both consumers build; 29 iOS app checks, four final Android
media checks, fourteen component/activity regressions and four UI unit checks
per platform pass. App walkthroughs separate evidence and limits. Next: what
record-admission policy should precede dynamic carousel content replacement?

## Communication and attachments

Claim: communication compositions can project caller draft/transfer values and
independent actions without becoming a messaging or upload service.

Added 2026-10-09 for the ninth batch. New leaves:

- [ConversationRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/conversationrow/ConversationRow.kt).
- [MessageBubble](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/messagebubble/MessageBubble.kt).
- [MessageComposer](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/messagecomposer/MessageComposer.kt).
- [AttachmentRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/attachmentrow/AttachmentRow.kt).
- [TransferStatus](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/transfer/TransferStatus.kt).
- [TypingIndicator](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/typing/TypingIndicator.kt).

MessageComposer takes caller TextFieldState rather than reconstructing text on
recomposition. Its field and send action are gated internally; arbitrary slot
actions consume the supplied interactive Boolean. FlowRow wraps native targets.
MessageBubble uses logical row spacing and a SelectionContainer around message
text only. TypingIndicator's native infinite transition exists only when scoped
motion permits. Its dots and duplicated visual label have cleared child semantics,
leaving one supplied passive summary.

ConversationRow has one native action and hides only passive identity artwork;
unread/time copy is supplied and native ListRow stacks metadata at larger text.
AttachmentRow hides passive preview semantics and retains independent action
children. TransferStatus uses existing native ProgressIndicator only while
transferring; phase/copy/actions are supplied, with no transition logic in core UI.

Example: the feature keeps a draft while a file transfer fails, supplies Retry to
TransferStatus, and only enables MessageComposer's send when the file is ready.
Do not put buttons in artwork slots or assume a send callback clears text.
Apply native bounds/modifiers and keep pinned slot content compact; keyboard
placement belongs to the destination.

Read [native mechanics](../../../../substrate/compose-composer-and-ime.md),
[shared ownership](../../../../../../../notes/patterns/composer-drafts-and-transfer-ownership.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#communication-and-attachments).
[Native consumer checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/CommunicationComponentTest.kt) exercise actual hosted layout or native semantics
and interaction. Both apps build; all 32 iOS app checks, four final Android
communication checks, thirteen existing component/media regressions and four
UI unit checks per platform pass. App walkthroughs record the test corrections
and manual keyboard observation separately. No full accessibility, localization,
device or upload integration audit is claimed.
Next: which draft/operation identity should a real message feature admit?

## Selection, tokens and row editing

Claim: wrapping tokens, controlled choices and native row actions can share UI
primitives while admission, collection identity and undo remain feature-owned.

Added 2026-10-09 for the tenth batch. New leaves:

- [WrapLayout](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/wrap/WrapLayout.kt).
- [RemovableChip](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/removablechip/RemovableChip.kt).
- [TokenField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/tokenfield/TokenField.kt).
- [SelectionRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/selectionrow/SelectionRow.kt).
- [SwipeActionRow / SwipeAction](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/swipeactionrow/SwipeActionRow.kt).

WrapLayout adapts native FlowRow and exposes its slot scope. TokenField uses
value/onValueChange input and supplies interactivity to arbitrary token controls.
SelectionRow exposes native checkbox toggle semantics. SwipeActionRow remembers
ephemeral native gesture state, observes settledValue with snapshotFlow, resets
before dispatch and reads current callbacks through rememberUpdatedState.

RemovableChip is one native action with supplied identity-aware remove copy.
TokenField's explicit Add and native Done share eligibility, enabled and busy
guards; no parsing, trimming, duplicates, limit or automatic clearing is built
into the control. SelectionRow's marker/leading artwork is passive; other row
commands belong outside that selection target. SwipeAction carries callback/copy,
destructive tone and per-action eligibility. It never owns removal or undo.

Example: a library admits one unique tag, renders it in removable slots, keys
rows by stable record IDs and supplies Archive/Remove through swipe and menu.
Use small eager wrapping groups, compatible child bounds and host-owned scrolling.
Swift gestures need List; Compose host keys retain row identity. Persist domain
values separately from a transient swipe position.

Read [native mechanics](../../../../substrate/compose-wrapping-and-swipe-actions.md),
[shared ownership](../../../../../../../notes/patterns/selection-identity-and-undo.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#selection-tokens-and-row-editing).
[Native checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/EditingComponentTest.kt) exercise hosted wrapping or semantics/actions.
Both apps build; 35 iOS app checks, five final Android editing checks, thirteen
existing Android component/communication regressions and four UI unit checks per
platform pass. The app walkthrough records harness corrections and manual iOS
observations separately. No full accessibility, all locales/devices, persistent
collection or real command integration audit is claimed.
Next: how should editable token identity differ from its visible label?

## Insights and small charts

Claim: small native charts can project admitted data and readable meaning while
periods, units, comparison scales and goal changes remain feature-owned.

Added 2026-10-09 for the eleventh batch. New leaves:

- [TrendBadge / TrendDirection](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/trendbadge/TrendBadge.kt).
- [LegendItem / LegendMark](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/charts/legend/LegendItem.kt).
- [Sparkline](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/charts/sparkline/Sparkline.kt).
- [BarChart / ChartBar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/charts/barchart/BarChart.kt).
- [ProgressRing](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/charts/progressring/ProgressRing.kt).
- [ChartPanel](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/chartpanel/ChartPanel.kt).

Sparkline maps normalized Double coordinates into Float pixel positions inside
native Canvas/DrawScope; Dp stroke width converts at draw time. ProgressRing uses
native arc drawing with bounded progress and supplied stateDescription semantics.
Its value moves below the circle at fontScale >= 1.5. BarChart retains native merged
category/value rows and hides tracks. ChartPanel leaves footer controls independent.

TrendBadge receives explicit direction and tone; an increase is not automatically
success. LegendItem carries supplied series copy with passive dot/line/square.
Sparkline connects equally-spaced finite samples in input order, without smoothing
or a time axis. Empty draws no mark, single draws a centered dot and constants a
midline. Scaling before extrema subtraction prevents finite signed-range overflow.
Bars require stable unique IDs, nonnegative finite values and an explicit positive
maximum large enough for every value. Rings clamp finite progress geometry but
receive truthful formatted copy from the caller. External data admission belongs
at a result/service boundary before reaching these programmer preconditions.

Example: compare Week/Month categories on the same 200-minute scale while giving
each sparkline its own descriptive summary and an exact-values disclosure. Keep
the independent goal outside those projections. Small eager composition has no
promised dense-series/device budget. Use a richer native chart for real timestamps,
axes, negative/diverging bars, selection, pan/zoom or large history.

Read [native mechanics](../../../../substrate/compose-chart-drawing-and-semantics.md),
[chart meaning/scales](../../../../../../../notes/patterns/chart-meaning-and-scales.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#insights-and-small-charts).
[Numerical checks](../../../../../project/core/ui/src/test/kotlin/dev/mobilefoundry/ui/ChartTest.kt) exercise finite extrema and degenerate sequences;
[consumer checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/InsightsComponentTest.kt) cover native layout, drawing or semantics/actions.
Both consumers build; all 37 iOS app checks, four final Android Insights checks,
five Android editing regressions and six UI unit checks per platform pass. App
notes distinguish manual observation, harness corrections and platform limits.
Next: what shared axis/selection model should a time-aware chart receive?

## Dates and agendas

Claim: calendar/date interpretation and availability remain feature policies;
the UI owns native picker drafts, selection presentation and copy layout.
Added 2026-10-09: [TimeField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/timepicker/TimeField.kt), [DateRangeField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/daterange/DateRangeField.kt), [DayStrip](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/daystrip/DayStrip.kt), [AgendaRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/agendarow/AgendaRow.kt).

TimeField/ClockTime separates hour/minute meaning from native picker encoding.
DateRangeField composes two independently committed dates and caller help/error,
without swapping endpoints. DayStrip wraps supplied stable IDs and independent
selected/disabled states. AgendaRow keeps its status/actions outside merged
passive copy. DateField now dismisses disabled drafts; Swift modal themes inherit
their caller's scope.

The [native consumer checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SchedulingComponentTest.kt) exercise actual controls/layout,
with feature admission in the app. Read [native mechanics](../../../../substrate/compose-time-and-date-drafts.md),
[shared ownership](../../../../../../../notes/patterns/calendar-dates-and-clock-readings.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#dates-and-agendas).
Checks and limits are recorded in the app walkthrough; no calendar service,
recurrence, DST resolution or booking guarantee is established.
Next: use these slots in a concrete planner with an admitted scheduling command.

## Adaptive workspaces

Claim: destination/path affordances and local pane presentation can remain reusable
while feature identity, compact intent and route policy stay above their slots.
Origin, 2026-10-09: thirteenth batch, source and native consumer checks.

- [DestinationRail / RailDestination](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/navigationrail/DestinationRail.kt).
- [BreadcrumbTrail / BreadcrumbItem](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/breadcrumbs/BreadcrumbTrail.kt).
- [SplitPane / PaneMode](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/splitpane/SplitPane.kt).

DestinationRail uses native Material NavigationRail/NavigationRailItem and selected semantics; labels remain
visible and icons passive. The host bounds its width/height and chooses a compact
alternative. BreadcrumbTrail renders supplied ancestors as native actions and
the last item as passive current copy. Unique IDs are programmer preconditions;
unknown selection does not select a default. Empty paths are valid.

SplitPane receives positive finite primary/minimum detail widths and callbacks
building native content for the actual PaneMode. Local bounds, text size and
forceSingle choose one or two slots. It requires bounded height; pane scrolling
belongs to the host. Mode changes can recreate slot-local state. Do not put
drafts, service owners or selection inside those conditional slots. The example
retains selection when compact Back closes detail or a collection hides that ID.

Read [native mechanics](../../../../substrate/compose-bounded-panes-and-navigation.md),
[shared ownership](../../../../../../../notes/patterns/adaptive-layout-and-navigation-state.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#adaptive-workspaces).
[Native checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/WorkspaceComponentTest.kt) cover actual pane layout and feature/control boundaries.
Verification, 2026-10-09: both native consumers build; all 41 iOS app
checks, five focused Android Workspace UI checks and seven Swift/six
Kotlin UI package checks pass. `make notes-check` validates links and
example labels, not native behavior.
No draggable divider, root router, fold hinge, deep-link or focus-restoration
guarantee is implemented. Next: use the slots in a real routed editor and decide
which state should be serialized by its native navigation owner.

## Tables and pagination

Claim: small admitted pages can retain aligned presentation and independent cell
controls without moving sort/page policy into reusable UI.
Origin, 2026-10-09: fourteenth component batch, source and native consumer checks.

- [TableSortHeader / TableSortOrder](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/tablesortheader/TableSortHeader.kt).
- [DataTable / DataTableColumn](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/layout/datatable/DataTable.kt).
- [PaginationBar](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/pagination/PaginationBar.kt).

DataTable uses explicit rowKey/key scopes and composable slots. Shared Row/Box widths preserve column alignment while native text grows vertically.
One horizontal scroller owns all headers/rows. Positive finite widths include
padding; rows/columns require stable unique IDs and a finite total width. Empty
rows retain headers. The host provides vertical scrolling and contextual cell
narration; interactive cells remain independent. Small eager pages are deliberate;
there is no virtualization or implicit native table header association.

TableSortHeader projects optional order and supplied localized state copy, with
a decorative arrow and one native action. TableSortOrder and DataTableColumn
keep Foundation.SortOrder and SwiftUI.TableColumn available to consumers.
PaginationBar projects a valid one-based page and disables first/last/global
actions. It emits adjacent page intent without updating data or starting work.
The ledger example sorts the full fixture before projecting three records, keeps
the chosen page and preserves hidden inspected identity under empty projection.

Read [native mechanics](../../../../substrate/compose-table-columns-and-scrolling.md),
[shared ownership](../../../../../../../notes/patterns/table-sorting-and-page-ownership.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#tables-and-pagination).
[Native checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/TableComponentTest.kt) exercise geometry, scrolling or action/state boundaries.
Verification, 2026-10-09: both native consumers build; all 43 iOS app checks,
four focused Android Tables UI checks, seven Swift and six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
No sticky columns, spreadsheet editing, dense-data budget, localization audit or
server ordering guarantee is established. Next: admit a real ordered page through
an existing service seam before choosing a cursor or numbered-page contract.

## Products and order composition

Claim: supplied price meaning and native action slots keep calculation and command
admission outside reusable product/code/order presentation.

Origin, 2026-10-09: seventeenth UI batch and native consumer checks.
Read [PriceLabel](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/pricelabel/PriceLabel.kt),
[ProductRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/productrow/ProductRow.kt),
[OrderSummary](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/ordersummary/OrderSummary.kt)
and [InlineActionField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/inlineaction/InlineActionField.kt).

PriceLabel projects formatted current/comparison/detail copy with one supplied
accessible description. ProductRow hides only decorative artwork; price/status/
native action slots remain independent. OrderSummary reuses opaque Card,
SectionHeader, native divider and KeyValueRow; arithmetic and checkout do not enter
the component. Caller Modifier and slots supply placement and extra controls.

InlineActionField uses one native OutlinedTextField and ActionButton with shared
enabled/not busy/canSubmit gating. BoxWithConstraints stacks below 400 dp or
fontScale >= 1.5, otherwise aligning the button and input in a Row. Help/error
copy stays below both controls, with native field error semantics. Done submission
keeps caller keyboard input options, and a synchronous value callback retains the
draft above layout branches. Parsing, validation and request state stay supplied.

Example: supply complete price comparison/unit narration and put native quantity
controls in product actions. InlineActionField can serve promotional or invite
codes; an ineligible keyboard action leaves text intact. Busy disables edits,
while canSubmit=false alone gates only submission.

[Component checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/CommerceComponentTest.kt)
exercise native admission, passive price meaning, 240-dp large-text/RTL bounds
and retained draft on layout change. Both consumers build; 49 iOS app, four
focused Android Commerce UI and seven Swift/six Kotlin UI package checks pass.
No full TalkBack/localization/device or monetary-domain audit is established.
Read [native mechanics](../../../../substrate/compose-inline-fields-and-order-composition.md),
[catalog flow](../../app/README.md#commerce-gallery),
[shared ownership](../../../../../../../notes/patterns/price-copy-and-committed-cart-values.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#products-and-order-composition).
Next: admit real quote values through a feature seam outside these compositions.

## Search and discovery

Claim: literal annotated text and independent native row actions receive search
meaning from a feature without owning search or result state.

Origin, 2026-10-09: sixteenth UI batch and native consumer checks.
Read [HighlightedText / HighlightSegment](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/highlightedtext/HighlightedText.kt),
[SearchSuggestionRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/searchsuggestionrow/SearchSuggestionRow.kt),
[SearchResultRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/searchresultrow/SearchResultRow.kt)
and the extended [SearchField](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/searchfield/SearchField.kt).

HighlightSegment holds literal text and emphasis. HighlightedText uses one
AnnotatedString with optional native TextStyle/color overrides. SearchResultRow
keeps the clickable native Button role/contentDescription on its open container
and clears only passive preview descendants. Native actions are siblings outside
that container; row enabled controls only opening. SearchField's final optional
enabled parameter preserves existing positional callers and guards native edit,
clear and keyboard submission. Matching/history/filter/routing policy stays out.

Example: supply complete localized open copy and put Save in actions, never
inside a passive preview. Clearing the clickable container's semantics would
erase its native role/action. Disabled fields remove SetText; test their label
instead of requiring editable-only semantics.

[Component checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DiscoveryComponentTest.kt)
exercise literal text, native independent/disabled actions and narrow larger-text
RTL bounds. Both consumers build; 47 iOS app, four focused Android Discovery UI
and seven Swift/six Kotlin UI package checks pass. A full TalkBack/localization/
device audit remains open. Read
[native text mechanics](../../../../substrate/compose-annotated-text-and-search-actions.md),
[catalog flow](../../app/README.md#discovery-gallery),
[shared reasoning](../../../../../../../notes/patterns/search-projection-and-filter-drafts.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#search-and-discovery).
Next: integrate query lifetime and locale matching outside the reusable UI layer.

## Accounts and access

Claim: identity/session/capability presentation can be reusable without taking
ownership of authenticated context or OS prompting.
Origin, 2026-10-09: fifteenth UI batch and native consumer checks.

- [ProfileHeader](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/profileheader/ProfileHeader.kt).
- [AccountSwitcher / AccountOption](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/navigation/accountswitcher/AccountSwitcher.kt).
- [SessionRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/sessionrow/SessionRow.kt).
- [PermissionCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/permissioncard/PermissionCard.kt).

Compose supplies composable slots and Modifier. Native DropdownMenu entries retain
selected and disabled states. A unique supplied AccountOption list and optional
selection control the menu. Unknown IDs show the caller's placeholder. Current
and unavailable choices do not dispatch. ProfileHeader puts decorative artwork
above copy for larger native text; all supplied action slots remain independent.
SessionRow accepts activity copy rather than dates or session objects.
PermissionCard reuses opaque Card and imposes no capability enum or adapter.

Example: an account center supplies profile copy, per-device removal actions and
an Ask/Allowed/Denied photo projection. The feature owns current-device protection,
confirmation and actual operation admission. Decorative slots must not contain
interactive controls or information absent from the supplied copy/status.

Verification, 2026-10-09: both native consumers build; all 45 iOS app checks,
four focused Android Account UI checks and seven Swift/six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
[Native checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/AccountComponentTest.kt) cover supplied-state guards, independent native actions
or actual narrow/large-text/RTL geometry. Read [native mechanics](../../../../substrate/compose-account-menus-and-action-slots.md),
[shared scope](../../../../../../../notes/patterns/account-context-and-device-capabilities.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#accounts-and-access).
No credentials, permission API, session revocation, secure storage or full
assistive-technology audit is supplied. Next: use these slots with a feature-owned
identity service and capability adapter once their scopes are defined.

## Notifications and inbox

Claim: supplied count/read meaning and independent native actions keep inbox
policy outside reusable components.

Origin/evidence, 2026-10-09: the eighteenth UI batch adds
[CountBadge](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/countbadge/CountBadge.kt) and
[NotificationRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/notificationrow/NotificationRow.kt).
CountBadge replaces passive text semantics with full narration; it does not
parse/cap a count or decide whether zero is visible. NotificationRow retains
native clickable Role.Button and enabled semantics, while only passive artwork/
copy children clear theirs. Caller action slots are independent siblings.

Example: show 99+ with 128 unread updates as its complete meaning. Open a row
with a supplied read label and independent mark-read/archive controls. Its
enabled flag gates opening only; each native action has its own eligibility.
Weighted native copy grows vertically; artwork follows logical leading direction.
All time formatting, read-on-open, grouping and receipt policy remain caller work.

Verification: both consumers build; 51 iOS app cases, four focused Android inbox
cases and seven Swift/six Kotlin UI package cases pass.
[Component cases](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/NotificationComponentTest.kt)
exercise passive narration, native role/disabled state, independent callbacks
and 240-dp larger-text/RTL bounds. No TalkBack traversal is established. Clear
only passive descendants whose complete meaning is supplied; a decorative slot
cannot contain independent actions or unique information.
Read [native mechanics](../../../../substrate/compose-notification-actions-and-narration.md),
[consumer](../../app/README.md#notifications-gallery),
[identity pattern](../../../../../../../notes/patterns/inbox-projection-and-read-identity.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#notifications-and-inbox).
Next: connect scoped real commands above these value/callback APIs.

## Plans and usage

Claim: explicit native choice controls and passive supplied quota/feature meaning
let plan views stay reusable across entitlement domains.

Origin/evidence, 2026-10-09: the nineteenth UI batch adds
[FeatureRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/featurerow/FeatureRow.kt),
[PlanCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/plancard/PlanCard.kt) and
[UsageMeter](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/usagemeter/UsageMeter.kt).
The passive rows/meters replace child semantics with complete supplied meaning.
PlanCard leaves its native slots independent and gives its explicit ActionButton
selected semantics. Selected/disabled choices reject dispatch, while caller help/
status controls retain their own eligibility. The card itself has no click action.

Example: display a full yearly charge beside its monthly-equivalent price, and
project 50 of 5 usage after an applied downgrade. Finite bar fractions clamp; null/
nonfinite omits the decorative bar. Count arithmetic, quota/receipt scope and
billing policy stay above the component. Passive labels must include all meaningful
details; an included mark does not perform capability authorization.

Verification, 2026-10-09: both consumers build; 53 iOS app cases, four focused
Android Plans cases and seven Swift/six Kotlin UI package cases pass.
[Native component cases](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlanComponentTest.kt)
cover narration, selected/disabled controls, independent callbacks, 240-dp
font-scale-two/RTL growth and minimum-sized choice/action bounds.
No full TalkBack or billing integration is established.
Read [native mechanics](../../../../substrate/compose-plan-slots-and-usage-bars.md),
[consumer](../../app/README.md#plans-gallery),
[shared pattern](../../../../../../../notes/patterns/plan-choice-and-applied-allowance.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#plans-and-usage).
Next: real entitlement adapters and localized assistive traversal outside these
native value/callback components.

## Files and hierarchy

Claim: flattened caller state and sibling native targets let file rows be reused
without importing hierarchy or provider behavior into the UI module.

Origin/evidence, 2026-10-09: the twentieth UI batch adds
[FileTypeMark](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/display/filetypemark/FileTypeMark.kt)
and [TreeRow/TreeDisclosure](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/treerow/TreeRow.kt).
Read the passive mark, opening clickable with native Button role, supplied
selection/full narration, separate disclosure and sibling action slot. Passive
descendants clear semantics while the clickable root retains its native meaning.
No tree traversal, expansion ownership or file inference enters this module.

Example: display a nested PNG with an independent favorite target. Indentation
uses nonnegative depth and finite step/maximum, defaults to 16 dp capped at 48,
and follows logical start. Font scale at least 1.5 stacks artwork above copy.
Opening's enabled flag leaves disclosure/action eligibility independent. Leading
artwork must be passive and included in the complete supplied row narration.

Verification, 2026-10-09: both consumers build; 55 iOS app checks, four focused
Android Files cases and seven Swift/six Kotlin UI package cases pass.
[Native component cases](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/FileComponentTest.kt)
exercise selected/disabled controls, separate callbacks, passive mark narration
and extreme-depth 240-dp font-scale-two/RTL action bounds. Full TalkBack traversal
and provider permissions remain unverified.
Read [native mechanics](../../../../substrate/compose-tree-actions-and-indentation.md),
[consumer](../../app/README.md#files-gallery),
[shared pattern](../../../../../../../notes/patterns/tree-projection-and-retained-selection.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#files-and-hierarchy).
Next: multilingual labels and loaded-child provider admission outside these rows.

## Sharing and access

Claim: passive identity and link text plus independent native slots let sharing
compositions remain reusable across membership and authorization models.

Origin/evidence, 2026-10-09: the twenty-first UI batch adds
[MemberRow](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/memberrow/MemberRow.kt)
and [ShareLinkCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/sharelinkcard/ShareLinkCard.kt).
Read the passive identity/decoration group with complete contentDescription,
sibling access/actions and narrowly scoped SelectionContainer. Native controls
retain their own semantics. No membership policy or clipboard work enters UI.

Example: put a native role menu below identity and an independently eligible
removal action beneath it. At font scale at least 1.5 avatar stacks above copy;
logical alignment follows RTL. A nonnull link is selectable monospaced Text;
null uses caller unavailable copy. Only passive identity clears child semantics.
Selection must not swallow independent action/status slots.

Verification, 2026-10-09: both consumers build; 57 iOS app cases, four focused
Android Sharing cases and seven Swift/six Kotlin UI package cases pass.
[Native component cases](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SharingComponentTest.kt)
exercise independent callbacks, passive identity, missing-link copy and 240-dp
font-scale-two RTL growth/native target bounds. Full TalkBack, the native selection
toolbar and clipboard feedback are not exercised.
Read [native mechanics](../../../../substrate/compose-member-slots-and-selectable-links.md),
[consumer](../../app/README.md#sharing-gallery),
[shared pattern](../../../../../../../notes/patterns/membership-identity-and-confirmed-revisions.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#sharing-and-access).
Next: localized traversal and service-owned identity/version admission outside
these native compositions.

## Playback and timeline

Claim: independently eligible native targets and passive media identity keep a
playback composition separate from player and timeline policy.

Origin/evidence, 2026-10-09: the twenty-second UI batch adds
[PlaybackControls](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/playbackcontrols/PlaybackControls.kt)
and [NowPlayingCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/nowplayingcard/NowPlayingCard.kt).
Read IconAction targets inside WrapLayout, middle stateDescription, decorative
fractional Canvas glyphs and the passive heading's clearAndSetSemantics. Timeline,
controls and action lambdas remain native siblings outside that group.

Example: compose a ValueSlider, transport controls and an independent favorite.
Each transport action supplies its own admission and current action/state copy.
Passive artwork defaults to 80 dp and requires finite positive host-fitting size;
font scale at least 1.5 stacks it above growing identity. LocalContentColor gives
glyphs the action's existing theme contrast without another icons dependency.

Verification, 2026-10-09: both consumers build; 59 iOS app cases, four focused
Android Playback cases and seven Swift/six Kotlin UI package cases pass.
[Component checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlaybackComponentTest.kt)
exercise native roles/state, independent disabled/favorite actions, passive cover,
240-dp font-scale-two RTL growth and bounded targets. They establish neither full
TalkBack nor media playback. The owner gallery drives native slider SetProgress;
it does not simulate a physical slider drag.
Read [native mechanics](../../../../substrate/compose-playback-slots-and-native-transport.md),
[consumer](../../app/README.md#playback-gallery),
[shared pattern](../../../../../../../notes/patterns/media-timeline-and-transport-admission.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#playback-and-timeline).
Next: engine snapshots/seek admission at the platform boundary.

## Verification and code entry

Claim: a native controlled code field and separate slots keep reusable entry UI
independent of challenge identity, clocks and authentication.

Origin/evidence, 2026-10-09: the twenty-third UI batch adds
[OneTimeCodeField/CodeFormat](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/forms/onetimecode/OneTimeCodeField.kt)
and [VerificationCard](../../../../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/patterns/verificationcard/VerificationCard.kt).
Read canonical String admission, nullable rejection, Number/Done options,
SmsOtpCode semantics and guarded explicit submission. Native edit/autofill does
not submit. CodeFormat bounds length to 1...12 and preserves leading zeroes.

Example: use six digits with supplied error/help, independent canEdit/canSubmit
and explicit verify callback. Only passive delivery identity/artwork clears child
semantics; content/status/actions keep their native roles. Large text stacks
artwork above growing copy. Input uses TextDirection.Ltr inside logical RTL.
Invalid/oversized edits reject wholly rather than extracting a plausible prefix.

Verification, 2026-10-09: both consumers build; 61 iOS app cases, four focused
Android Verification cases and nine Swift/eight Kotlin UI package cases pass.
[Component checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/VerificationComponentTest.kt)
exercise native replacement/Done, autofill hint semantics, invalid input rejection,
independent actions and 240-dp font-scale-two RTL growth/targets.
[Format checks](../../../../../project/core/ui/src/test/kotlin/dev/mobilefoundry/ui/CodeFormatTest.kt)
exercise empty/partial digits, leading zeroes, separators, bounds and Unicode.
Disabled input removes native SetText semantics; stable labels remain test targets.
Physical paste/Autofill suggestions and full TalkBack are separate observations.
Read [native mechanics](../../../../substrate/compose-code-entry-and-autofill-hints.md),
[consumer](../../app/README.md#verification-gallery),
[shared pattern](../../../../../../../notes/patterns/challenge-drafts-and-attempt-identity.md)
and [usage](../../../../../../../docs/blueprints/ui-components.md#verification-and-code-entry).
Next: real challenge services and native Autofill observations outside shared UI.
