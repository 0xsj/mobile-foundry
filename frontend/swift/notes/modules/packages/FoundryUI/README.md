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
