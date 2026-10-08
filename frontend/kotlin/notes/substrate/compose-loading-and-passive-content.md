# Compose loading and passive content

Remove continuous animation from composition under reduced motion, and keep
decorative content separate from real semantic targets and recovery actions.

## Origin and evidence

Added 2026-10-08 for the third component batch. Kotlin 2.3.20, Compose BOM
2026.03.01 and AGP 9.0.1 build with compile SDK 36/minimum API 24. The app/UI
walkthroughs record emulator interaction and actual pixel evidence separately
from compilation and existing unit tests.

Primary references: [value animations](https://developer.android.com/develop/ui/compose/animation/value-based),
[animation testing](https://developer.android.com/develop/ui/compose/animation/testing),
and [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

## Mechanics and reasons

Skeleton creates rememberInfiniteTransition only in its animated, nonreduced
branch. A reversing float animation drives opacity; the other branch is a static
shape. Leaving the branch disposes the transition rather than hiding its output.
The theme already observes the system animator-duration switch and inherits
explicit motion reduction. This is native Compose decoration, not a GL surface.

```kotlin
// Excerpt: Skeleton's animated branch, after the reduction condition.
val transition = rememberInfiniteTransition(label = "Skeleton pulse")
val pulse by transition.animateFloat(0.45f, 0.85f,
    infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "Skeleton opacity")
```

The animation test controls Compose's mainClock and samples actual native
captured pixels before/after time advances. It also changes the theme reduction
while composed. This checks executed output rather than asserting an internal
Boolean. The app walkthrough records its outcome; an emulator pixel check is
not a physical-device energy benchmark.

Avatar's clearAndSetSemantics supplies one identity and replaces child artwork
semantics. Its nullable BoxScope content lambda chooses supplied artwork versus
caller fallback; Material text auto-sizing accommodates short fallback copy.
No network, decode or image-cache policy enters this control. Skeleton clears
decorative semantics entirely and has no click action. The host supplies a real
loading label. FieldGroup establishes traversal grouping without merging child
editing targets; error replaces group help but does not attach per-field errors.

ToastBanner's message uses a polite live region while its native buttons remain
separate targets. Its callbacks do not infer retry/removal policy. The gallery's
notice uses remember, while committed controls/counters use rememberSaveable
above the family switch. Old transient recovery prompts are not replayed on
saved-instance restoration. Reading-time, queueing and automatic timeout policy
would belong to a feature presentation owner.

Read [the UI walkthrough](../modules/project/core/ui/README.md#display-feedback-and-collection-components)
and [app walkthrough](../modules/project/app/README.md#display-feedback-and-collections-gallery),
then [shared lifetime reasoning](../../../../notes/patterns/collection-projections-and-feedback-lifetime.md).
Next: which real notice requires timeout cancellation and event identity, and
which bulk action should include IDs hidden by its current filter?
