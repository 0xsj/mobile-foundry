# Reusable UI components

Status: Implemented four batches on SwiftUI and Compose. Other component leaves
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

## Catalog

Studio → Open catalog → Components exposes Actions, Content, Patterns, Controls
and Overlays, plus Display, Feedback, Collections, Context and Layout.
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

## Evidence boundary

Builds establish API and consumer compatibility. Interaction checks must cover
disabled/busy actions, recovery, selection, theme/state retention and native
presentation. A complete VoiceOver/TalkBack, localization, keyboard, large-text
and physical-device audit remains separate. Shared contracts agree on behavior,
not pixel identity or identical native widget implementation.
