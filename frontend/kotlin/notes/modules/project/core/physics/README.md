# Kotlin physics module

core/physics exposes immutable toss snapshots independently of Compose and the
frame clock.

## Origin and reading order

Implemented 2026-10-11. Read the
[contract](../../../../../../../contracts/behavior/physics-playground.md), then
[ThrowSimulation](../../../../../project/core/physics/src/main/kotlin/dev/mobilefoundry/physics/ThrowSimulation.kt),
[numeric checks](../../../../../project/core/physics/src/test/kotlin/dev/mobilefoundry/physics/ThrowSimulationTest.kt),
and [catalog consumer](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/physics/PhysicsPlaygroundScreen.kt).
Compare [fixed-step ownership](../../../../../../../notes/patterns/fixed-step-physics-and-presentation.md).

## Walkthrough and mechanics

Vectors expose pure operators. Settings and simulation are data classes with
private constructors and `@ConsistentCopyVisibility`: callers use the admitted
factory and transitions, rather than calling generated `copy` with invalid fields.
The Kotlin compiler initially warned that private constructor visibility alone
still exposed generated copy; the annotation closes that admission bypass.
[Kotlin's documentation](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-consistent-copy-visibility/)
defines the annotation's behavior. Data-class equality lets tests compare whole
snapshots; the engine's internal `copy` stays private.

Compose owns `var simulation by remember { mutableStateOf(...) }`. A transition
returns a new snapshot and assignment publishes it. The bounded trail is separate
presentation state. No renderer or lifecycle is stored in the numeric module.
`LaunchedEffect(running)` obtains frame timestamps through `withFrameNanos` and
cancels on manual pause, native lifecycle loss or leaving composition. Restarting
obtains a fresh baseline. These are session values; Activity recreation is a new
session rather than pretending that `remember` is durable/saveable storage.

The gesture reads the original pointer down with `awaitFirstDown` inside
`awaitEachGesture`; only a down near the Ready ball is consumed. Subsequent events
map total displacement into world velocity, and only a real release launches.
This avoids checking the ball hit target at the post-slop position, which could
reject a fast drag. Cancellation clears aiming. Native sliders/buttons provide
semantic alternatives to this custom precision gesture.

## Verification and limits

Five JVM tests pass, including shared shots, finite inputs/world bounds, numerical
gravity, cadence equivalence and contacts. The Android app builds with the new
module. Focused device interaction evidence is recorded in the app walkthrough.
General multi-body contacts, 3D, spin, saved scores, device performance and CLI
physics combinations remain future work.

## References and questions

- [Compose gesture ownership](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures).
- [Compose drawing coordinates](https://developer.android.com/develop/ui/compose/graphics/draw/overview).
- [Compose effect lifetime](../../../../substrate/okhttp-and-compose-effect-lifetime.md).
- Why does a private data-class constructor also need a copy-visibility policy?
- What state should be retained if the product later promises recreation recovery?
