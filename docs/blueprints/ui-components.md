# Reusable UI components

The reusable component batches populate the existing family leaves on both
native platforms. [Behavior](../../contracts/behavior/ui-components.md) defines
the boundaries; [the component map](../COMPONENTS.md) lists source placement.
No new package/module or third-party UI dependency is introduced.

The catalog currently contains seven batches with 51 building blocks. Activity
adds feed composition, native refresh and explicit pagination examples.

## Construction

Swift source stays in FoundryUI/Components; Kotlin mirrors the family/component
folders in core/ui. Component names and filenames have no Foundry prefix.
`ActionButton` and `LabeledTextField` avoid collisions with native Button/TextField.
Component helper types use ButtonVariant, MessageTone, SurfaceRole, TabItem,
CheckState, AvatarShape, SkeletonShape, PasswordPurpose, ValidationItem, StepItem,
StepStatus, LoadMorePhase and explicit action values.
Theme, package and app names retain their existing identities.

Button styles and status tones resolve existing theme roles. Native buttons
provide activation/disabled semantics; SubmitButton delegates to ActionButton.
Card composes Surface. SettingsSection and SelectionCard compose Card. Headers,
rows and feedback expose native content slots rather than feature-specific models.
Keep slot content interactive only where its host is not already a control.

The app owns ComponentCatalogView / ComponentCatalogScreen and all preview
state. A catalog route opens it. Actions, Content, Patterns, Controls, Overlays,
Display, Feedback, Collections, Context, Layout, Details, Journeys and Activity organize examples;
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
