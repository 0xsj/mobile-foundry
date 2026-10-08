# SwiftUI layout and contextual presentation

Measure native children under width proposals, keeping geometry separate from
identity, scrolling and the app's navigation stack.

## Origin and evidence

Added 2026-10-08 for the fourth UI batch. Toolchain: Swift 6.2.3, Xcode 26.2,
SwiftUI iOS 17+/macOS 14+. Native builds establish API compatibility; hosted
measurement checks exercise width, Dynamic Type, RTL and media/container bounds.
The app walkthrough records popup/navigation observations separately.

Primary references: [Layout](https://developer.apple.com/documentation/swiftui/layout),
[custom layout composition](https://developer.apple.com/documentation/swiftui/composing-custom-layouts-with-swiftui),
[ScaledMetric](https://developer.apple.com/documentation/swiftui/scaledmetric),
[LayoutDirection](https://developer.apple.com/documentation/swiftui/layoutdirection),
[NavigationLink](https://developer.apple.com/documentation/swiftui/navigationlink),
and [compact presentation adaptation](https://developer.apple.com/documentation/swiftui/view/presentationcompactadaptation(_:)).

## What and why

A private Layout implementation answers sizeThatFits and placeSubviews. It
measures children with the same proposed cell width, finds each row's maximum
height and then places the row. No GeometryReader must infer the grid's own
height from an unbounded vertical scroll proposal. The cache type is Void,
written as inout (); this small layout deliberately does not retain measurement
results between separate framework passes. Native Layout owns subview proxies,
while the caller's ForEach owns stable identities.

```swift
// Excerpt: measurement uses a width and an unconstrained natural height.
view.sizeThatFits(.init(width: cell, height: nil)).height
```

ScaledMetric grows the minimum item width with Dynamic Type. Its initialized
backing property uses `_scaledMinimum = ScaledMetric(wrappedValue: ..., relativeTo: .body)`
because the public initializer accepts a configurable baseline. The declaration
uses `@ScaledMetric` without a relativeTo-only initializer: that initializer also
requires a wrappedValue. This compiler finding is separate from observed reflow.
Columns are bounded by maximumColumns and can fall to one when the available
width is below the nominal minimum. SwiftUI automatically mirrors Layout placement under native layoutDirection.
An initial explicit reversal canceled that mirroring; the actual RTL geometry
check caught it. Placement now uses ordinary forward x coordinates, leaving
mirroring to SwiftUI. Text itself still follows native semantics. The grid has no
scroll view and is intended for small sets, not long feeds.

ContentContainer applies padding before its readable maximum width, then centers
that bounded content in the parent. MediaFrame establishes a ratio from width
and overlays/clips caller artwork; it does not infer a crop or accessible label.
Modifier order matters: placing fixedSize around a parent's bounds or adding
another page inset can defeat the intended proposal or double the spacing.

## Context and route ownership

NavLink composes native NavigationLink with ListRow and a decorative forward
chevron. Its caller supplies a NavigationStack and destination; no reusable route
enum or feature store is created. PopoverPanel attaches to its supplied anchor,
receives a Binding, and uses presentationCompactAdaptation(.popover) to retain
anchored presentation at compact widths. It copies the anchor's token/color
scheme into the presentation so a scoped Dark preview remains coherent.

HelpTooltip composes that panel with a visible native button. It is persistent
tap help with explicit/native dismissal, not an automatic hover tooltip. Keep
copy short; use an explicitly scrolling slot or sheet/detail for longer content.
The generic panel does not implement a second focus or outside-hit system.

Read [the UI walkthrough](../modules/packages/FoundryUI/README.md#context-navigation-and-adaptive-layouts),
[app walkthrough](../modules/apps/FoundryCatalog/README.md#context-and-layout-gallery),
and [shared reasoning](../../../../notes/patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08).
Next: what measured collection size justifies a lazy grid, and which help copy
requires moving from an anchored panel to a scrolling detail destination?

## Disclosure and bounded detail regions — 2026-10-08

The fifth batch uses a native Button header with controlled expansion rather
than depending on platform-specific DisclosureGroup styling. The full header
is one action; fields/buttons in the revealed slot keep independent targets.
Conditional content has no promised retained lifetime. Hoisted bindings preserve
drafts across collapse; the app explicitly clears note focus on collapse.
Token motion drives expansion and a reduced transaction disables animations.
[DisclosureGroup](https://developer.apple.com/documentation/swiftui/disclosuregroup)
remains a native alternative when its platform styling suits the consumer.

ChoiceChip uses a native button with selected traits and a token Capsule.
ValueStepper composes separate native action buttons so each operation has a
caller-provided label and disabled state. This is not a native adjustable Stepper
role. Read [bounded arithmetic](../language/swift-bounded-integer-arithmetic.md)
for the overflow-before-clamp reason. KeyValueRow uses ViewThatFits for short
inline copy and a wrapping stack otherwise; accessibility sizes choose the stack
explicitly. Its supplied strings combine into one passive reading unit.

DetailShell reserves intrinsically sized header/actions around a flexible body.
The consumer supplies ScrollView; putting the shell inside an unbounded outer
scroll defeats its viewport contract. ActionBar is floating Card composition,
not a positioning primitive. The native hosted check records header/body/footer
CGRects before and after scrolling to the twentieth row, then grows Dynamic Type
to accessibility3 and checks that footer/body bounds still do not overlap.

A scoped theme on the detail body does not necessarily style the enclosing
native navigation bar. The first dark screenshot exposed black navigation/status
text over the dark screen. The app now sets token toolbar background, visible
background and toolbarColorScheme at its destination boundary. Read
[toolbarColorScheme](https://developer.apple.com/documentation/swiftui/view/toolbarcolorscheme(_:for:))
and [toolbarBackground](https://developer.apple.com/documentation/swiftui/view/toolbarbackground(_:for:)).
This policy belongs to the app route, not DetailShell. The app walkthrough records
final build/capture evidence separately from the preceding automated suite.

Follow the UI/app walkthroughs' choices/disclosure/detail sections from reading
step 21. Next: which products need a collapsing header, large-screen pane layout
or explicit keyboard inset policy instead of this simple bounded composition?
