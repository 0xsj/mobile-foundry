# Flow invalidation and lifecycle collection

Claim: cold subscriptions with explicit cleanup let independent readers follow
local commits while their native screen owner controls collection lifetime.

Origin: observation batch, 2026-10-10. Tested Kotlin 2.3.20, coroutines 1.10.2,
AGP 9.0.1, JDK 17, Compose BOM 2026.03.01 and API 36 emulator; min SDK remains 24.
Primary references: pinned coroutines 1.10.2
[callbackFlow source/documentation](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/Builders.kt)
and [StateFlow source/documentation](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/StateFlow.kt).

## What and why

callbackFlow registers on collection. awaitClose unregisters on completion or
cancellation, and buffer(Channel.CONFLATED) bounds pending invalidations. Registry
registration/removal must be thread-safe because cleanup can race callbacks.
StateFlow retains current state and conflates equal values; a slow collector can
skip intermediate updates. These semantics fit snapshots rather than write receipts.
Flow fits the app's existing coroutine ownership and StateFlow rendering. A
product using Room could adapt its invalidation API behind the same domain port.

Memory keeps owned immutable List copies in MutableStateFlow, publishing inside
the CRUD mutex. SQLite registers a process-local path callback before the initial
read, maps invalidations to list(), and suppresses equal results. Each read owns
its helper/handle; observation holds none open. Successful-write invalidation
happens inside IO after commit, before returning through withContext, where a
canceled caller might lose its acknowledgement. This placement is source evidence;
the native checks observe commits and independent readers, not every possible OS
cancellation scheduling point.

```kotlin
// Conceptual: each feature owns its collector and cancels it on pause/close.
val collector = scope.launch {
    observer.observe().collect { answer -> applyCurrentSnapshot(answer) }
}
collector.cancel()
```

## Actual use and gotchas

LiveNotesReader owns one lazy Job, assigned before it starts, and one generation
on the UI executor. It admits values/errors against the current generation and
optional session lease. Expected read failures remain Flow values; exceptions end
collection and reach the current diagnostic boundary. Pause keeps prior content,
restart rereads, and terminal shutdown clears data and prevents deferred restart.

The route ViewModel retains source bundles across storage branches and Activity
recreation. CatalogPresentationModel owns the nested catalog ViewModelStore above
composition; Activity recreation retains it and explicit dismissal clears it.
repeatOnLifecycle(STARTED) collects only while the screen is active. The account
parent disposes its reader on lease replacement or closure. StateFlow collection
for rendering does not itself own the repository subscription.

JVM tests cover the shared scenario, independent cleanup, read failure recovery,
late defects and reentrant admission closure. Native SQLite and gallery results
are recorded in the [app handoff](../modules/project/app/README.md#live-notes).
No cross-process invalidation, background worker, remote sync, device benchmark
or TalkBack traversal is established. Memory remains process-local, regardless
of saved route selection.

Related: [Services](../modules/project/core/services/README.md#live-read-capability),
[Data](../modules/project/core/data/README.md#live-local-observation),
[existing SQLite ownership](sqlite-helpers-and-nondestructive-recovery.md)
and [shared reasoning](../../../../notes/patterns/current-snapshots-and-subscription-lifetime.md).
Next: when does a product need Room invalidation, shared caching or foreground
refresh beyond this explicit in-process adapter?


## Owned socket callbacks — 2026-10-17

Claim: a callbackFlow must own native cleanup and bound queued callbacks, while
protocol/scope admission stays in its service layer. Origin: account live hints,
OkHttp 5.4.0 and coroutines 1.10.2. Read [adapter](../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/AccountLiveHints.kt).

Native callbacks only trySend text; the collector validates ordered ready/changed
frames on the session's executor. Sixteen queued callbacks are allowed; overflow
closes the connection rather than accumulating messages. awaitClose cancels the
socket, evicts its dedicated pool and shuts down its executor. The feature owner
cancels collection on background/lease change and applies generation guards to
presentation. Known IOException is sanitized; cancellation remains cancellation.

Conceptual: a late old-session onMessage may reach a closed channel, but cannot
populate a replacement account through an old collector. Text size is checked
after OkHttp has materialized a callback; it is not an arbitrary-peer allocation
cap. Dedicated explicit API selection and backend bounds remain relevant.
Real native acceptance separately observes ready/changed and local SMTP lifecycle;
see [dated run](../../../../docs/blueprints/native-identity-live.md).
Next: keep broker/push/background scheduling separate from this foreground flow.
