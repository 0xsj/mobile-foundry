# Swift protocols and opaque return types

An opaque return type exposes a protocol requirement while preserving a
specific underlying type chosen by the implementation.

## Origin

The catalog's `View` conformance uses `var body: some View`. The Swift language
guide explains that `some` hides the concrete result type while retaining its
identity, unlike a boxed `any` protocol value. Read [opaque and boxed types](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/opaquetypes/).
Reviewed 2026-10-07 with Swift 6.2.3; the existing app compiled during bootstrap.

## What and why

In `struct CatalogView: View`, the colon declares conformance to a protocol.
The computed `body` property returns a composed view. Writing `some View`
lets the implementation select its concrete type without putting the entire
composition into the property's signature. [SwiftUI's View reference](https://developer.apple.com/documentation/swiftui/view/)
describes `body` as the hierarchy supplied by a custom view.

This preserves the implementation's type information. It differs from a
generic parameter, where the caller supplies the type, and from `any View`,
which is an existential protocol type.

## Example

Selected from the catalog; the omitted list contents and SwiftUI import are
provided by the source linked in the module walkthrough:

```swift
// Excerpt
struct CatalogView: View {
    var body: some View {
        NavigationStack {
            List {
                // Catalog sections are defined here.
            }
            .navigationTitle("Mobile Foundry")
        }
    }
}
```

## Gotchas

An ordinary opaque-returning function must have matching underlying return
types across its return paths. SwiftUI builder composition has additional
rules; study those rules when adding conditional layouts. Avoid inferring
rendering performance or thread safety from `some` alone.

## Used in and related

Use opaque return types when a caller needs a protocol's behavior while the
implementation owns the concrete type. The catalog walkthrough connects this
mechanism to its app and root view. Result builders and state ownership are
separate topics to investigate when the first interactive component is added.
