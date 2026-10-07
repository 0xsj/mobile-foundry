# Kotlin outcomes and owned payloads

A covariant sealed outcome keeps both branches typed, while copied and wrapped
collection payloads prevent callers from mutating a failure after construction.

## Origin and evidence

Observed with Kotlin 2.3.20 on 2026-10-08. Tests exercise mapping branches,
exception identity, validation-map mutation, and retry bounds. Primary references:
[generics and variance](https://kotlinlang.org/docs/generics.html),
[collections](https://kotlinlang.org/docs/collections-overview.html),
[inline functions](https://kotlinlang.org/docs/inline-functions.html), and
[value classes](https://kotlinlang.org/docs/inline-classes.html).

## What and why

The standard Kotlin `Result<T>` fixes its failure payload to `Throwable`. A
foundation result with ordinary typed failure data instead uses a sealed
`Outcome<Value, Problem>`. `Ok` carries only Value and `Err` carries only Problem;
the compiler checks exhaustive `when` branches. The earlier
[sealed-state note](kotlin-sealed-ui-states-and-data-classes.md) explains that
mechanism and data class equality.

`out` declares covariance: the outcome produces its parameter values, and an
outcome of a narrower type can be used where a wider type is expected. `Nothing`
has no values and is a subtype of every type, so the inactive branch does not
need a fabricated payload. An `Ok<Int>` can fit an outcome whose Problem is
Failure, and an `Err<Failure>` can fit one whose Value is Int.

A generic extension function names its type parameters before its receiver.
The small mapping extensions use `inline` so their callback and branch body
can be inlined at the call site. They do not catch exceptions or schedule work.
Thrown callback defects and cancellation exceptions retain their identity.
Inline callbacks can also use non-local returns; callers should use deliberate
control flow rather than assume every callback returns to its extension.

`val` prevents property reassignment, while `Map` offers a read-only interface.
Neither prevents aliasing an underlying mutable map. The invalid variant copies
its input to a privately owned LinkedHashMap, then exposes an unmodifiable JDK
wrapper. Its keys and values are immutable strings. It uses a regular class
with value equality so a generated data-class `copy` cannot bypass ownership.
Generic successful payloads are not automatically immutable.

`@JvmInline value class RetryAfter` introduces a distinct millisecond type. Its
private constructor and nullable factory prevent negative values through the
public API. It may be boxed when used as nullable or generic data; the type
boundary does not promise allocation-free behavior in every context.

## Example

From the outcome tests:

```kotlin
// Excerpt
val missing: AppResult<String?> = Outcome.Ok(null)
assertEquals(Outcome.Ok(null), missing.map { it?.length })
```

The nullable success remains success. The error parameter is still a typed
Failure, selected by the alias. A lambda that only throws may need an explicit
result type because it supplies no successful value for type inference.

## Gotchas and actual use

This module is Kotlin/JVM and uses JDK collection support; it is not a Kotlin
Multiplatform artifact. Field-map ownership is tested on the JVM, including a
write attempt through a MutableMap cast. Async coroutine cancellation remains
an adapter responsibility; synchronous exception propagation does not establish
task ownership or cancellation behavior for a future network call.

The [kernel walkthrough](../modules/project/core/kernel/README.md) links the
source and executable tests using these mechanics.
