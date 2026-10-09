# SwiftUI wrapping and List actions

Claim: a native Layout can wrap intrinsic-width controls while List supplies
row swipe behavior, leaving selection, mutation and recovery with the caller.

## Origin and evidence

Added 2026-10-09 for the tenth UI batch. Swift 6.2.3, Xcode/SwiftUI 26.2,
iOS 17 / macOS 14 UI-package minimum. Primary references:
[Layout](https://developer.apple.com/documentation/swiftui/layout) and
[swipeActions](https://developer.apple.com/documentation/swiftui/view/swipeactions(edge:allowsfullswipe:content:)).
The implementation and hosted native measurements establish the local layout
behavior; the app walkthrough distinguishes owner tests from manual actions.

## What and why

WrapArrangement implements sizeThatFits and placeSubviews. It measures each
child's ideal width, proposes no more than available width to flexible content,
and starts another row when the next measured width would overflow. A row's
height is its tallest child. Both methods compute the same arrangement; there
is no cache whose invalidation could disagree with changing child measurements.
An unspecified parent width uses the natural total width. Empty content has zero
height. Finite nonnegative spacing defaults to the scoped inline token.

```swift
// Excerpt: placement uses the measured size and logical native anchor.
view.place(at: CGPoint(x: bounds.minX + item.origin.x, y: bounds.minY + item.origin.y),
           anchor: .topLeading, proposal: .init(item.size))
```

Native layout direction mirrors placement; the hosted check measures actual RTL
right edges. This differs from AdaptiveGrid's equal-width cells. Fixed-size
children can refuse a narrower proposal, so the host must keep those bounds
compatible. The layout is eager and does not own scrolling or child identity.

TokenField reuses LabeledTextField and external Binding/FocusState values. Add and
onSubmit share one enabled, nonbusy, canAdd guard. The caller clears text only
after admission. Its disabled subtree includes removable token buttons.
SelectionRow exposes one native button with selected trait and supplied value;
artwork and marker are passive. A separate row menu handles other commands.

SwipeActionRow applies native swipeActions at each logical edge. List supplies
gesture/reveal/full-swipe behavior; the wrapper supplies enabled native Buttons,
destructive roles and scoped tint. It does not build a parallel drag recognizer,
erase data, save a swipe position or decide confirmation/undo.

## Actual use and limits

Hosted iOS 26.2 geometry checks pass at widths 400 and 220: variable-width controls
reflow, mixed heights set subsequent row gaps, and RTL edges mirror. Owner checks
exercise tag rejection, disabled callbacks, hidden selection and latest undo.
The app walkthrough records native row action/menu observation separately.
No full VoiceOver, arbitrary fixed-size children, localization, macOS runtime,
all iPad sizes/rotation or physical-device gesture audit is claimed.
Next: how should a product preserve scroll position when undo inserts above the viewport?

## Related

- [UI walkthrough](../modules/packages/FoundryUI/README.md#selection-tokens-and-row-editing).
- [Editing gallery](../modules/apps/FoundryCatalog/README.md#editing-gallery).
- [Shared identity/undo](../../../../notes/patterns/selection-identity-and-undo.md).
