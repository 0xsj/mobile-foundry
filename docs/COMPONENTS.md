# Native component map

The reusable UI catalog follows Bento's family/component split. All initially
reserved UI component leaves now contain implementation. Add variants and
configuration beside a component when a consumer needs them. App routes, stores, repositories and GPU renderers
stay outside this tree.

The roots are:

- [Swift Components](../frontend/swift/packages/FoundryUI/Sources/FoundryUI/Components/)
- [Kotlin components](../frontend/kotlin/project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/)

Swift uses PascalCase folders; Kotlin uses lowercase package folders. These
families stay within the existing UI package/module.

## Implemented controls

| Swift / Kotlin relative folder | Contents |
| --- | --- |
| `Forms/TextField` / `forms/textfield` | Labeled native input, help and error projection. |
| `Forms/SubmitButton` / `forms/submitbutton` | Busy/disabled submit action. |
| `Feedback/Query` / `feedback/query` | Async state rendering and copy. |
| `Feedback/Mutation` / `feedback/mutation` | Write progress, failure and success rendering. |
| `Layout/Surface` / `layout/surface` | Solid/glass surfaces; Kotlin also owns backdrop capture. |
| `Layout/Stack` / `layout/stack` | VerticalStack and HorizontalStack: eager native layouts with theme defaults, explicit spacing and native alignment. |
| `Layout/Divider` / `layout/divider` | SectionDivider / DividerAxis: decorative theme line with horizontal/vertical orientation, inset, thickness and color. |
| `Shells/AppShell` / `shells/appshell` | AppShell: passive background, flexible content and independent navigation slots; host owns routes, scrolling and safe areas. |
| `Navigation/TabBar` / `navigation/tabbar` | TabBar / TabItem: controlled native TabView on Swift; floating Material navigation bar on Kotlin. App owns selection and destinations. |
| `Forms/Button` / `forms/button` | ActionButton: primary, secondary, quiet, destructive, busy/disabled; Swift also exposes ActionButtonStyle. |
| `Forms/SearchField` / `forms/searchfield` | SearchField: controlled search, explicit clearing, native submission and disabled state. |
| `Display/Badge` / `display/badge` | Badge with neutral/info/warning/critical MessageTone. |
| `Display/Card` / `display/card` | Card with content slots, padding and content/floating surface roles. |
| `Display/ListRow` / `display/listrow` | ListRow with leading/trailing accessories and adaptive copy. |
| `Display/EmptyState` / `display/emptystate` | EmptyState with artwork and action slots. |
| `Feedback/Alert` / `feedback/alert` | InlineAlert with message tone and caller actions. |
| `Feedback/Progress` / `feedback/progress` | Labeled native ProgressIndicator. |
| `Navigation/Tabs` / `navigation/tabs` | Controlled Tabs with native segmented/menu or scrollable presentation. |
| `Patterns/PageHeader` / `patterns/pageheader` | PageHeader with copy and action slot. |
| `Patterns/SettingsSection` / `patterns/settingssection` | SettingsSection for native controls, rows and footer copy. |
| `Patterns/SelectionCard` / `patterns/selectioncard` | Single-choice SelectionCard with caller state. |
| `Forms/Toggle` / `forms/toggle` | ToggleField: full-row native switch and optional help. |
| `Forms/Checkbox` / `forms/checkbox` | Checkbox / CheckState: off, on and mixed with caller-owned transitions. |
| `Forms/RadioGroup` / `forms/radiogroup` | RadioGroup: labeled single-choice options. |
| `Forms/Select` / `forms/select` | SelectField: controlled native menu picker. |
| `Forms/Slider` / `forms/slider` | ValueSlider: continuous or stepped bounded values and caller-formatted copy. |
| `Forms/DatePicker` / `forms/datepicker` | DateField: date-only picker with confirm/discard draft. |
| `Overlays/Sheet` / `overlays/sheet` | SheetPanel / Swift sheetPanel modifier: titled native sheet and content slot. |
| `Overlays/Dialog` / `overlays/dialog` | Swift confirmationPrompt / Kotlin ConfirmationDialog: explicit confirmation. |
| `Overlays/Menu` / `overlays/menu` | ActionMenu / MenuAction: enabled/destructive native actions. |
| `Display/Avatar` / `display/avatar` | Avatar / AvatarShape: fallback or passive artwork, clipped shape and accessible identity. |
| `Display/Stat` / `display/stat` | StatCard: supplied value, units, detail and trend copy. |
| `Feedback/Skeleton` / `feedback/skeleton` | Skeleton / SkeletonShape: decorative loading blocks with reduced-motion-aware pulse. |
| `Feedback/Toast` / `feedback/toast` | ToastBanner / ToastAction: caller-presented message with explicit action/dismissal. |
| `Forms/FieldGroup` / `forms/fieldgroup` | FieldGroup: related native fields with group help/error. |
| `Patterns/CollectionToolbar` / `patterns/collectiontoolbar` | CollectionToolbar: summary, filter and action slots. |
| `Navigation/NavLink` / `navigation/navlink` | NavLink: full-row native navigation affordance and caller route/destination. |
| `Overlays/Popover` / `overlays/popover` | PopoverPanel: anchored caller-controlled contextual content. |
| `Overlays/Tooltip` / `overlays/tooltip` | HelpTooltip: explicitly presented short tap help. |
| `Layout/Container` / `layout/container` | ContentContainer: centered readable bounds including configurable insets. |
| `Layout/Grid` / `layout/grid` | AdaptiveGrid: small eager compositions reflowing with width and text size. |
| `Layout/AspectRatio` / `layout/aspectratio` | MediaFrame: ratio-controlled clipped media slot. |
| `Forms/Chip` / `forms/chip` | ChoiceChip: compact caller-controlled selection and disabled state. |
| `Forms/Stepper` / `forms/stepper` | ValueStepper: bounded integer controls, supplied copy and explicit endpoint clamping. |
| `Patterns/Disclosure` / `patterns/disclosure` | DisclosureSection: full-header expansion and interactive content slot. |
| `Display/KeyValue` / `display/keyvalue` | KeyValueRow: adaptive passive label/value/detail copy. |
| `Patterns/ActionBar` / `patterns/actionbar` | ActionBar: floating summary/action composition; host owns placement. |
| `Shells/DetailShell` / `shells/detailshell` | DetailShell: bounded header, flexible body and persistent action regions. |
| `Forms/Password` / `forms/password` | PasswordField / PasswordPurpose: native obscured input and current/new autofill purpose. |
| `Forms/Multiline` / `forms/multiline` | MultilineField: native growing text input with bounded visible lines. |
| `Feedback/ValidationChecklist` / `feedback/validationchecklist` | ValidationChecklist / ValidationItem: passive caller-evaluated requirements. |
| `Navigation/StepIndicator` / `navigation/stepindicator` | StepIndicator / StepItem / StepStatus: passive ordered progress with supplied statuses. |
| `Patterns/OnboardingPage` / `patterns/onboardingpage` | OnboardingPage: scrolling artwork/copy/content and separate actions. |
| `Shells/AuthShell` / `shells/authshell` | AuthShell: readable scrolling header/form/footer layout. |
| `Patterns/SectionHeader` / `patterns/sectionheader` | SectionHeader: compact heading, supporting copy and independent action slot. |
| `Display/AvatarGroup` / `display/avatargroup` | AvatarGroup: bounded passive avatar slots, supplied overflow copy and one accessible summary. |
| `Display/TimelineItem` / `display/timelineitem` | TimelineItem: decorative marker/connector beside supplied update content. |
| `Display/ExpandableText` / `display/expandabletext` | ExpandableText: caller-controlled long-copy disclosure. |
| `Patterns/RefreshContainer` / `patterns/refreshcontainer` | RefreshContainer: native refresh adapter around caller-supplied scrolling content. |
| `Feedback/LoadMore` / `feedback/loadmore` | LoadMoreFooter / LoadMorePhase: explicit idle/loading/failed/exhausted pagination projection. |
| `Forms/IconAction` / `forms/iconaction` | IconAction: localized native action with passive icon slot. |
| `Forms/Rating` / `forms/rating` | RatingField: controlled bounded integer choices and supplied value/option copy. |
| `Navigation/PageIndicator` / `navigation/pageindicator` | PageIndicator: passive small-carousel position and supplied accessible summary. |
| `Layout/Carousel` / `layout/carousel` | Carousel: native horizontal paging with caller-owned selection/PagerState and stable identity. |
| `Patterns/MediaTile` / `patterns/mediatile` | MediaTile: ratio frame, supplied metadata and independent action slot. |
| `Patterns/MediaOverlay` / `patterns/mediaoverlay` | MediaOverlay: decorative artwork, bottom scrim and independent overlay content. |
| `Patterns/ConversationRow` / `patterns/conversationrow` | ConversationRow: one native open action with supplied preview/time/unread copy and passive identity artwork. |
| `Display/MessageBubble` / `display/messagebubble` | MessageBubble / MessageDirection: logical incoming/outgoing placement, selectable text and independent accessories. |
| `Patterns/MessageComposer` / `patterns/messagecomposer` | MessageComposer: caller-owned multiline draft, send eligibility, attachments and action slots. |
| `Patterns/AttachmentRow` / `patterns/attachmentrow` | AttachmentRow: passive file preview and supplied metadata with independent native actions. |
| `Feedback/Transfer` / `feedback/transfer` | TransferStatus / TransferPhase: waiting/transferring/paused/failed/complete projection and caller recovery actions. |
| `Feedback/Typing` / `feedback/typing` | TypingIndicator: supplied readable status and reduced-motion-aware decorative dots. |
| `Layout/Wrap` / `layout/wrap` | WrapLayout: eager rows of intrinsic-width content, native wrapping and logical layout direction. |
| `Forms/RemovableChip` / `forms/removablechip` | RemovableChip: one labeled removal action with native target bounds. |
| `Forms/TokenField` / `forms/tokenfield` | TokenField: controlled single-line draft, supplied token slots and guarded explicit/keyboard add. |
| `Patterns/SelectionRow` / `patterns/selectionrow` | SelectionRow: whole-row choice with supplied state copy and passive artwork. |
| `Patterns/SwipeActionRow` / `patterns/swipeactionrow` | SwipeActionRow / SwipeAction: native logical-edge actions around keyed row content. |
| `Display/TrendBadge` / `display/trendbadge` | TrendBadge / TrendDirection: caller-formatted comparison with explicit direction/tone. |
| `Charts/Legend` / `charts/legend` | LegendItem / LegendMark: passive series label with decorative mark. |
| `Charts/Sparkline` / `charts/sparkline` | Sparkline: native small sample sequence with supplied summary. |
| `Charts/BarChart` / `charts/barchart` | BarChart / ChartBar: labeled nonnegative bars with an explicit common maximum. |
| `Charts/ProgressRing` / `charts/progressring` | ProgressRing: determinate ring, formatted copy and larger-text layout. |
| `Patterns/ChartPanel` / `patterns/chartpanel` | ChartPanel: heading, plot, wrapping legend and independent footer slots. |
| `Forms/TimePicker` / `forms/timepicker` | TimeField / ClockTime: native time draft with explicit confirmation. |
| `Forms/DateRange` / `forms/daterange` | DateRangeField: independent date endpoints and caller validation copy. |
| `Navigation/DayStrip` / `navigation/daystrip` | DayStrip / DayOption: supplied day choices with selected/available semantics. |
| `Patterns/AgendaRow` / `patterns/agendarow` | AgendaRow: readable schedule copy, status and independent action slots. |
| `Navigation/NavigationRail` / `navigation/navigationrail` | DestinationRail / RailDestination: supplied destinations, native selected/disabled actions and passive icon slots. |
| `Navigation/Breadcrumbs` / `navigation/breadcrumbs` | BreadcrumbTrail / BreadcrumbItem: wrapping ancestor actions and passive current-location copy. |
| `Layout/SplitPane` / `layout/splitpane` | SplitPane / PaneMode: bounded primary/detail slots, local-width reflow and caller-owned compact presentation. |
| `Navigation/TableSortHeader` / `navigation/tablesortheader` | TableSortHeader / TableSortOrder: native sort action, supplied order and localized state narration. |
| `Layout/DataTable` / `layout/datatable` | DataTable / DataTableColumn: small eager records with stable IDs, fixed column widths and horizontal scrolling. |
| `Navigation/Pagination` / `navigation/pagination` | PaginationBar: caller-controlled one-based page and explicit previous/next actions. |
| `Patterns/ProfileHeader` / `patterns/profileheader` | ProfileHeader: supplied identity, decorative avatar and independent status/action slots. |
| `Navigation/AccountSwitcher` / `navigation/accountswitcher` | AccountSwitcher / AccountOption: native menu of caller-selected available identities. |
| `Patterns/SessionRow` / `patterns/sessionrow` | SessionRow: supplied device/session/activity copy with independent caller actions. |
| `Patterns/PermissionCard` / `patterns/permissioncard` | PermissionCard: rationale, supplied capability status and caller actions; no OS permission work. |
| `Display/HighlightedText` / `display/highlightedtext` | HighlightedText / HighlightSegment: literal native text runs with supplied emphasis. |
| `Patterns/SearchSuggestionRow` / `patterns/searchsuggestionrow` | SearchSuggestionRow: one enabled/disabled native action with supplied suggestion copy. |
| `Patterns/SearchResultRow` / `patterns/searchresultrow` | SearchResultRow: one open action with passive preview and independent sibling actions. |
| `Display/PriceLabel` / `display/pricelabel` | PriceLabel: caller-formatted current/comparison prices, detail and complete narration. |
| `Patterns/ProductRow` / `patterns/productrow` | ProductRow: passive product artwork/copy/price and independent status/action slots. |
| `Patterns/OrderSummary` / `patterns/ordersummary` | OrderSummary: opaque supplied lines, total copy and independent footer slots. |
| `Forms/InlineAction` / `forms/inlineaction` | InlineActionField: controlled draft and a shared guarded button/keyboard action. |
| `Display/CountBadge` / `display/countbadge` | CountBadge: supplied compact count text and full accessible narration; caller owns capping and zero visibility. |
| `Patterns/NotificationRow` / `patterns/notificationrow` | NotificationRow: one open action, supplied read/time copy, unread emphasis and independent sibling actions. |
| `Display/FeatureRow` / `display/featurerow` | FeatureRow: passive feature/availability/detail copy, decorative inclusion mark and complete supplied narration. |
| `Patterns/PlanCard` / `patterns/plancard` | PlanCard: supplied price/status/feature slots and an explicit selected/disabled native choice button. |
| `Display/UsageMeter` / `display/usagemeter` | UsageMeter: supplied usage/value/detail/narration with an optional bounded decorative bar. |
| `Display/FileTypeMark` / `display/filetypemark` | FileTypeMark: passive caller-supplied format text and complete narration, with no file-type inference. |
| `Patterns/TreeRow` / `patterns/treerow` | TreeRow / TreeDisclosure: native open action, independent branch disclosure, passive artwork and sibling actions with bounded logical indentation. |
| `Patterns/MemberRow` / `patterns/memberrow` | MemberRow: passive supplied identity, decorative avatar and independent access/action slots. |
| `Patterns/ShareLinkCard` / `patterns/sharelinkcard` | ShareLinkCard: supplied selectable link or unavailable copy with independent status/actions; no copying, URL creation or authorization. |
| `Patterns/PlaybackControls` / `patterns/playbackcontrols` | PlaybackControls: independent native previous, play/pause and next actions with supplied labels, state and eligibility. |
| `Patterns/NowPlayingCard` / `patterns/nowplayingcard` | NowPlayingCard: passive media identity/artwork and independent timeline, controls and action slots; no engine or clock. |
| `Forms/OneTimeCode` / `forms/onetimecode` | OneTimeCodeField / CodeFormat: one native field, bounded ASCII digits, admitted separators, native code hint and guarded explicit submission. |
| `Patterns/VerificationCard` / `patterns/verificationcard` | VerificationCard: supplied passive delivery identity and independent content, status and actions; no delivery or auth work. |

Component APIs and their helper types have no Foundry prefix. Existing controls
are LabeledTextField, SubmitButton, Surface, Backdrop, TabBar and TabItem.
See [usage examples](blueprints/ui-components.md#using-the-apis) and
[behavior](../contracts/behavior/ui-components.md). Open Studio → Open catalog →
Components for interactive Actions, Content, Patterns, Controls and Overlays
previews, with Display, Feedback, Collections, Context, Layout, Details, Journeys, Activity, Media, Communication, Editing, Insights, Scheduling, Workspace, Tables, Account, Discovery, Commerce, Notifications, Plans, Files, Sharing, Playback, Verification and Shells examples.
Verification opens local code entry with explicit checking/completion, resend cooldowns and expiry.
Playback opens a manual local timeline with independent transport, seeking, remembered track positions and recovery states.
Sharing opens local membership and link settings with guarded invitations, role changes and confirmed removal.
Files opens a local hierarchy with separate open/disclosure actions, ancestor-aware search, favorites and a native inspector.
Plans opens a local plan picker with billing choices, reviewed application, unavailable options and retained current usage.
Notifications opens a local inbox with grouped updates, unread filtering, independent read/archive actions and latest-archive undo.
Communication opens Design room for a local conversation with a persistent composer.
Editing opens a native library editor with tags, filtering, selection, swipe/menu actions and undo.
Insights opens a local dashboard with period selection, readable charts, empty data and a session goal.
Scheduling opens a local planner with day choices, date ranges, native time input and explicit session application.
Workspace opens a local project browser with compact detail/back, wider panes, collection destinations and breadcrumbs.
Tables opens a local project ledger with sorting, pages, independent cell actions and empty/disabled states.
Account opens a local identity/access center with account-scoped device removal, protected current devices and shared photo-access preview states.
Discovery opens a local search workspace with recent queries, explicit filter drafts, literal emphasis and independent bookmarks.
Commerce opens a local cart with bounded quantities, applied discounts, delivery choices and review snapshots.
Details and removal examples reuse the shared overlay wrappers.

## Directory completion

No reserved UI component leaves remain on either platform. This closes the
original UI directory sketch; backend, data/session/sync and project-template
reservations are separate work. The catalog now has 112 building blocks across
30 families. **Shells** opens a bounded tab preview with spacing customization,
hide/show navigation and retained per-destination marker values.

## Ownership

The working five-tab prototype consumes shared AppShell layout and TabBar
adapters. App composition owns Home, Library, Camera, Studio and Account and opens
the existing catalog from Studio. Reusable controls accept values/content and
callbacks; they never choose service adapters or create feature state owners.
See [shell behavior](../contracts/behavior/app-shell.md),
[construction](blueprints/app-shell.md), and [Styles](../STYLES.md).

Native graphics adapters remain in FoundryGraphics/core/graphics. Image studio,
Product studio and Compositor studio are app examples, not UI components.
Camera capture and photo admission are also app features. Camera and Image
studio reuse one app-owned photo editor per platform; the GPU preview adapter
still receives only admitted pixels and bounded edit values.
Charts now contains small summary visuals. Dense interactive charts and workspace
editors can receive further native slices when a concrete consumer needs them.
