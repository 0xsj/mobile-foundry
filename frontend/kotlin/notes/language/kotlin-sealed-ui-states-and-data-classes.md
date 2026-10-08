# Kotlin sealed UI states and data classes

A sealed hierarchy describes a controlled set of state variants, and data
classes attach the payload needed by each variant.

## Origin

The generated screen declares Loading, Success, and Error variants. Reviewed
2026-10-07 against Kotlin's [sealed hierarchy](https://kotlinlang.org/docs/sealed-classes.html),
[data class](https://kotlinlang.org/docs/data-classes.html), and
[delegated property](https://kotlinlang.org/docs/delegated-properties.html)
references. The app using these types compiled with Kotlin 2.3.20 during bootstrap.

## What and why

`sealed interface` restricts direct implementations to the same module and
package. The compiler can check exhaustive `when` handling. This fits a state
whose variants have different payloads instead of combining independent flags.

`object Loading` declares a singleton. `data class Success(val data: List<String>)`
declares a payload property and receives generated equality, hashing, copying,
and other data operations. The constructor's `val` makes the property read-only;
it does not make every object reachable through it deeply immutable.

## Example

Selected from the view model; the module walkthrough links the full file:

```kotlin
// Excerpt
sealed interface MainScreenUiState {
  object Loading : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
  data class Success(val data: List<String>) : MainScreenUiState
}
```

The screen uses `when (state)` to choose a branch; `is Success` tests a variant's
type. Its `val state by ...` declaration is a delegated property: reading the
property calls the delegate's getter. Lifecycle collection and Compose's state
delegate are framework behavior, separate from the Kotlin `by` syntax.

## Gotchas

A data class's `copy` is shallow. Sealed cases do not determine which failures
are expected, who owns asynchronous work, or how a screen should present an
error. Carrying `Throwable` in generated UI state does not establish a typed
failure contract or a safe user-facing message.

## Admitted data and copy visibility

The 2026-10-08 graphics previews add private-constructor data classes with bounded
factories. A private constructor alone historically did not make generated copy
private. [ConsistentCopyVisibility](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-consistent-copy-visibility/)
opts into matching copy/constructor visibility. The initial Kotlin 2.3.20 build
reported a migration warning; adding the annotation removed it. Init requirements
also protect the stored invariants. An ordinary class with manually implemented
equality is an alternative when generated data-class behavior is unsuitable.

Excerpt from the graphics values:

```kotlin
// Excerpt
@ConsistentCopyVisibility
data class ImageViewport private constructor(val zoom: Float, val x: Float, val y: Float)
```

Asset classes deliberately use ordinary class identity rather than data-class
array equality. Their constructors copy caller arrays and expose no mutable
storage. A `val ByteArray` alone would only prevent reassigning the reference;
it would not protect the elements. See the [graphics walkthrough](../modules/project/core/graphics/README.md#product-previews)
for the actual factories, init checks and mutation-isolation tests.

## Used in and related

Use variants when each state has a distinct shape and callers need explicit
handling. The starter walkthrough links the actual state production and
rendering. Domain outcomes and UI state can use related language mechanisms
while owning different meanings.
