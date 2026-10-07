# Kotlin HTTP walkthrough

The HTTP module admits requests and wire responses while keeping domain validation, retry policy, and presentation outside its boundary.

## Origin

Implemented and checked 2026-10-08. This note mirrors `project/core/http/`.
[HTTP contract](../../../../../../../contracts/behavior/http.md) controls behavior; [Construction blueprint](../../../../../../../docs/blueprints/http-health.md) records construction and deviations.

## Reading order

1. [HTTPTypes.kt](../../../../../project/core/http/src/main/kotlin/dev/mobilefoundry/http/HTTPTypes.kt).
2. [RequestPreparation.kt](../../../../../project/core/http/src/main/kotlin/dev/mobilefoundry/http/RequestPreparation.kt).
3. [DefaultHTTPClient.kt](../../../../../project/core/http/src/main/kotlin/dev/mobilefoundry/http/DefaultHTTPClient.kt).
4. [ProblemDecoder.kt](../../../../../project/core/http/src/main/kotlin/dev/mobilefoundry/http/ProblemDecoder.kt).
5. [OkHttpTransport.kt](../../../../../project/core/http/src/main/kotlin/dev/mobilefoundry/http/OkHttpTransport.kt).

Tests: [HTTPTest.kt](../../../../../project/core/http/src/test/kotlin/dev/mobilefoundry/http/HTTPTest.kt), [TransportTest.kt](../../../../../project/core/http/src/test/kotlin/dev/mobilefoundry/http/TransportTest.kt). Both platforms read [Shared response fixtures](../../../../../../../contracts/fixtures/http/responses.json).

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
[Async ports](../../../../language/kotlin-suspending-ports-and-cancellation.md) explains the async syntax and [Native transport lifetime](../../../../substrate/okhttp-and-compose-effect-lifetime.md) the SDK lifetime.

The client retains response metadata, admits JSON, and decodes non-2xx problems.
Malformed successful JSON is internal invalid_response; malformed or broken
error JSON preserves status fallback. Problem kind may override that fallback.
Only selected string fields become failure payloads. Request ID and correlation
precedence are deliberate. ETag stays response metadata. Retry timing uses an
injected clock and rejects overflow; it does not trigger a retry.

## Verification and limits

`make http-test` runs both packages/modules. 12 Kotlin HTTP tests passed, including MockWebServer body interruption, redirects and cancellation.
The app consumer also compiled. These checks establish admission, metadata,
known network behavior and cooperative cancellation for the tested scenarios.
They do not establish production TLS policy, radio loss, streaming bounds,
background execution or retry safety. [Fixture and adapter checks](../../../../../../../notes/techniques/shared-fixtures-and-native-adapters.md) separates fixture evidence
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

[Health services](../services/README.md), [Kernel walkthrough](../kernel/README.md), and [Deadlines and cancellation](../../../../../../../notes/concepts/deadlines-and-owned-cancellation.md).
