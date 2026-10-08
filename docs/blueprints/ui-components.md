# Reusable UI components

The reusable component batches populate the existing family leaves on both
native platforms. [Behavior](../../contracts/behavior/ui-components.md) defines
the boundaries; [the component map](../COMPONENTS.md) lists source placement.
No new package/module or third-party UI dependency is introduced.

## Construction

Swift source stays in FoundryUI/Components; Kotlin mirrors the family/component
folders in core/ui. Component names and filenames have no Foundry prefix.
`ActionButton` and `LabeledTextField` avoid collisions with native Button/TextField.
Component helper types use ButtonVariant, MessageTone, SurfaceRole, TabItem,
CheckState, AvatarShape, SkeletonShape and explicit action values.
Theme, package and app names retain their existing identities.

Button styles and status tones resolve existing theme roles. Native buttons
provide activation/disabled semantics; SubmitButton delegates to ActionButton.
Card composes Surface. SettingsSection and SelectionCard compose Card. Headers,
rows and feedback expose native content slots rather than feature-specific models.
Keep slot content interactive only where its host is not already a control.

The app owns ComponentCatalogView / ComponentCatalogScreen and all preview
state. A catalog route opens it. Actions, Content, Patterns, Controls, Overlays,
Display, Feedback, Collections, Context and Layout organize examples;
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

## Verification

Run `make ios-generate`, native builds, iOS app checks and the focused Android
ComponentCatalogTest/forms/shell checks. Inspect the actual gallery on a native
simulator, including the composed content, theme toggles and native sheet.
Run `make notes-check` after learning handoffs and source-link renames.
Record actual outcomes and remaining accessibility/device limits in the native
UI and app walkthroughs.
