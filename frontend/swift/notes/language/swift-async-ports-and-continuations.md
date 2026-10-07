# Swift async ports and continuations

An async throwing protocol can return expected failures as values while preserving cancellation and defects as control flow.

## Origin

Swift 6.2.3, HTTP slice inspected and executed 2026-10-08. Swift's
[concurrency guide](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/concurrency/)
and [continuation API](https://developer.apple.com/documentation/swift/checkedcontinuation)
document the mechanics; host tests establish this implementation's behavior.

## What and why

`any HTTPTransport` stores a concrete implementation behind a protocol
existential. `async` permits suspension; `throws` preserves errors the boundary
does not classify; `AppResult<WireResponse>` carries expected failure data.
`Sendable` describes values safe to pass across concurrency domains. It does
not make mutable state safe automatically.

A callback API cannot suspend directly. A checked throwing continuation bridges
it into async code: completion resumes with a value or throws the original
unexpected error. Every installed continuation must resume exactly once. A
cancellation handler cancels the native task, including cancellation before
that task has been installed. `NSLock` protects this install/cancel race and
the delegate's pending-task dictionary. Continuations resume outside the lock.
`@unchecked Sendable` places proof of synchronization on the implementation.
An actor is an alternative, but these delegate callbacks are synchronous and
the small protected state must be updated before returning.

## Example

```swift
// Conceptual: the omitted adapter implements the protocol and native lifecycle.
func read(using transport: any HTTPTransport, request: PreparedRequest) async throws -> AppResult<WireResponse> {
    try Task.checkCancellation()
    return try await transport.send(request)
}
```

The deadline uses `withThrowingTaskGroup`: exchange and timer are child tasks;
the first answer wins, then `defer` cancels the loser. Scope exit waits for both
children. This keeps cleanup structured but requires cooperative adapters.

`JSONValue` is an `indirect enum` because objects and arrays recursively contain
JSONValue. Its manual `Codable` conformance selects the JSON branch from a
single-value decoding container. `try?` here tries alternative JSON types,
not a generic transport exception classifier. Failed admission ultimately
throws DecodingError. Finite Double numbers keep wire arithmetic bounded;
identifiers needing exact large integers should be strings.

## Gotchas

Swift task cancellation sets a flag; it does not forcibly interrupt arbitrary
code. Check the flag before sends and after suspension. A canceled view task
must also check before publishing state. `Result.get()` throws a stored Failure;
use it when deliberate error handling surrounds configuration, while service
results are switched explicitly.

## Used in

Native callback adapters, injected service ports, and screen-owned requests.
The HTTP module walkthrough links the concrete source and tests.

## Related

[Protocols and opaque returns](swift-protocols-and-opaque-return-types.md),
[Result values](swift-result-and-failure-values.md), and
[URLSession ownership](../substrate/urlsession-and-view-task-lifetime.md).
