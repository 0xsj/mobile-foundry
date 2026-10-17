# Kotlin suspending ports and cancellation

A suspending port returns expected failures as Outcome values and keeps coroutine cancellation in control flow.

## Origin

Kotlin 2.3.20 and kotlinx.coroutines 1.10.2, observed 2026-10-08. Primary API:
[suspendCancellableCoroutine](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html),
[withTimeoutOrNull](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout-or-null.html).
Executed host tests cover the selected boundaries.

## What and why

A `suspend fun` can pause without blocking its calling thread. It does not itself
select a dispatcher or create background work. `fun interface HTTPTransport`
contains one abstract suspending operation, so tests can inject a suspending
lambda. The domain result remains `Outcome`; suspension does not change its
value/error vocabulary.

`suspendCancellableCoroutine` bridges OkHttp callbacks. It installs a native
Call cancellation handler and resumes with a result or an unexpected exception.
Callback code closes every response body with `use`, including a response
arriving after the coroutine was canceled. The continuation has prompt
cancellation behavior; an attempted resume does not grant permission to publish
a stale successful result.

## Example

```kotlin
// Conceptual: omitted values use the HTTP module's declared types.
val transport = HTTPTransport { request ->
    currentCoroutineContext().ensureActive()
    Outcome.Ok(WireResponse(204, body = byteArrayOf()))
}
```

`withTimeoutOrNull(budget)` returns null for its own timeout, which the client
projects into a timeout failure. Caller cancellation and nested cancellation
still throw. `ensureActive()` checks before sending and before returning data.
An adapter that catches every Exception and returns unavailable would swallow
CancellationException; the adapter catches known IOExceptions only.

The JSON library's `JsonElement` tree represents wire data without generating
endpoint classes. `JsonObject` admission and `JsonPrimitive.isString` make the
health service's schema choice explicit. A kotlinx serialization compiler
plugin is unnecessary for parsing this tree; generated serializers for
application classes are a separate use.

## Gotchas

`finally` executes on cancellation, but cancellable suspension inside cleanup
requires an explicit policy. Ordinary response.close does not suspend.
Coroutine debug stack-trace recovery can copy standard exceptions and retain
the original as cause. A custom defect carrying state establishes that the
client adds no classification or replacement; universal object identity is
not a guarantee of the coroutine runtime. The versioned
[stack-trace recovery source](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/internal/StackTraceRecovery.kt)
shows exception copying and cause preservation.

## Used in

Callback adapters, endpoint services, and Compose effect-owned requests.
The module walkthrough supplies actual source and check links.

## Related

[Outcome payloads](kotlin-outcomes-and-owned-payloads.md),
[UI state types](kotlin-sealed-ui-states-and-data-classes.md), and
[OkHttp and effect lifetime](../substrate/okhttp-and-compose-effect-lifetime.md).


## Finally and early stale-result returns — 2026-10-17

Claim: finally runs on early return, so an unconditional flag reset can discard
information needed to classify an interrupted operation. Origin: WorkspaceModel's
six state checks on Kotlin 2.3.20/coroutines 1.10.2. The primary
[exception reference](https://kotlinlang.org/docs/exceptions.html#finally-block)
describes scope cleanup; the owner tests establish our result-publication policy.

Excerpt from the submitted operation's cleanup:

```kotlin
// Excerpt: WorkspaceModel submitted-operation cleanup.
if (expected == generation) {
    if (creating && !controller.isCurrent(lease)) uncertainOwner = owner(lease)
    mutableState.value = state.value.copy(busy = false); creating = false; task = null
}
```

This block is inside finally. A return@launch after the lease check still executes
it. The first version reset creating before any stale-lease path retained the
original uncertainty; its focused test failed. Record that evidence before clearing
the flag. The generation guard prevents cleanup from overwriting a replacement
operation. No suspending cleanup is needed here. A plain suspendCoroutine test
double deliberately finishes late, demonstrating why ensureActive plus current
lease/generation checks are separate from provider cooperation.

Used in the app-owned workspace overlay; the reusable service still propagates
cancellation. Read [the shared pattern](../../../../notes/patterns/current-state-is-not-a-command-receipt.md)
and [execution evidence](../../../../docs/blueprints/native-workspaces.md).
Next: which facts must finally preserve even when success is no longer publishable?
