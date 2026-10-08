# Service interfaces and observable state

A domain interface makes the provider replaceable, while a ViewModel owns
feature work and exposes observable state without exposing its mutation API.

## Origin

The notes/query slice on 2026-10-08 uses Kotlin 2.3.20, coroutines 1.10.2,
Lifecycle 2.10.0 and Navigation3 1.0.1. Service and ViewModel host tests verify
the domain boundary and its state behavior. Follow the [services](../modules/project/core/services/README.md)
and [catalog](../modules/project/app/README.md) walkthroughs for concrete sources.

## What and why

`fun interface` defines an interface with one abstract operation. A lambda can
implement that interface through SAM conversion, which is useful for catalog
decorators and test services. Production memory and HTTP adapters are named
classes implementing the same suspending domain operation.

Sealed state variants describe Idle, Loading, Loaded and Failed without unrelated
boolean combinations. Previous content is nullable: `null` means no successful
snapshot, while an empty list is a successful snapshot containing no notes.
Checking `isNullOrEmpty` here would accidentally discard that distinction.

The ViewModel owns a private `MutableStateFlow` and exposes `asStateFlow()`.
Consumers can observe the current state but cannot use that public handle to
assign new values. `collectAsStateWithLifecycle` connects collection to the
Android UI lifecycle; it does not decide which request result may publish.

## Example

Excerpt from the service port:

```kotlin
// Excerpt
fun interface NotesService {
    suspend fun list(): AppResult<List<Note>>
}
```

The return type remains a kernel outcome. A native ViewModel can map its branches
directly to screen state; it does not need a query-library exception bridge.

## Lifetime and cancellation gotchas

`viewModelScope` cancels owned coroutines when the ViewModel is cleared.
Navigation entry decorators supply an entry-specific ViewModelStoreOwner;
without that owner, `viewModel()` can bind to a wider activity scope. Compose
disposal cancels the active catalog read even when a keyed ViewModel is retained.

Replacement advances a generation before canceling the prior Job. A coroutine
can resume late, so `ensureActive()` and the generation check precede publication.
CancellationException is rethrown; other Exceptions reach diagnostics only if
the request is still current. Test gates deliberately ignore cancellation to
check this admission policy without relying on sleep timing.

The installed Lifecycle 2.10.0 artifact supplies the navigation entry decorator.
Do not assume every API in unversioned online documentation exists in that
installed artifact. The catalog retains at most eight provider/scenario models
inside one entry; popping it clears them. This is feature state, not durable data.

## Used in and related

See [provider seams](../../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state)
and [owned cancellation](../../../../notes/concepts/deadlines-and-owned-cancellation.md).
Primary references: [functional interfaces](https://kotlinlang.org/docs/fun-interfaces.html),
[StateFlow 1.10.2 source](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/StateFlow.kt),
and [Navigation3 ViewModel scoping](https://developer.android.com/guide/navigation/navigation-3/save-state#scoping-viewmodels).
