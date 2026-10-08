# Collection projections and feedback lifetime

Keep durable intent separate from visible projections and temporary presentation,
so filtering or dismissing feedback cannot silently change application meaning.

## Origin and evidence

Added 2026-10-08 with the third native UI component batch. Both implementations
keep records, selected IDs, filtering, formatting and actions in the gallery;
shared components accept copy, native slots and callbacks. Native interaction
and pixel checks are recorded in each app/UI walkthrough. Primary references:
Android's [state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting),
[semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics),
and Apple's [accessibility grouping](https://developer.apple.com/documentation/swiftui/view/accessibilityelement(children:)).

## Projection is not selection

A filter computes which records are visible. Selection is a separate set of
stable IDs; a selected item does not stop being selected just because a search
hides it. Bulk actions need explicit scope. The gallery's Select visible adds
matching IDs, while Clear selection clears all IDs. Its summary counts both
visible records and total selections so hidden intent is not silently lost.
Another product may deliberately clear hidden selections, but that is feature
policy rather than a toolbar behavior. The toolbar only composes controls.

```swift
// Conceptual: the feature owns both the projection and the selection policy.
let visible = records.filter(matchesFilter)
selectedIDs.formUnion(visible.map(\.id)) // Select visible, not all records.
```

```kotlin
// Conceptual: filtering changes visibility without rewriting selected IDs.
val visible = records.filter(matchesFilter)
onSelected((selectedIDs + visible.map { it.id }).distinct())
```

Filtering/sorting three local records is an example, not a shared query engine.
Large collections need their own data pipeline, stable item identities and
native lazy presentation. A toolbar cannot infer whether an action should apply
to current matches, selected hidden records, or a server-wide result set.

## Feedback is not an operation

A toast displays supplied copy. Showing it cannot confirm a write, execute a
retry or create an undo transaction. Recovery and dismissal are separate explicit
callbacks. If an action should remove or replace a notice, its caller does that.
The gallery keeps notices until dismissal/replacement and clears them on leaving
the family. Android deliberately uses remember for the notice rather than
rememberSaveable, so restoring a screen does not replay an old prompt to undo.
Counters and selection may survive screen restoration independently.

Adding automatic dismissal would require an owner for event identity, reading
time, cancellation and stale-timeout protection. A timer for notice A must not
remove a newer notice B. Critical recovery often belongs in persistent inline
feedback instead. Those policies are not hidden inside ToastBanner.

## Passive visuals and native targets

Avatar replaces decorative child artwork semantics with one supplied identity.
Its caller decides image admission, loading/error fallback and whether a nearby
name makes that identity redundant. StatCard receives formatted values and trend
meaning, avoiding a false assumption that every increase is good. Neither starts
work or creates an implicit action.

Skeleton shapes expose no fake titles, controls or data. The host supplies real
loading copy and replaces them when content becomes available; an empty result
is a different state. Reduced motion removes the animation branch rather than
running an invisible pulse. FieldGroup preserves individual native field focus
targets and supplies group help/error; it cannot replace per-field error
association, validation, keyboard or submit policy.

## Used in and related

Display, Feedback, Controls and Collections exercise these choices with local
example data. Read [component slots and caller state](component-slots-and-caller-owned-state.md),
[query rendering](query-state-and-rendering.md), and
[forms/mutation ownership](forms-and-mutation-ownership.md).
Next: should a real bulk command include hidden IDs, and what transaction would
make an advertised Undo action valid after the screen disappears?
