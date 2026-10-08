# Reusable UI components

Status: Implemented seven batches on SwiftUI and Compose. Other component leaves
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
| SearchField | Controlled text, explicit clearing and native Search submission. Caller supplies title, clear label, filtering and execution. |
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

## Catalog

Studio → Open catalog → Components exposes Actions, Content, Patterns, Controls
and Overlays, plus Display, Feedback, Collections, Context, Layout, Details, Journeys and Activity.
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

## Evidence boundary

Builds establish API and consumer compatibility. Interaction checks must cover
disabled/busy actions, recovery, selection, theme/state retention and native
presentation. A complete VoiceOver/TalkBack, localization, keyboard, large-text
and physical-device audit remains separate. Shared contracts agree on behavior,
not pixel identity or identical native widget implementation.
