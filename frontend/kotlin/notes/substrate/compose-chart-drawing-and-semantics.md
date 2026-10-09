# Compose chart drawing and semantics

Claim: DrawScope geometry and native semantics/text layout can make small chart
adapters readable without turning drawing state into a data or analytics owner.

## Origin and evidence

Added 2026-10-09 for the eleventh UI batch. Kotlin 2.3.20, AGP 9.0.1,
Compose BOM 2026.03.01, Material3 1.4.0, JDK 17, minSdk 24 / compileSdk 36.
Primary references: [native drawing](https://developer.android.com/develop/ui/compose/graphics/draw/overview)
and [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).
The module walkthrough links executed pixel, semantics and interaction evidence.

## What and why

Canvas's DrawScope size uses pixels. Sparkline converts its Dp stroke into pixels,
then maps normalized coordinates into inset bounds. Native drawing responds to
current supplied data; it starts no coroutine, clock, smoothing or selection.
Double normalization scales before subtracting extrema, then bounded coordinates
convert to Float for native Path. The provided contentDescription carries the
readable summary; exact sample values remain a caller-rendered native Text view.

```kotlin
// Excerpt: fraction geometry and human copy are projected separately.
modifier.progressSemantics(progress).semantics {
    contentDescription = label
    stateDescription = valueLabel
}
```

ProgressRing uses a native Canvas arc and bounded progress semantics. Its visible
value leaves the drawing at fontScale >= 1.5. Text/decorative children have cleared
semantics so the parent exposes one supplied label/value. BarChart keeps native
label/value Text in merged per-bar rows and hides decorative tracks. FlowRow wraps
copy while fills start at logical Alignment.CenterStart. Legend marks and arrows
are decorative; supplied series/trend copy provides their meaning.

ChartPanel reuses Card and WrapLayout, retaining independent footer actions.
The screen, not the drawing adapter, owns scrolling and saved primitive values.
The dashboard's period tabs add a horizontal scroll scope inside vertical content;
interaction tests return the outer viewport to its header before clicking a tab.

## Actual use and limits

Instrumentation captures the native sparkline to pixels: signed Double extremes
draw within the expected ends, constant values form a midline and empty input
clears the path. A 240dp font-scale-two panel retains readable category/value
semantics, clamped progress/state copy, an independent minimum-height footer
action and disabled control state. Catalog checks exercise period/empty/goal
independence and restoration. Numerical package tests reject nonfinite samples
and negative bars. Native checks do not establish full TalkBack, all languages,
all screen sizes, large-series performance or device hardware budgets.
Next: which timestamp/selection model should a time-aware native chart receive?

## Related

- [UI walkthrough](../modules/project/core/ui/README.md#insights-and-small-charts).
- [Insights gallery](../modules/project/app/README.md#insights-gallery).
- [Chart meaning/scales](../../../../notes/patterns/chart-meaning-and-scales.md).
