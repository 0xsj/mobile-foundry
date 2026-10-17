# Observable stores and service protocols

A protocol makes a dependency replaceable; actor isolation and observation solve
separate state-management problems.

## Origin

The notes/query slice on 2026-10-08 uses Swift 6.2.3 and iOS 17+ Observation.
Its service tests and iOS store tests exercise the port and state transitions.
Follow the [services](../modules/packages/FoundryServices/README.md) and
[catalog](../modules/apps/FoundryCatalog/README.md) walkthroughs for actual sources.

## What and why

`any NotesService` stores a value of any conforming implementation. The async
protocol describes domain results, so memory and HTTP adapters can satisfy it
without the consumer naming either concrete type. `Sendable` states that a
dependency can cross concurrency boundaries; it does not make mutable state safe
automatically. The memory adapter's immutable values satisfy that requirement.

`@MainActor` isolates the store's mutable state. `@Observable` supplies change
tracking for properties the view reads. Neither replaces the other. `private(set)`
lets consumers read the state while restricting its mutation to the store.
`@ObservationIgnored` excludes dependency handles, counters and tasks from
observation; changing them is not itself a rendering event.

## Example

Excerpt from the store linked in the catalog walkthrough:

```swift
// Excerpt
@MainActor @Observable
final class NotesStore {
    private(set) var state: QueryState<[Note]> = .idle
    @ObservationIgnored private let service: any NotesService
    // Remaining stored properties and initializer are omitted.
}
```

The view owns the reference with `@State`. That preserves the store across body
evaluation; changing an explicit view identity creates a new ownership scope.
Rendering receives a state value and callbacks instead of constructing dependencies.

## Suspension and cancellation gotchas

Main-actor isolation does not make an async method indivisible. While a load
suspends, another load or cancellation can advance its generation. The resumed
method must verify that it still owns publication. Cancellation asks work to
stop; the generation decides whether a result is still relevant.

An unstructured `Task` handle is canceled explicitly. Awaiting its value does
not by itself forward caller cancellation, so the store uses
`withTaskCancellationHandler` to cancel the child. The screen also cancels the
store when it disappears. A dependency ignoring cancellation can delay settlement;
generation checks still keep its late result out of current state.

## Session ownership extension

The 2026-10-10 session owner uses the same Observation/MainActor separation.
Snapshots are observed, while secret grants, task handles, generations and
dependencies are ignored. Derived lease checks still validate expiry at use.
Its app model retains the clock/authority/controller as one @State reference;
new view values must not pair fresh fixture dependencies with an old controller.

Sign-out awaits the cancelled predecessor before clearing its vault. Shutdown
advances generation and permanently closes that owner while retaining credentials
for a fresh owner. It does not await the predecessor or promise cancellation
rolls back native work. [Session walkthrough](../modules/packages/FoundrySession/README.md)
links controlled continuation tests for those distinct behaviors.

## Used in and related

Use this pattern for a small feature with an injected asynchronous dependency.
It does not implement a shared cache, durable state or background scheduling.
See [provider seams](../../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state)
and [owned cancellation](../../../../notes/concepts/deadlines-and-owned-cancellation.md).
Primary references: [Swift concurrency](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/concurrency/)
and [Observation](https://developer.apple.com/documentation/observation).

## Computed admission needs an observed input

Origin: connected product extraction, 2026-10-17. A credential grant is deliberately
observation-ignored; a computed lease that reads only that grant does not register
an observable dependency for SwiftUI. A view which chooses its signed-out branch
using only that lease can stay on the branch after authentication changes.

Read the public observable snapshot when deciding which branch to render, then
use the lease for actual request admission. This preserves credential privacy and
makes presentation reactive. The computed property itself is not a notification
source; its tracked inputs matter. The product presentation check hosts Library
before sign-in, then observes native form fields appear/disappear through the
controller lifecycle. See the [product walkthrough](../modules/apps/ProductApp/README.md#connected-daily-output)
for source and independently observed behavior.


## Socket cancellation follow-up — 2026-10-17

The LiveSocketTransport protocol is Sendable; its URLSession implementation creates
and owns one ephemeral native session per watch. withTaskCancellationHandler closes
the socket when the owner task is canceled, and defer invalidates the session on
all exits. A small actor serializes message sequence admission; MainActor checks
the exact lease before handing a hint to observable product state. This separates
native callback/resource safety from the account's presentation authority.

Conceptual: canceling a watch does not retroactively stop an already queued
callback, so Task cancellation and the session/generation predicate both matter.
Known network refusal becomes a public failure; unexpected defects propagate.
Read [source](../../packages/FoundrySession/Sources/FoundrySession/AccountLiveHints.swift) and [observed acceptance](../../../../docs/blueprints/native-identity-live.md).
