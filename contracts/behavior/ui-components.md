# Reusable UI components

Status: Implemented twenty-three batches on SwiftUI and Compose. Other component leaves
in the directory map remain reserved.

## Ownership and customization

- Components use descriptive names without a Foundry prefix. Package, app,
  token and theme names keep their existing naming.
- Components receive values, content and callbacks. They never select a service,
  create a feature store, run a request, debounce search or retain business state.
- Native view modifiers / Compose Modifier, content slots, semantic variants
  and the scoped theme supply customization. Avoid a second layout language.
- Use the existing color/type/space/shape roles. Content cards stay opaque;
  floating cards/settings sections follow Solid/Glass and transparency reduction.
- Text can grow and wrap. Actions retain native semantics and at least the
  platform's token minimum touch bounds. Color is supplemented with readable copy.
- No deprecated prefixed component aliases are retained; all local consumers
  and learning-note source links move to the new names.

## First batch

| API | Behavior |
| --- | --- |
| ActionButton / ButtonVariant | Primary, secondary, quiet and destructive. Disabled or busy actions cannot dispatch. Caller supplies busy copy in the label. |
| SearchField | Controlled text, explicit clearing and native Search submission. Default enabled; disabled editing, clear and keyboard submission cannot dispatch. Caller supplies title, clear label, filtering and execution. |
| Badge / MessageTone | Noninteractive neutral/info/warning/critical status with explicit copy. |
| Card | Padded content composition, configurable padding and surface role; no implied action. |
| ListRow | Title, supporting copy and leading/trailing content. No implied route or click. Accessories stack at accessibility text sizes. |
| EmptyState | Title, message, decorative artwork and caller actions. |
| InlineAlert | Persistent feedback and caller recovery/action slot; no automatic dismissal or request. |
| ProgressIndicator | Native labeled progress; nil/null or nonfinite input is indeterminate, finite fractions clamp to 0...1. |
| Tabs | Controlled within-screen choice. Unique, nonempty options must include selection. Native segmented/menu choice on Swift; scrollable native tabs on Android. |
| PageHeader | Title, supporting copy and caller actions. |
| SettingsSection | Header, grouped content, footer and surface role. Caller supplies native controls and separators. |
| SelectionCard | A single-choice option with selected and disabled semantics. Caller coordinates a group; label content cannot contain nested controls. |

Existing APIs are named LabeledTextField, SubmitButton, Surface, SurfaceRole,
Backdrop (Android), TabBar and TabItem (Android). SubmitButton composes
ActionButton, retaining its previous busy-copy API. QueryContent,
MutationFeedback and QueryCopy already have unbranded names.

## Selection controls and overlays

| API | Behavior |
| --- | --- |
| ToggleField | Controlled Boolean switch, optional help, disabled activation and one full-row target. |
| Checkbox / CheckState | Off/on/mixed choice with caller-supplied state copy. Caller decides the next value; mixed does not imply a built-in cycle. |
| RadioGroup | Controlled single choice; unique nonempty options must contain the selection. One full-row action per option. |
| SelectField | Controlled single choice in a native menu. Same option constraints as RadioGroup. Opening/dismissing never selects an option. |
| ValueSlider | Finite value in a finite increasing range. Zero steps is continuous; positive steps count intermediate stops, so 3 gives 5 positions. Caller supplies formatted value copy. |
| DateField | Date-only native picker with a temporary draft. Confirm commits; Cancel/outside dismissal discards. Swift uses Date in the supplied environment calendar/time zone; Compose uses nullable UTC-midnight milliseconds. These are calendar labels, not backend timestamps. |
| SheetPanel / sheetPanel | Titled native sheet with caller content and close copy. Caller controls presentation and supplies any scrolling needed by its content. Dismissal is not confirmation. Swift also exposes the body independently for native presentation customization. |
| confirmationPrompt (Swift) / ConfirmationDialog (Kotlin) | Native alert with explicit confirm/cancel copy and optional destructive emphasis. Only Confirm invokes the supplied effect. |
| ActionMenu / MenuAction | Unique action IDs, enabled and destructive options. Disabled entries never invoke actions; an empty list disables the trigger. Only selecting an enabled action invokes its callback. |

Transient menu expansion and date drafts are component mechanics. The caller
owns committed values, save policy, real operations and presentation flags for
shared sheets/dialogs. Selection labels are passive; do not nest actions in a
whole-row control. Swift checkbox/radio rows use native button semantics with
selected/value descriptions; Android uses native checkbox/radio roles. This is
behavioral parity rather than identical accessibility roles.

## Display, feedback and collection composition

| API | Behavior |
| --- | --- |
| Avatar / AvatarShape | Caller-supplied fallback copy or passive artwork slot, positive finite size and circle/rounded clipping. One accessible identity replaces child artwork semantics. No fetching, decoding, initials generation or implicit tap. |
| StatCard | Preformatted title/value, optional detail/trend copy, MessageTone and content/floating role. No numeric calculation, inferred trend meaning or automatic count animation. |
| Skeleton / SkeletonShape | Decorative rounded/circular loading geometry with positive finite dimensions. Optional native opacity pulse; reduced motion removes the pulse implementation. No fake content semantics or hit targets. The caller supplies a real loading label and decides when to replace it. |
| ToastBanner / ToastAction | Caller-presented message, tone, optional action and explicit dismissal. No internal queue, timeout, retry or removal after action. Action and dismissal are separate callbacks. Android message uses a polite live region; Swift announcement policy remains with the host. |
| FieldGroup | Title, native content slot and group-level help/error; error takes precedence. Does not merge child field focus, validate, submit, or associate errors with individual fields. The caller supplies those policies. |
| CollectionToolbar | Title, caller summary and native filter/action slots. No item model, selected IDs, filter/sort implementation or bulk-action policy. The host supplies responsive slot layout. |

Artwork slots are passive. Mark duplicate/decorative identities hidden at the
host. Keep units and trend meaning in stat copy. Group-level feedback supplements
per-field accessibility and validation; it does not replace them. Loading
placeholders represent unavailable content rather than a successful empty result.
Toasts are presentation, not confirmation that an operation completed.

## Context, navigation and layout

| API | Behavior |
| --- | --- |
| NavLink | A full-row route affordance with title, subtitle and leading slot. Swift wraps native NavigationLink and requires a caller NavigationStack; Kotlin invokes onNavigate. Routes, destination content and back-stack/state policy stay in the app. Native disabled modifiers remain available. |
| PopoverPanel | Caller-controlled anchored content, heading and close action. Native outside/back dismissal updates presentation without choosing an option. Swift uses popover with compact popover adaptation; Kotlin uses a focusable DropdownMenu surface. Use short content: Swift callers supply scrolling for long slots; Android's menu already scrolls vertically. |
| HelpTooltip | Short contextual copy and a visible tap trigger, built on PopoverPanel. Caller controls presence. Explicit/native dismissal; no timer, hover or long-press requirement. This is persistent tap help, not an automatic native hover tooltip. |
| ContentContainer | Centered, configurable readable maximum width, including token page insets. No scroll view, system inset handling or route ownership. Swift accepts inset; Kotlin accepts native PaddingValues. |
| AdaptiveGrid | Eager non-scrolling layout for small compositions, with positive minimum item width, positive maximum columns and nonnegative spacing. Text scaling increases the minimum width; width determines equal-sized columns, and the tallest child determines each row's height. Incomplete rows retain column widths. Caller supplies stable identities and adequate vertical space. Use bounded width and an outer scroll view where needed; large collections need native lazy grids. |
| MediaFrame | Positive finite width/height ratio, default 16:9; fills available width and clips overflowing artwork. Caller supplies crop/fitting and accessible media meaning. Requires enough height for its ratio; it does not load or admit images. |

Layout inputs are native points/dp rather than a separate responsive model.
Grid children remain individually accessible and interactive; layouts do not
combine them into a single focus target. Native RTL placement keeps the first
column at the leading edge. MediaFrame does not add a label for unknown imagery.
Popover content must not contain another unbounded vertically scrolling list
inside Android's menu. Use a sheet/detail page for substantial content.

## Choices and detail composition

| API | Behavior |
| --- | --- |
| ChoiceChip | Compact selected/unselected native action with optional passive leading artwork and disabled state. Caller coordinates single/multiple choices. Swift exposes selected button traits; Kotlin uses Material FilterChip selection semantics. No internal selected set or filtering. |
| ValueStepper | Controlled native integer value within an inclusive range and a positive step. Increase/decrease clamp to endpoints, including a final partial step; bounds/disabled state prevent dispatch. Arithmetic handles native integer extremes. Caller supplies value text and accessible button labels. No hold-to-repeat, parsing or operation execution. |
| DisclosureSection | Controlled expanded flag, title/subtitle, caller state description and interactive content slot. The entire header toggles expansion; content controls remain separate targets. Token motion/reduction govern reveal. Hoist important drafts above the section: collapsed child state has no promised lifetime. No validation or discard policy. |
| KeyValueRow | Passive supplied label/value/detail, combined into one accessible reading unit. Long values wrap; native larger text uses stacked presentation. No implicit copy action, formatting or domain model. |
| ActionBar | Floating Card with optional supplied summary and caller action slot. Native action layout, busy/disabled values and effects remain caller-owned. The host determines placement; the bar alone is not sticky. |
| DetailShell | Bounded header/body/action regions. Header and actions remain outside the flexible body slot. Caller supplies body scrolling, safe areas/system insets, keyboard behavior and routing. No implicit scroll, navigation bar or feature state. |

ValueStepper requires a valid current value inside the range and a positive
step. For 0...5 with step 2, increasing yields 0 → 2 → 4 → 5; decreasing from 5
yields 5 → 3 → 1 → 0. Swift Int and Kotlin Int retain native widths rather than
claiming a shared storage encoding. Business admission belongs at the feature
boundary, beyond these UI preconditions.

Keep header/action content short enough for the viewport and text settings;
substantial content belongs in the caller's scrolling body. A shell must receive
bounded height and must not be placed inside another unbounded vertical scroll.
ActionBar slots preserve independent native controls. Disclosure hides content,
not its caller-owned draft or validation outcome.

## Rich input and journeys

| API | Behavior |
| --- | --- |
| PasswordField | Native obscured input with supplied label/help/error, disabled state, current/new password autofill purpose and keyboard submission policy. Swift takes a binding and focus binding; Kotlin takes caller-owned TextFieldState and native keyboard options/action handler. Error takes precedence over help. No validation, reveal switch, session or credential storage. |
| MultilineField | Native wrapping/growing editor with a positive inclusive visible-line range, default 3...6. The field scrolls beyond the visible limit without discarding text. Caller owns draft, error/help and keyboard/focus policy. Swift takes a binding; Kotlin takes TextFieldState. No character cap or feature command execution. Native hardware/software return-key behavior can differ. |
| ValidationChecklist | Passive uniquely identified ValidationItem values: title, satisfied flag and supplied accessible state description. Empty lists are allowed. Symbols convey supplied state and are decorative to accessibility; rows expose title/state without checkbox actions. No built-in rules or aggregate validity. |
| StepIndicator | Passive ordered uniquely identified StepItem values, each with title, StepStatus and supplied accessible state description, plus summary copy. Current/completed/upcoming are caller projections; zero current steps is allowed. No tap navigation, timer or automatic advancement. |
| OnboardingPage | Bounded page with caller artwork, title/message, content and action slots. Body scrolls; actions sit outside it using DetailShell. Consumer owns page identity, draft lifetime, focus, transitions and navigation. Artwork semantics remain caller-owned. |
| AuthShell | Scrolling header/form/footer slots inside a capped readable container, default maximum width 480 native units including insets. Footer scrolls with the form. No identity/auth behavior, system bar or persistence. Do not nest inside an unbounded vertical scroller. |

Native autofill purpose supplies metadata; these components do not establish an
associated domain, provider session or end-to-end autofill integration. A text
field's display obfuscation does not choose the draft's storage lifetime. Keep
secret state ownership explicit. Kotlin callers may use ephemeral remember for
password TextFieldState and rememberTextFieldState for saveable nonsecret drafts.
Do not recreate a state holder from its current text on every composition.

## Activity and paged collections

| API | Behavior |
| --- | --- |
| SectionHeader | Compact heading, optional supporting copy and independent action slot. Vertical layout allows long labels to grow. No implied navigation or selection. |
| AvatarGroup | Caller-supplied stable identities and passive avatar slots. Positive maximum visible count and finite positive size. Overlapping artwork, optional overflow circle when count exceeds the maximum, and one supplied accessible summary replacing child identities. Caller supplies localized overflow copy; no image loading, member model or implicit action. |
| TimelineItem | Decorative marker and optional connector beside supplied title, timestamp and interactive content slot. The caller owns ordering, timestamp formatting, status meaning and the final connector. Child actions remain independent. |
| ExpandableText | Supplied text, controlled expanded flag, positive collapsed line count (default 3), and caller-provided expand/collapse labels. Native text and a separate toggle action. No overflow measurement or internal expansion lifetime; use for known long copy. Full text remains available to accessibility even when visually collapsed. |
| RefreshContainer | Native refresh behavior around a supported scrolling child with bounded layout. Swift applies refreshable and directly awaits the supplied async action; Kotlin wraps PullToRefreshBox with caller isRefreshing/onRefresh. Native gesture state is local; work, busy admission, errors and cancellation policy remain with the host. No implicit scroll view, request or spinner timer. |
| LoadMoreFooter / LoadMorePhase | Idle/failed show supplied message and explicit action; loading shows native labeled progress; exhausted shows passive supplied copy. Busy/exhausted cannot dispatch. An enabled flag gates available actions. No request on appearance, page cursor, automatic retry or collection mutation. |

The host must keep avatar slots passive and supply a meaningful summary for all
members, including hidden ones. Size/count must fit the host's available width.
Expansion is keyed by caller identity, rather than row position. Refresh and
pagination are separate operations; their coordination is app policy. Native
refresh availability differs by platform/container, so use List on Swift and a
bounded LazyColumn within the Kotlin adapter, not another unbounded vertical
scroll inside the catalog.

## Media browsing and actions

| API | Behavior |
| --- | --- |
| IconAction | Localized native button label, passive icon slot, enabled flag and ButtonVariant. Reuses ActionButton styling and minimum target bounds; icon semantics are replaced by the supplied label. No implied toggling or route. |
| RatingField | Controlled integer value from zero through maximum; maximum is 1...10 (default 5). Zero is unrated. Independent native choice buttons have supplied accessible labels and selected semantics for the current value. Glyphs fill through that value; wrapping/grid layout retains native touch bounds. Title/value copy, enabled state, meaning and explicit clearing remain caller-owned. No averaging, persistence or inferred review policy. |
| PageIndicator | Passive position for 1...20 pages, with valid zero-based selected index and caller-supplied accessible summary. Decorative dots are replaced by one reading unit. No click, implicit page change or announcement policy. Larger collections need another supplied position presentation. |
| Carousel | Native horizontal full-page snapping around bounded content. Swift uses unique Identifiable records and a valid selected-ID binding; Kotlin receives caller PagerState and optional stable page keys. Require nonempty pages and valid native selection; the host handles empty collections and selection admission when records change. Native modifiers/userScrollEnabled control gestures. No autoplay, image loading, coroutine/task or route policy. |
| MediaTile | Card with positive finite ratio frame (default 4:3), title/supporting copy and independent action slot. Native metadata grows with text size. No implied whole-card click or image admission; caller supplies artwork accessibility meaning/fitting. |
| MediaOverlay | Decorative artwork behind a configurable bottom scrim and bottom-leading independent overlay content. Finite scrim opacity in 0...1, default 0.75. Artwork/scrim have no accessible identity; overlay contains meaning and controls. Caller provides bounded layout, clipping and readable action surfaces. No image/editor ownership or tap on the whole media surface. |

Keep overlay artwork passive and use explicit meaning/actions in the overlay.
The default white overlay text and bottom scrim aid contrast but do not establish
contrast for arbitrary content; supplied actions retain their own native theme
surfaces. Do not place controls in slots whose semantics are intentionally hidden.
The carousel page position is native presentation state, not a domain record ID
on every platform. The host maps it to stable records and keeps ratings/favorites
outside lazy page composition. Dynamic record replacement must admit a valid
selection before presenting the new set.

## Communication and attachments

| API | Behavior |
| --- | --- |
| ConversationRow | One native open action with supplied title, preview, timestamp, optional unread copy and enabled state. Leading identity artwork is passive and hidden from accessibility to avoid repeating the name. Copy wraps and metadata stacks at native larger text sizes. No router, timestamp formatting, unread computation or nested controls in leading artwork. |
| MessageBubble / MessageDirection | Incoming/outgoing use logical leading/trailing space and distinct theme surfaces. Supplied author, selectable native message text, metadata and independent accessories. No delivery inference, whole-bubble action, timestamp, message identity or transcript scrolling. |
| MessageComposer | Floating surface with native multiline draft, visible line range 1...5, supplied label/help/send copy, canSend, isSending, enabled and independent attachment/action slots. Disabled or busy input/send cannot dispatch; canSend only gates sending. Swift takes draft/focus bindings and disables its subtree; Compose takes TextFieldState and passes interactive = enabled && !isSending into slots, whose controls must use it. Does not trim, clear, validate, send, focus, scroll or handle keyboard insets on its own. Return remains native multiline editing rather than a send shortcut. |
| AttachmentRow | Supplied filename/detail, bounded 48-unit passive preview and separate action slot. Preview semantics are hidden; meaningful file copy and actions remain readable. No decoding, file access, type/size formatting, whole-row action or transfer operation. |
| TransferStatus / TransferPhase | Waiting/transferring/paused/failed/complete presentation with caller status copy and independent action slot. Transferring shows native progress, using existing finite clamping / nonfinite indeterminate behavior; other phases omit progress. Failed copy uses the critical role. No state transitions, work, cancellation, retry or implicit percentage copy. |
| TypingIndicator | Caller controls presence and supplies readable status. Three decorative dots pulse only when animated and scoped motion permits. One passive accessible summary, no live announcement, timeout, participant model or typing event subscription. |

Place important drafts above route and conditional composition. Keep attachment
admission, transfer identity and stale-completion protection in the feature owner.
Slot controls must remain independently accessible; passive artwork slots cannot
contain actions. The host gives a composer bounded width and space for its
attachments, chooses keyboard/safe-area handling and keeps long transcript and
transfer controls in a scrolling region.

## Selection, tokens and row editing

| API | Behavior |
| --- | --- |
| WrapLayout | Eager non-scrolling composition of variable intrinsic-width children, top-aligned within wrapping rows. Finite nonnegative optional horizontal/vertical spacing defaults to inline tokens. Oversized flexible children receive available width; fixed-size children must fit their host. Swift uses native Layout, Compose uses FlowRow. Host supplies width, sufficient height, child identity and any scrolling. No equal columns or lazy realization. |
| RemovableChip | One native removal action with visible title, caller-supplied accessible remove label including identity, and enabled state. X artwork is passive. Copy wraps and native minimum touch bounds remain. No toggling, selection, deletion or confirmation policy. |
| TokenField | Caller-owned single-line draft, supplied token slots, add copy/eligibility, enabled/busy/help/error and onAdd. Explicit button and native Done submission use the same enabled && !busy && canAdd guard. Swift takes text/focus bindings; Compose takes value/onValueChange. Existing input error projection takes precedence over help. Swift disables its token subtree; Compose slots receive interactivity and must gate controls with it. No splitting, trimming, duplicate/limit rules, automatic clearing or token model. |
| SelectionRow | One whole-row choice with title/supporting copy, controlled selected flag, supplied state description and enabled/onToggle. Swift exposes selected traits; Compose exposes native checkbox toggle state. Leading artwork and selection marker are passive; child controls belong outside the row. No opening, selection collection or aggregation policy. |
| SwipeActionRow / SwipeAction | Up to one enabled action per logical edge, with title, destructive tone and callback. Swift attaches native swipeActions to List content, including native full-swipe behavior; Compose uses native SwipeToDismissBox with a half-width threshold, ephemeral gesture state and reset before dispatch. Disabled actions/edges are unavailable. Compose exposes supplied-label custom accessibility actions; the app also provides visible native menus. Caller keys row identity and owns effects, confirmation, undo and removal animation. No implicit data removal or saved swipe intent. |

WrapLayout is for small token/action groups, not long history. Keep token/selection
values above conditional composition and use stable IDs rather than visible row
positions. Swift swipe actions require a native List context; a ScrollView card
does not gain List gestures. Native swipe physics/presentation can differ while
dispatching the same caller action. Always supply a discoverable alternative to
gesture-only commands. Expected operation failures and stale callback admission
remain feature boundaries.

## Insights and small charts

| API | Behavior |
| --- | --- |
| TrendBadge / TrendDirection | Passive caller-formatted comparison with up/down/steady artwork and explicit MessageTone. Direction never chooses a positive/negative judgment. Readable copy must include the comparison's meaning. No delta calculation, unit/date formatting or animation. |
| LegendItem / LegendMark | Passive supplied series copy with decorative dot/line/square and optional native color override. Text carries meaning; no toggling or hidden-series state. |
| Sparkline | Small eager finite Double sample sequence, equally spaced in input order from physical left to right. Native Canvas draws a piecewise straight path with scoped accent or supplied color. Scale uses the sequence's own minimum/maximum and safe finite normalization. Empty draws no mark, one value draws a centered dot, constant values draw a midline. Positive finite height/lineWidth with height > lineWidth; defaults 64/3 native units. Caller supplies summary and real data alternative. No axes, time-spacing inference, smoothing, selection or fetching. |
| ChartBar / BarChart | Immutable stable ID, label, finite nonnegative numeric value and formatted valueLabel. Small eager horizontal bars share a caller-supplied finite positive maximum; every value must fit it and IDs must be unique. Track/fill use line/accent or supplied color and logical start alignment. Labels/values grow and wrap. Swift exposes one supplied-title element with all category/value pairs; Compose keeps readable merged per-bar rows. No implicit maximum, hidden overflow, sort or axis formatting. |
| ProgressRing | Passive determinate ring with finite fraction clamped to 0...1, caller label/valueLabel and native optional color. Positive finite diameter/thickness with thickness < diameter/2; defaults 120/8. Native scalable text moves below the ring at Swift accessibility sizes or Compose fontScale >= 1.5. One accessible label/value; Compose also exposes bounded progress semantics. No timer, completion judgment, gesture, indeterminate animation or count interpolation. Host must supply enough width or choose smaller compatible bounds. |
| ChartPanel | Card composition with heading, optional supporting copy, native plot, wrapping legend and independent footer slots. Configurable content/floating role; no combined semantics that hide controls. The host supplies bounded plot geometry, accessible summaries, empty-data UI and any actions. No store, chart engine or analytics integration. |

These adapters are intended for small summary visuals. Validate external numbers
at the feature/service boundary before passing them to APIs; violated numeric
or identity preconditions are programmer errors. Values, grouping, units, period
selection and aggregation remain feature-owned. Sparklines convey relative shape,
so comparisons requiring a shared scale need a richer chart. Dense time series,
negative/diverging bars, axes, pan/zoom and data cursors are separate capabilities.
Both platforms use native drawing/layout, not the optional GPU renderer module.

## Catalog

Studio → Open catalog → Components exposes Actions, Content, Patterns, Controls
and Overlays, plus Display, Feedback, Collections, Context, Layout, Details, Journeys, Activity, Media, Communication, Editing and Insights.
Local Dark/Glass previews retain example state. Examples include search → empty
→ clear recovery, busy/disabled actions, status rows, feedback, progress, settings
and single-choice cards. Controls adds mixed aggregate selection, choice groups,
a stepped slider and date confirmation. Overlays adds menu-driven duplication,
sheet dismissal and a reset that requires confirmation. These examples only
change local gallery state. Existing details/removal examples reuse the shared
overlay APIs.

Display demonstrates fallback/custom avatars and supplied stats. Feedback
switches placeholders to content, allows explicit motion reduction and exercises
notice replacement/dismissal/undo/retry using local counters. Notices are cleared
on changing family and are not replayed after Android saved-state restoration.
Controls also includes grouped native editing and caller validation. Collections
filters and sorts three local records, retains selected IDs when filters hide
them, adds only visible IDs with Select visible and clears all with Clear selection.
An empty projection disables Select visible and offers filter reset.

Context demonstrates short help, explicit close, storage-choice admission and a
separate placeholder detail route. Android preserves saveable gallery state
across that route using an app-owned SaveableStateHolder. Presentation flags are
transient and not replayed on restoration. Layout demonstrates narrow/readable
bounds, three keyed cards, 4:3 previews and caller-owned selection. Preview themes
and family changes retain picked values. No destination data or storage is added.

Details adds compact export choices, disabled Print, a bounded quantity, optional
editable note and long passive detail copy. Open delivery preview presents a
separate DetailShell with scrolling cards and a persistent ActionBar. Both views
share app-owned values. Apply preview increments a local example counter only
when copies are nonzero; Reset delivery resets the draft without applying again.
Android retains the draft above route replacement; Swift passes a binding through
the native link. No actual export, delivery or storage operation is implemented.

Journeys demonstrates obscured input, a multiline profile note, passive completion
requirements, an account-form layout and a three-step onboarding preview. Password
and note values remain above family/route replacement. Android's password is
ephemeral; its note and progress survive saved-instance restoration. Swift keeps
both only in the current view's state. The account action requires an enabled,
nonempty password and increments a local counter. The first onboarding step
requires a nonblank note; Previous and Restart retain the draft/preferences.
Finish increments once per explicit completion and disables until back/restart.
These examples do not create accounts, save credentials or persist a profile.

Activity composes a collaborator group and long update, then opens a separate
native lazy/list destination. Its local fixture starts with three rows, loads
three at a time up to nine, and supports Fail next page → Retry page without
losing rows/expansion. Pull-to-refresh and the accessible Refresh updates action
reset to the first page, retain expansion for surviving IDs and increment a
local counter. Refresh and page work cannot overlap. Leaving cancels/invalidates
pending work while retaining loaded rows and expansion above the route; these ephemeral examples do not persist a feed or contact a service.

Media presents three procedural studies in a native carousel with Previous/Next,
passive position, per-study ratings/favorites and enabled controls. MediaTile
shows the selected study's supplied metadata and an explicit Use action that
increments a local preview count. A separate Media preview screen shares values
and native selection above navigation. Android restores primitive values and
PagerState; Swift keeps values in current view state. Clear rating sets only the
selected study to zero. No images are fetched, reviews saved or editing performed.

Communication adds a Design room destination sharing draft, attachment and sent
preview values with the gallery. Swift uses a bottom safe-area inset; Android
uses a flexible scrolling body and app-owned IME padding. Explicit controls step
one fixture file through waiting, transferring (35%), paused, failed and complete.
Failure/retry/cancel preserve draft text. A nonblank text or ready attachment can
be sent; an incomplete attachment blocks send. Send appends one local preview
and then clears the draft/attachment. Disabled controls admit no transitions.
Typing is a toggleable fixture. Inspect increments a local counter. Android
restores nonsecret draft text and primitive values; Swift retains current view
state. No files are read, uploaded or messaged to anyone; no backend is added.

Editing adds a six-record native library editor sharing caller values with its
gallery. Tag admission trims, rejects case-insensitive duplicates and limits the
fixture to six tags; rejected submissions retain draft text, and removal leaves
the draft intact. Filtering retains selected IDs, including hidden records.
Select visible toggles only the current projection. Bulk archive retains selection;
removal clears only removed IDs from it. Undo restores the latest removal batch,
its previous selection and archive state, in original fixture order. A subsequent
removal replaces the single undo snapshot. Dismiss discards that snapshot without
restoring records. Disabled domain callbacks are guarded. Swipe and native menu
actions share those guards. Android restores primitive values/undo intent without
saving gesture state; Swift retains current view state. No real files are deleted,
tags persisted or backend mutations performed.

Insights shares period, empty-data flag, goal count and enabled values between
gallery and dashboard. Week/Month projects local focus samples/categories using
a fixed 200-minute bar maximum and supplied comparison copy. Show focus values
reveals exact samples. Empty focus data leaves the independent 0..20 session goal
intact; disabling controls retains the count, and Reset returns it to 14. Values
survive Back/family/theme changes; Android additionally restores primitive values
and route. No analytics provider, event tracking or data persistence is added.

## Evidence boundary

Builds establish API and consumer compatibility. Interaction checks must cover
disabled/busy actions, recovery, selection, theme/state retention and native
presentation. A complete VoiceOver/TalkBack, localization, keyboard, large-text
and physical-device audit remains separate. Shared contracts agree on behavior,
not pixel identity or identical native widget implementation.

## Dates and agendas

| API | Behavior |
| --- | --- |
| TimeField / ClockTime | A controlled hour (0–23) and minute (0–59), with caller-formatted value copy. Opening copies the current value into a disposable native modal draft. Confirm commits once; cancel/outside dismissal discards. Disabling closes the draft. Swift uses a fixed UTC Gregorian Date bridge, with a native wheel on iOS; Compose uses Material TimeInput and the system 12/24-hour preference. |
| DateRangeField | Two independent DateField controls plus caller help/error. Swift retains the caller environment calendar/time zone; Compose retains nullable UTC-midnight date labels. No automatic ordering, swapping, bounds, inclusive policy or transaction between endpoints. Disabling either date field closes its modal draft. Swift date modals inherit scoped appearance/material. |
| DayStrip / DayOption | A small supplied list with unique IDs, labels, accessible full-date/availability copy, optional detail and per-day/global enabled state. Whole-day selection is caller-controlled and native; selected and disabled states remain distinct. Wraps with available width. Empty lists and absent/unknown selection are allowed without generating or selecting days. |
| AgendaRow | Passive time/title/detail copy, independent status and action slots. No event model, date arithmetic, route, loading, reminder or reservation. Copy can grow vertically; actions keep their native focus and touch targets. |

ClockTime is a wall-clock reading, not an instant, duration or recurrence. The
fixed Swift picker date is an implementation bridge and must not enter a service
payload. Combining a date and reading into an instant requires an explicit
calendar, zone and policy for ambiguous/nonexistent local times in the feature.
The component does not decide that policy.

Scheduling uses October 5–11, 2026 as a fixed UTC fixture. Sunday is unavailable;
range bounds disable supplied days without changing the selected ID. Reversed
dates show an error and block Apply. A separate session draft is copied into one
local planned session only on Apply; editing the draft or showing an empty agenda
does not change that session. Feature values live above family/route/theme
changes; Compose saves their primitive values. Picker presentation/drafts are
ephemeral and never restored as an outstanding commit. No calendar permissions,
notification scheduling, availability service or durable booking is implemented.

## Adaptive workspaces

| API | Behavior |
| --- | --- |
| DestinationRail / RailDestination | Small supplied destination list with unique stable IDs, visible labels, full accessible copy, enabled state and a passive native icon slot. Native selected and disabled semantics remain separate. Unknown/absent selection does not pick a fallback. The host supplies bounded height/width, placement and its compact alternative. Swift uses native buttons; Compose wraps Material NavigationRail/NavigationRailItem without shadowing their names. Rail content scrolls vertically. |
| BreadcrumbTrail / BreadcrumbItem | Supplied unique-ID path. Every ancestor is an enabled/disabled native action; the last item is passive current-location text with caller-localized accessible copy. Decorative separators stay out of narration. Wraps at local width, including long copy. Empty paths render no crumbs. The component does not derive routes, synthesize a root, activate the current crumb or mutate a back stack. |
| SplitPane / PaneMode | Bounded primary/detail slots receive the actual single/split mode. Two panes require local width >= primaryWidth + minimumDetailWidth + token inline spacing, ordinary text size and forceSingle=false. Widths must be positive and finite. Primary stays at the logical leading edge. In one pane, detailPresented selects the detail slot; otherwise primary is shown. In two panes, both slots always render, so the host provides an unselected detail placeholder. Slots own scrolling; values that must survive reflow belong above them. |

SplitPane requires a finite viewport, not an unbounded vertical scroller.
Compose rejects unbounded constraints; Swift's GeometryReader requires its host
to give a useful bounded height. Swift accessibility Dynamic Type sizes and
Compose font scale >= 1.5 prefer one pane. These are native text policies, not
identical numerical size categories. forceSingle can request one pane at any
width. No draggable divider, column persistence, hinge exclusion, animation,
navigation stack, focus restoration or predictive-back transition is supplied.
Use native navigation containers or adaptive navigation scaffolds when a feature
needs those behaviors; SplitPane is a local composition inside an owned route.

The Workspace fixture keeps collection, selected project ID, starred IDs,
compact detail intent and single-pane override above family/route/theme changes.
Compose saves these primitive values. Opening a supplied visible project presents
detail. Back to projects clears compact detail intent and retains selection/stars;
Android system Back closes compact detail before leaving the preview. The outer
Back to components exits the preview. Wide layout can keep the retained project
detail visible alongside its list. Collection changes return to browsing without
repairing a hidden selected ID; the wide detail then shows Choose a project until
that ID is visible again. Breadcrumbs describe the active browse path, including
the project while detail intent is active. Current copy remains passive.
Shared is disabled. No real router, deep links, durable project storage or service
adapter is introduced by this local example.

## Tables and pagination

| API | Behavior |
| --- | --- |
| TableSortHeader / TableSortOrder | One native enabled/disabled action with ascending, descending or absent order. The arrow is decorative; the host supplies localized accessible state copy. Activation emits onSort only; it does not choose a cycle, sort records or reset pages. TableSortOrder avoids colliding with Foundation.SortOrder. |
| DataTable / DataTableColumn | A small eager table of caller-supplied rows and nonempty unique-ID columns with positive finite widths. Row identities must also be unique. Widths include cell padding; the total must be finite. Header/cell slots retain independent native control semantics. All columns share one native horizontal scroller, preserving alignment. Empty rows retain headers. The host supplies a bounded horizontal viewport, vertical scrolling, cell/row meaning, empty copy, data ordering and page size. Kotlin optionally accepts hoisted ScrollState. |
| PaginationBar | Controlled one-based page with totalPages >= 1 and page within that total. Caller-formatted page/previous/next copy. Previous is disabled on the first page; Next on the last; global disabled blocks both. Native actions emit the adjacent valid page without mutating it. Layout wraps with width/text size. No request, loading state, page count inference or default page selection. |

These are eager compositions for small admitted pages. No row virtualization,
sticky header, frozen column, spreadsheet editing, drag reorder, native table
header association or server pagination is provided. The host supplies contextual
accessible cell copy when a visual column heading would otherwise be ambiguous.
Headers and cell actions remain separate controls; the table does not merge an
interactive row into one accessible label. Use native platform table/list APIs
when a feature needs dense data, native grid semantics or large record sets.

The Tables fixture has nine stable-ID projects, three records per page and
Project/Sessions/Status sort actions. Sorting orders the full fixture before
paging and keeps the chosen page. Equal primary values use ascending ID as a
deterministic tie-breaker in either direction. Inspect admits only a visible row,
records its ID and increments a local counter. Empty records is a projection:
it shows no rows and a disabled Page 1 of 1 control, retaining the actual page,
sort and inspected ID. Disabling actions retains all values and blocks sort,
page changes and inspection. Feature values live above family/route/theme
changes; Compose additionally saves primitives and the preview route.
No project service, cursor, total-count reconciliation, durable storage or
inspection navigation is implemented.

## Accounts and access

| API | Behavior |
| --- | --- |
| ProfileHeader | Supplied identity title/detail, decorative avatar, independent status and action slots. Title has native heading semantics. Larger native text places the avatar above copy; actions remain below and keep independent focus. No account lookup, image fetching, profile mutation or route. |
| AccountSwitcher / AccountOption | Native menu of a small unique-ID option list with title, optional detail and per-option enabled state. Selection is caller-owned and may be absent or unknown; the supplied placeholder is shown without selecting a fallback. Current and unavailable options do not dispatch; selected and disabled remain separate native semantics. Global disabled or empty options disable the opener. Kotlin's ephemeral menu closes when disabled or emptied and is not saved as feature intent. No credentials, sign-in, account activation or service selection. |
| SessionRow | Supplied device/session title, detail and activity copy, decorative icon, independent status/actions. No time formatting, session lookup, current-device inference or revoke policy. The caller labels any action with its device context and chooses confirmation and enabled state. |
| PermissionCard | Opaque content card with rationale, decorative icon, supplied status and independent actions. No permission enum is imposed. It never reads OS permission state, requests access or opens settings; the host owns platform capability checks, prompting, limited access, rationale and settings policy. |

Artwork slots in these compositions must be passive. Avatar/device meaning is
in supplied copy; hiding decorative artwork avoids duplicate narration. Use
native text and action slots for any identity or permission detail that must be
announced. These compositions add no authentication or privacy guarantee.

The Account fixture has Personal, Studio team and an unavailable invitation.
Device lists start with the same three fixture devices in each account; removal
keys include account ID and device ID. Current iPhone cannot be removed. Cancel
keeps every device. Confirm validates enabled state, captured account context,
existing device and current-device protection before removing one local record.
Switching account or disabling actions dismisses pending prompts. Profile and
settings actions increment local preview counts only.

Photo-access Ask/Allowed/Denied values are device-scoped in this example and
survive account changes. Ask opens a local confirmation; only Allow preview
changes Ask to Allowed. Denied offers Preview settings without changing its
status. A separate scenario selector can project each supplied state. No actual
photo authorization, credential, session revocation, settings launch or backend
request is performed. Values live above family/route/theme changes; Compose
also restores primitive values and route while discarding outstanding prompts.

## Search and discovery

| API | Behavior |
| --- | --- |
| HighlightedText / HighlightSegment | One native text value from supplied literal runs in order; highlighted runs use semibold/accent or caller overrides. Empty runs/lists are allowed. No query matching, offset interpretation, markup parsing, links or interaction. |
| SearchSuggestionRow | One enabled/disabled native action with supplied title/detail, decorative leading slot and contextual accessible action label. No query/history/ranking ownership. |
| SearchResultRow | One enabled/disabled open action with supplied title/detail, passive leading/preview slots and a complete caller-provided accessible label. Action slots are independent siblings with caller eligibility. No result model, route or bookmark ownership. |

Passive slots cannot contain nested controls. Result open narration replaces
passive child semantics; the host includes all meaningful copy in that label.
Disabling the primary action does not automatically disable sibling actions.
Native text growth, touch targets, modifiers and existing theme roles apply.

The Discovery fixture searches six stable local records by a trimmed,
case-insensitive literal term across title/excerpt/topic. Topic and archived
facets are independently applied. A native filter sheet copies applied values
into a temporary draft on open; Reset affects that draft, Apply commits it,
and any dismissal discards it. Applied chips remove only their facet. Losing
availability closes the sheet; Android restoration discards it.

Saving and opening are independent guarded actions on visible record IDs.
Hidden saved IDs and last-opened identity remain retained. Search submission,
suggestions and opening remember nonblank trimmed queries, case-insensitively
deduplicated to three most recent entries. Clearing search retains history;
Clear recent searches clears it. Disabled actions retain feature values.
Values survive family/route/theme changes; Android also saves primitives and
preview route. Highlighting marks only the first literal excerpt match and
preserves complete text. No service, request, debounce, multilingual ranking,
full-text engine, durable history or actual record destination is implemented.

## Products and order composition

| API | Behavior |
| --- | --- |
| PriceLabel | Supplied current price, optional previous comparison with strike-through, optional detail and complete accessible narration. Native wrapping type, no monetary model, formatting, numeric comparison or discount inference. |
| ProductRow | Passive product title/detail/artwork/price with independent status/action slots. Artwork is decorative. No implied row activation, product lookup, cart state or stock rules. |
| OrderSummary | Opaque card with supplied heading, lines, total title/value/detail and independent footer. No arithmetic, quote, currency, tax or checkout policy. |
| InlineActionField | Controlled single-line draft with one guarded native button/Done callback. Both require enabled && !busy && canSubmit. Busy disables editing/activation; canSubmit alone gates submission. Error takes precedence over help. No parsing, validation, clearing, request or focus change. Swift supplies FocusState; native layout stacks for narrow bounds/larger text. |

Price narration must include meaningful comparison/unit information. Keep product
artwork passive and independent native controls in its action slots. Localization,
amount semantics, inventory and authoritative pricing remain with the host.

The Commerce fixture has Studio kit ($29, previous $39, max three), Pocket
notebook ($12, max five) and unavailable Travel case ($18). It guards admitted
quantities and delivery changes. Ship costs $5 on a nonempty cart; Pick up is free.
Applying a trimmed, case-insensitive STUDIO10 takes 10% off items only. Editing
code does not alter the applied discount; invalid application projects an error
and retains any existing discount. Removing it leaves draft text intact.

Busy code state blocks code editing, apply/remove and review. Global disabled
blocks all cart commands. Clearing quantities preserves delivery/draft/discount,
shows zero delivery/total and disables review; restoring sets kit=1/notebook=0
without resetting other values. Review records total/item count and opens a
temporary native sheet; subsequent cart changes do not rewrite that snapshot.
Loss of review availability dismisses the sheet, and Android recreation does not
restore it. Feature primitives and route live above family/theme changes.
Integer cents and fixed USD formatting serve bounded local fixtures only. No
tax, production rounding/currency model, reservation, service quote, payment or
purchase effect is implemented.

## Notifications and inbox

| API | Behavior |
| --- | --- |
| CountBadge | Passive caller-formatted text in an accent/tint capsule with complete supplied accessible narration. No parsing, integer storage, capping, zero hiding, pluralization, live-region announcement or action. Uses scoped tokens and accepts native view modifiers. |
| NotificationRow | Supplied title, message, time, read-state copy, unread emphasis and complete accessible label. One native enabled/disabled open target contains passive leading artwork/copy; native action slots are independent siblings. Enabled gates only opening. The host supplies action eligibility. Copy grows vertically and logical leading placement follows RTL. No route, receipt mutation, date formatting or permission work. |

The Notifications fixture stores read/archive identities separately from the
All/Unread projection, groups and active-inbox unread count. Opening a known
active ID marks it read, records its identity/count and presents native details.
Filter disappearance alone does not dismiss those details. Archiving the opened
ID or disabling commands does. Unknown, archived or disabled command targets are
rejected in the feature as well as guarded in native controls.

Mark visible as read snapshots current active visible IDs before changing their
read status. Archived IDs stay unchanged. Archive retains read state and replaces
a single latest-archive undo ID. Undo restores only that ID at the original
fixture order and clears the opportunity; it does not rewind unrelated read
commands. Dismissal clears the opportunity without restoring anything. Reset
restores fixture read/archive/filter values, clears the opened ID/undo, and
retains the open-operation count.

Feature filter/read/archive/undo/opened values survive gallery/preview/theme
changes; Android nonsecret primitives also survive saved-state recreation.
Transient detail-sheet presence is not saved. Earlier Android family owners
remain composed while the new inbox route is open. This establishes bounded
fixture behavior, not OS notifications, real time grouping, push tokens,
server receipts, offline merge policy or account synchronization.

## Plans and usage

| API | Behavior |
| --- | --- |
| FeatureRow | Passive title, supplied availability label, optional detail and complete narration. Included chooses a decorative inclusion mark; unavailable meaning is conveyed in copy as well as styling. No feature evaluation or command. Copy grows vertically and logical leading placement follows RTL. |
| PlanCard | Opaque title/subtitle and native price/status/feature slots plus an explicit native choice button. Selected outline and native selected state are separate from enabled state. A selected or disabled choice does not dispatch. Enabled applies to choice only; independent slot actions retain caller eligibility. Host supplies the choice label. No whole-card click, product loading, route or purchase. |
| UsageMeter | Passive supplied title/value/detail/narration and optional decorative native determinate bar. Finite fractions clamp to 0…1; nil/nonfinite omits the bar. This is distinct from ProgressIndicator's indeterminate loading policy. Complete supplied narration replaces passive child semantics, retaining quota/overflow/unmetered meaning. No allowance arithmetic or authorization. |

The Plans fixture separates selected plan/billing choice, reviewed plan/cycle,
current applied plan/cycle and used exports. Starter allows five monthly exports;
Studio fifty. Team is unavailable. Monthly Studio copy is $8 per month; Yearly
copy is $6 per month with $72 billed yearly. These fixed USD labels are examples,
not fetched store products or a pricing domain.

Review is admitted only for an available changed choice while enabled/not pending.
It stores the selected plan/cycle and increments a review count. Apply requires
the same reviewed draft and still-admitted changed choice; it changes the current
plan/cycle and increments an application count once. Disabled, pending, unavailable,
changed/stale draft and already-applied review attempts do not change current
allowance. Native review presentation closes when eligibility becomes false.

Application, billing changes and downgrade preserve usage. Simulated export is
admitted only below the current applied allowance while enabled/not pending.
Reach current limit uses the greater of existing usage and the current allowance;
Reset usage explicitly clears usage. Exhausted and exceeded meaning is supplied
in copy even when the visual bar is clamped. No monthly reset timer is installed.

Feature values persist across gallery/theme/preview changes, and Android nonsecret
primitives survive saved-state recreation. Transient review presence is discarded.
Earlier catalog owners stay composed during the Plans route. This bounded fixture
does not establish receipt verification, real billing/renewal/cancellation,
proration, offline entitlement policy or service-authorized export admission.

## Files and hierarchy

| API | Behavior |
| --- | --- |
| FileTypeMark | Passive supplied format text and complete narration. Semantic caption/monospaced ink, intrinsic width and a minimum passive mark size. No parsing, MIME inference, file reading or command. |
| TreeRow | One native open action with supplied complete label and selected state, passive leading artwork, optional independent TreeDisclosure and sibling actions below. Enabled gates opening only. Selected state does not suppress opening. Copy grows vertically; accessibility text moves leading artwork above copy. Leaves reserve disclosure alignment without adding an action. |
| TreeDisclosure | Supplied expanded flag, action label, state label, independent enabled flag and callback. Native disclosure action retains its role and supplied expanded/collapsed meaning; no recursion, loading or expansion state ownership. |

TreeRow depth must be nonnegative; finite nonnegative indentation and maximum
are required. Defaults are 16 points/dp per level and a 48-point/dp cap. Logical
leading/start padding follows layout direction. Indentation is capped rather than
promising every level has a unique horizontal position. Complete row narration
must include all meaningful passive artwork/type/status information.

The Files fixture contains eight ordered, acyclic records. Default expansion
includes Atlas and Studies, with References collapsed. Without a query its
depth-first projection respects saved expansion. Trimmed title search includes
matches plus their ancestors in stable sibling order, revealing necessary paths
without overwriting saved expansion. Matching folders alone do not include
unmatched descendants. Manual disclosure and Collapse all are disabled during
search; clearing search restores expansion choices. Swift uses localized
case-insensitive title matching; Kotlin uses Locale.ROOT case folding for the
English fixture names. This is no guarantee of multilingual search equivalence.

Open requires an enabled nonempty feature, known available identity and current
visibility; admission stores selected identity and increments open count.
Unavailable Restricted.txt, unknown or hidden IDs do not open. Disclosure also
requires a visible known folder and absent query. Favorite accepts a known
available ID while enabled/nonempty, independently of current visibility.
Inspector eligibility refers to retained selected identity and availability,
rather than its current filtered row. Commands guard admission in the feature
as well as projecting native disabled states.

Collapsing, filtering and entering the empty scenario preserve selection and
favorites. Empty/disabled state closes transient inspector presentation without
clearing identity. Reset browser clears query/selection, restores default
expansion and leaves favorites/open count intact. Search admission requires
enabled state; reset can leave the empty scenario while enabled.

Feature primitives persist across gallery/preview/themes, and Android nonsecret
values survive saved-state recreation. Transient inspector presence is discarded.
Earlier Android family owners remain composed during the Files route. This local
fixture does not establish filesystem permissions, provider identity/loading,
recursive graph safety, import/export, rename/move or persisted file content.

## Sharing and access

| API | Behavior |
| --- | --- |
| MemberRow | Passive supplied name/detail and complete identity narration with decorative avatar and independent access/action slots. Only the passive group replaces child narration; native controls remain separate. No whole-row click, global enabled propagation, role interpretation, lookup or membership command. Accessibility text stacks avatar above growing copy; logical alignment follows RTL. |
| ShareLinkCard | Opaque supplied title, optional detail, nonnil selectable monospaced link or caller unavailable copy, and independent status/actions. Selection is scoped to link text. No URL parsing/creation, browser destination, clipboard side effect or authorization. Nil means absent; empty/non-URL strings remain caller values. |

The Sharing fixture owns known contacts, present member IDs, per-ID Viewer/Editor
roles, invite address/role/error, link choice, eligibility, membership revision and
action counts. Alex is a protected owner with supplied Owner status; Jamie starts
as Editor and Sam as Viewer. River can be invited; Lena is unavailable. This is
one local workspace, not a generic identity/email validation or access-control domain.

Enabled and not pending gates mutations. Invite additionally requires a nonblank
draft. Trim/case-fold maps known example addresses to IDs; unknown, unavailable or
already-present IDs produce supplied error copy without changing membership,
clearing the draft or incrementing invite count. Success appends the ID, applies
invite role, increments revision/count and clears draft/error. Editing the draft
clears its error; selecting invite role changes no member.

Role changes require a known present available nonowner. Selecting the current
role is a no-op. Removal request captures ID and current revision under the same
admission; confirmation rechecks identity, availability, protected status and
revision. Every successful invitation, changed role, removal or reset increments
the workspace-wide revision, so unrelated membership changes can also make a
request stale. Cancellation and unknown/protected/removed/stale/ineligible
requests change nothing. Invalidated dialogs close. The revision is local
presentation/command protection rather than a service concurrency guarantee.

Reset members restores initial IDs/roles, clears error and invalidates requests
while preserving invite draft/role, link choice and action counts. Role/removal
counts increment only for admitted changes; repeated stale requests do not apply.

Link access is Off/Can view/Can edit. Changing it requires mutation eligibility
and does not change membership revision. Off removes displayed link text and
disables the app's Copy workspace link action. Copy additionally requires active
link choice; it invokes an explicit native app clipboard callback for the fixed
example URL and records a request count. Ineligible copy dispatches nothing.
There is no automatic copy on rendering, selection, theme or route changes.
Previous clipboard values are not erased by turning access Off. Native manual
selection remains available for displayed text independently of command eligibility.

Feature values persist across gallery/preview/themes, and Android nonsecret
IDs/role map/drafts/revision/counts survive saved-state recreation. Transient
removal presence is discarded. Earlier Android family owners remain composed
while the Sharing route is open. No actual invitations/messages, token creation/
revocation, server authorization, account-scoped provider persistence or server
version/idempotency admission is established by this fixture.

## Playback and timeline

| API | Behavior |
| --- | --- |
| PlaybackControls | Independent native previous, play/pause and next actions with caller labels, state narration, eligibility and callbacks. isPlaying selects the middle glyph only. Native button roles remain; no implicit selected trait, queue policy, seeking, clock or media effect. Reflows at narrow bounds; logical layout follows RTL while transport glyphs retain temporal direction. |
| NowPlayingCard | Opaque card with supplied title/detail and passive artwork grouped under complete heading narration. Independent timeline, controls and actions keep their native semantics. Larger text stacks artwork above growing copy. Artwork size defaults to 80 points/dp and requires a finite positive value fitting the host. No media loading, engine state interpretation, clock or transport command. |

The Playback fixture has Coastline study (92 seconds), Orbit session (146 seconds)
and unavailable Night walk (75 seconds). It owns selected identity, per-ID finite
positions, favorites, playing/speed/repeat/scenario, enabled/empty flags and command
counts. Initial selection is Coastline at zero, paused, normal speed, no repeat,
Ready and enabled. Play changes only local UI state; manual Advance is the clock.

Transport and seek require enabled, nonempty, Ready and a known available selected
track. Selection requires enabled, nonempty, Ready and a known available different
ID; it pauses without clearing any track's position. Previous/Next accept only
-1/+1, are bounded and skip unavailable entries. Unknown/unavailable/same-ID
selection and out-of-range neighbors are no-ops.

Seek rejects nonfinite input, clamps to 0...duration and counts only a changed
position. Reaching duration pauses even with repeat. Toggling from a paused end
resets position to zero before playing. Advance additionally requires playing,
finite positive seconds and a finite computed target. Speed choices multiply
the supplied delta. Reaching/passing duration pauses at the end without repeat;
repeat stores target modulo duration and keeps playing. Rejected/paused steps
leave state and counts unchanged.

Choosing Buffering/Failed requires enabled/nonempty and pauses. Ready and Retry
do not resume. Retry is admitted only for enabled/nonempty Failed and restores
Ready without clearing position, speed, repeat or favorites. Speed/repeat changes
share transport admission. Favorite accepts a known available ID while enabled/
nonempty, independently of readiness. Disabled or empty transitions pause while
retaining identities, positions and metadata. Empty changes require enabled.

Reset requires enabled and restores first-track selection, empty position map,
paused/normal/no repeat/Ready/nonempty, while retaining favorites and command
counts. Values persist across gallery/preview/themes, and Android local maps/
lists/enums restore through native saved state. Earlier family owners remain
composed during the Playback route. No audio playback, automatic clock, remote
media loading, background session or engine restoration is established here.

## Verification and code entry

| API | Behavior |
| --- | --- |
| CodeFormat / OneTimeCodeField | Configured length 1...12 (default six). Controlled canonical ASCII digit draft, including empty/partial values. Edits remove only ASCII space/tab/CR/LF/hyphen; other characters or excess digits reject the whole edit. Leading zeroes remain. One native text field with platform code hint, native editing/paste, growing help/error and left-to-right digit order. Done requires enabled, complete and caller canSubmit; no auto-submit or persistence. |
| VerificationCard | Opaque supplied title/destination/passive artwork under complete heading narration. Independent content/status/action slots retain native semantics. Larger text stacks artwork above growing copy. Caller supplies bounded decoration and masking; no parsing, delivery, auth, countdown, global enabled propagation or command policy. |

The Verification fixture owns Email/SMS channel, six-digit draft, response choice,
challenge generation, cooldown/lifetime, enabled/verified state, current attempt,
error and counters. It begins at generation one, Email, empty draft, enabled,
unverified, no attempt/error, 30-second cooldown and 120-second lifetime. Code
123456 matches the local response; choosing Service unavailable overrides matching.

Edit requires enabled, unverified, absent request and positive lifetime. It uses
CodeFormat admission and clears error only for a changed admitted draft. Begin
additionally requires exactly six digits, increments attempts and captures ID
(from attempts), generation, channel and code. There is no automatic completion.
Pending blocks editing, another begin, channel/response choice and resend.

Finish requires enabled, unverified, positive lifetime and a request exactly equal
to the captured attempt, matching current generation/channel. Accepted clears
draft/error and marks local verified; incorrect/unavailable clear pending and
set supplied error while retaining draft. Duplicate, canceled, disabled, expired,
reset or superseded results cannot apply. Cancel requires enabled/pending and
discards only that attempt; a later begin has a new attempt ID.

Advance requires enabled, unverified, positive lifetime and positive integer
seconds. Cooldown/lifetime subtract their bounded admitted delta without overflow;
time is manual. At zero lifetime, pending/draft are discarded and expired error
appears. Resend requires enabled, unverified, no pending and zero cooldown; it
increments generation/resends, clears draft/error, restores unverified and resets
cooldown/lifetime. A different channel requires enabled/no pending and creates the
same new challenge without counting resend. Response choice shares that admission.

Disabling invalidates pending while retaining draft/times/choices. Reset requires
enabled and creates a fresh Email challenge with normal response, preserving
attempt/resend counts. Verified state blocks edit/begin/resend/time; changing
channel or Reset starts a new local challenge. Unknown remote challenge behavior
and authentication are outside this fixture.

Feature values remain in memory across route/family/theme changes. Android
restores nonsecret channel/response/generation/times/enabled/counters but discards
code, pending attempt, error and verified presentation. Earlier owners remain
composed during the Verification route. This is no durable challenge clock,
delivery, retry transport, SMS permission, real code validation or auth state.
