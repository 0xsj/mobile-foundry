# Swift Result and failure values

Standard Result and enums with associated values describe typed outcomes, while
value semantics and Sendable conformance support immutable failure payloads.

## Origin and evidence

Observed with Swift 6.2.3 on 2026-10-08. Kernel tests exercise branch mapping,
projection, dictionary mutation isolation, and retry bounds. Primary references
are [Result](https://developer.apple.com/documentation/swift/result),
[associated values](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/enumerations/),
and [Sendable](https://docs.swift.org/latest/documentation/swift/sendable/).

## What and why

`Result<Success, Failure>` is a generic enum with `success` and `failure`
branches. Its failure parameter must conform to `Error`. A `typealias` supplies
a convenient name while keeping all standard operations; it does not create a
new runtime wrapper. Error conformance permits standard Result use without
requiring services to throw expected failures.

An enum case can require associated data. An invalid-input case requires both
metadata and a field dictionary; a rate-limited case has optional timing.
`switch` checks exhaustiveness and extracts each case's payload. A struct's
`let` properties cannot be reassigned after initialization.

String-valued dictionaries have value semantics: mutating the caller's map or
an extracted local map does not mutate the failure's stored map. This differs
from storing a mutable reference object. `Sendable` conformance is compiler
checked for the failure's members; it does not make arbitrary success payloads
of a generic Result safe to send across concurrency boundaries.

`init?` is a failable initializer. Negative retry timing returns `nil`; admitted
timing contains an `Int64` value. This lets future external-input adapters
decide how to treat invalid timing without an unchecked integer in the failure.

## Example

From the kernel's successful-absence test:

```swift
// Excerpt
let missing: AppResult<String?> = .success(nil)
#expect(missing.map { $0?.count } == .success(nil))
```

`String?` is an optional success payload. The result remains successful when
the payload is absent. `map` does not treat `nil` as failure.

## Throwing mapping is rejected

Standard `Result.map`, `mapError`, and `flatMap` require non-throwing callbacks.
Throwing dependency work belongs in the enclosing operation or adapter. This
complete compiler example uses only standard-library types:

```swift
// Compile-fail
enum MappingDefect: Error { case unexpected }
let result: Result<Int, MappingDefect> = .success(1)
let mapped = result.map { _ -> Int in throw MappingDefect.unexpected }
```

From the Swift package directory:

```sh
swiftc -typecheck Examples/ThrowingMap.swift
```

Swift 6.2.3 rejected it with an invalid conversion from a throwing function to
a non-throwing function type. The example is excluded from package targets.
Do not use `Result(catching:)` as a universal service wrapper: it converts thrown
errors into values and needs an explicit boundary contract, particularly for
cancellation and defects.

## Immutable references and asset identity

The 2026-10-08 graphics previews need an asset's stable identity across small
edit snapshots. A final class with immutable Sendable members can conform to
Sendable with compiler checking; it does not need unchecked conformance. Data
and Float arrays provide value semantics for their stored content. This differs
from sharing an arbitrary mutable reference across Task boundaries.

`===` and `!==` compare class object identity. Struct equality compares admitted
edit values. Two assets with equal bytes can still have different identity and
therefore deliberately trigger different uploads. Identity is not a persistent
asset ID or content hash. An explicit versioned cache key is an alternative for
a future feature-owned asset cache.

The [graphics walkthrough](../modules/packages/FoundryGraphics/README.md#product-previews)
links actual values, admission/mutation tests and renderer identity guards.
Primary language reference: [classes and structures](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/classesandstructures/).

## Used in and related

Use associated-value enums when cases require different data. Use standard
Result when its typed failure and composition behavior fit the boundary.
The [kernel walkthrough](../modules/packages/FoundryKernel/README.md) links the
actual source, tests, and compiler example.
