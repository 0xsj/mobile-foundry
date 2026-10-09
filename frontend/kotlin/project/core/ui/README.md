# UI core

Android Compose `QueryContent` renders generic query state with caller copy,
emptiness, a content slot, and callbacks. Its host owns scrolling, observation,
and work. FoundryTheme supplies V1 tokens and maps them into MaterialTheme.
Sources follow styles/tokens, styles/presets, theme, and
components/feedback/query; see [Styles](../../../../../STYLES.md).
Forms/TextField and Forms/SubmitButton wrap native controls; Feedback/Mutation
renders write progress, public failures and caller success content. Features own
validation, focus policy and execution. See [forms behavior](../../../../../contracts/behavior/forms-mutations.md).

See [the contract](../../../../../contracts/behavior/query-ui.md) and
[walkthrough](../../../notes/modules/project/core/ui/README.md).
Build with `make android-build`; `make android-ui-test` includes its instrumented
presentation checks in the catalog app. `make ui-test` checks shared token
fixtures, contrast, and reduction. See [the token contract](../../../../../contracts/behavior/ui-tokens.md).
Open **Tokens**, **Async UI patterns**, **Notes service seam**, or **Forms and mutations**.

FoundryTheme also selects Solid/Glass surface styles. `Surface` keeps
content panels opaque; floating controls sample the host's `Backdrop`
on API 31+, with an opaque fallback. See [surface themes](../../../../../STYLES.md#swappable-surface-themes).

See [the component map](../../../../../docs/COMPONENTS.md) for implemented and reserved
catalog folders. Twenty-three reusable batches add 108 controls and compositions, including
native selection, overlays, contextual help, adaptive layouts, detail compositions, onboarding, activity feeds, media browsing, communication, collection editing and small charts; see [usage](../../../../../docs/blueprints/ui-components.md).
Navigation/TabBar's Kotlin counterpart
is implemented as TabBar: items, selection and a callback over the
floating surface. Route identity and presentation lifetime stay in the app.

Scheduling adds TimeField/ClockTime, DateRangeField, DayStrip/DayOption and
AgendaRow. Picker drafts are temporary; date interpretation, range validation,
day availability and session application stay in the feature. See the
component usage guide and native module walkthrough for the planner example.

Workspace adds DestinationRail, BreadcrumbTrail and SplitPane. Callers provide
stable IDs, path copy, selected state, native slots and compact Back policy.
SplitPane requires bounded height and preserves values only when the host keeps
them above its layout branches. See the usage guide and workspace walkthrough.

Tables adds TableSortHeader/TableSortOrder, DataTable/DataTableColumn and PaginationBar.
The host owns sort/page policy and record actions. Small pages scroll horizontally
at fixed supplied widths; native cell controls remain independent.

Account adds ProfileHeader, AccountSwitcher/AccountOption, SessionRow and
PermissionCard. Identity/capability values and action admission stay in the host;
menus and artwork presentation stay native. Account context and device permission
scope are separate. The local preview never performs authentication or OS access.

Discovery adds HighlightedText/HighlightSegment, SearchSuggestionRow and
SearchResultRow. Literal styling and native action slots receive caller values;
matching, history, filters, bookmarks and navigation stay in the feature.
SearchField now accepts default-enabled state for editing/clear/submission.

Commerce adds PriceLabel, ProductRow, OrderSummary and InlineActionField. The
feature supplies formatted prices, totals, independent product actions and code
admission. Field button/keyboard share eligibility without starting a request.

Notifications adds CountBadge and NotificationRow. Count text and full narration
are supplied; rows expose an open action, unread emphasis and independent native
action slots. Inbox state, grouping, read policy, archive/undo and routes stay in
the app. No notification permission or push integration is introduced.

Plans adds FeatureRow, PlanCard and UsageMeter. Copy, prices, feature availability
and quota fractions are supplied. The plan card has an explicit native choice
button and independent slots; it is not a whole-card tap target. Draft plan,
reviewed choice, current allowance and usage stay with the consumer.

Files adds FileTypeMark and TreeRow with the TreeDisclosure helper. The feature
supplies flattened depth/selection and separate open/disclosure/action eligibility;
the row caps logical indentation and grows for large text. Format artwork is
passive, with complete row narration supplied by the caller. Hierarchy/search,
favorites, provider identity and OS file access remain feature/adapter work.

Sharing adds MemberRow and ShareLinkCard. Member identity/decoration is passive;
access/action slots retain independent native controls. Supplied nonnull link text
is selectable, with unavailable copy for null. Lookup, roles, command admission,
URL/token creation and clipboard work remain feature/platform responsibilities.

PlaybackControls supplies independent previous/play-pause/next native actions
with caller labels, state and eligibility. NowPlayingCard groups passive identity/
artwork and keeps timeline, controls and action lambdas separate. Artwork size
is finite positive and host-bounded; larger text stacks the identity region.
Engine state, loading, timeline scheduling, seek/queue policy and OS playback
effects stay with consumers. Read [usage](../../../../../docs/blueprints/ui-components.md#playback-and-timeline).

OneTimeCodeField/CodeFormat provide a single native field for canonical partial
ASCII codes, bounded length, admitted paste separators and explicit guarded
submission. Compose supplies the native SMS-code content-type hint; actual
autofill delivery is separate evidence. VerificationCard accepts supplied delivery
identity and independent content/status/action lambdas. Challenge identity, clocks,
delivery, resend, validation and auth stay outside shared UI.
Read [usage](../../../../../docs/blueprints/ui-components.md#verification-and-code-entry).
