# SwiftUI loading and passive content

Use native animation only while a decorative loading view exists, and preserve
real content and control semantics separately from that decoration.

## Origin and evidence

Added 2026-10-08 for Avatar, StatCard, Skeleton, ToastBanner, FieldGroup and
CollectionToolbar. Swift 6.2.3/Xcode 26.2 compile the package for macOS 14 and
the app for iOS 17+. The native walkthrough records actual simulator checks
separately from existing app and token/material tests.

Primary references: [PhaseAnimator](https://developer.apple.com/documentation/swiftui/phaseanimator),
[animation phases](https://developer.apple.com/documentation/swiftui/controlling-the-timing-and-movements-of-your-animations),
and [accessibility grouping](https://developer.apple.com/documentation/swiftui/view/accessibilityelement(children:)).

## Mechanics and reasons

Skeleton's two Boolean phases drive only opacity. The phaseAnimator modifier
exists in the animated, nonreduced branch; the static branch contains just the
shape. Replacing/removing the skeleton removes its animation view. No task,
timer or GPU surface is created. The host can supply animated: false and the
theme incorporates system/ancestor motion reduction. This uses APIs available
at the project's iOS 17/macOS 14 minimum rather than introducing another renderer.

```swift
// Excerpt: Skeleton's animation branch; the surrounding branch checks reduction.
placeholder.phaseAnimator([false, true]) { content, phase in
    content.opacity(phase ? 0.45 : 0.85)
} animation: { _ in .easeInOut(duration: 0.9) }
```

Skeleton hides its accessibility element and disables hit testing. A real
loading label remains the caller's responsibility. Avatar instead uses
accessibilityElement(children: .ignore) with one supplied label; passive artwork
does not become duplicate text/images or nested controls. Native modifiers can
hide a redundant avatar at the host. StatCard combines its supplied text into
one reading unit while retaining units and trend meaning in copy.

Avatar's optional generic content distinguishes admitted artwork from fallback.
Its constrained EmptyView initializer omits artwork without AnyView; this builds
on the existing [generic builders](../language/swift-generic-query-state-and-content-builders.md).
The caller chooses loading/error fallback, cropping and image lifetime. A slot
is not an image loader. FieldGroup's contain behavior keeps child fields separate
instead of flattening their focus/editing into a single element.

## Feedback lifetime and limits

ToastBanner contains native action buttons and no timer/queue. Its host controls
presence and decides whether an explicit action should replace/dismiss it.
Announcement timing remains host policy; readable labels do not establish a
complete VoiceOver announcement flow. The gallery clears notices when families
change and does not treat display as proof of a successful operation.

Read [the UI walkthrough](../modules/packages/FoundryUI/README.md#display-feedback-and-collection-components)
and [app walkthrough](../modules/apps/FoundryCatalog/README.md#display-feedback-and-collections-gallery),
then [shared lifetime reasoning](../../../../notes/patterns/collection-projections-and-feedback-lifetime.md).
Next: which real operation justifies automatic dismissal, and how should its
notice owner protect a newer message from an older timeout?
