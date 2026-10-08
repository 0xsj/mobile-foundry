# Swift generic state and content builders

Generic value state and a ViewBuilder content slot let one native component
render unrelated domain payloads without knowing their services.

## Origin and evidence

Introduced by the query/UI extraction, 2026-10-08, using Swift 6.2.3 and
SwiftUI from Xcode 26.2. Query tests and the iOS consumer build passed.
The [query walkthrough](../modules/packages/FoundryQuery/README.md) and
[UI walkthrough](../modules/packages/FoundryUI/README.md) link the actual use.

## State mechanics

`Value` is a type parameter, so QueryState of notes and QueryState of a string
share structure while retaining their payload types. `Value: Sendable` requires
the payload to be safe to transfer under Swift's concurrency rules. It does
not deep-copy reference types.

```swift
// Excerpt
extension QueryState: Equatable where Value: Equatable {}
```

This conditional conformance provides equality only when the payload also
supports equality. The compiler synthesizes comparisons of enum cases and
associated values. It avoids forcing equality on every future payload.
Associated `Value?` distinguishes absent previous data from a present empty
collection or string. The shared tests exercise both cases.

## Content mechanics

```swift
// Excerpt
@ViewBuilder content: @escaping (Value) -> Content
```

The caller's closure supplies domain rendering. ViewBuilder permits multiple
views and conditional view construction; the resulting concrete view type is
the generic `Content: View`. `@escaping` permits storing that closure until
the component's body evaluates. A generic parameter keeps its concrete type
without erasing it through AnyView.

The component returns grouped rows instead of owning a List. A host can use
those rows inside its own Section or stack. Services and task lifetimes remain
outside the content closure.

## Gotchas and related reading

Public transformations return new states; they do not start tasks or reject
obsolete results. Observation remains a store responsibility. Follow
[observable stores](swift-observable-stores-and-service-protocols.md) and
[opaque view types](swift-protocols-and-opaque-return-types.md).
Primary references: [Swift generics](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/generics/)
and [Apple ViewBuilder](https://developer.apple.com/documentation/swiftui/viewbuilder).
