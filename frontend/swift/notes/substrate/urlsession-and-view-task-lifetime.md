# URLSession and view-task lifetime

A transport owns its URLSession while each SwiftUI task owns one cancellable service operation.

## Origin

Observed 2026-10-08 with Swift 6.2.3, Xcode 26.2, and iOS deployment target 17.
Primary APIs: [URLSession](https://developer.apple.com/documentation/foundation/urlsession),
[SwiftUI task](https://developer.apple.com/documentation/swiftui/view/task(id:name:priority:file:line:_:)).
Host adapter tests and the iOS simulator build passed. Apple's current reference
includes newer naming arguments; the checkout uses the `.task(id:)` call form
accepted by the Xcode 26.2 SDK.

## What and why

The adapter copies an ephemeral session configuration, disables cookies,
credentials and cache, and refuses redirects in the session delegate. The data
delegate retains received status and headers while collecting body bytes, so
a body-read failure can preserve status classification. Cancellation calls
URLSessionDataTask.cancel. Invalidation and deinitialization cancel owned tasks.
The diagnostic observer receives original known dependency errors separately
from public Failure values; it must not throw or block.

The catalog uses `@State` to let SwiftUI retain selection and phase for a view
identity. `$scenario` projects a Binding so Picker can read and update that
state; `scenario` reads the value itself. This is UI memory, not persisted
application data. The catalog's `.task(id:)` owns loading work. Its ID includes the selected
scenario and a replay counter. SwiftUI cancels the old task on replacement or
view removal. State is set on the main actor and checked for cancellation
before publication. This is foreground view lifetime, not background execution.

## Example

Healthy uses a 250 ms injected response to make loading visible. Timeout uses a
100 ms budget with a 1000 ms injected exchange. Both still use the real HTTP
client and health admission. Native adapter tests separately use URLProtocol
and real loopback sockets.

## Gotchas and limits

An ephemeral configuration can still inherit caller-supplied protocol classes,
network policies or additional headers. Configure production trust and network
policies deliberately. Buffered bodies are suitable for the bounded exemplar;
streaming and size budgets need another contract. A build and these host tests
do not establish radio loss, physical-device performance, or OS background timing.

## Used in

Application-scoped transports and cancellable SwiftUI feature reads.

## Related

[Async ports](../language/swift-async-ports-and-continuations.md),
[build wiring](swift-package-and-xcode-project-wiring.md).
