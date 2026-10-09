# Reusable UI components

The reusable component batches populate the existing family leaves on both
native platforms. [Behavior](../../contracts/behavior/ui-components.md) defines
the boundaries; [the component map](../COMPONENTS.md) lists source placement.
No new package/module or third-party UI dependency is introduced.

The catalog currently contains twenty-four batches with 112 building blocks across 30
families. Insights adds small native charts, trends, legends and a dashboard
alongside Communication's conversation and Media's paging examples.

## Construction

Swift source stays in FoundryUI/Components; Kotlin mirrors the family/component
folders in core/ui. Component names and filenames have no Foundry prefix.
`ActionButton` and `LabeledTextField` avoid collisions with native Button/TextField.
Component helper types use ButtonVariant, MessageTone, SurfaceRole, TabItem,
CheckState, AvatarShape, SkeletonShape, PasswordPurpose, ValidationItem, StepItem,
StepStatus, LoadMorePhase, MessageDirection, TransferPhase, SwipeAction and explicit action values.
Theme, package and app names retain their existing identities.

Button styles and status tones resolve existing theme roles. Native buttons
provide activation/disabled semantics; SubmitButton delegates to ActionButton.
Card composes Surface. SettingsSection and SelectionCard compose Card. Headers,
rows and feedback expose native content slots rather than feature-specific models.
Keep slot content interactive only where its host is not already a control.

The app owns ComponentCatalogView / ComponentCatalogScreen and all preview
state. A catalog route opens it. Actions, Content, Patterns, Controls, Overlays,
Display, Feedback, Collections, Context, Layout, Details, Journeys, Activity, Media, Communication, Editing, Insights, Scheduling, Workspace, Tables, Account, Discovery, Commerce, Notifications, Plans, Files, Sharing, Playback and Verification organize examples;
Dark/Glass controls scope the theme without recreating the state owner. Android
provides a bounded Backdrop; Swift supplies native surface sampling. Native
sheet and confirmation flags remain app-owned and use shared native wrappers.

## Using the APIs

```swift
// Conceptual: inside a SwiftUI view, with caller-owned draft and save action.
Card(.floating) {
    PageHeader("Workspace", subtitle: "Your next idea")
    LabeledTextField("Name", text: $draft, focus: $nameFocused)
    ActionButton("Save", variant: .primary, action: save)
}
```

```kotlin
// Conceptual: inside Compose, with caller-owned draft and save action.
Card(role = SurfaceRole.FLOATING) {
    PageHeader("Workspace", "Your next idea")
    LabeledTextField("Name", draft, onValueChange = onDraftChange)
    ActionButton(onClick = save) { Text("Save") }
}
```

Use a custom label builder for icons, leading/trailing slots for row accessories,
and padding/modifiers to fit the host. Import shared components explicitly when
a name such as Card or Surface also exists in Material. Native widgets remain
available through their own imports or qualified names.

## Selection and presentation

```swift
// Conceptual: these bindings and the reset operation belong to the feature.
ToggleField("Updates", isOn: $updates, help: "Follow workspace activity.")
RadioGroup("Format", selection: $format, options: formats, label: { $0 })
ValueSlider("Intensity", value: $intensity, steps: 3, valueLabel: intensityCopy)
DateField("Review date", selection: $reviewDate, confirmLabel: "Use date", cancelLabel: "Cancel")
ActionButton("Reset", variant: .destructive) { confirmReset = true }
    .confirmationPrompt("Reset?", message: "Clear the draft.", isPresented: $confirmReset,
                        confirmLabel: "Reset", cancelLabel: "Cancel", destructive: true, onConfirm: reset)
```

```kotlin
// Conceptual: controlled values and callbacks come from the feature.
ToggleField("Updates", updates, onUpdates, help = "Follow workspace activity.")
RadioGroup("Format", formats, format, onFormat, label = { it })
ValueSlider("Intensity", intensity, onIntensity, intensityCopy, steps = 3)
DateField("Review date", reviewDateUtcMillis, onReviewDate, "Use date", "Cancel", "Choose date")
ConfirmationDialog("Reset?", "Clear the draft.", confirmReset, onDismissReset,
    "Reset", "Cancel", reset, destructive = true)
```

Checkbox receives CheckState plus onToggle; an aggregate derives its state from
children and decides whether mixed activation selects all. RadioGroup and
SelectField receive unique options containing the current selection. ValueSlider
uses intermediate-stop counts on both platforms; 3 means 0%, 25%, 50%, 75%, 100%
in the default range. The host decides when editing should trigger an expensive
preview or write; the slider never starts one itself.

DateField creates a fresh draft each time it opens. Swift Date remains in the
host's calendar/time zone; Android's native picker represents a date as UTC
midnight milliseconds. Decode/encode those as calendar fields at a domain
boundary rather than reinterpreting them as local appointment instants. The
Android display deliberately formats in UTC and supports the project's API 24
minimum without requiring java.time APIs from API 26.

Use `.sheetPanel(...) { ... }` on Swift or `SheetPanel(..., content = { ... })`
on Android for titled native presentations. Put large content in a native
ScrollView/scrolling Column. Swift's standalone SheetPanel body also fits a
caller-owned `.sheet` with custom detents. Confirmation is `.confirmationPrompt`
on Swift and ConfirmationDialog on Compose. ActionMenu takes stable MenuAction
IDs and callbacks; a destructive menu item can request confirmation rather than
perform the operation immediately, as the gallery demonstrates.

## Display and feedback

Avatar takes caller fallback copy and an optional artwork slot. Swift's no-art
initializer is constrained to EmptyView; Compose uses a nullable content lambda.
Supply already-admitted imagery with your native crop/scaling modifiers. It
does not fetch images, generate initials or wrap another control. Its accessible
label replaces child artwork semantics; hide it at the host if a neighboring
name already provides the same identity. StatCard accepts formatted strings,
including units and trend meaning, so finance, health and workspace values do
not require a shared numeric model or implicit good/bad classification.

```swift
// Conceptual: caller-owned loading/presentation and recovery decisions.
Avatar("Jordan profile", fallback: "JL", size: 48)
StatCard("Projects", value: "12", trend: "3 added this week", tone: .info)
if loading {
    Text("Loading projects…")
    Skeleton(height: 18)
}
if showNotice {
    ToastBanner("Draft saved", action: ToastAction("Undo", onPerform: undo),
                dismissLabel: "Dismiss", onDismiss: dismissNotice)
}
```

```kotlin
// Conceptual: the host controls presence; the component never starts recovery.
Avatar("Jordan profile", "JL")
StatCard("Projects", "12", trend = "3 added this week", tone = MessageTone.INFO)
if (loading) {
    Text("Loading projects…")
    Skeleton(height = 18.dp)
}
if (showNotice) ToastBanner("Draft saved", "Dismiss", dismissNotice,
    action = ToastAction("Undo", undo))
```

Skeleton is decorative and defaults to a gentle native opacity pulse. Scoped
reduced motion or `animated = false` removes the animation branch. No renderer
or shader is needed. ToastBanner has no timer or queue. If a product adds those,
keep event identity, cancellation, accessible reading time and stale-timeout
protection in its presentation owner. The gallery deliberately keeps notices
until explicit dismissal/replacement, and clears them on changing families.

## Grouping fields and collections

FieldGroup wraps native fields and chooses error over help. Its slot preserves
individual editing/focus targets. Native field validation/semantics and submission
remain feature policy; this component only supplies group-level copy and layout.

CollectionToolbar accepts filter and action builders, with a summary string from
the host. Put native search/picker/toggle controls in its filters slot and native
buttons or an adaptive action layout in its actions slot. The gallery places it
in a floating Card. Its app example owns the records, derives the visible sorted
projection and holds selected IDs separately. Filtering does not silently clear
hidden selections. Select visible adds the projection to selection; Clear
selection clears all IDs. These policies belong to the example, not the toolbar.
For large datasets, supply an appropriate app-owned lazy list and data pipeline.

## Context and routes

```swift
// Conceptual: inside a caller NavigationStack; help is a caller-owned Binding.
NavLink("Storage details", subtitle: "Manage local copies") { StorageDetailView() }
HelpTooltip("About previews", message: "Previews stay on this device.",
            isPresented: $help, closeLabel: "Close help")
PopoverPanel("Storage options", isPresented: $options, closeLabel: "Close options") {
    ActionButton("Options") { options = true }
} content: {
    Text("Keep a local copy for quick access.")
    ActionButton("Use local storage") { chooseLocal(); options = false }
}
```

```kotlin
// Conceptual: the feature supplies navigation, presentation and storage policy.
NavLink("Storage details", onNavigate = openStorage, subtitle = "Manage local copies")
HelpTooltip("About previews", "Previews stay on this device.", help,
    onShow = { help = true }, onDismissRequest = { help = false }, closeLabel = "Close help")
PopoverPanel("Storage options", options, { options = false }, "Close options",
    anchor = { ActionButton({ options = true }) { Text("Options") } }) {
    Text("Keep a local copy for quick access.")
    ActionButton({ chooseLocal(); options = false }) { Text("Use local storage") }
}
```

NavLink does not create a router. Swift's destination builder supplies a native
NavigationLink; Compose's callback can use the app's chosen navigation owner.
The gallery's Android detail route preserves the gallery's saveable state with
SaveableStateHolder. This is an app example, not a navigation dependency in core/ui.
HelpTooltip is short persistent tap help, not a timed hover tooltip. PopoverPanel
uses a native anchored popover on Swift (including compact adaptation) and a
focusable Material menu popup on Android. Close/outside/back dismiss without
performing the content action. Use sheets/details for substantial content; Swift
long slots need caller scrolling, while Android's menu already scrolls.

## Small responsive compositions

```swift
// Conceptual: caller supplies stable records, artwork fitting and selection.
ScrollView {
    ContentContainer(maximumWidth: 720) {
        AdaptiveGrid(minimumItemWidth: 160, maximumColumns: 3) {
            ForEach(items) { item in
                Card {
                    MediaFrame(ratio: 4 / 3) { artwork(item).resizable().scaledToFill() }
                    Text(item.title)
                    ActionButton("Open", action: { open(item.id) })
                }
            }
        }
    }
}
```

```kotlin
// Conceptual: caller supplies scrolling, keyed records and admitted artwork.
Column(Modifier.verticalScroll(rememberScrollState())) {
    ContentContainer(maximumWidth = 720.dp) {
        AdaptiveGrid(minimumItemWidth = 160.dp, maximumColumns = 3) {
            items.forEach { item -> key(item.id) {
                Card {
                    MediaFrame(ratio = 4f / 3f) { Artwork(item, Modifier.fillMaxSize()) }
                    Text(item.title)
                    ActionButton({ open(item.id) }) { Text("Open") }
                }
            } }
        }
    }
}
```

ContentContainer's bound includes padding; use inset: 0 / PaddingValues(0.dp)
when the parent already owns page insets. AdaptiveGrid has no item model, scrolling
or lazy loading. It keeps equal column widths, measures each row's tallest child
and increases minimum item width with native text scaling. Native RTL placement
changes column direction. Stable ForEach/key identity prevents reflow from
recreating a child's state. Supply bounded width and enough vertical space.
Use LazyVGrid/LazyVerticalGrid with appropriate app scroll ownership for long
collections. MediaFrame controls bounds and clipping; native image fitting and
accessibility labels remain caller policy.

## Choices, disclosure and detail screens

```swift
// Conceptual: bindings, copy and callbacks belong to the feature.
ChoiceChip("Compact", selected: format == "Compact") { format = "Compact" }
ValueStepper("Copies", value: $copies, valueLabel: "\(copies) copies",
             decreaseLabel: "Fewer copies", increaseLabel: "More copies", range: 0...5, step: 2)
DisclosureSection("Delivery note", isExpanded: $expanded,
                  stateDescription: expanded ? "Expanded" : "Collapsed") {
    LabeledTextField("Note", text: $note, focus: $noteFocused)
}
KeyValueRow("Destination", value: "Personal collection on this device")
```

```kotlin
// Conceptual: the host controls choice, expansion and the retained draft.
ChoiceChip("Compact", format == "Compact", { format = "Compact" })
ValueStepper("Copies", copies, onCopies, "$copies copies", "Fewer copies", "More copies", range = 0..5, step = 2)
DisclosureSection("Delivery note", expanded, onExpanded,
    stateDescription = if (expanded) "Expanded" else "Collapsed") {
    LabeledTextField("Note", note, onNote)
}
KeyValueRow("Destination", "Personal collection on this device")
```

A chip does not decide whether another chip becomes unselected. ValueStepper
uses independent native buttons with caller labels and disables unavailable
changes. An endpoint can be reached with a partial final step: 0, 2, 4, 5.
It does not create a repeated timer or call a service. DisclosureSection's content
can contain fields/buttons because the header is the only expansion control.
Keep important drafts above that conditional content; collapsing never implies
discarding or committing a draft. KeyValueRow is passive and combines its copy
for accessibility; use a separate explicit action if copying is needed.

```swift
// Conceptual: place at a bounded screen root, outside an existing ScrollView.
DetailShell {
    ContentContainer { PageHeader("Delivery", subtitle: "Review your choices") }
} content: {
    ScrollView { ContentContainer { detailCards } }
} actions: {
    ContentContainer {
        ActionBar(summary: summary) {
            ActionButton("Apply", enabled: canApply, action: apply)
        }
    }
}
```

```kotlin
// Conceptual: body scrolling and app/system insets remain with the screen.
DetailShell(header = {
    ContentContainer { PageHeader("Delivery", "Review your choices") }
}, content = {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentContainer { DetailCards() }
    }
}, actions = {
    ContentContainer {
        ActionBar(summary = summary) {
            ActionButton(apply, enabled = canApply) { Text("Apply") }
        }
    }
})
```

The shell reserves header/action space and gives the body remaining height.
ActionBar alone does not pin itself. Use readable insets once per region, and
keep header/footer copy concise enough for the viewport and text settings. Supply
native scrolling for long body content and app-owned safe-area/keyboard policy.
The example passes the same app-owned draft to its gallery and detail route;
Reset resets only draft values, and Apply changes a local counter. Scoped Swift
detail themes also set native navigation-bar color scheme/background at the app
boundary so the bar remains readable. DetailShell itself never styles a router.

## Verification

Run `make ios-generate`, native builds, iOS app checks and the focused Android
ComponentCatalogTest/forms/shell checks. Inspect the actual gallery on a native
simulator, including the composed content, theme toggles and native sheet.
Run `make notes-check` after learning handoffs and source-link renames.
Record actual outcomes and remaining accessibility/device limits in the native
UI and app walkthroughs.

## Rich input and onboarding

PasswordField uses native obscured entry. The consumer supplies validation and
keyboard submission; PasswordPurpose chooses current/new autofill metadata.
MultilineField bounds visible lines, not stored characters. Newlines remain
editable text; provide an explicit Done action when the host wants to clear focus.
In the iOS Simulator hardware-keyboard check, Return ended field editing and
Option-Return inserted a newline. Keep native keyboard differences in mind rather
than promising one Return behavior across every platform/input device.

```swift
// Conceptual: bindings and focus are owned by the enclosing feature view.
PasswordField("Password", text: $password, error: passwordError,
              purpose: .new, focus: $passwordFocused, submitLabel: .next,
              onSubmit: { passwordFocused = false; noteFocused = true })
MultilineField("Introduction", text: $introduction, help: "A little about you.",
               lines: 3...6, focus: $noteFocused)
```

```kotlin
// Conceptual: inside Compose. Choose each draft's native lifetime deliberately.
val password = remember { TextFieldState() }
val introduction = rememberTextFieldState()
PasswordField("Password", password, error = passwordError, purpose = PasswordPurpose.NEW,
    onKeyboardAction = { focusManager.clearFocus() })
MultilineField("Introduction", introduction, lines = 3..6)
```

Kotlin's state-based fields retain native selection/composition in TextFieldState;
read its text for feature validation and use its edit methods for programmatic
changes. This differs from existing value/callback LabeledTextField. PasswordField
wraps Material's native secure field, rather than manually masking ordinary text.
The gallery keeps its password state above routes with remember, without saving it.
The note uses native save/restore. Swift bindings remain view-local State. These
are draft-lifetime choices, not an authentication/session architecture.

ValidationChecklist receives unique ValidationItem IDs, supplied satisfaction and
localized state copy. It is passive; tapping a requirement does not toggle it.
StepIndicator similarly receives StepItem/StepStatus values and summary copy.
Neither component computes readiness or chooses the next screen. Supply stable
IDs across updates, and expose progress through copy as well as color.

```swift
// Conceptual: the feature projects its own readiness and step status.
ValidationChecklist([
    ValidationItem(id: "note", title: "Introduction added", satisfied: hasNote,
                   stateDescription: hasNote ? "Satisfied" : "Needed")
])
StepIndicator("Step 2 of 3", steps: [
    StepItem(id: "profile", title: "Profile", status: .completed, stateDescription: "Completed"),
    StepItem(id: "preferences", title: "Preferences", status: .current, stateDescription: "Current"),
    StepItem(id: "review", title: "Review", status: .upcoming, stateDescription: "Upcoming")
])
```

AuthShell owns vertical scrolling around readable header/form/footer slots,
including the footer. It is useful for account screens without a routing or auth
runtime. OnboardingPage instead puts actions outside its scrolling artwork/copy/
content using DetailShell. Give it a bounded screen root, keep action content
concise and let the host handle system insets/navigation/keyboard behavior.

```kotlin
// Conceptual: one screen root; draft and callbacks belong to the feature.
AuthShell(header = { PageHeader("Welcome back") }, content = {
    PasswordField("Password", password)
}, footer = {
    ActionButton(onClick = submit, enabled = ready) { Text("Continue") }
})
// In a separate bounded destination:
OnboardingPage("Make it yours", "Build your profile.", artwork = { hero() }, content = {
    MultilineField("Introduction", introduction)
}, actions = {
    ActionBar { ActionButton(onClick = next, enabled = hasNote) { Text("Next") } }
})
```

Open Components → Journeys, then Open account preview or Open onboarding preview.
The three steps share one profile note and update preference. Next is disabled
for a blank initial note; Previous and Restart preserve edits. Finish only changes
an explicit local count. Page identity changes reset scroll placement, while the
important draft stays above that identity. The examples set native destination
appearance and dismiss input focus before moving between steps.

## Activity feeds and pagination

SectionHeader is a smaller section heading with optional actions. AvatarGroup
accepts keyed passive slots: use Avatar for initials/admitted artwork, supply a
summary for all collaborators and a localized overflow label. TimelineItem
supplies only the marker and reading structure; your records provide title/time
and your content slot provides copy, media or actions. ExpandableText exposes a
separate native toggle for known long copy. Keep its flag keyed by record ID.

RefreshContainer adapts native scrolling rather than creating it. Give it a
bounded List on Swift or LazyColumn on Kotlin. Swift's refresh action must await
the real work; spawning a detached task would end the spinner early. Kotlin's
host owns the refreshing flag for the same lifetime. Neither wrapper owns data,
a coroutine/task or failure policy. LoadMoreFooter accepts a presentation phase,
copy and callback; it never loads on appearance. Keep current rows visible while
loading another page or showing a retry.

```swift
// Conceptual: inside a bounded screen with a caller-owned feed model.
RefreshContainer(onRefresh: { await model.refresh() }) {
    List {
        ForEach(model.records) { record in
            TimelineItem(record.title, timestamp: record.displayTime) {
                ExpandableText(record.body, expanded: expansionBinding(record.id),
                               moreLabel: "Read update", lessLabel: "Collapse update")
            }
        }
        LoadMoreFooter(model.pagePhase, message: model.pageMessage, actionLabel: model.pageAction,
                       enabled: !model.refreshing, onLoad: model.requestNextPage)
    }
}
```

```kotlin
// Conceptual: inside a bounded screen; the host coordinates refresh and page jobs.
RefreshContainer(model.refreshing, model::refresh, Modifier.fillMaxSize()) {
    LazyColumn {
        items(model.records, key = { it.id }) { record ->
            TimelineItem(record.title, record.displayTime) {
                ExpandableText(record.body, record.id in expanded, { setExpanded(record.id, it) },
                    moreLabel = "Read update", lessLabel = "Collapse update")
            }
        }
        item { LoadMoreFooter(model.pagePhase, model.pageMessage, model.pageAction,
            model::nextPage, enabled = !model.refreshing) }
    }
}
```

Open Components → Activity → Open activity preview. Expand a row, enable Fail
next page, load, retry, then load to the end. Pull down or use Refresh updates to
reset to three rows. Existing expansion survives for those three identities.
The example serializes work, cancels/invalidates pending work on leaving and
uses a 450 ms local delay. It implements no feed provider, automatic paging or
durable cache. Read [the ownership note](../../notes/patterns/refresh-and-pagination-ownership.md)
and native module walkthroughs for actual checks and limits.

## Media browsing and actions

IconAction wraps the existing native button style with a supplied accessible
label and passive icon. RatingField receives a bounded integer, value copy and
localized option labels; zero is unrated and Clear is an app action. PageIndicator
is passive: give it a valid small count/index and a complete localized summary.

Carousel is a native paging adapter. Swift supplies stable records plus a
selected-ID binding. Kotlin supplies PagerState and stable keys; the host owns
rememberPagerState and any scrollToPage/animateScrollToPage command scope.
Provide bounded width and height, handle empty data before rendering, and keep
per-item values outside lazily created pages. No timer, autoplay or image loader
is built into the component.

```swift
// Conceptual: inside a bounded screen with stable records and caller-owned selected ID.
Carousel(studies, selection: $selectedID) { study in
    MediaOverlay {
        admittedArtwork(study)
    } overlay: {
        VStack(alignment: .leading) {
            Text(study.title)
            IconAction("Favorite study", variant: .secondary, action: { favorite(study.id) }) {
                Image(systemName: "heart")
            }
        }
    }
}.frame(height: 300)
```

```kotlin
// Conceptual: host owns pager state and valid nonempty studies.
val pager = rememberPagerState { studies.size }
Carousel(pager, Modifier.fillMaxWidth().height(300.dp), key = { studies[it].id }) { page ->
    val study = studies[page]
    MediaOverlay(Modifier.fillMaxSize(), artwork = { admittedArtwork(study) }, overlay = {
        Text(study.title)
        IconAction("Favorite study", { favorite(study.id) }, variant = ButtonVariant.SECONDARY) {
            Icon(favoriteIcon, contentDescription = null)
        }
    })
}
```

MediaTile composes a ratio frame, supplied metadata and independent actions.
MediaOverlay hides decorative artwork semantics, adds a bottom scrim and leaves
its overlay controls separate. Keep controls out of the decorative artwork slot.
Use native modifiers for clipping, crop and bounds; the host chooses contrast
and actual image ownership. None of these controls admits or edits photo data.

Open Components → Media → Open media preview. Rate/favorite a study, change page
with swipe or Previous/Next, then return to verify per-item retention. Clear
resets the current rating; Use selected study increments a local counter. Disable
media controls in the gallery to disable gestures and actions. Values stay above
family/route changes; Android additionally restores primitive values/native
PagerState. Read [selection and artwork ownership](../../notes/patterns/media-selection-and-passive-artwork.md)
and native walkthroughs for execution evidence and limits.

## Communication and attachments

ConversationRow is one native open action with supplied preview/time/unread copy.
Its identity artwork must be passive. MessageBubble uses logical incoming/outgoing
placement and keeps attachment/actions independent of selectable message text.
AttachmentRow displays supplied metadata and a passive thumbnail without opening
or loading a file. TransferStatus projects an external operation's phase/copy;
its buttons are supplied by the host and never start work inside the component.

```swift
// Conceptual: bindings, eligibility and the send operation are feature-owned.
MessageComposer("Reply", text: $draft, sendLabel: "Send reply", canSend: canSend,
                isSending: sending, enabled: enabled, focus: $replyFocused,
                onSend: sendReply, attachments: {
    if let attachment {
        AttachmentRow(attachment.name, detail: attachment.formattedDetail,
                      preview: { Image(systemName: "doc") }, actions: {
            IconAction("Remove attachment", action: removeAttachment) { Image(systemName: "xmark") }
        })
    }
}, actions: {
    IconAction("Attach file", action: chooseAttachment) { Image(systemName: "paperclip") }
})
```

```kotlin
// Conceptual: retain TextFieldState above routes; gate independent slot actions.
MessageComposer("Reply", draft, "Send reply", canSend, sendReply,
    isSending = sending, enabled = enabled, attachments = { interactive ->
        attachment?.let { file ->
            AttachmentRow(file.name, file.formattedDetail, preview = { Text("PDF") }, actions = {
                IconAction("Remove attachment", removeAttachment, enabled = interactive) { Text("×") }
            })
        }
    }, actions = { interactive ->
        IconAction("Attach file", chooseAttachment, enabled = interactive) { Text("+") }
    })
```

canSend is supplied admission, not a rule inferred from the string. In a product,
an attachment may allow an empty message or block send while its upload is pending.
The caller decides when successful work clears a draft. Busy/disabled values block
input and sending. Swift disables its slot subtree; Compose provides interactivity
to slot lambdas so native controls can use it. Return is multiline editing. There
is no send-on-return shortcut or keyboard inset policy in the shared composer.

Keep the composer outside the transcript scroller and provide native safe-area/
IME handling at the screen. Keep substantial file recovery controls in scrolling
content, rather than making the pinned composer grow without bounds. Apply native
width/padding modifiers to fit a product's viewport. TypingIndicator receives
presence/copy from the app; animation can be disabled and respects reduced motion.
It adds no subscription, timeout or accessibility announcement.

Open Components → Communication → Design room. Type a reply, add the fixture
attachment, then Start → Fail → Retry → Pause → Resume → Finish. Draft text survives
each transition, and Send preview becomes available when the file is ready. Cancel
or Remove preserves text. Send appends a local message and clears only after that
admission; Inspect sent attachment increments a counter. Back/theme/family changes
retain values. The preview file and transfer are UI fixtures. Read
[draft and transfer ownership](../../notes/patterns/composer-drafts-and-transfer-ownership.md)
for the boundary a real service adapter would need.

## Selection, tokens and row editing

WrapLayout places variable-width children in small wrapping rows; native modifiers
set the host's width. Use AdaptiveGrid when equal columns are the intended layout.
RemovableChip is one removal action. Supply an accessible label that includes
the token's identity, and keep token IDs stable if their labels can change.

TokenField receives a draft, token slots and add eligibility. The Add button and
native Done action share that eligibility. The caller decides parsing, duplicate
rules, limits and whether successful admission clears text. Swift inherits
disabled/busy state into slots; Compose slot controls consume interactivity.

```swift
// Conceptual: the feature owns text, focus, token identity and admission.
TokenField("New tag", text: $draft, addLabel: "Add tag", canAdd: canAdd,
           focus: $tagFocused, onAdd: admitTag) {
    ForEach(tags) { tag in
        RemovableChip(tag.title, removeLabel: "Remove tag \(tag.title)") { removeTag(tag.id) }
    }
}
```

```kotlin
// Conceptual: the feature owns values and gates slot controls.
TokenField("New tag", draft, onDraftChange, "Add tag", canAdd, admitTag) { interactive ->
    tags.forEach { tag -> key(tag.id) {
        RemovableChip(tag.title, "Remove tag ${tag.title}", { removeTag(tag.id) }, enabled = interactive)
    } }
}
```

SelectionRow is one whole-row choice; keep opening and other actions outside it.
It projects selected state and supplied state copy, without owning a selection
collection. SwipeActionRow attaches one action per logical edge. Swift requires a
native List row; Compose uses a native SwipeToDismissBox and resets ephemeral
gesture state before dispatch. Key rows by stable IDs and provide a visible menu
or button alternative. Effects, confirmation and undo remain feature-owned.

Open Components → Editing → Open library editor. Add/remove tags, select two
records and filter to one of them: the bottom summary identifies the hidden
selection. Select visible items changes only that projection; bulk actions affect
the selected IDs, including hidden ones. Archive, remove and Undo preserve record
identity, prior selection and archive flags. Only the latest removal batch can
be undone. These are local preview records; no files or remote records are edited.
Back/theme/family changes retain values; Android also restores primitive state.
Read [selection identity and undo](../../notes/patterns/selection-identity-and-undo.md)
and native walkthroughs for checks and framework limits.

## Insights and small charts

ChartPanel composes a titled Card, plot, wrapping legend and independent footer.
Use role .floating / FLOATING for scoped Solid/Glass surfaces. Keep plots bounded
and leave actions as native controls in the footer. Data, summaries, formatting,
period selection and empty-data decisions belong to the feature.

```swift
// Conceptual: values and formatted copy are admitted by the feature.
ChartPanel("Focus activity", plot: {
    Sparkline(samples, summary: focusSummary, height: 96)
}, legend: {
    LegendItem("Daily focus minutes", mark: .line)
}, footer: {
    TrendBadge(comparisonCopy, direction: .up, tone: .info)
    ActionButton("Inspect values", variant: .secondary, action: inspectValues)
})
```

```kotlin
// Conceptual: values and formatted copy are admitted by the feature.
ChartPanel("Focus activity", plot = {
    Sparkline(samples, focusSummary, height = 96.dp)
}, legend = {
    LegendItem("Daily focus minutes", mark = LegendMark.LINE)
}, footer = {
    TrendBadge(comparisonCopy, TrendDirection.UP, tone = MessageTone.INFO)
    ActionButton(inspectValues, variant = ButtonVariant.SECONDARY) { Text("Inspect values") }
})
```

Sparkline uses finite Double samples with equal spacing. It communicates relative
shape, with its own min/max per sequence; do not infer comparable magnitude from
two differently scaled lines. Empty draws no mark, one sample is a dot and a
constant sequence is a midline. Supply a useful accessible summary and an exact
data alternative. Real uneven timestamps, axes or selection need a fuller chart.

BarChart receives ChartBar IDs, labels, numeric values and formatted valueLabel,
plus an explicit common positive maximum. Values are finite, nonnegative and no
larger than that maximum. Keep the maximum fixed when comparing periods. Both
labels and formatted values remain readable independently of decorative tracks.
Native color overrides and legend marks customize presentation without implied
units or a hidden scale policy.

ProgressRing projects a finite clamped fraction and readable caller value. Select
compatible diameter/thickness bounds for the host; the value moves outside the
ring at larger text. TrendBadge receives direction and an independent tone:
an increase in errors can be critical, while an increase in completed work can
be neutral or informational. The component does not calculate that judgment.

Open Components → Insights → Open insights dashboard. Week/Month changes focus
samples and categories while preserving the independent session goal. Show empty
insight data reveals the empty-data alternative. Increase/decrease/reset changes
the local goal; disabling goal controls retains its value. Show focus values
reveals exact samples. Back/family/theme retain feature values; Android also
restores primitive values and route. Read
[chart meaning and scales](../../notes/patterns/chart-meaning-and-scales.md)
and native walkthroughs for observed checks and limits.

## Dates and agendas

TimeField receives a ClockTime and caller-formatted copy. Its native modal owns
only a temporary draft. Use the feature's hour/minute value after confirmation;
do not persist the Swift Date used to bridge the native picker. DayStrip receives
already generated/localized days with stable IDs, full accessible copy and
availability. Keep the list small; a complete month calendar needs its own slice.

```swift
// Conceptual — bindings and calendar policy belong to the consuming feature.
TimeField("Start time", selection: $time, valueLabel: formattedTime,
          confirmLabel: "Use time", cancelLabel: "Keep time")
DayStrip("Choose a day", options: days, selection: $selectedDay)
DateRangeField("Available dates", start: $startDate, end: $endDate,
               startLabel: "Start date", endLabel: "End date",
               confirmLabel: "Use date", cancelLabel: "Keep date", error: rangeError)
AgendaRow("Design review", timeLabel: "09:30–10:00", detail: "Studio",
          status: { Badge("Confirmed", tone: .info) },
          actions: { ActionButton("Open session", variant: .quiet, action: openSession) })
```

```kotlin
// Conceptual — UTC-midnight dates are calendar labels; callbacks update feature state.
TimeField("Start time", time, onTimeChange, formattedTime, "Use time", "Keep time")
DayStrip("Choose a day", days, selectedDay, onDayChange)
DateRangeField("Available dates", startMillis, endMillis, onStartChange, onEndChange,
    "Start date", "End date", "Use date", "Keep date", "Choose a date", error = rangeError)
AgendaRow("Design review", "09:30–10:00", detail = "Studio",
    status = { Badge("Confirmed", tone = MessageTone.INFO) },
    actions = { ActionButton(openSession, variant = ButtonVariant.QUIET) { Text("Open session") } })
```

DateRangeField composes two independent DateFields; reversed endpoints remain
visible so the feature can explain a validation error. It does not swap dates or
automatically repair selection. Apply should check availability and range policy
again at the command boundary. AgendaRow status/actions are independently
accessible slots; do not put a whole-row button around interactive child actions.

Open Components → Scheduling → Open schedule planner. Pick Tuesday to see an
empty day, edit Session time and choose Keep time or Use time, then Apply session.
Preview reversed dates shows range feedback and disables Apply; Reset dates
restores the week. Sunday stays unavailable. The toggles expose empty/disabled
states, and local values survive family, route and theme changes. This is fixture
state without calendar integration or a booking service. Start with the
[calendar ownership note](../../notes/patterns/calendar-dates-and-clock-readings.md).

## Adaptive workspaces

```swift
// Conceptual: feature-owned values and a bounded route viewport.
SplitPane(detailPresented: showDetail, primaryWidth: 350, minimumDetailWidth: 320,
          primary: { mode in
    ProjectList(selection: $projectID, showRail: mode == .split)
}, detail: { mode in
    ProjectDetail(id: projectID, showBack: mode == .single,
                  onBack: { showDetail = false })
})
```

```kotlin
// Conceptual: called inside a bounded screen, with state above its branches.
SplitPane(showDetail, Modifier.fillMaxSize(), primaryWidth = 350.dp,
    primary = { mode -> ProjectList(projectID, showRail = mode == PaneMode.SPLIT) },
    detail = { mode -> ProjectDetail(projectID, showBack = mode == PaneMode.SINGLE,
        onBack = { showDetail = false }) })
```

The default primary/minimum detail widths are 320 native points/dp; token inline
spacing separates them. Customize the two widths and forceSingle at the host.
Slots receive the actual PaneMode so rail, compact choices and Back affordances
follow one layout decision. Give each pane its own ScrollView/LazyColumn and keep
selection, draft edits and service owners above the slots. The component needs
bounded height; do not put it directly inside a vertical catalog scroller.

DestinationRail receives title, unique-ID RailDestination values, optional
selection, onSelect and a passive icon builder. Apply native frame/Modifier width
and height to bound it. Keep labels short and use a compact alternative at larger
text sizes. Unknown selection stays unselected; disabled items cannot activate.
The rail follows floating Solid/Glass surfaces and native selected semantics.

BreadcrumbTrail receives unique-ID BreadcrumbItem values, a localized
currentAccessibilityLabel and onActivate. The final item is text; every earlier
item is a native enabled/disabled action. The caller builds the path and performs
the route change. Empty paths are allowed; decorative separators stay passive.

Open Components → Workspace → Open adaptive workspace. Open Orbit study, star it,
return to projects and inspect Starred. Widen the viewport to show the destination
rail beside the list and detail; Single pane preview requests the compact flow
even at wider widths. Selection/stars stay above layout/theme/family changes.
Archived hides a selected active project without choosing another one. Shared is
unavailable. This is a local browser, with no service, router or durable storage.
Read [adaptive state ownership](../../notes/patterns/adaptive-layout-and-navigation-state.md).

## Tables and pagination

```swift
// Conceptual: rows, sort state and page policy belong to the feature.
DataTable("Project records", rows: pageRows, columns: columns, header: { column in
    TableSortHeader(column.label, order: orderFor(column.id),
                    accessibilityValue: sortCopy(column.id)) { sortBy(column.id) }
}, cell: { record, column in
    ProjectCell(record: record, columnID: column.id)
})
PaginationBar(page: page, totalPages: totalPages, pageLabel: pageCopy,
              previousLabel: "Previous page", nextLabel: "Next page") { page = $0 }
```

```kotlin
// Conceptual: supplied immutable rows, stable IDs and native content slots.
DataTable("Project records", pageRows, columns, { it.id }, Modifier.fillMaxWidth(),
    header = { column -> TableSortHeader(column.label, orderFor(column.id),
        sortCopy(column.id), { sortBy(column.id) }) },
    cell = { record, column -> ProjectCell(record, column.id) })
PaginationBar(page, totalPages, pageCopy, "Previous page", "Next page", onPageChange)
```

DataTableColumn supplies id, label and a positive finite width (default 160 points/dp,
including padding). The column list must be nonempty; row and column IDs must be
unique. Give DataTable a bounded horizontal viewport and use an outer native
vertical scroller. This is eager rendering for small pages, without virtualization,
sticky headers or frozen columns. Kotlin can hoist its native ScrollState.

Header/cell builders may contain native controls; keep their semantics independent.
Supply contextual cell labels such as “Atlas workspace, sessions: 12” so a scrolled
value still makes sense. DataTable does not infer native table header associations.
Empty rows retain headers; the host composes its EmptyState separately.

TableSortHeader receives an optional TableSortOrder and localized accessible
state copy. The caller decides whether a click toggles ascending/descending,
changes the sort column or resets a page. PaginationBar requires a valid one-based
page and total; it emits the adjacent valid page. Compose loading/error and
unknown-total/cursor policies at the feature boundary, using LoadMoreFooter for
an incremental list when numbered pages do not match the data source.

Open Components → Tables → Open project ledger. Sort Sessions, move between pages
and scroll horizontally to Inspect a visible record. Empty records retains the
actual page while showing disabled Page 1 of 1 controls; turning it off restores
the page. Disabling actions preserves sort/page/inspection values. The local
fixture performs no service request. Read
[sort and page ownership](../../notes/patterns/table-sorting-and-page-ownership.md).

## Accounts and access

```swift
// Conceptual: identity, context, status and actions are supplied by a feature.
ProfileHeader(displayName, detail: identityCopy, avatar: {
    Avatar(displayName, fallback: initials, size: 64)
}, status: { Badge(roleCopy) }, actions: {
    ActionButton("Edit profile", action: openProfile)
})
AccountSwitcher("Switch account", options: accountOptions, selection: selectedID,
                placeholder: "Choose an account", enabled: canSwitch, onSelect: chooseAccount)
SessionRow(deviceName, activityLabel: activityCopy, detail: deviceDetail,
           icon: { Image(systemName: "laptopcomputer") }, status: { Badge(sessionCopy) }, actions: {
    ActionButton("Remove device", variant: .destructive, enabled: canRemove) { confirmRemoval(deviceID) }
})
PermissionCard("Photos", message: rationale, icon: { Image(systemName: "photo") },
               status: { Badge(permissionCopy) }, actions: {
    ActionButton(permissionActionCopy, enabled: canRequest, action: permissionAction)
})
```

```kotlin
// Conceptual: native slots and Modifier keep policy in the caller.
ProfileHeader(displayName, detail = identityCopy, avatar = { Avatar(displayName, initials, size = 64.dp) },
    status = { Badge(roleCopy) }, actions = { ActionButton(openProfile) { Text("Edit profile") } })
AccountSwitcher("Switch account", accountOptions, selectedID, "Choose an account", chooseAccount, enabled = canSwitch)
SessionRow(deviceName, activityCopy, detail = deviceDetail, icon = { DeviceIcon() },
    status = { Badge(sessionCopy) }, actions = {
        ActionButton({ confirmRemoval(deviceID) }, variant = ButtonVariant.DESTRUCTIVE, enabled = canRemove) { Text("Remove device") }
    })
PermissionCard("Photos", rationale, icon = { PhotoIcon() }, status = { Badge(permissionCopy) },
    actions = { ActionButton(permissionAction, enabled = canRequest) { Text(permissionActionCopy) } })
```

AccountOption has a unique stable ID, title, optional detail and enabled flag.
AccountSwitcher displays the supplied selection; unknown/absent IDs show the
placeholder without activating an account. Current and unavailable choices emit
no intent. Native menus show selection separately from availability. Kotlin's
menu visibility is ephemeral and closes when disabled/emptied. Callers control
service/account transitions; this component never chooses credentials.

ProfileHeader uses a native heading, grows copy vertically and stacks avatar/copy
at larger native text sizes. ProfileHeader, SessionRow and PermissionCard artwork
slots are decorative; put meaningful identity or status copy outside them. Their
action slots remain independent native controls. SessionRow never decides whether
the current device can be removed. Label removal with its device context, capture
the original account and device IDs, and validate them again after confirmation.

PermissionCard composes an opaque Card and imposes no permission state enum.
Supply rationale, readable status and native request/recovery actions. Actual
capability queries, OS prompts, limited-access interpretation and settings links
belong in the feature's platform adapter, outside the UI component.

Open Components → Account → Open account center. Cancel then confirm a device
removal, switch to Studio team, return to Personal and compare retained devices.
The current iPhone is protected. Try photo access, cancel/allow the local prompt,
and project Denied to try Preview settings. Permission values are shared across
preview accounts; removals are account-scoped. Dark/Glass and route changes retain
feature values. This is a local UI fixture with no auth/backend or OS permission
work. See [context and capability scope](../../notes/patterns/account-context-and-device-capabilities.md).

## Search and discovery

```swift
// Conceptual: search, result identity and commands belong to the feature.
SearchField("Search library", text: $query, clearLabel: "Clear search",
            enabled: canSearch, onSubmit: submitSearch)
SearchSuggestionRow(suggestion, accessibilityLabel: suggestionActionCopy,
                    enabled: canSearch, onUse: useSuggestion)
SearchResultRow(title, detail: categoryCopy, accessibilityLabel: openCopy,
                enabled: canOpen, onOpen: openResult, leading: { Image(systemName: "doc.text") },
                preview: { HighlightedText(excerptRuns) }, actions: {
    ActionButton(bookmarkCopy, enabled: canBookmark, action: toggleBookmark)
})
```

```kotlin
// Conceptual: admitted runs and independent native actions are supplied.
SearchField("Search library", query, onQuery, "Clear search", enabled = canSearch, onSubmit = submitSearch)
SearchSuggestionRow(suggestion, suggestionActionCopy, useSuggestion, enabled = canSearch)
SearchResultRow(title, openCopy, openResult, detail = categoryCopy, enabled = canOpen,
    preview = { HighlightedText(excerptRuns) }, actions = {
        ActionButton(toggleBookmark, enabled = canBookmark) { Text(bookmarkCopy) }
    })
```

HighlightSegment contains text and a Boolean emphasis flag. HighlightedText
appends those literal runs into one native text value in order. Empty runs/lists
are allowed. It adds no matching, Markdown/HTML parsing, links, selection or
interaction. Emphasis uses existing accent and semibold type; override native
font/style and emphasis color when a consumer needs another treatment. The
feature supplies localized complete copy and computes any matches.

SearchSuggestionRow emits one supplied action and takes decorative leading
artwork plus optional detail. SearchResultRow puts passive leading/preview slots
inside its native open action, with native action slots as siblings below.
Supply the complete accessible open label, including meaningful excerpt/status
copy, because passive children are hidden from narration. Never put another
button/link in a passive slot. Row enabled controls its primary action; the host
chooses independent action eligibility. Neither component owns queries, history,
ranking, routes or bookmarks. Native frame/Modifier and slots customize placement.

Open Components → Discovery → Open search workspace. Try motion, Save a result,
then open it: only opening increments Records opened. Filters starts from applied
values; changing Topic/Include archived and choosing Discard leaves results
unchanged. Reset filter draft resets only the draft. Apply commits it, while
removing an applied chip changes only its facet. Search submission/opening/
suggestions remember trimmed nonblank queries, deduplicated case-insensitively
and bounded to three. Clearing text preserves recent queries; Clear recent
searches removes them. Hidden saved IDs and the last-opened identity survive
filtering, empty results and disabling actions. Feature values stay above family,
route and theme changes; Android restores primitives while discarding an open
filter sheet. This small eager fixture styles its first literal excerpt match;
it supplies no backend, debounce, full-text ranking or durable history. Read
[search projections and drafts](../../notes/patterns/search-projection-and-filter-drafts.md).

## Products and order composition

```swift
// Conceptual: admitted price copy and action eligibility come from the feature.
PriceLabel(currentPrice, comparison: previousPrice, detail: unitCopy, accessibilityLabel: completePriceCopy)
ProductRow(title, detail: description, artwork: { ProductArtwork() },
           price: { PriceLabel(currentPrice, accessibilityLabel: completePriceCopy) },
           status: { Badge(stockCopy) }, actions: { QuantityControls() })
InlineActionField("Promo code", text: $draft, actionLabel: "Apply code", canSubmit: canApply,
                  enabled: canEdit, isBusy: applying, error: codeError, focus: $focused, onSubmit: applyCode)
OrderSummary("Order summary", totalTitle: "Total", totalValue: totalCopy, lines: {
    KeyValueRow("Items", value: subtotalCopy)
}, footer: { ActionButton("Review", enabled: canReview, action: review) })
```

```kotlin
// Conceptual: slots contain native views; amounts and admission stay caller-owned.
PriceLabel(currentPrice, completePriceCopy, comparison = previousPrice, detail = unitCopy)
ProductRow(title, detail = description, artwork = { ProductArtwork() },
    price = { PriceLabel(currentPrice, completePriceCopy) }, status = { Badge(stockCopy) },
    actions = { QuantityControls() })
InlineActionField("Promo code", draft, onDraft, "Apply code", canApply, applyCode,
    enabled = canEdit, isBusy = applying, error = codeError)
OrderSummary("Order summary", "Total", totalCopy, lines = { KeyValueRow("Items", subtotalCopy) },
    footer = { ActionButton(review, enabled = canReview) { Text("Review") } })
```

PriceLabel accepts strings, not a numeric money/currency type. Supply the complete
accessible current/previous/unit meaning; a strike-through alone cannot carry
that distinction. It does not format money, infer discounts, compare values or
generate a marketing claim. ProductRow is passive copy/artwork/price with separate
status/action slots. Artwork is decorative; meaningful product variants and
availability belong in readable copy. Price stays below product identity so it
can wrap. Native actions remain independent; there is no implicit row tap/cart.

OrderSummary composes opaque Card, SectionHeader, supplied lines, a native divider,
KeyValueRow total copy and independent footer. Taxes, currency, delivery rules,
rounding, validation and authoritative quotes belong to a feature/service.
Use native frame/Modifier and slots for extra disclosures and native controls.

InlineActionField combines controlled native input, field feedback and ActionButton. Button
and native Done action share enabled, busy and canSubmit admission. Ineligible
submission leaves the caller's text intact; empty/invalid/duplicate policy is
supplied. Busy disables editing and activation, while canSubmit=false alone
leaves editing enabled. Error copy takes precedence over help. It never clears
text, parses a code, starts work or chooses focus changes. Swift requires a
caller FocusState binding and chooses horizontal/stacked layouts with
ViewThatFits/larger text. Kotlin stacks below 400 dp or fontScale >= 1.5; native
keyboard options may customize input while this component keeps the Done action.
These are native layout policies with shared behavior, rather than identical
breakpoints. Test the actual localized action label in its host viewport.

Open Components → Commerce → Open cart preview. Studio kit is limited to three,
Pocket notebook to five, and Travel case is unavailable. Adjust quantities and
Ship/Pick up, then enter STUDIO10 and apply by button or keyboard. Merely editing
the draft does not apply a discount; invalid attempts leave any applied discount
intact. Remove discount changes its applied state. Busy code field blocks code
editing/application and review while retaining cart choices.

Clear cart retains delivery/code/applied discount, shows a zero total and disables
review. Restore cart restores sample quantities only. Preview review records
the current item count/total and opens a disposable native sheet; later edits
recalculate the current total without rewriting that review snapshot. Values
survive family/route/theme changes; Android restores primitives and route, while
discarding review presentation. This bounded fixture uses integer cents and fixed
USD copy. It has no tax, currency conversion, inventory reservation, payment or
backend integration. Read [price and command ownership](../../notes/patterns/price-copy-and-committed-cart-values.md).

## Notifications and inbox

CountBadge accepts supplied display text and a complete accessible label. Use
the same API for unread messages, pending tasks or cart counts. The host chooses
zero visibility, locale, plural forms and any cap; `99+` does not imply a
numeric limit inside the component.

NotificationRow supplies title/message/time/read copy, unread emphasis and one
native open action. Its leading slot is passive artwork and its actions are
independent native siblings. The row's enabled flag gates opening only; pass
separate eligibility to each supplied action. The complete label must include
all meaningful copy and state. Copy grows vertically at narrow widths and larger
text sizes, without compressing independent action targets.

```swift
// Conceptual: the host supplies localized meaning and guards both commands.
CountBadge("99+", accessibilityLabel: "128 unread updates")
NotificationRow(title, message: message, timeLabel: timeCopy, stateLabel: readCopy,
                isUnread: unread, accessibilityLabel: completeCopy, enabled: canOpen,
                onOpen: open, leading: { Image(systemName: "bell") }, actions: {
    ActionButton("Mark read", enabled: canMarkRead, action: markRead)
})
```

```kotlin
// Conceptual: actions are separate from the open target.
CountBadge("99+", "128 unread updates")
NotificationRow(title, message, timeCopy, readCopy, unread, completeCopy, open,
    enabled = canOpen, actions = {
        ActionButton(markRead, enabled = canMarkRead) { Text("Mark read") }
    })
```

Open Components → Notifications → Open inbox preview. Today/Earlier groups are
assembled with existing SectionHeader/Card; no new grouping wrapper is needed.
Unread count covers the active inbox, while visible count follows All/Unread.
Opening marks the update read and shows details even when it leaves Unread.
Mark visible as read affects the current active projection; archived updates
are excluded. Archive preserves read state, and Undo restores only the latest
archived ID at its fixture position. Reset restores the fixtures and keeps the
open-operation counter. Detail sheets close on archive or disabled availability;
saved-state restoration discards sheet presentation while retaining feature values.
The Android route also preserves earlier family owners, including cart quantities.

These are local UI fixtures. Account receipt scope, real dates/relative times,
push permission/delivery, pagination and synchronized read/archive mutations
need feature/platform services. Read [the identity notes](../../notes/patterns/inbox-projection-and-read-identity.md)
for the ownership reasoning and native source/test links.

## Plans and usage

FeatureRow is passive feature/availability/detail copy with a decorative mark
and complete supplied narration. It can describe subscription benefits, account
capabilities or product options; the included flag is visual availability, not
an authorization check.

PlanCard supplies an opaque structured card with title/subtitle, native
price/status/feature slots and one explicit choice button. The whole card is not
clickable, so help/status actions can remain independent. Selected choices have
an accent outline and native selected semantics; their choice button is disabled.
Enabled controls that button only, leaving each slot's own controls with their
own eligibility. The caller supplies its localized selected/unavailable action label.

UsageMeter accepts supplied value/detail/narration plus an optional visual
fraction. Finite fractions clamp to 0…1. Nil/nonfinite hides the bar rather than
showing indeterminate loading; callers can describe unlimited or unknown usage
in their copy. Passive child copy/bar semantics are replaced by complete supplied
meaning. Do not infer allowance, remaining quantity or permission from the fraction.

```swift
// Conceptual: feature meaning, prices, selection and commands belong to the host.
PlanCard("Studio", selected: selected, actionLabel: selected ? "Selected Studio" : "Choose Studio",
         enabled: canChoose, onSelect: chooseStudio, price: {
    PriceLabel("€6 / month", detail: "€72 billed yearly", accessibilityLabel: fullPriceCopy)
}, status: { Badge("More room") }, features: {
    FeatureRow("Offline drafts", stateLabel: "Included", included: true,
               accessibilityLabel: "Studio, offline drafts included")
})
UsageMeter("Exports", value: "8 of 5", detail: "Allowance exceeded", fraction: 1.6,
           accessibilityLabel: "Exports, 8 of 5, allowance exceeded")
```

```kotlin
// Conceptual: the card emits choice intent; the meter receives complete usage meaning.
PlanCard("Studio", selected, if (selected) "Selected Studio" else "Choose Studio",
    chooseStudio, enabled = canChoose,
    price = { PriceLabel("€6 / month", fullPriceCopy, detail = "€72 billed yearly") },
    features = { FeatureRow("Offline drafts", "Included", true, "Studio, offline drafts included") })
UsageMeter("Exports", "8 of 5", "Exports, 8 of 5, allowance exceeded",
    detail = "Allowance exceeded", fraction = 1.6f)
```

Open Components → Plans → Open plans preview. Starter allows five monthly
exports, Studio fifty; Team is an unavailable fixture. Studio shows $8/month
with Monthly or $6/month and $72 billed yearly with Yearly. Selecting a plan or
cycle only changes a draft. Review records that plan/cycle; Apply preview change
rechecks the same draft, enabled/not pending and availability before applying it.
No actual billing takes place.

Usage remains unchanged across application, billing-choice changes and downgrade.
Reach current limit cannot lower existing usage. A Studio-to-Starter downgrade
after fifty exports therefore shows 50 of 5 and an exceeded status; the bar stays
full. Simulate export stops at the current applied allowance. Reset usage is
explicit. Pending/disabled states reject changes and close an outstanding review;
changing the reviewed draft also closes it. Feature values survive family/theme/
route changes and Android saved-state recreation, while review-sheet presence
is transient. The new route preserves earlier catalog owners, including the cart.

See [plan/allowance ownership](../../notes/patterns/plan-choice-and-applied-allowance.md)
for module/source links. Store product loading, price localization, quote identity,
renewal/receipt policy and real entitlement validation need service/platform adapters.

## Files and hierarchy

FileTypeMark projects caller-supplied format text and full narration. It does not
parse names, infer MIME types, read files or request access. TreeRow receives a
flattened row with nonnegative depth, complete open narration, selected state and
independent eligibility. Optional TreeDisclosure supplies a branch's expanded
state, localized action/state labels and callback. The row does not own a tree.

```swift
// Conceptual: values and commands belong to the feature's flattened projection.
TreeRow("References", subtitle: "2 items", accessibilityLabel: "Open References, folder, 2 items",
        depth: 1, selected: selected, enabled: canOpen,
        disclosure: TreeDisclosure(expanded: expanded, actionLabel: disclosureCopy,
                                   stateLabel: expansionCopy, enabled: canDisclose, onToggle: toggle),
        onOpen: open, leading: {
    FileTypeMark("DIR", accessibilityLabel: "Folder")
}, actions: { IconAction(favoriteCopy, enabled: canFavorite, action: favorite) { Image(systemName: "star") } })
```

```kotlin
// Conceptual: values and commands belong to the feature's flattened projection.
TreeRow("References", "Open References, folder, 2 items", open,
    subtitle = "2 items", depth = 1, selected = selected, enabled = canOpen,
    disclosure = TreeDisclosure(expanded, disclosureCopy, expansionCopy, canDisclose, toggle),
    leading = { FileTypeMark("DIR", "Folder") },
    actions = { IconAction(favoriteCopy, favorite, enabled = canFavorite) { Text("☆") } })
```

Opening and disclosure are separate native actions; optional actions below the
row retain their own eligibility. Enabled gates opening only. Leading content
is passive and hidden from row narration, so include its meaningful type/status
in the supplied full label. Selected traits do not change expansion. Leaves omit
disclosure while keeping alignment. Copy grows vertically; accessibility text
moves artwork above copy. Logical indentation defaults to 16 points/dp per depth,
capped at 48; step/maximum must be finite nonnegative and depth nonnegative.

Open Components → Files → Open files preview. Expand/collapse folders or open a
file's native inspector. Favorites are independent actions. Search is trimmed
and case-insensitive for these English fixtures, includes matching titles plus
ancestors, reveals paths and disables manual disclosure. It does not rewrite
saved expansion. Matching a folder does not automatically include every child.
Clear search restores the saved view.

Last-opened identity and favorites survive collapsing, filtering, themes/routes
and the empty scenario; Android primitives also survive saved-state recreation.
Open requires a known available visible identity and enabled nonempty feature.
The inspector/favorite command can still refer to a retained selected item hidden
by filtering. Empty/disabled state closes the inspector; recreation discards its
transient presence. Reset browser clears query/selection and restores initial
expansion while retaining favorites and open count. Restricted.txt is unavailable.
The eight records are an acyclic local fixture, with no OS filesystem/provider
access, real permission evaluation, import, loading, rename or decoding.

## Sharing and access

MemberRow composes passive supplied identity/decoration with independent access
and action slots. Complete identity narration replaces only the passive group.
No whole-row action or global enabled flag is inferred; each role/menu/removal
control receives caller eligibility. Avatar content must be passive and add no
unique meaning beyond supplied identity. Large text stacks avatar above copy,
which grows vertically and follows logical layout direction.

ShareLinkCard displays a supplied nonnil link as selectable native monospaced
text, or supplied unavailable copy for nil. Header/detail/status/actions stay
independent. It does not create URLs, infer a browser destination, copy text or
grant access. Native selection follows the installed platform: iOS 26 and earlier
offer whole-Text context actions, while Compose uses SelectionContainer.

```swift
// Conceptual: identity, role choices and commands belong to the feature.
MemberRow(name, detail: address, accessibilityLabel: identityCopy,
          avatar: { avatarView }, access: { rolePicker }, actions: {
    ActionButton(removeCopy, variant: .destructive, enabled: canRemove, action: requestRemoval)
})
ShareLinkCard("Workspace link", link: activeLink, unavailableLabel: "Link access is off",
              detail: accessCopy, status: { statusView }, actions: {
    ActionButton("Copy link", enabled: canCopy, action: copyLink)
})
```

```kotlin
// Conceptual: identity, role choices and commands belong to the feature.
MemberRow(name, identityCopy, detail = address, avatar = { avatarView() },
    access = { rolePicker() }, actions = {
        ActionButton(requestRemoval, enabled = canRemove) { Text(removeCopy) }
    })
ShareLinkCard("Workspace link", activeLink, "Link access is off", detail = accessCopy,
    status = { statusView() }, actions = {
        ActionButton(copyLink, enabled = canCopy) { Text("Copy link") }
    })
```

Open Components → Sharing → Open sharing preview. Choose Invite as, use River's
address and Add preview member; no invitation is sent. The bounded contact lookup
trims/case-folds known example addresses rather than validating arbitrary email.
Unknown, duplicate and unavailable contacts show an error and preserve the draft.
Owner Alex remains protected. Native role menus change present nonowner members;
selected-role no-ops do not increment the role-change count.

Remove opens a native confirmation for member ID plus current membership revision.
Cancel changes nothing. Any invite, role change, removal or reset increments the
revision; stale or currently ineligible confirmations do not remove members and
invalidated dialogs close. Reset members restores initial members/roles, clears
error, invalidates requests and preserves counts, link choice and invite draft/role.

Link access controls Off/Can view/Can edit in the local preview. Off hides the link
and disables the app's copy action. Explicit Copy writes the example URL through
the app's native clipboard callback; rendering, changing theme or choosing access
does not copy anything. Copy requests records admission, not a paste or real
authorization. Previously copied text remains on the clipboard. Displayed text
stays manually selectable while feature controls are disabled/pending.

Feature values survive family/theme/route changes; Android membership IDs/role
map and other nonsecret values survive saved-state recreation. Transient removal
presence is discarded. This one-workspace fixture does not send messages, create
real access tokens, enforce backend permissions or provide server concurrency.

## Playback and timeline

PlaybackControls composes three independent IconAction buttons. The caller
supplies previous/toggle/next labels, current-state narration, eligibility and
callbacks. isPlaying selects only play/pause artwork. Each button retains its
native role; the middle action exposes state as an accessibility value/description
rather than a selected choice. Swift tries a horizontal row then a vertical stack;
Compose wraps controls. Transport glyphs retain temporal direction in RTL.

NowPlayingCard groups supplied title/detail and passive artwork into one narrated
heading. Timeline, controls and actions remain independent sibling slots. Larger
text stacks the artwork above growing identity copy. Artwork defaults to 80
points/dp; its size must be finite and positive, and fit the host's bounds. The
card does not load media, crop an image on behalf of the caller, interpret state,
own an engine or schedule a clock.

```swift
// Conceptual: the feature supplies player values, formatted copy and commands.
NowPlayingCard(title, detail: creator, accessibilityLabel: identityCopy,
    artwork: { coverView }, timeline: {
        ValueSlider("Position", value: $position, in: 0...duration, valueLabel: timeCopy)
    }, controls: {
        PlaybackControls(isPlaying: playing, previousLabel: "Previous",
            toggleLabel: playing ? "Pause" : "Play", nextLabel: "Next", stateLabel: stateCopy,
            previousEnabled: canPrevious, toggleEnabled: canToggle, nextEnabled: canNext,
            onPrevious: previous, onToggle: toggle, onNext: next)
    }, actions: { favoriteButton })
```

```kotlin
// Conceptual: the feature supplies player values, formatted copy and commands.
NowPlayingCard(title, identityCopy, detail = creator,
    artwork = { coverView() }, timeline = {
        ValueSlider("Position", position, onSeek, timeCopy, range = 0f..duration)
    }, controls = {
        PlaybackControls(playing, "Previous", if (playing) "Pause" else "Play",
            "Next", stateCopy, previous, toggle, next,
            previousEnabled = canPrevious, toggleEnabled = canToggle, nextEnabled = canNext)
    }, actions = { favoriteButton() })
```

Open Components → Playback → Open playback preview. Play only enables a local
manual timeline. Advance 10 seconds applies the selected speed; there is no
automatic timer or audio. Coastline study and Orbit session remember separate
positions. Previous/Next are bounded and skip unavailable Night walk; switching
tracks pauses. Seeking uses the existing continuous ValueSlider callback and
clamps finite input. This is immediate local state, not an expensive seek command
or a draft/committed seek protocol.

Starting at the end replays from zero. A manual advance past the end pauses at
duration unless Repeat is on; repeat wraps by duration. Seeking to the end always
pauses. Buffering/Failed pause and disable timeline/transport/queue changes while
an independent favorite remains available. Retry restores Ready and keeps the
position, speed, repeat and favorites without resuming. Empty/disabled states
retain those values. Reset clears positions, restores the first track/normal
speed/no repeat/Ready, and retains favorites and action counts.

Feature values survive families, themes and routes; Android nonsecret primitives
also survive saved-state recreation. The saved playing flag is only local UI
state. A real player adapter must reconcile actual engine state, lifecycle,
duration, commands and asynchronous outcomes before claiming media is playing.

## Verification and code entry

OneTimeCodeField is one native editable field. CodeFormat admits up to a configured
1...12 ASCII digits (default six), preserving leading zeroes. Edits/pastes may
contain ASCII space, tab, CR, LF or hyphen, which are removed. Any other character
or excess digit rejects the whole edit; nothing is silently truncated. Caller
values must already be canonical partial digits. Native code hints describe
input purpose: iOS oneTimeCode and Compose SmsOtpCode. Actual autofill delivery
depends on the OS/provider and is not exercised by the preview.

The field uses native cursor, paste and editing behavior, an iOS number pad or
Android number keyboard, monospaced semantic type and left-to-right digit order.
Label/help/error remain in the surrounding logical layout. Error takes precedence
over help. Caller focus is explicit on Swift; Compose accepts native focus
modifiers. Done invokes onSubmit only when enabled, complete and caller canSubmit.
Editing/autofill never submits automatically; iOS number-pad users have a visible
feature-owned verification button.

VerificationCard groups supplied title/destination/passive artwork into one full
heading narration. Content, status and actions are independent slots. Larger text
stacks artwork above growing identity. The caller supplies bounded passive artwork
and any destination masking; the card does not parse an address, send a code,
verify an account or infer command eligibility.

```swift
// Conceptual: the feature owns the challenge, draft, focus and submit command.
VerificationCard("Verify email", destination: destinationCopy, accessibilityLabel: identityCopy,
    artwork: { mailMark }, content: {
        OneTimeCodeField("Code", text: $draft, format: CodeFormat(length: 6),
            help: "Enter six digits.", error: errorCopy, enabled: canEdit,
            canSubmit: canVerify, focus: $focused, onSubmit: verify)
    }, status: { statusView }, actions: {
        ActionButton("Verify", enabled: canVerify, action: verify)
        ActionButton(resendCopy, variant: .quiet, enabled: canResend, action: resend)
    })
```

```kotlin
// Conceptual: the feature owns the challenge, draft and submit command.
VerificationCard("Verify email", destinationCopy, identityCopy,
    artwork = { mailMark() }, content = {
        OneTimeCodeField("Code", draft, onDraft, format = CodeFormat(6),
            help = "Enter six digits.", error = errorCopy, enabled = canEdit,
            canSubmit = canVerify, onSubmit = verify)
    }, status = { statusView() }, actions = {
        ActionButton(verify, enabled = canVerify) { Text("Verify") }
        ActionButton(resend, variant = ButtonVariant.QUIET, enabled = canResend) { Text(resendCopy) }
    })
```

Open Components → Verification → Open verification preview. Demo code 123456
matches the local fixture; no message is sent and no account is authenticated.
A complete draft enables Verify/Done. Begin captures an attempt ID, challenge
generation, channel and code; checking disables input/channel/resend. Finish local
check applies a matching response, while Cancel leaves the draft and discards
that attempt. Incorrect/unavailable responses preserve the draft; editing clears
the error. Successful preview verification clears the draft.

Time advances only through Advance 30 seconds. A challenge starts with a
30-second resend cooldown and 120-second lifetime. Expiry discards pending check/
draft and requires a new code. Resend after cooldown creates a new generation,
clears draft/error and resets the timers. Changing Email/SMS or Reset also creates
a new generation; Reset retains counters. Results must match the current attempt
and challenge and still be enabled/unexpired. Cancellation, disabling, expiry or
reset prevent an older result from applying.

Route/family/theme changes retain feature values in memory. Android saves only
nonsecret choices, remaining times, generation, enabled state and counters; code,
pending attempt, error and success presentation are transient on recreation.
This manual clock is an example UI policy. A real service owns challenge identity,
delivery, time limits, verification and auth state at the existing thin seams.

## Stacks, separators and shells

Open **Components → Shells → Open shell preview**. Add a marker on Overview,
switch to Activity and add another, then revisit Overview. Try Stack spacing,
hide/show bottom navigation, and switch the gallery theme. Values belong to the
app fixture rather than the native tab content. The existing five-tab app also
consumes AppShell and TabBar.

VerticalStack/HorizontalStack use theme `stack`/`inline` spacing unless overridden.
They preserve native alignment and do not add scrolling or wrapping.
SectionDivider is decorative; use surrounding headings/copy for meaning.
Give a vertical divider a bounded row height. Its inset, thickness and color
are explicit caller options.

```swift
// Conceptual: the caller owns selection, destinations and page values.
AppShell(background: { Color.clear }, content: {
    TabBar(items: [
        TabItem(id: "home", label: "Home") { Image(systemName: "house") },
        TabItem(id: "library", label: "Library") { Image(systemName: "books.vertical") }
    ], selectedId: $selection) { id in
        ScrollView {
            VerticalStack {
                Text(id)
                SectionDivider(inset: 8)
                Text("Supplied page content")
            }.padding(20)
        }
    }
})
```

```kotlin
// Conceptual: host supplies a bounded viewport, admitted selection and system insets.
AppShell(Modifier.fillMaxSize(), background = { SceneBackground() }, content = {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) { PageContent(selectedId) }
}, navigation = {
    TabBar(items, selectedId, onSelect, Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)))
})
```

AppShell requires bounded geometry, not a slot inside an unbounded scroller.
Hosts supply scrolling inside the content slot and decide safe-area and keyboard
policy. Swift native TabView owns its chrome/safe areas and therefore occupies
the content slot with no separate navigation slot. Compose places a floating bar
in the navigation slot; TabBar disables internal Material insets, so the host
applies them once. Do not hide interactive controls in the passive background.

TabBar's one-to-five item limit and selected-ID admission are repository policy.
IDs must be unique/nonempty; selection must exist before replacing items.
Repeated selection produces no change. Keep feature values above content/layout
branches; neither native tabs nor AppShell guarantee feature-resource lifetime.
Camera/session and catalog presentation owners remain app responsibilities.

Both consumers build. The 2026-10-09 checks cover 64 hosted iOS app cases,
five focused Android shell/navigation cases, and nine Swift/eight Kotlin UI
package cases. Native tab labels/icons and programmatic Swift selection, stack
spacing/RTL, bounded slots, Android duplicate suppression and saved-state gallery
retention were observed. Physical-device rendering, assistive-technology traversal,
older OS execution and deep links were not measured in this batch.
