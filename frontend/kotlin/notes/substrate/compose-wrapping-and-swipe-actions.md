# Compose wrapping and swipe actions

Claim: native FlowRow and ephemeral SwipeToDismissBox state can adapt controlled
editing UI without restoring a completed gesture as a new domain command.

## Origin and evidence

Added 2026-10-09 for the tenth UI batch. Kotlin 2.3.20, AGP 9.0.1,
Compose BOM 2026.03.01, Material3 1.4.0, JDK 17, minSdk 24 / compileSdk 36.
Primary references: [Flow layouts](https://developer.android.com/develop/ui/compose/layouts/flow),
[swipe to dismiss](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss)
and [AndroidX source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SwipeToDismissBox.kt).
Current AndroidX main source informs inspection; it is not assumed identical to
pinned Material3 1.4.0. Native compilation and instrumentation establish local use.

## What and why

WrapLayout adapts FlowRow with scoped spacing and a FlowRowScope slot. Controls
keep their own widths, and a new line starts when space runs out. FlowRow weights
are per line, so a weighted cell is not a general equal-column grid. This eager
layout suits small tag/action groups; the screen owns lazy collections/scrolling.

SwipeActionRow creates SwipeToDismissBoxState with remember, rather than a
saveable factory. Stable lazy item keys identify the row. A LaunchedEffect
collects settledValue through snapshotFlow, reads the latest enabled callback via
rememberUpdatedState, resets the gesture to Settled, then dispatches that action.
Resetting allows the surviving row to swipe again. Ephemeral gesture state also
prevents restoration from replaying a dismissed position as archive/removal.

```kotlin
// Excerpt: transient presentation resets before the feature callback runs.
state.snapTo(SwipeToDismissBoxValue.Settled)
action?.onPerform?.invoke()
```

Only enabled edge actions participate. CustomAccessibilityAction supplies the
same callbacks as gestures, while visible native menus provide another route.
Decorative action-background text has cleared semantics. The feature still
guards ID/disabled admission: UI eligibility alone is not transaction authority.

SelectionRow uses native toggleable with Role.Checkbox and a passive checkbox
marker. TokenField uses the existing value/onValueChange single-line input;
IME Done and Add share a guard. Token slots receive interactivity because Compose
does not inherit a universal disabled environment into arbitrary lambdas.
Draft parsing, duplicates and clearing remain feature policy.

## Actual use and limits

The module walkthrough records native swipe/reset, saved-instance restoration,
selection/undo, IME admission, disabled controls and narrow larger-text checks.
CustomActions is a semantics property containing a list, not a performable
SemanticsActions callback key; tests read that list and invoke the action on the
UI thread. Input error assertions use native Error semantics, independent of the
supporting text's visible “Error:” prefix.
No full TalkBack, all IMEs/locales, rotation, large data, service integration or
physical-device animation/performance audit is claimed.
Next: which domain revision should a real asynchronous row command capture?

## Related

- [UI walkthrough](../modules/project/core/ui/README.md#selection-tokens-and-row-editing).
- [Editing gallery](../modules/project/app/README.md#editing-gallery).
- [Shared identity/undo](../../../../notes/patterns/selection-identity-and-undo.md).
