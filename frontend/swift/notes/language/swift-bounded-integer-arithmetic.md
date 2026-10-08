# Bounded integer arithmetic

Detect overflow before clamping an integer UI change to its permitted range.

## Origin and evidence

Added 2026-10-08 for ValueStepper. Swift 6.2.3 compiles the native implementation
for macOS 14/iOS 17+. Source inspection establishes the reporting-overflow path;
manual iOS activation exercises ordinary endpoint clamping. Kotlin's native
extreme-value test is separate evidence, not execution of Swift Int extremes.
Primary reference: [addingReportingOverflow](https://developer.apple.com/documentation/swift/int/addingreportingoverflow(_:)).

## What and why

A bounded value does not make `value + step` safe. At Int.max - 1, adding a
positive step can overflow before min(upperBound, result) gets a result to clamp.
Ordinary Swift integer arithmetic traps on overflow; wrapping arithmetic changes
the sign and can pick the wrong endpoint. Reporting arithmetic returns both a
partialValue and an overflow Boolean instead. A positive-step addition that
overflows saturates to the upper bound; subtraction saturates to the lower bound.
Otherwise clamp the ordinary result to the configured bound.

```swift
// Excerpt: the positive-step increase branch in ValueStepper.
let next = value.addingReportingOverflow(step)
value = next.overflow ? range.upperBound : min(range.upperBound, next.partialValue)
```

The range contains the current value, and step must be positive. Those are API
preconditions, not a domain validator. Buttons at endpoints cannot dispatch.
A final partial step reaches the endpoint even if the range length is not a
multiple of step. Swift Int has platform-native width; an eventual API encoding
must choose its own numeric limits instead of copying an incidental UI type.

## Used in and related

Read [the UI walkthrough](../modules/packages/FoundryUI/README.md#choices-disclosure-and-detail-composition)
for source and consumer links, then [native composition](../substrate/swiftui-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08).
Next: should a domain quantity allow arbitrary final partial steps, or require
validation against a fixed lattice rather than this UI saturation policy?
