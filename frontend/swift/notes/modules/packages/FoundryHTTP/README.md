# Swift HTTP walkthrough

The HTTP module admits requests and wire responses while keeping domain validation, retry policy, and presentation outside its boundary.

## Origin

Implemented and checked 2026-10-08. This note mirrors `packages/FoundryHTTP/`.
[HTTP contract](../../../../../../contracts/behavior/http.md) controls behavior; [Construction blueprint](../../../../../../docs/blueprints/http-health.md) records construction and deviations.

## Reading order

1. [HTTPTypes.swift](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/HTTPTypes.swift).
2. [RequestPreparation.swift](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/RequestPreparation.swift).
3. [DefaultHTTPClient.swift](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/DefaultHTTPClient.swift).
4. [ProblemDecoder.swift](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/ProblemDecoder.swift).
5. [JSONValue.swift](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/JSONValue.swift).
6. [URLSessionTransport.swift](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/URLSessionTransport.swift).

Tests: [HTTPTests.swift](../../../../packages/FoundryHTTP/Tests/FoundryHTTPTests/HTTPTests.swift), [TransportTests.swift](../../../../packages/FoundryHTTP/Tests/FoundryHTTPTests/TransportTests.swift), [LoopbackTransportTests.swift](../../../../packages/FoundryHTTP/Tests/FoundryHTTPTests/LoopbackTransportTests.swift). Both platforms read [Shared response fixtures](../../../../../../contracts/fixtures/http/responses.json).

## Walkthrough

The factory validates the configured API base and default timeout. A relative
`health/ready` path under a `/v1` base becomes `/v1/health/ready`. Query pairs
preserve repeated names. Preparation validates traversal, strict UTF-8 percent
encoding, method/body rules and headers before any native send. Common headers
are normalized first; per-call headers then override them case-insensitively.
Accept is deliberate and Content-Type is present only for supplied JSON bodies.

The client begins with a cancellation check, admits the per-call budget, and
runs exchange within an owned deadline. Zero budget never sends. An expected
transport failure passes through as a value; unexpected errors propagate.
The native adapter owns call cancellation and buffered body resources.
[Async ports](../../../language/swift-async-ports-and-continuations.md) explains the async syntax and [Native transport lifetime](../../../substrate/urlsession-and-view-task-lifetime.md) the SDK lifetime.

The client retains response metadata, admits JSON, and decodes non-2xx problems.
Malformed successful JSON is internal invalid_response; malformed or broken
error JSON preserves status fallback. Problem kind may override that fallback.
Only selected string fields become failure payloads. Request ID and correlation
precedence are deliberate. ETag stays response metadata. Retry timing uses an
injected clock and rejects overflow; it does not trigger a retry.

## Verification and limits

`make http-test` runs both packages/modules. 13 Swift HTTP tests passed, including real loopback exchanges and URLSession cancellation.
The app consumer also compiled. These checks establish admission, metadata,
known network behavior and cooperative cancellation for the tested scenarios.
They do not establish production TLS policy, radio loss, streaming bounds,
background execution or retry safety. [Fixture and adapter checks](../../../../../../notes/techniques/shared-fixtures-and-native-adapters.md) separates fixture evidence
from native SDK evidence.

## Gotchas

A WireResponse null body means read failure; empty bytes mean empty content.
Successful HEAD/204/205 ignore body admission and expose JSON null. Invalid
health shapes are still a service responsibility. Failure public projection
happens at presentation; private causes stay with diagnostic observers.

## Questions for the next session

- Why can valid JSON still fail health admission?
- Which two owners cancel a request, and which one returns timeout data?
- What backend evidence would make an uncertain write safe to retry?

## Related

[Health services](../FoundryServices/README.md), [Kernel walkthrough](../FoundryKernel/README.md), and [Deadlines and cancellation](../../../../../../notes/concepts/deadlines-and-owned-cancellation.md).

## Files binary boundary — 2026-10-17

Claim: an account-bound file reader/writer can share the existing expected-failure,
HTTP and exact-session-lease policy while carrying immutable admitted binary bytes.
Origin: Files slice; [the source](../../../../packages/FoundryHTTP/Sources/FoundryHTTP/HTTPTypes.swift) and [the behavior contract](../../../../../../contracts/behavior/files-media.md).

JSON and binary request bodies are mutually exclusive. Generic transports bound
response buffering at 16 MiB; Files accepts 1 byte–10 MiB and verifies type/size/
SHA-256 after download. Provider errors remain failures and cancellation retains
native identity. Neither transport renews credentials or replays uploads. Swift
Data uses value semantics; Kotlin binary bodies/results copy arrays on ownership
boundaries. Binary responses decode problem JSON on HTTP failure.

The catalog selects HTTPRemoteFilesRepository; its reader/writer interfaces can
be replaced without camera/editor or provider SDK dependencies. Pending command
input stays with its original account through this owner's renewal. A photo
selected under an older scope generation cannot start a new account's upload.

Verification: HTTP/session regressions, real Go raw-byte transfers and dropped
request/completion receipt recovery passed on URLSession/OkHttp. Both catalogs
build; each platform passes two file-owner cases. These observations do not
establish photo-picker gestures, background transfers, durable pending input or
cloud storage. Related: [object lifecycle reasoning](../../../../../../backend/go/notes/patterns/metadata-and-object-lifecycles.md). Next: what extra ownership is needed when uploads
must survive native process termination?
