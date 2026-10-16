# Async streams, buffering and subscription cleanup

Claim: a bounded native stream and an explicit subscription owner provide
independent live readers without retaining a SQLite handle or every write event.

Origin/evidence: observation slice, 2026-10-10. Tested with Swift 6.2.3,
Xcode 26.2, macOS 26.3 and iOS 26.2 Simulator; deployment remains iOS 17/macOS 14.
Primary reference: [SE-0314](https://github.com/swiftlang/swift-evolution/blob/main/proposals/0314-async-stream.md).
Its continuation yields without waiting for consumption; bufferingNewest drops
old buffered elements. Termination callbacks bridge cleanup to a producer.

## What and why

NotesStream carries AppResult arrays in AsyncThrowingStream. A failure value can
be rendered while observation remains active; throwing ends that subscription.
NotesSubscription pairs values with explicit idempotent cancellation. Consumers
defer its cancel even if iteration breaks early. Never-iterated streams can be
canceled too. This is our owned API, not a promise that a loop break cancels all
native producer resources automatically.

The standard-library sequence fits the existing async/throws service ports and
Task owners. A Combine publisher could be adapted for a product already using
Combine; the present feature needs no extra observation framework or shared cache.

Memory registers continuations inside its actor and yields owned Array snapshots.
Its termination handler schedules actor-isolated removal. SQLite first registers
a path listener, then starts a task that reads current data after each signal.
Both the signal and snapshot buffers keep at most one pending element. A lock
protects the process registry; yields occur outside the lock. The producer's
defer unregisters its listener, while outer termination cancels that producer.

```swift
// Conceptual: a consumer owns cleanup even when it exits iteration early.
let subscription = try await observer.observe()
defer { subscription.cancel() }
for try await answer in subscription.values {
    try Task.checkCancellation()
    applyCurrentSnapshot(answer)
}
```

## Actual use and gotchas

LiveNotesStore runs on MainActor and owns a generation, task and optional lease
predicate. It checks cancellation and admission on values/errors, including after
diagnostics. Pause retains its query value; shutdown clears it permanently.
The SwiftUI route retains sources in @State above storage branches. Scene activity
and view disappearance pause collection; active resumption rereads current data.
An explicitly paused second reader stays paused.

Observed: eleven Services tests, fourteen Data tests and 78 hosted app cases pass.
Repository tests cover owned snapshots, bursts, concurrent instance notifications,
registration/read races and registry cleanup after cancellation, including unused
subscriptions. Hosted owner checks cover the portable scenario, recovery, late
defects, closed admission and retained Memory/device source lifetimes. The final
simulator build passes. These are native/model checks, not manual iOS gallery taps,
VoiceOver, process death or physical-device performance.

Related: [Services](../modules/packages/FoundryServices/README.md#live-read-capability),
[Data](../modules/packages/FoundryData/README.md#live-local-observation),
[catalog](../modules/apps/FoundryCatalog/README.md#live-notes)
and [shared reasoning](../../../../notes/patterns/current-snapshots-and-subscription-lifetime.md).
Next: when should multiple views deliberately share a cached projection instead
of owning independent scoped reads?
