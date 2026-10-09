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

## Media browsing and actions

Added 2026-10-09 for the eighth batch. New component leaves:

- [IconAction](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/IconAction/IconAction.swift).
- [RatingField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/Rating/RatingField.swift).
- [PageIndicator](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/PageIndicator/PageIndicator.swift).
- [Carousel](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/Carousel/Carousel.swift).
- [MediaTile](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/MediaTile/MediaTile.swift).
- [MediaOverlay](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/MediaOverlay/MediaOverlay.swift).

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

Carousel uses a horizontal lazy native stack with full-viewport pages, native
paging behavior and stable-ID scroll position. Its optional native ID bridge
writes only valid IDs to the nonoptional caller binding. The caller supplies
nonempty unique records, a valid selection, bounds and any native scrollDisabled
modifier; it creates no request, page command or autoplay task.

Read [native mechanics](../../../substrate/swiftui-media-paging-and-overlays.md),
[shared ownership](../../../../../../notes/patterns/media-selection-and-passive-artwork.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#media-browsing-and-actions).
[Native consumer checks](../../../../apps/FoundryCatalog/Tests/MediaComponentTests.swift) cover actual native layout/semantics rather
than a second UI model. Both consumers build; 29 iOS app checks, four final Android
media checks, fourteen component/activity regressions and four UI unit checks
per platform pass. App walkthroughs separate evidence and limits. Next: what
record-admission policy should precede dynamic carousel content replacement?

## Communication and attachments

Claim: communication compositions can project caller draft/transfer values and
independent actions without becoming a messaging or upload service.

Added 2026-10-09 for the ninth batch. New leaves:

- [ConversationRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/ConversationRow/ConversationRow.swift).
- [MessageBubble](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/MessageBubble/MessageBubble.swift).
- [MessageComposer](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/MessageComposer/MessageComposer.swift).
- [AttachmentRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/AttachmentRow/AttachmentRow.swift).
- [TransferStatus](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Transfer/TransferStatus.swift).
- [TypingIndicator](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Typing/TypingIndicator.swift).

MessageComposer takes a draft binding and external focus binding. MultilineField
retains native text editing while canSend/isSending/enabled project app admission.
The inherited disabled environment gates its slot subtree. ViewThatFits chooses
action layout without recreating the field. MessageBubble uses logical spacers,
native selectable text and separate interactive accessories. TypingIndicator uses
the existing native phase animation only when scoped motion permits.

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

Read [native mechanics](../../../substrate/swiftui-composer-and-safe-area.md),
[shared ownership](../../../../../../notes/patterns/composer-drafts-and-transfer-ownership.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#communication-and-attachments).
[Native consumer checks](../../../../apps/FoundryCatalog/Tests/CommunicationComponentTests.swift) exercise actual hosted layout or native semantics
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

- [WrapLayout](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/Wrap/WrapLayout.swift).
- [RemovableChip](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/RemovableChip/RemovableChip.swift).
- [TokenField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/TokenField/TokenField.swift).
- [SelectionRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SelectionRow/SelectionRow.swift).
- [SwipeActionRow / SwipeAction](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SwipeActionRow/SwipeActionRow.swift).

WrapArrangement measures intrinsic child widths and reflows at finite available
width, using the tallest row height for the next gap. Native Layout placement
mirrors for RTL. TokenField receives text/focus bindings and inherits disabled
state into its slot subtree. SelectionRow exposes a selected native button;
SwipeActionRow attaches native List swipe actions with supplied Buttons/roles.

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

Read [native mechanics](../../../substrate/swiftui-wrapping-and-list-actions.md),
[shared ownership](../../../../../../notes/patterns/selection-identity-and-undo.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#selection-tokens-and-row-editing).
[Native checks](../../../../apps/FoundryCatalog/Tests/EditingComponentTests.swift) exercise hosted wrapping or semantics/actions.
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

- [TrendBadge / TrendDirection](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/TrendBadge/TrendBadge.swift).
- [LegendItem / LegendMark](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Charts/Legend/LegendItem.swift).
- [Sparkline](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Charts/Sparkline/Sparkline.swift).
- [BarChart / ChartBar](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Charts/BarChart/BarChart.swift).
- [ProgressRing](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Charts/ProgressRing/ProgressRing.swift).
- [ChartPanel](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/ChartPanel/ChartPanel.swift).

Sparkline draws an inset Path through normalized CGPoint values in native Canvas.
ProgressRing draws Circle trim/stroke and moves value text below the fixed circle
at accessibility DynamicType sizes. BarChart uses native Text/ViewThatFits and
GeometryReader; its final accessible value includes every category/formatted value.
ChartPanel keeps footer controls independent rather than combining their semantics.

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

Read [native mechanics](../../../substrate/swiftui-chart-drawing-and-summaries.md),
[chart meaning/scales](../../../../../../notes/patterns/chart-meaning-and-scales.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#insights-and-small-charts).
[Numerical checks](../../../../packages/FoundryUI/Tests/FoundryUITests/ChartTests.swift) exercise finite extrema and degenerate sequences;
[consumer checks](../../../../apps/FoundryCatalog/Tests/InsightsComponentTests.swift) cover native layout, drawing or semantics/actions.
Both consumers build; all 37 iOS app checks, four final Android Insights checks,
five Android editing regressions and six UI unit checks per platform pass. App
notes distinguish manual observation, harness corrections and platform limits.
Next: what shared axis/selection model should a time-aware chart receive?

## Dates and agendas

Claim: calendar/date interpretation and availability remain feature policies;
the UI owns native picker drafts, selection presentation and copy layout.
Added 2026-10-09: [TimeField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/TimePicker/TimeField.swift), [DateRangeField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/DateRange/DateRangeField.swift), [DayStrip](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/DayStrip/DayStrip.swift), [AgendaRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/AgendaRow/AgendaRow.swift).

TimeField/ClockTime separates hour/minute meaning from native picker encoding.
DateRangeField composes two independently committed dates and caller help/error,
without swapping endpoints. DayStrip wraps supplied stable IDs and independent
selected/disabled states. AgendaRow keeps its status/actions outside merged
passive copy. DateField now dismisses disabled drafts; Swift modal themes inherit
their caller's scope.

The [native consumer checks](../../../../apps/FoundryCatalog/Tests/SchedulingComponentTests.swift) exercise actual controls/layout,
with feature admission in the app. Read [native mechanics](../../../substrate/swiftui-time-and-date-drafts.md),
[shared ownership](../../../../../../notes/patterns/calendar-dates-and-clock-readings.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#dates-and-agendas).
Checks and limits are recorded in the app walkthrough; no calendar service,
recurrence, DST resolution or booking guarantee is established.
Next: use these slots in a concrete planner with an admitted scheduling command.

## Adaptive workspaces

Claim: destination/path affordances and local pane presentation can remain reusable
while feature identity, compact intent and route policy stay above their slots.
Origin, 2026-10-09: thirteenth batch, source and native consumer checks.

- [DestinationRail / RailDestination](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/NavigationRail/DestinationRail.swift).
- [BreadcrumbTrail / BreadcrumbItem](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/Breadcrumbs/BreadcrumbTrail.swift).
- [SplitPane / PaneMode](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/SplitPane/SplitPane.swift).

DestinationRail uses native SwiftUI Buttons and selected traits; labels remain
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

Read [native mechanics](../../../substrate/swiftui-bounded-panes-and-navigation.md),
[shared ownership](../../../../../../notes/patterns/adaptive-layout-and-navigation-state.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#adaptive-workspaces).
[Native checks](../../../../apps/FoundryCatalog/Tests/WorkspaceComponentTests.swift) cover actual pane layout and feature/control boundaries.
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

- [TableSortHeader / TableSortOrder](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/TableSortHeader/TableSortHeader.swift).
- [DataTable / DataTableColumn](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/DataTable/DataTable.swift).
- [PaginationBar](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/Pagination/PaginationBar.swift).

DataTable uses Identifiable row values and generic ViewBuilder slots. Shared HStack frames preserve column width while native text grows vertically.
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

Read [native mechanics](../../../substrate/swiftui-table-columns-and-scrolling.md),
[shared ownership](../../../../../../notes/patterns/table-sorting-and-page-ownership.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#tables-and-pagination).
[Native checks](../../../../apps/FoundryCatalog/Tests/TableComponentTests.swift) exercise geometry, scrolling or action/state boundaries.
Verification, 2026-10-09: both native consumers build; all 43 iOS app checks,
four focused Android Tables UI checks, seven Swift and six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
No sticky columns, spreadsheet editing, dense-data budget, localization audit or
server ordering guarantee is established. Next: admit a real ordered page through
an existing service seam before choosing a cursor or numbered-page contract.

## Products and order composition

Claim: price meaning and command policy arrive as caller values while native
compositions keep product, code and summary actions independent.

Origin, 2026-10-09: seventeenth UI batch and native consumer checks.
Read [PriceLabel](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/PriceLabel/PriceLabel.swift),
[ProductRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/ProductRow/ProductRow.swift),
[OrderSummary](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/OrderSummary/OrderSummary.swift)
and [InlineActionField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/InlineAction/InlineActionField.swift).

PriceLabel accepts formatted current/comparison/detail text and a complete native
accessible label. No money model or comparison inference exists. ProductRow
composes ListRow with decorative artwork, then independent price/status/actions.
OrderSummary reuses Card, SectionHeader, native Divider and KeyValueRow; its
supplied lines/footer retain native focus targets. The card never calculates totals.

InlineActionField combines FieldGroup feedback, a native rounded TextField and
ActionButton. ViewThatFits uses a horizontal input/action row then a stacked
fallback; accessibility sizes always stack. Both submit paths use one explicit
enabled/not busy/canSubmit guard. Text/focus bindings remain supplied; the caller
decides parsing, validation, keyboard dismissal and clearing. Help/error sits
outside the control row so it does not disturb input/button alignment.

Example: render an admitted price and supply its previous-price meaning in
narration; put bounded quantity controls in product actions. Use InlineActionField
for a promo/invite code while keeping its command in the owner. Busy gates editing
and submission, while canSubmit=false alone still allows text edits.

[Native checks](../../../../apps/FoundryCatalog/Tests/CommerceComponentTests.swift)
cover fixture command/totals/snapshot policy and 240-point larger-text/RTL
geometry. Both consumers build; 49 iOS app, four focused Android Commerce UI
and seven Swift/six Kotlin UI package checks pass. These checks do not establish
full VoiceOver/localization/device or payment behavior. Read
[native mechanics](../../../substrate/swiftui-inline-fields-and-order-composition.md),
[catalog flow](../../apps/FoundryCatalog/README.md#commerce-gallery),
[shared ownership](../../../../../../notes/patterns/price-copy-and-committed-cart-values.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#products-and-order-composition).
Next: admit real server quote copy without moving arithmetic into these views.

## Search and discovery

Claim: literal text runs, supplied suggestion actions and independent result
actions form a reusable search presentation without owning a search engine.

Origin, 2026-10-09: sixteenth UI batch and native catalog consumer checks.
Read [HighlightedText / HighlightSegment](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/HighlightedText/HighlightedText.swift),
[SearchSuggestionRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SearchSuggestionRow/SearchSuggestionRow.swift),
[SearchResultRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SearchResultRow/SearchResultRow.swift)
and the extended [SearchField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/SearchField/SearchField.swift).

HighlightSegment holds complete literal text and an emphasis flag. HighlightedText
appends attributed runs into one native Text, with optional font/color overrides.
The rows receive title/detail, accessible action copy and decorative artwork.
SearchResultRow puts passive preview inside its open Button and independent
actions below it; enabled applies to opening, while the host gates each sibling.
SearchField's default-enabled extension guards editing, clearing and submission.
The component has no matching, history, filters, routing or service logic.

Example: render an admitted excerpt with highlighted runs, provide a complete
open label and put Save in actions rather than inside preview. Meaningful
preview content must be included in narration. Combining Button children keeps
its native role; replacing them with ignore initially produced an untyped
accessibility element in the simulator and was corrected during interaction QA.

[Consumer checks](../../../../apps/FoundryCatalog/Tests/DiscoveryComponentTests.swift)
cover feature guards/text preservation and actual narrow, larger-text/RTL bounds.
Both consumers build; 47 iOS app, four focused Android Discovery UI and seven
Swift/six Kotlin UI package checks pass. These checks do not establish a full
assistive-technology, localization or device audit. See
[native text mechanics](../../../substrate/swiftui-attributed-text-and-search-actions.md),
[catalog flow](../../apps/FoundryCatalog/README.md#discovery-gallery),
[shared reasoning](../../../../../../notes/patterns/search-projection-and-filter-drafts.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#search-and-discovery).
Next: integrate locale-aware matching and actual query state outside these views.

## Accounts and access

Claim: identity/session/capability presentation can be reusable without taking
ownership of authenticated context or OS prompting.
Origin, 2026-10-09: fifteenth UI batch and native consumer checks.

- [ProfileHeader](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/ProfileHeader/ProfileHeader.swift).
- [AccountSwitcher / AccountOption](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/AccountSwitcher/AccountSwitcher.swift).
- [SessionRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/SessionRow/SessionRow.swift).
- [PermissionCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/PermissionCard/PermissionCard.swift).

Swift stores generic ViewBuilder values. Menu/Buttons retain native presentation;
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
[Native checks](../../../../apps/FoundryCatalog/Tests/AccountComponentTests.swift) cover supplied-state guards, independent native actions
or actual narrow/large-text/RTL geometry. Read [native mechanics](../../../substrate/swiftui-account-menus-and-action-slots.md),
[shared scope](../../../../../../notes/patterns/account-context-and-device-capabilities.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#accounts-and-access).
No credentials, permission API, session revocation, secure storage or full
assistive-technology audit is supplied. Next: use these slots with a feature-owned
identity service and capability adapter once their scopes are defined.

## Notifications and inbox

Claim: count/notification components project supplied meaning and expose native
actions without owning inbox identities or receipts.

Origin/evidence, 2026-10-09: the eighteenth UI batch adds
[CountBadge](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/CountBadge/CountBadge.swift) and
[NotificationRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/NotificationRow/NotificationRow.swift).
Generic ViewBuilder values keep artwork and actions native. CountBadge accepts
opaque visible copy plus full narration. NotificationRow has a native open
Button, supplied read/time copy and semibold unread emphasis; sibling actions
remain outside its label. Enabled controls opening only. The feature chooses
each action's eligibility, rather than disabling all descendants automatically.

Example: display 99+ while narrating 128 unread updates, then compose a row with
independent read and archive commands. Count caps, zero hiding, plural forms,
grouping and relative time remain caller policy. Decorative slots must be passive
and must not carry meaning absent from the supplied narration. Read state is not
native selection state or permission authorization.

Verification: both consumers build; 51 iOS app cases, four focused Android inbox
cases and seven Swift/six Kotlin UI package cases pass. The
[hosted cases](../../../../apps/FoundryCatalog/Tests/NotificationComponentTests.swift)
exercise fixture admission and real 240-point large-text/RTL geometry with
independent minimum-sized actions. No full assistive traversal is established.
Read [native mechanics](../../../substrate/swiftui-notification-actions-and-narration.md),
[consumer](../../apps/FoundryCatalog/README.md#notifications-gallery),
[identity pattern](../../../../../../notes/patterns/inbox-projection-and-read-identity.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#notifications-and-inbox).
Next: supply localized identity/read copy and scoped real command adapters.

## Plans and usage

Claim: native feature/price slots and an explicit choice control keep entitlement
and usage policy outside reusable plan presentation.

Origin/evidence, 2026-10-09: the nineteenth UI batch adds
[FeatureRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/FeatureRow/FeatureRow.swift),
[PlanCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/PlanCard/PlanCard.swift) and
[UsageMeter](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/UsageMeter/UsageMeter.swift).
Feature/usage views are passive, with complete supplied narration. Decorative
marks/bar do not carry independent meaning. PlanCard stores native price/status/
feature builders and has an explicit ActionButton; selected traits and enabled
state are distinct. Selection rejects repeated choice; independent slot actions
stay outside that target and keep their own eligibility.

Example: supply yearly charge meaning beside a monthly-equivalent price, then
show usage 50 of 5 after a local downgrade. The meter clamps its decorative bar
without hiding overflow copy. Nil/nonfinite omits the bar rather than loading.
Draft/current/review values, quota calculation and receipt admission are host policy.

Verification, 2026-10-09: both consumers build; 53 iOS app cases, four focused
Android Plans cases and seven Swift/six Kotlin UI package cases pass.
[Hosted/owner checks](../../../../apps/FoundryCatalog/Tests/PlanComponentTests.swift)
exercise admission, retained usage and 240-point larger-text/RTL native geometry
with independent slot action bounds. iOS review interaction is source/build-checked;
Android consumer checks exercise actual sheet/restore/application. No full
VoiceOver or billing integration is established.
Read [native mechanics](../../../substrate/swiftui-plan-slots-and-usage-bars.md),
[consumer](../../apps/FoundryCatalog/README.md#plans-gallery),
[shared pattern](../../../../../../notes/patterns/plan-choice-and-applied-allowance.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#plans-and-usage).
Next: localized assistive traversal and feature-owned real quote/receipt admission.

## Files and hierarchy

Claim: flattened caller state and sibling native targets let file rows be reused
without importing hierarchy or provider behavior into the UI package.

Origin/evidence, 2026-10-09: the twentieth UI batch adds
[FileTypeMark](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Display/FileTypeMark/FileTypeMark.swift)
and [TreeRow/TreeDisclosure](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/TreeRow/TreeRow.swift).
Read the passive format mark, native opening Button, sibling disclosure IconAction
and independent action builder. Complete supplied labels include format/folder
meaning; passive leading artwork is hidden. Selected traits stay separate from
expanded or disabled state.

Example: display a nested PNG with a separate favorite action. The caller supplies
a depth and full narration. Defaults cap 16-point indentation at 48; larger text
moves artwork above growing copy. Opening's enabled flag leaves disclosure/action
eligibility independent. Do not place interactive controls in the leading slot.

Verification, 2026-10-09: both consumers build; 55 iOS app checks, four focused
Android Files cases and seven Swift/six Kotlin UI package cases pass.
[Hosted/owner cases](../../../../apps/FoundryCatalog/Tests/FileComponentTests.swift)
exercise fixture admission/projection plus narrow extreme-depth large-text/RTL
geometry, passive artwork bounds and minimum action targets. iOS sheet interaction
and full VoiceOver traversal are not established by these geometry checks.
Read [native mechanics](../../../substrate/swiftui-tree-actions-and-indentation.md),
[consumer](../../apps/FoundryCatalog/README.md#files-gallery),
[shared pattern](../../../../../../notes/patterns/tree-projection-and-retained-selection.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#files-and-hierarchy).
Next: localized hierarchy narration and provider-owned loaded-child scope.

## Sharing and access

Claim: passive identity and link text plus independent native slots let sharing
compositions remain reusable across membership and authorization models.

Origin/evidence, 2026-10-09: the twenty-first UI batch adds
[MemberRow](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/MemberRow/MemberRow.swift)
and [ShareLinkCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/ShareLinkCard/ShareLinkCard.swift).
Read the passive identity group, supplied full narration, independent access/
action builders and narrowly scoped native text selection. Only the identity/
decoration group ignores child accessibility; controls remain separate.

Example: place an Avatar beside a member identity, a native role menu below it
and an independently eligible removal action. Larger text stacks avatar above
identity. A nonnil link uses selectable monospaced Text, nil uses caller unavailable
copy. The card does not copy, create a link or choose a browser destination.
Native selection granularity follows the OS; do not wrap the card's action slots
inside its selectable text or one ignored accessibility element.

Verification, 2026-10-09: both consumers build; 57 iOS app cases, four focused
Android Sharing cases and seven Swift/six Kotlin UI package cases pass.
[Hosted/owner cases](../../../../apps/FoundryCatalog/Tests/SharingComponentTests.swift)
exercise membership admission and 240-point accessibility-text/RTL identity/link
growth with bounded native action targets. They do not drive the iOS selection
menu, clipboard presentation, confirmation interaction or full VoiceOver traversal.
Read [native mechanics](../../../substrate/swiftui-member-slots-and-selectable-links.md),
[consumer](../../apps/FoundryCatalog/README.md#sharing-gallery),
[shared pattern](../../../../../../notes/patterns/membership-identity-and-confirmed-revisions.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#sharing-and-access).
Next: localized assistive traversal and service-backed access decisions outside
these passive/slot components.

## Playback and timeline

Claim: independent native controls and passive media identity keep playback
compositions reusable across engines and feature policies.

Origin/evidence, 2026-10-09: the twenty-second UI batch adds
[PlaybackControls](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/PlaybackControls/PlaybackControls.swift)
and [NowPlayingCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/NowPlayingCard/NowPlayingCard.swift).
Read the three IconAction values, horizontal/vertical ViewThatFits, supplied
accessibility value, passive heading group and independent ViewBuilder slots.
Only identity/artwork ignores child narration; the slider and actions remain
native elements outside that group.

Example: put a ValueSlider in timeline, PlaybackControls in controls and an
independent favorite in actions. Give each transport action its own admission.
Artwork defaults to an 80-point square and requires finite positive host-fitting
size. Accessibility Dynamic Type stacks artwork above growing title/detail.
The card does not load media or reinterpret its caller's playback state.

Verification, 2026-10-09: both consumers build; 59 iOS app cases, four focused
Android Playback cases and seven Swift/six Kotlin UI package cases pass.
[Hosted/owner checks](../../../../apps/FoundryCatalog/Tests/PlaybackComponentTests.swift)
exercise timeline admission, 240-point accessibility-text/RTL growth, independent
slot bounds and minimum native targets. These are not native slider gestures,
full VoiceOver traversal, audio playback or physical-device performance evidence.
Read [native mechanics](../../../substrate/swiftui-playback-slots-and-native-transport.md),
[consumer](../../apps/FoundryCatalog/README.md#playback-gallery),
[shared pattern](../../../../../../notes/patterns/media-timeline-and-transport-admission.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#playback-and-timeline).
Next: engine-backed snapshots and seek completion outside these UI components.

## Verification and code entry

Claim: one native code field and independent verification slots provide reusable
entry UI without importing authentication or delivery policy.

Origin/evidence, 2026-10-09: the twenty-third UI batch adds
[OneTimeCodeField/CodeFormat](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/OneTimeCode/OneTimeCodeField.swift)
and [VerificationCard](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Patterns/VerificationCard/VerificationCard.swift).
Read the controlled Binding setter, optional admission result, iOS content/keyboard
hints and guarded onSubmit. CodeFormat keeps partial ASCII digits as text; nil
rejects an edit and an empty string clears. Length is configured from 1...12.

Example: configure six digits, help/error, feature eligibility and focus; place
the field in the card's content slot with status and native Verify/Resend actions.
Only passive destination/artwork ignores child narration. Larger text stacks
artwork above copy. The code field fixes digit direction without changing the
surrounding logical layout. Caller canonical values cannot contain separators;
only edits/pastes strip ASCII space/tab/CR/LF/hyphen.

Verification, 2026-10-09: both consumers build; 61 iOS app cases, four focused
Android Verification cases and nine Swift/eight Kotlin UI package cases pass.
[Hosted/owner checks](../../../../apps/FoundryCatalog/Tests/VerificationComponentTests.swift)
inspect the actual UITextField hint/number pad and narrow accessibility-text/RTL
bounds, plus challenge/attempt admission.
[Format checks](../../../../packages/FoundryUI/Tests/FoundryUITests/CodeFormatTests.swift)
cover partial/clear codes, leading zeroes, overflow and Unicode rejection.
No iOS paste/menu/AutoFill suggestion or full VoiceOver interaction is established.
Read [native mechanics](../../../substrate/swiftui-code-entry-and-content-hints.md),
[consumer](../../apps/FoundryCatalog/README.md#verification-gallery),
[shared pattern](../../../../../../notes/patterns/challenge-drafts-and-attempt-identity.md)
and [usage](../../../../../../docs/blueprints/ui-components.md#verification-and-code-entry).
Next: observe native code suggestion delivery separately from content hints.
