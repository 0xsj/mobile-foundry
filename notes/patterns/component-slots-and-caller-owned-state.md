# Component slots and caller-owned state

A reusable component owns presentation and interaction mechanics while its
caller owns application meaning, state and effects.

## Origin and evidence

Added 2026-10-08 for the everyday native component batch. Source inspection
shows the same dependency direction in SwiftUI and Compose: values flow into
components and callbacks flow out. Native execution evidence belongs to the
platform walkthroughs. Primary references: Apple's
[Button](https://developer.apple.com/documentation/swiftui/button) and Android's
[state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting).

## What and why

Begin with a small default API, then open the parts consumers need to compose.
A card needs a content slot and surface role, not a project model. A list row
needs leading/trailing accessories, not a destination enum. A search field needs
text and a clear action, not a repository or debounce policy. Keeping these
choices outside the component makes the same control useful across verticals.

Swift uses generic View content and @ViewBuilder. A constrained initializer
with EmptyView gives the no-actions case without type erasure. Compose uses
composable lambdas; RowScope and ColumnScope receivers expose appropriate
layout operations such as weight to slot content. These are native composition
mechanisms, not a shared cross-platform widget model.

## Example

```swift
// Conceptual: the host owns both state and the operation.
ActionButton(isBusy: saving, enabled: canSave, action: save) {
    Label(saving ? "Saving…" : "Save", systemImage: "checkmark")
}
```

```kotlin
// Conceptual: rendering a busy state never starts the operation itself.
ActionButton(isBusy = saving, enabled = canSave, onClick = save) {
    Text(if (saving) "Saving…" else "Save")
}
```

The button prevents activation while busy. It cannot decide whether retrying a
write is safe, whether to retain a draft, or what successful confirmation means.
Those decisions remain with mutation state and the feature's operation owner.

## Gotchas

- Do not place a button inside SelectionCard's label or another whole-row button.
  Interactive accessories belong in a noninteractive ListRow or separate region.
- A surface role determines material eligibility. Glass selection does not turn
  reading cards transparent; choose floating deliberately and provide a backdrop.
- Long text is a layout input. Avoid fixed text heights; move accessories below
  copy at accessibility sizes and use native scrollable/menu choices where needed.
- Status color cannot replace copy. A neutral/critical tone styles a supplied
  message; it does not classify an application failure or schedule announcements.
- Unbranded component names can overlap native names. Use descriptive names for
  native controls that must stay available and explicit imports for Card/Surface.

## Committed values and transient interaction — 2026-10-08

The next batch makes the ownership distinction concrete. A switch or radio group
receives committed selection and a callback. A menu may own whether it is open,
and DateField may own its uncommitted modal draft. These small mechanics do not
turn a component into a feature store. Each date opening copies the caller's
current value; cancel discards it and confirm emits one committed value. The
caller still decides whether that value is saved or synced.

An aggregate checkbox derives off/on/mixed from its children. Its onToggle
callback defines the policy: the gallery selects all from mixed and clears all
from on. The reusable checkbox does not invent a third persistent business value
or assume all mixed choices should cycle the same way. Whole-row selection uses
one activation target and a passive glyph/native indicator.

Native date representations differ. Swift Date is an instant interpreted by the
view's calendar/time zone; Compose's date picker uses UTC-midnight milliseconds
for a date label. Formatting those milliseconds in a western local zone can show
the previous day. DateField uses UTC for Android display. A domain date should
cross a service seam as calendar fields or an agreed date-only encoding, rather
than as an accidental appointment timestamp.

Sliders also need a shared vocabulary: steps counts intermediate stops, not
intervals. Three intermediate stops give five positions including both bounds.
Formatting and expensive edits remain caller policy. Dialogs and menus emit
effects only through explicit enabled actions; opening, canceling or recomposing
cannot invoke confirmation. Native sheets have dismissal callbacks, which must
not be reused as successful submission signals.

Primary references: Apple's
[modal presentations](https://developer.apple.com/documentation/swiftui/modal-presentations),
Android's [date pickers](https://developer.android.com/develop/ui/compose/components/datepickers),
[UTC picker state](https://developer.android.com/reference/kotlin/androidx/compose/material3/rememberDatePickerState),
and [checkboxes](https://developer.android.com/develop/ui/compose/components/checkbox).
Read each native UI/app walkthrough for source links and actual checks. Next:
which feature needs range-limited dates or an externally coordinated draft?

## Used in and related

The component gallery combines primitives into collection, settings, selection,
native details and confirmation examples without choosing data providers.
Read [semantic theme ownership](semantic-tokens-and-native-themes.md),
[material ownership](material-themes-and-backdrops.md), and
[form/mutation ownership](forms-and-mutation-ownership.md).
Next: which repeated screen composition justifies another shared pattern, and
which customization is better left as native caller content?
