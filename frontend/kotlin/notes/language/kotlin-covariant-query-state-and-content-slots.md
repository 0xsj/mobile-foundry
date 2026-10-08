# Kotlin covariant state and content slots

Covariant query values and a composable content lambda let one component
render unrelated immutable domain snapshots.

## Origin and evidence

Introduced 2026-10-08 with Kotlin 2.3.20, AGP 9.0.1, and Compose BOM 2026.03.01.
Shared query host tests, ViewModel regressions, and device presentation tests
passed. The [query](../modules/project/core/query/README.md) and
[UI](../modules/project/core/ui/README.md) walkthroughs link their usage.

## State mechanics

```kotlin
// Excerpt
sealed interface QueryState<out Value : Any> {
    data object Idle : QueryState<Nothing>
}
```

`Value : Any` excludes null payloads, leaving null to mean no snapshot. Empty
collections and strings remain valid payloads. A domain value can represent
successful absence when needed.

`out` makes the type covariant: values can be read as a wider type, but the
interface cannot consume Value in a member parameter. `Nothing` has no values
and is a subtype of every Kotlin type; combined with covariance, the single
Idle object fits any query payload. Data classes own phase-specific payloads
and structural equality. None of this freezes a mutable list passed by a caller.

Generic extension functions supply `starting`, `settled`, and `restored`.
They declare their own type parameter, allowing a settled result parameter
without violating the interface's producer-only variance. Their receiver and
result retain the same payload type. Projection properties also use extensions;
import them when reading a value through QueryState rather than a concrete case.

## Content mechanics

```kotlin
// Excerpt
content: @Composable (Value) -> Unit
```

This lambda emits the caller's domain views. It is a UI slot, not an async
callback. The component calls it only for a present, non-empty snapshot.
Refresh/cancel are ordinary callbacks invoked by native buttons.

## Gotchas and related reading

State transformations are synchronous and perform no coroutine admission.
StateFlow and main-thread mutation remain ViewModel concerns. Read
[interfaces and StateFlow](kotlin-service-interfaces-and-stateflow.md) and
[Kotlin generics](https://kotlinlang.org/docs/generics.html), particularly
declaration-site variance. The [shared ownership pattern](../../../../notes/patterns/query-state-and-rendering.md)
explains why this extraction stops before a request runtime.
