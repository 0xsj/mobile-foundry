# CryptoKit partitions and authenticated URLSession

Native digest naming and URLSession transport ownership can remain independent of account feature lifetime.

## Origin, versions and evidence

Observed 2026-10-10 with Swift 6.2.3, Xcode 26.2, macOS 26.3 and iOS 26.2
Simulator; package minima remain iOS 17/macOS 14. Primary references:
[CryptoKit SHA256](https://developer.apple.com/documentation/cryptokit/sha256),
[URLSessionConfiguration](https://developer.apple.com/documentation/foundation/urlsessionconfiguration)
and [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750#section-2.1).
Compiled/observed tests verify native digest output against shared fixtures and
an owned Authorization header through URLSession with a URLProtocol fixture.
The latter exercises the native API, not a remote server or TCP/TLS exchange.

## What and why

CryptoKit hashes canonical UTF-8 data and exposes digest bytes. Formatting every
byte as two lowercase hex digits yields a fixed 64-character partition name.
Do not use Swift Hasher for saved filenames: it is an in-process hashing facility,
not a persistent cross-process digest contract. Synthesized Hashable on the
lease instead serves SwiftUI identity: owner, scope and epoch all participate.

The session adapter implements the existing asynchronous HTTPClient port on
MainActor. Calls suspend into the supplied transport and resume on the owner
actor before admitting a result. Async protocol conformance does not require
storing SwiftUI types in the networking layer. A main-actor reference is Sendable;
the actor serializes access, while cancellation and epoch checks still matter
across suspension.

## Example and gotchas

```swift
// Conceptual: stable data identity differs from transient view identity.
let partition = SHA256.hash(data: Data(canonical.utf8))
let name = partition.map { String(format: "%02x", $0) }.joined()
accountEditor.id(lease)
```

The digest does not encrypt notes. A new lease to the same account must get a
fresh editor even though its disk filename remains the same. Native URLSession
configuration disables ambient credential/cookie storage and the owned transport
refuses redirects. Bearer syntax is admitted before request preparation; caller
Authorization overrides are rejected. HTTPS is the default; an explicit loopback
HTTP option enables local development without weakening certificate validation.

## Used in and next

Read the [Session](../modules/packages/FoundrySession/README.md#authenticated-http),
[Data](../modules/packages/FoundryData/README.md#account-collections) and
[catalog](../modules/apps/FoundryCatalog/README.md#account-access) walkthroughs.
Controlled transport continuations establish late-result suppression; shared
partition fixtures and actual SQLite reads establish naming/persistence.
Physical device, OS backup and production HTTPS/provider behavior remain untested.
Next: what external callback should own a connected session, and how should
account records be classified for device backup and protection?
