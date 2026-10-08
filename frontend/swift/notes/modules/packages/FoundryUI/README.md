# Native UI package walkthrough

FoundryUI renders query state through native SwiftUI rows, caller copy and a
domain content builder, without owning requests or services. It also owns
semantic tokens, one preset, and a scoped native theme.

## Origin and reading order

Extracted 2026-10-08 using Xcode 26.2 and Swift 6.2.3. Read
[the contract](../../../../../../contracts/behavior/query-ui.md),
[manifest](../../../../packages/FoundryUI/Package.swift), and
[QueryContent](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Query/QueryContent.swift).
Then compare [NotesScreen](../../../../apps/FoundryCatalog/Sources/Notes/NotesScreen.swift)
with [QueryCatalogView](../../../../apps/FoundryCatalog/Sources/Query/QueryCatalogView.swift).

## Walkthrough and gotchas

QueryCopy carries operation-specific strings and action labels. QueryContent
switches on state, labels progress as loading or refreshing, and renders a
retained snapshot through either empty copy or the content builder. Failed
state projects publicInfo before presenting its message. Buttons forward
callbacks; construction and state changes invoke no action.

The notes feature supplies note rows and list emptiness. The gallery supplies
one string and string emptiness. Group emits rows inside the host's Section;
the component introduces no nested List or task. The host still owns scrolling,
theme, observation, and lifecycle. Copy can be localized before injection.
See [content builder mechanics](../../../language/swift-generic-query-state-and-content-builders.md).

## Verification and limits

`make ios-test` compiled the package and catalog and passed seven store tests.
Simulator interactions on iPhone 17 Pro/iOS 26.2 confirmed idle/initial loading,
content/empty, refreshing both snapshots, failure retaining both snapshots,
generic internal failure copy, Retry, and Cancel restoration. Notes still showed
both content rows through the extracted component. A current screenshot showed
the populated gallery's native List layout; screenshot output was usable in
this slice, unlike the earlier notes session.

These are semantic/manual presentation checks, not automated SwiftUI rendering
tests or a VoiceOver, large-text, dark-mode, and physical-device audit. Android
instrumentation independently covers the shared presentation matrix. The token
slice below extends appearance checks; forms and further controls remain future work.

## Token families and native theme

The 2026-10-08 token slice follows [Styles](../../../../../../STYLES.md) and
[the token contract](../../../../../../contracts/behavior/ui-tokens.md).
Read the actual source in this order:

1. [Primitives](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Primitives.swift)
   stores owned sRGB/alpha values and internal palette steps.
2. [Semantic](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Semantic.swift)
   defines the role set and resolved token bundle.
3. [Space](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Space.swift),
   [Typography](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Typography.swift),
   [Shape](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Shape.swift), and
   [Motion](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Motion.swift)
   hold native scales and reduction behavior.
4. [V1](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Presets/V1.swift)
   maps both appearances without duplicating components.
5. [FoundryTheme](../../../../packages/FoundryUI/Sources/FoundryUI/Theme/FoundryTheme.swift)
   resolves environment inputs and supplies descendant defaults.
6. [Token tests](../../../../packages/FoundryUI/Tests/FoundryUITests/TokensTests.swift)
   compare the portable fixtures, contrast pairs, and reduced-motion values.
7. [TokenCatalogView](../../../../apps/FoundryCatalog/Sources/Tokens/TokenCatalogView.swift)
   owns preview controls and native samples, outside the reusable package.

The app entry installs the provider. A second provider wraps only the catalog
examples, keeping controls outside the appearance override. System Font styles
retain text scaling; point-based minimums allow content growth. Read
[SwiftUI environment mechanics](../../../substrate/swiftui-token-environment.md)
for property wrappers, key paths, inheritance, and reduction.
The catalog's action-label minimum is inside Button's label, with a rectangular
content shape; an outer layout frame alone is not the sizing example being used.

QueryContent and [QueryCopy](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Query/QueryCopy.swift)
now sit together under Components/Feedback/Query; their public names are unchanged.
QueryContent reads semantic feedback colors from the environment, while the
host retains native List row layout. Feature observation and request lifetime
are unaffected by the directory/theme changes.

Three package tests pass. Both the final simulator app build and seven store
regressions pass. Manual iOS 26.2 checks showed light/dark swatches, zero durations
with preview reduction, action/position changes, and those values surviving
Dark → Light. A screenshot showed the dark preview beneath light controls.
There is no automated SwiftUI scope/rendering test or measured iOS touch-bounds
check in this slice; exhaustive Dynamic Type/VoiceOver/device checks remain open.

## Foundry Studio revision

Revised 2026-10-08 after the initial palette was judged too close to Bento's
Supabase styling. V1 now maps porcelain and blue-graphite surfaces, cobalt actions,
and sky/ochre/vermilion statuses. Space uses a four-point rhythm with page=20,
section=24, stack=12; panels use radius 16. Titles/headings are semibold native
styles, and standard custom motion uses 200 ms deceleration. The iOS AccentColor
asset mirrors the preset's light/dark accent as the app fallback.

The existing three package tests pass with the revised fixtures, including added
muted-text and inverse-accent contrast pairs. `make ios-build` compiles the updated
consumer and asset catalog. See [the shared pattern](../../../../../../notes/patterns/semantic-tokens-and-native-themes.md#visual-identity-and-semantic-stability)
for why directory parity does not require inheriting another product's palette.
Simulator accessibility state confirmed both revised palettes, the new scale
captions and timings, action/position changes, and zero durations after reduction.
Screenshot capture returned blank images during this revision, so it supplies
no new visual-layout evidence beyond the earlier preset's inspection.

## Swappable material slice

Added 2026-10-08. Read [Material](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Material.swift),
the updated provider and V1, then [Surface](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/Surface/Surface.swift).
The surface's content/floating role determines whether it can use the selected
glass treatment. Caller content stays in one stable view structure while its
background switches. Padding and callbacks remain caller-owned.

The app entry owns a session-scoped Glass surfaces switch; CatalogView exposes
its binding. TokenCatalogView has an optional local style (nil means inherit),
explicit transparency preview, and a geometric scene with independent selection
and position state. ViewThatFits can arrange scene actions vertically if their
horizontal form does not fit. Light/dark, material, and motion are separate axes.
See [framework mechanics](../../../substrate/swiftui-token-environment.md#glass-material-extension)
and [shared material ownership](../../../../../../notes/patterns/material-themes-and-backdrops.md).

Four package tests pass, including shared material role/reduction cases and
palette/motion stability. Both native apps build. iOS 26.2 accessibility checks
observed app-theme inheritance, local Solid selection, reduced transparency
resolving Glass to Solid, and scene state surviving those changes and dark
appearance. Screenshot capture still returned blank white output, so no new
visual inspection of glass is claimed. Older-system Material, live accessibility
setting changes, large-text layout, VoiceOver, and physical GPU cost remain
unverified. The scene is a native 2D material demonstration, not a 3D renderer.

## Native forms and write feedback

The 2026-10-08 forms slice adds
[LabeledTextField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/TextField/LabeledTextField.swift),
[SubmitButton](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/SubmitButton/SubmitButton.swift), and
[MutationFeedback](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Mutation/MutationFeedback.swift).
The first wraps native editing with persistent labels, help/error and caller
focus/submit callbacks. The second renders busy copy/spinner and a disabled
native action. Feedback projects public failure copy or caller success content.
None imports services, performs validation or launches requests.

These live in separate Forms and Feedback families following Styles. Native
text and minimum dimensions can grow. The form gallery's corrected outer panels
select floating Surface so Solid/Glass affects their material; explicit
content-role panels still stay opaque. Read [FocusState and native submission](../../../substrate/swiftui-form-focus-and-submit.md)
for why text/focus bindings differ and why the feature action runs synchronously.
`make ios-test` compiles the real consumer and passes thirteen feature tests;
the UI package's four token/material tests remain separate from form behavior.
Actual keyboard and error presentation require simulator/device checks; compile
success alone is not layout or VoiceOver evidence.

## Next questions

Which failure needs a feature-specific recovery action beyond Retry? Which
layout choices belong to the host rather than QueryContent? Read
[the shared pattern](../../../../../../notes/patterns/query-state-and-rendering.md).
How should a rendered scene provide its backdrop without moving frame ownership
into the UI theme? Which floating controls need material interaction or grouping?

## Reserved component catalog

The 2026-10-08 [component map](../../../../../../docs/COMPONENTS.md) adds
.gitkeep-only planned leaf directories grouped like Bento. These do not add
public APIs, variants, tests or module dependencies. The native TabView shell
belongs to app composition; Swift's Navigation/TabBar and Shells/AppShell are
reserved for a later justified reusable consumer. See
[tabs and feature lifetime](../../../../../../notes/patterns/tabs-and-feature-lifetime.md).

## Everyday component batch and naming

Added 2026-10-08. Twelve reserved leaves now contain reusable components. Read
[the API/usage guide](../../../../../../docs/blueprints/ui-components.md), then
[ActionButton](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Button/ActionButton.swift),
[Card](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/Card/Card.swift),
[ListRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/ListRow/ListRow.swift),
and [SelectionCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SelectionCard/SelectionCard.swift).
All component names and helper types have no Foundry prefix; package/theme names
retain it. LabeledTextField keeps native TextField available. SubmitButton now
composes ActionButton without changing its busy-copy API.

Native Button and ButtonStyle retain activation and disabled semantics. The
style reads isEnabled and theme roles; the action wrapper disables while busy.
Generic labels accept text/icons without AnyView. Card supplies padded Surface
composition; SettingsSection and SelectionCard build on it. A selection label
must not contain nested actions. ListRow keeps route ownership with its caller
and stacks its trailing accessory at accessibility text sizes.

Constrained initializers with EmptyView let PageHeader/InlineAlert omit actions
without erased content. Tabs wraps a native Picker, switching from segmented to
menu at accessibility sizes or for more than three options. The caller supplies
a unique nonempty option set containing the current selection. SearchField owns
neither debounce nor request execution. Read
[generic builders](../../../language/swift-generic-query-state-and-content-builders.md)
and [shared slot ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md).

Both app builds and all eighteen iOS app checks pass. The Android focused
eleven-check run supplies separate interaction coverage. iOS manual evidence
and keyboard/accessibility/device limits are in the catalog walkthrough.
No new pure tests merely mirror view construction.

## Selection controls and native overlays

Added 2026-10-08. Six controls and three overlay patterns fill existing leaves.
Read [ToggleField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Toggle/ToggleField.swift),
[Checkbox](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Checkbox/Checkbox.swift),
[RadioGroup](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/RadioGroup/RadioGroup.swift),
[SelectField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Select/SelectField.swift),
[ValueSlider](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Slider/ValueSlider.swift),
and [DateField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/DatePicker/DateField.swift).
Boolean and choice values are bindings or caller callbacks. Mixed checkbox state
is a projection of children, not an internally stored cycle. Slider steps count
intermediate positions, converted to a native interval. DateField's State draft
is copied on opening and committed only by Use date; the native DatePicker and
display share environment calendar, locale and time zone.

[SheetPanel](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Overlays/Sheet/SheetPanel.swift)
provides a native sheet convenience modifier and independently composable body.
[confirmationPrompt](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Overlays/Dialog/ConfirmationPrompt.swift)
uses a native alert, and
[ActionMenu](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Overlays/Menu/ActionMenu.swift)
uses native Menu with unique IDs, disabled entries and destructive roles. The
caller controls sheet/dialog presence, content, scrolling and effects. Dismissing
or recomposing cannot confirm. Checkbox/radio rows expose button selected/value
semantics on iOS; native Android roles differ.

Read [SwiftUI selection and modal drafts](../../../substrate/swiftui-selection-and-modal-drafts.md)
for State identity, native differences, date interpretation and alternatives.
The package compiles on macOS and passes its four existing token/material tests;
the iOS consumer builds and passes all eighteen existing app checks. Native
interaction evidence belongs to the app walkthrough; these existing tests do
not imply automated iOS widget interaction coverage. Next: which feature needs
range-limited dates, a nullable date binding or externally coordinated drafts?

The sheet convenience modifier fills the native presentation bounds around its
body so the scoped theme background covers the whole sheet. The independently
usable SheetPanel remains intrinsically sized for caller composition. A fresh
iOS build and native dark-sheet screenshot verify that layout correction; the
app walkthrough records the preceding visual failure and exercised interactions.

## Display feedback and collection components

Added 2026-10-08. Read
[Avatar](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/Avatar/Avatar.swift),
[StatCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/Stat/StatCard.swift),
[Skeleton](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Skeleton/Skeleton.swift),
[ToastBanner](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Toast/ToastBanner.swift),
[FieldGroup](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/FieldGroup/FieldGroup.swift),
and [CollectionToolbar](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/CollectionToolbar/CollectionToolbar.swift).
These six APIs fill existing reserved leaves, bringing the three component
batches to 27 building blocks without a new module or dependency.

Avatar accepts passive artwork or short fallback copy and replaces child
semantics with one identity. Its optional generic content and EmptyView
initializer avoid AnyView. StatCard accepts formatted strings, units and trend
meaning rather than a numeric/domain model. Skeleton's native phaseAnimator
branch disappears under motion reduction or animated: false; its geometry is
hidden from accessibility and hit testing. The host supplies actual loading
copy and controls when content replaces it.

ToastBanner is a floating Card with optional ToastAction and explicit dismissal.
It does not time, queue, announce, retry or remove itself. FieldGroup preserves
native child targets using contain and prefers error over help; the caller owns
validation and per-field associations. CollectionToolbar composes native filter
and action builders with supplied summary copy; it does not own records or
selection. Its host supplies responsive action layout.

Read [native mechanics](../../../substrate/swiftui-loading-and-passive-content.md),
[shared projection/lifetime](../../../../../../notes/patterns/collection-projections-and-feedback-lifetime.md),
and [usage](../../../../../../docs/blueprints/ui-components.md#display-and-feedback).
The package compiles on macOS and passes four token/material checks; the iOS
consumer compiles and all eighteen existing app checks pass. Android's nineteen
focused checks provide separate native interaction and pixel evidence. Manual
iOS observations are recorded in the app walkthrough, without claiming added
automated iOS widget coverage. Next: which product needs image cache ownership,
a notice queue, or a server-wide bulk selection scope?

## Context navigation and adaptive layouts

Added 2026-10-08. The fourth batch fills six existing leaves, bringing the
component batches to 33 building blocks. Read
[NavLink](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/NavLink/NavLink.swift),
[PopoverPanel](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Overlays/Popover/PopoverPanel.swift),
[HelpTooltip](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Overlays/Tooltip/HelpTooltip.swift),
[ContentContainer](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/Container/ContentContainer.swift),
[AdaptiveGrid](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/Grid/AdaptiveGrid.swift),
and [MediaFrame](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/AspectRatio/MediaFrame.swift).
No component or helper gains a Foundry prefix, package or third-party dependency.

NavLink supplies a native NavigationLink around a ListRow; the consumer owns the
NavigationStack and destination. PopoverPanel attaches native presentation to a
generic anchor and receives the caller's Binding. Its close action only changes
presentation. Scoped tokens/color scheme cross the presentation boundary. Native
compact adaptation preserves the anchored popover. HelpTooltip is visible tap
help with short copy and no auto-hide/hover timer. Use a sheet/detail or an
explicitly scrolling panel slot for longer content.

ContentContainer caps and centers the padded region; MediaFrame establishes
ratio bounds and clips unknown caller artwork without choosing a crop or label.
AdaptiveGrid uses a private native Layout. ScaledMetric grows its minimum cell
width with Dynamic Type; each row reserves its tallest measured child. It places
columns in native reading direction, retains incomplete-row widths and introduces
no nested scroll or item model. Stable ForEach identity stays with the consumer.
These eager layouts serve small compositions; long feeds need native lazy grids.

Read [native mechanisms](../../../substrate/swiftui-layout-and-contextual-presentation.md),
[shared ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08),
and [usage](../../../../../../docs/blueprints/ui-components.md#small-responsive-compositions).
Checks live in [ComponentLayoutTests](../../../../apps/FoundryCatalog/Tests/ComponentLayoutTests.swift):
actual hosted geometry at normal/narrow/large-text/RTL settings, plus readable
insets and a 4:3 media frame. The package compiles on macOS and passes its four
existing token/material tests. The app walkthrough separates native measurement,
manual interactions and remaining device/accessibility limits.
Next: which consumer needs lazy scrolling, configurable alignment or a different
compact presentation instead of these small native compositions?

The final iOS suite passes all twenty checks, including both new geometry cases.
Its RTL case caught double mirroring in the first implementation; SwiftUI already
mirrors custom Layout positions, so no explicit reversal is retained. This is a
native execution finding supported by the framework reference, not an inferred
promise from a passing formula test. Manual gallery evidence stays in the app
walkthrough; broad device/accessibility coverage remains open.

## Choices disclosure and detail composition

Added 2026-10-08. The fifth component batch adds six APIs, bringing the total to
39. Read [ChoiceChip](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Chip/ChoiceChip.swift),
[ValueStepper](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Stepper/ValueStepper.swift),
[DisclosureSection](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/Disclosure/DisclosureSection.swift),
[KeyValueRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/KeyValue/KeyValueRow.swift),
[ActionBar](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/ActionBar/ActionBar.swift),
and [DetailShell](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Shells/DetailShell/DetailShell.swift).
The components keep values, formatted copy, routes and effects with their caller.

ChoiceChip is a native button with selected semantics and a passive optional
leading slot. ValueStepper uses independently labeled native buttons, validates
its range/current value/positive step, and checks integer overflow before
clamping. Its endpoint policy permits a shorter final step: 0...5 by two becomes
0, 2, 4, 5. Read [bounded integer arithmetic](../../../language/swift-bounded-integer-arithmetic.md)
for why clamping the result of ordinary addition is insufficient.

DisclosureSection controls a native button header and conditional content. The
caller hoists an editable draft above that conditional branch. Motion reduction
disables its animation; focus dismissal remains feature policy. KeyValueRow is
passive combined copy, using native fitting or stacked text rather than truncating
a long value. ActionBar is a floating surface with supplied summary/actions;
its placement determines whether it stays visible.

DetailShell reserves header and action regions around a flexible body inside a
bounded screen. The consumer supplies body scrolling, native navigation and
insets. Do not place the whole shell in an unbounded vertical scroller. Read
[native layout and disclosure](../../../substrate/swiftui-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08),
[shared ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#collapsed-drafts-and-detail-viewports--2026-10-08),
and [usage examples](../../../../../../docs/blueprints/ui-components.md#choices-disclosure-and-detail-screens).

[DetailShellLayoutTests](../../../../apps/FoundryCatalog/Tests/DetailShellLayoutTests.swift)
measures actual hosted header/body/footer bounds before and after native scrolling
and accessibility3 text. All 21 iOS app checks pass; the macOS package compiles
and its four existing token/material tests pass. App interaction and navigation
appearance evidence is recorded in the catalog walkthrough. Next: which feature
needs step values on a fixed lattice, and when should large header/actions move
into the scrolling region instead of staying pinned?

## Rich input and journey pages

Added 2026-10-08. Six components bring the catalog batches to 45 building blocks.
Read [PasswordField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Password/PasswordField.swift),
[MultilineField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Multiline/MultilineField.swift),
[ValidationChecklist](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/ValidationChecklist/ValidationChecklist.swift),
[StepIndicator](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/StepIndicator/StepIndicator.swift),
[OnboardingPage](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/OnboardingPage/OnboardingPage.swift)
and [AuthShell](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Shells/AuthShell/AuthShell.swift).

PasswordField wraps SecureField, with iOS current/new content-type metadata and
caller bindings/focus/submit. MultilineField uses a vertical native TextField and
positive visible line range. Neither owns validation or character truncation.
ValidationChecklist and StepIndicator receive unique IDs, supplied states and
accessible descriptions. Their symbols are decorative; rows are passive, not
checkboxes or navigable tabs. Empty lists and an entirely completed journey are
valid projections. No automatic action follows a changed status.

AuthShell scrolls readable header/form/footer slots together. OnboardingPage
reuses DetailShell to scroll artwork/copy/content while reserving actions. Its
caller controls page identity, safe areas, focus and routing. Read
[native input and identity](../../../substrate/swiftui-rich-input-and-journey-pages.md),
[shared ownership](../../../../../../notes/patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08)
and [usage](../../../../../../docs/blueprints/ui-components.md#rich-input-and-onboarding).

[JourneyFieldTests](../../../../apps/FoundryCatalog/Tests/JourneyFieldTests.swift)
inspects the actual hosted native secure-input trait and measures multiline
viewport growth, its line cap and larger text without changing the retained
long draft. The catalog walkthrough records final execution and manual limits.
Next: which real editor needs TextEditor instead of a growing field, and which
account screen needs a different footer placement?

The final native consumer run passes all 22 iOS checks, including the added input
case; macOS package compilation and its four existing UI tests pass. The app
walkthrough explains the probe correction, manual keyboard differences and
limits. Passing layout checks do not establish complete keyboard/accessibility
or real provider autofill behavior.

## Activity and paged collections

Added 2026-10-09 for the seventh batch. New component leaves:

- [SectionHeader](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SectionHeader/SectionHeader.swift).
- [AvatarGroup](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/AvatarGroup/AvatarGroup.swift).
- [TimelineItem](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/TimelineItem/TimelineItem.swift).
- [ExpandableText](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/ExpandableText/ExpandableText.swift).
- [RefreshContainer](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/RefreshContainer/RefreshContainer.swift).
- [LoadMoreFooter](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/LoadMore/LoadMoreFooter.swift).

SectionHeader supplies compact reading structure and independent actions.
AvatarGroup receives stable identities and passive slots, limits visible artwork
and exposes one complete supplied summary. TimelineItem draws only a decorative
marker/connector; supplied content can contain native controls. ExpandableText
receives the expanded flag and explicit labels for known long copy. It changes
native text limits without guessing overflow or storing important row state.
LoadMoreFooter projects idle/loading/failed/exhausted phases; only enabled
idle/failed actions dispatch. It never loads on appearance.

RefreshContainer attaches native refreshable and directly awaits the supplied
async callback. It creates neither a scrolling container nor a request/task.
The caller must supply supported scrolling content, bounded layout, admission,
failure presentation and cancellation policy. The catalog supplies List.

Read [native mechanics](../../../substrate/swiftui-refresh-and-lazy-activity.md),
[shared ownership](../../../../../../notes/patterns/refresh-and-pagination-ownership.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#activity-feeds-and-pagination).
[Native consumer checks](../../../../apps/FoundryCatalog/Tests/ActivityPreviewTests.swift) exercise the operation/lifetime and disclosure
boundaries; the app walkthrough records final evidence and limits. Next: what
real feed needs a domain paging contract rather than more UI abstraction?

Final evidence for this batch, 2026-10-09: both consumers build, 27 iOS app
checks pass, four new Android activity checks pass, and nineteen existing Android
component regressions pass. Four UI unit checks per platform and notes validation
pass. The app walkthrough separates native refresh-control/gesture evidence,
manual visual observations, test-helper corrections and coverage limits.
