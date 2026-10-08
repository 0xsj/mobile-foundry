# Widen before integer arithmetic

Convert Int operands to Long before arithmetic, then clamp before narrowing back.

## Origin and evidence

Added 2026-10-08 for ValueStepper. Kotlin 2.3.20/JDK 17 with the API 24+ Android
consumer. Source inspection and a native emulator test exercise Int.MAX_VALUE,
zero and Int.MIN_VALUE through actual buttons, with four explicit callbacks.
Primary references: [numbers](https://kotlinlang.org/docs/numbers.html) and
[coerceAtMost](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.ranges/coerce-at-most.html).

## What and why

Int addition can overflow before coerceAtMost receives the value. Converting
`(value + step).toLong()` is too late: the addition already happened as Int.
Starting with value.toLong() selects Long arithmetic. Any Int value plus or minus
one positive Int step fits in Long. Clamp that Long result to an Int endpoint,
then convert back; the narrowed result is in range.

```kotlin
// Excerpt: the positive-step increase branch in ValueStepper.
(value.toLong() + step).coerceAtMost(range.last.toLong()).toInt()
```

The corresponding subtraction clamps at the lower endpoint. This reasoning is
specific to Int inputs and a positive Int step; it is not a general proof for
Long + Long or other arbitrary numeric operations. Nonempty range, contained
current value and positive step remain component preconditions. Domain admission
and service encoding belong elsewhere.

## Used in and related

Read [the UI walkthrough](../modules/project/core/ui/README.md#choices-disclosure-and-detail-composition)
for source/test links, then [native composition](../substrate/compose-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08).
Native endpoint tests establish the exercised Int behavior, not a shared Swift
Int width or durable quantity representation. Next: should a real domain use
fixed integer limits, a count type, or a decimal quantity instead?
