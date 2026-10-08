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

## Geometry and contextual navigation — 2026-10-08

A reusable layout measures native children; it need not own records, selection,
scroll position or routes. The fourth UI batch uses an eager grid for small
card sets. Available width and native text scaling determine its columns. Each
row uses its tallest child's measured height, so longer copy does not overlap
the next row. The last row retains column widths rather than stretching a
single final card. Large feeds still need native lazy collections and a feature
that owns their data/scroll pipeline.

Reflow must not change identity. Keep children under stable native ForEach/key
identities rather than rebuilding differently nested row trees at each width.
Native measurement checks exercise actual geometry after width/text-scale/RTL
changes; Android also retains a child's counter through those changes. This is
stronger evidence than checking only a column-count formula, and narrower than
a complete accessibility or device audit.

Contextual presentation and navigation are separate policies. A help trigger
can expose short copy through a caller-controlled anchored panel. Closing it
must not select an option or dispatch an effect. A navigation affordance can
use a native destination builder or invoke an app router without knowing either
route storage or domain services. When navigation removes the gallery from
composition, its app owner explicitly decides which saved values return and
which transient popup flags should disappear.

Example: open storage options, dismiss with zero choices, then reopen and choose
local storage once. Open a detail destination and return: the choice count is
still one, while no old help popup is replayed. That is presentation/state policy,
not evidence of persisted storage or an implemented account feature.

The native UI/app walkthroughs from reading step 20 link actual implementations
and checks. Next: which product needs a lazy collection, which needs an app rail,
and which detail route needs independent saved state instead of a simple example?

## Collapsed drafts and detail viewports — 2026-10-08

A disclosure controls visibility, not the lifetime of important edits. Place the
note draft above its revealed content, pass native values/bindings down, and let
collapse change only expansion. Native visibility animations may remove child
composition after exit. Retaining edits by accident inside a hidden widget is
not a cross-platform state policy. Focus also needs a caller decision when a
field leaves the active interface.

A bounded detail composition separates header, flexible body and action region.
The body can scroll while actions remain outside its scroll range. The action
bar supplies surface/copy/slots; it does not pin itself or start operations.
Viewport and draft ownership are separate: a different route can consume the
same parent-owned draft without moving that draft into a reusable shell.

Example: choose Compact, increase copies from 0 to 2, edit a delivery note and
collapse it. Reopen and the note remains. Open the detail preview and the same
values appear with persistent actions. Apply changes one local counter; Reset
returns the draft to defaults while preserving that counter. Neither action
proves an export or network write happened. Actual native tests cover retained
editing, explicit actions and footer geometry; comprehensive accessibility and
physical-device behavior remain separate.

Numeric UI controls need explicit endpoint policy. A positive step can overshoot
an endpoint or overflow its native integer before clamping. Saturating to the
endpoint supports a partial final step, while native checked/widened arithmetic
keeps the intermediate result valid. A domain with a fixed quantity lattice must
validate that policy separately. Read native language notes from reading step 21.
Next: which draft should be discarded on exit, and which real command needs a
confirmed receipt before its action region can show success?

## Input drafts and journey steps — 2026-10-08

Claim: a field's editing mechanism, a draft's lifetime and a journey's completion
policy need separate owners even when they appear on the same screen.

Origin: the sixth component batch adds native password/multiline input, passive
requirements/progress and account/onboarding layouts. Native module walkthroughs
link source and executed checks; this section explains the boundary.

A masked field changes presentation without removing its underlying value.
The component should not choose credential storage or save every draft. The
Android exemplar deliberately keeps password state ephemeral and profile text
saveable; Swift uses view-local bindings. Neither is durable account state.
Draft lifetime is a feature choice independent of keyboard or validation copy.

A requirement list receives evaluated values; a step indicator receives statuses.
Both remain passive. The feature owns whether an empty note blocks Next, whether
Previous preserves edits, and when Finish becomes disabled. This permits the
same components to serve profile setup, checkout, permissions or a device setup
flow with different admission and command policies.

For example, the gallery shares one introduction across its form and three
onboarding steps. Changing page identity resets scrolling; the introduction
survives because it lives above the page. Previous/Restart preserve preferences.
Finish changes a local count only after an explicit action; passive progress
never dispatches the action itself. A real write would need the existing
mutation boundary rather than treating a completed visual step as persistence.

AuthShell scrolls the whole account composition, including its footer.
OnboardingPage reserves a concise action region outside its scroll. These are
layout choices, not routing or identity providers. Both require sensible native
insets and viewport constraints from the host. Next: which workflow needs a
resumable draft, and which step needs remote admission before advancement?

Read [Swift native mechanisms](../../frontend/swift/notes/substrate/swiftui-rich-input-and-journey-pages.md)
and [Compose native mechanisms](../../frontend/kotlin/notes/substrate/compose-rich-input-and-journey-pages.md),
then [usage](../../docs/blueprints/ui-components.md#rich-input-and-onboarding).
