# SwiftUI chart drawing and summaries

Claim: native Canvas/shape drawing needs explicit readable summaries, while
native text layout should own labels, exact values and independent controls.

## Origin and evidence

Added 2026-10-09 for the eleventh UI batch. Swift 6.2.3, Xcode/SwiftUI 26.2,
iOS 17 / macOS 14 UI-package minimum. Primary references:
[Canvas](https://developer.apple.com/documentation/swiftui/canvas),
[accessibilityElement](https://developer.apple.com/documentation/swiftui/view/accessibilityelement(children:))
and [ViewThatFits](https://developer.apple.com/documentation/swiftui/viewthatfits).
Apple's Canvas web page required JavaScript and its Markdown endpoint could not
be fetched here; local source, compilation and native observation are the actual
evidence for this adapter. No undocumented drawing performance claim is inferred.

## What and why

Sparkline converts finite samples into normalized coordinates, then builds a
straight Path in Canvas's actual size with stroke insets. Scale before subtracting
extrema to avoid overflowing a finite signed range. Empty/single/constant sequences
have explicit drawing behavior. The Canvas exposes the caller's summary rather
than pretending it creates an accessible point tree. It has no TimelineView,
timer, implicit curve or gesture recognizer.

```swift
// Excerpt: explicit small-chart copy, independent of canvas geometry.
.accessibilityElement(children: .ignore).accessibilityLabel(summary)
```

BarChart uses native Text, ViewThatFits and GeometryReader. Labels/value copy stack
when the horizontal arrangement cannot fit. Native Capsule widths project value
over the supplied maximum. The final chart exposes one category/value summary;
its decoration is hidden. Manual inspection initially exposed only the container
title, so the final implementation supplies all category/value pairs explicitly.

ProgressRing uses native Circle trim/stroke, with an inset for the stroke width.
It clamps finite geometry and receives formatted value copy. At accessibility
DynamicType sizes, that value moves below the fixed drawing rather than shrinking
text inside it. Legend artwork is passive; TrendBadge copy conveys direction and
comparison without relying on the arrow. ChartPanel keeps footer controls as
independent native children and reuses existing Card/WrapLayout surfaces.

## Actual use and limits

UI-package tests exercise signed/extreme finite normalization, empty/single and
constant sequences. A hosted iOS 26.2 check measures a 240-point panel at normal
and accessibility3 text: copy grows, plot/action bounds remain inside the panel,
the sparkline stays 64 points tall and the independent action retains minimum
height. The app walkthrough records manual rendering/semantics separately.
No full VoiceOver, all locales, all window sizes, macOS runtime, interactive time
series, large-data budget or physical-device rendering audit is claimed.
Next: when should a real dashboard use Swift Charts instead of these small adapters?

## Related

- [UI walkthrough](../modules/packages/FoundryUI/README.md#insights-and-small-charts).
- [Insights gallery](../modules/apps/FoundryCatalog/README.md#insights-gallery).
- [Chart meaning/scales](../../../../notes/patterns/chart-meaning-and-scales.md).
