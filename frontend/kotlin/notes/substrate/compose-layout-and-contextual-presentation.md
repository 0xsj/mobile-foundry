# Compose layout and contextual presentation

Measure children once under native constraints, preserving their composition
identity while leaving scrolling and navigation with the app.

## Origin and evidence

Added 2026-10-08 for the fourth UI batch. Kotlin 2.3.20, AGP 9.0.1, Compose BOM
2026.03.01, compile SDK 36/minimum API 24. Builds establish API compatibility;
focused emulator checks exercise actual bounds, child state after reflow, popup
close/back, navigation return and saved-state behavior. Full accessibility and
hardware coverage remain separate.

Primary references: [custom layouts](https://developer.android.com/develop/ui/compose/layouts/custom),
[menus](https://developer.android.com/develop/ui/compose/components/menu),
and [SaveableStateHolder](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/SaveableStateHolder).

## What and why

AdaptiveGrid uses native Layout rather than a LazyVerticalGrid inside a scrolling
Column. Its MeasureScope receiver supplies constraints, density and fontScale.
It requires bounded width, computes equal cell widths and measures each child
once with Constraints.fixedWidth(cell). Height remains unconstrained so longer
copy contributes to its row maximum. constrainHeight is an imported native
extension, not a member available from importing Constraints alone.

```kotlin
// Excerpt: one measure per child, followed by native relative placement.
val placeables = measurables.map { it.measure(Constraints.fixedWidth(cell)) }
// In the placement block:
child.placeRelative((index % columns) * (cell + gap), y)
```

placeRelative handles RTL placement. The baseline dp minimum is multiplied by
fontScale, reducing columns as text grows. Fractional pixel division may leave
a tiny trailing remainder. Incomplete rows keep the same cell width. The grid
owns no item list or child state: stable key calls at the consumer keep remembered
state associated with each child even when columns change. This differs from
regrouping children into new nested Row composition paths on every reflow.
For large datasets use a native lazy grid with its own bounded scroll ownership.

ContentContainer bounds a centered Column including its native PaddingValues.
MediaFrame fills width, applies a positive ratio and clips the slot. Parent
constraints must allow sufficient height. Neither layout admits images or
supplies semantics for unknown artwork; the caller provides fitting and labels.

## Context and saved state

PopoverPanel uses a focusable native DropdownMenu anchored in a Box. Android
handles placement, outside/back dismissal and vertical scrolling. Do not put an
unbounded vertical scrolling list inside this already-scrolling menu. The caller
owns expanded state and only an explicit content action changes committed values.
HelpTooltip is a short persistent tap-help composition of that panel, not a
TooltipBox with an internal timeout/hover lifecycle.

NavLink is a full-row native clickable ListRow with a decorative directional
chevron and supplied callback. Route identity and back handling remain app policy.
The gallery example owns a small detail flag and a SaveableStateHolder. Its
SaveableStateProvider retains rememberSaveable values when the gallery leaves
composition for details. Ordinary remember-only help/option flags disappear.
Returning restores the selected family and counters; this is saved UI state,
not durable domain storage or a general router implementation.

Read [content receiver mechanics](../language/kotlin-covariant-query-state-and-content-slots.md),
[the UI walkthrough](../modules/project/core/ui/README.md#context-navigation-and-adaptive-layouts),
[app walkthrough](../modules/project/app/README.md#context-and-layout-gallery),
and [shared reasoning](../../../../notes/patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08).
Next: which real navigation stack needs entry-specific saved state and ViewModels,
and which collection should own a lazy grid rather than this eager composition?
