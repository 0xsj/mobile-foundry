# HTTP and health construction specification

## Status and scope

Implement the established [HTTP contract](../../contracts/behavior/http.md)
above the [kernel](../../contracts/behavior/kernel.md), following
[architecture](../../ARCHITECTURE.md), [Bento transport](../../../bento/frontend/next/lib/http/port.ts),
[Bento decoding](../../../bento/frontend/next/lib/http/problem.ts), and
[Bento health](../../../bento/frontend/next/lib/services/health/index.ts).
The [blueprint protocol](../../../IMPLEMENTATION-BLUEPRINT-PROTOCOL.md) controls
construction; [learning workflow](../NOTES.md) controls the notebook handoff.

## Exemplar workflow

A catalog selects a scenario and calls HealthService.ready through an injected
HTTP client. Request admission preserves the API prefix. Transport returns a
raw response; HTTP parses JSON or decodes a problem. Health validates status.
The screen renders deliberate state. Cancellation and defects propagate until
their owning lifecycle/reporting boundary.

## Nouns states and invariants

RequestOptions owns per-attempt input. PreparedRequest owns admitted URL,
method, headers, and encoded body. WireResponse owns status, headers, and body;
absent body represents a failed read, empty bytes represent empty content.
Entity owns JSON and ResponseMetadata. HTTPTransport is a cancellation-aware
network port. HTTPClient is an application-result port. HealthService returns
a narrow Health value. Catalog state is loading, healthy, or failed.

## Workflow and side effect ordering

Check cancellation and timing, admit request, then send once. The network catch
handles known networking errors only. Body reading completes inside its native
call's lifetime; parsing is outside the connection catch. Non-2xx failures select
public problem fields. Service validation follows successful decoding. No retry,
session renewal, redirect, or diagnostic serialization. Reports use original
dependency errors without attaching them to public values.

## Expected file tree

Required source follows these paired responsibilities:

```text
frontend/swift/packages/FoundryHTTP/
  Package.swift
  Sources/FoundryHTTP/{HTTPTypes,JSONValue,RequestPreparation,ProblemDecoder,DefaultHTTPClient,URLSessionTransport}.swift
  Tests/FoundryHTTPTests/{HTTPTests,TransportTests,LoopbackTransportTests}.swift
frontend/swift/packages/FoundryServices/
  Package.swift
  Sources/FoundryServices/HealthService.swift
  Tests/FoundryServicesTests/HealthServiceTests.swift
frontend/swift/apps/FoundryCatalog/Sources/HealthCatalogView.swift
frontend/kotlin/project/core/http/
  build.gradle.kts
  src/main/kotlin/dev/mobilefoundry/http/{HTTPTypes,RequestPreparation,ProblemDecoder,DefaultHTTPClient,OkHttpTransport}.kt
  src/test/kotlin/dev/mobilefoundry/http/{HTTPTest,TransportTest}.kt
frontend/kotlin/project/core/services/
  build.gradle.kts
  src/main/kotlin/dev/mobilefoundry/services/HealthService.kt
  src/test/kotlin/dev/mobilefoundry/services/HealthServiceTest.kt
frontend/kotlin/project/app/src/main/java/dev/mobilefoundry/catalog/ui/health/HealthCatalogScreen.kt
contracts/fixtures/http/{responses,paths,retry-after}.json
```

Also required: build registration, catalog navigation, dependency versions,
module READMEs, native HTTP/services walkthroughs, first-use language and
substrate notes, shared cancellation concept note, notebook reading orders,
and root verification commands. Maintenance/session/provider implementations
are deferred; no backend or universal exception classifier is added.

## File specifications

| Required file responsibility | Owns and symbols | Inputs and outputs | Verification |
| --- | --- | --- | --- |
| HTTPTypes | Methods, options, prepared request, wire response, entity, metadata, HTTPClient/Transport ports | Typed request values; immutable or owned response data | Preparation and fixture tests |
| Swift JSONValue | Sendable, Codable JSON tree | Valid JSON bytes ↔ supported JSON values; rejects nonfinite numbers | Null, malformed JSON, nested objects, response fixtures |
| RequestPreparation | Base admission and request preparation | API-relative path/options → prepared request or typed internal refusal | Shared paths; headers, query repetition, JSON body, prefix isolation; zero transport calls on refusal |
| ProblemDecoder | Status fallback, metadata selection, problem admission, Retry-After | Wire response and parsed body → Failure; injected now → optional timing | Shared responses and retry fixtures |
| DefaultHTTPClient | Per-attempt deadline and exchange pipeline | Admitted request + cooperative transport → entity or failure; cancellation/defects throw | Deadline, pre-cancel, active cancel, malformed and broken bodies, defect identity |
| URLSessionTransport / OkHttpTransport | Native call/body lifecycle and known network errors | Prepared request → wire response or known connection failure; original errors reported | Native adapter exchange, no redirect/retry, timeout, cancellation and body interruption |
| HealthService | live/ready endpoint and status admission | HTTP entity → Health; expected failures unchanged | Shared response health expectations; endpoint selection and failure propagation |
| Catalog health view/screen | Scenario selection, lifecycle work, loading/healthy/failure rendering | Injected fixture transport → service-derived state | Native builds and available runtime catalog checks |
| Native tests and fixtures | Independent parity oracles and adapter behavior | Canonical repository fixtures, test fakes/native test server | All meaningful branches and limits recorded |
| Manifests and build registration | Local module dependency wiring and pinned dependencies | Existing kernel → HTTP → services → app | Package tests and native consumer builds |

All production symbols stay within these owners. Configuration factories refuse
invalid inputs as values; public constructors store admitted values. Unknown errors propagate without application classification or replacement;
coroutine stack-trace recovery may preserve the original as cause. Cancellation checks occur before
sending and before committing successful results. Diagnostics callbacks are
observers and must not throw or block. No callback grants permission to retry.

## Cross file dependency map

Kernel → HTTP → services → catalogs. HTTP has no UI or domain dependencies;
services have no UI or native SDK singleton. Catalog fixtures are app-owned.
Tests read canonical fixtures; runtime artifacts do not load checkout files.

## Transport and persistence mapping

Swift uses URLSession; Kotlin uses OkHttp 5.4.0, coroutines 1.10.2, and JSON
serialization 1.9.0. Headers and failure projection match Bento's convention.
Persistence is not applicable. Native cancellation throws rather than returning
Bento web's canceled value; Swift cleanup requires cooperative child tasks.
These differences are explicit in the controlling contract.

## Verification plan

Run kernel, HTTP, and services host tests. Run native consumer builds and
Android existing tests. Exercise URLSession and OkHttp with controlled native
responses, including a delayed body and cancellation. Where local simulators
are available, inspect the catalog's healthy and failure scenarios. Run notes
and whitespace checks. Report any runtime checks unavailable in this environment.

## Construction order

1. Establish contract and shared fixtures.
2. Build request admission and error/JSON decoding.
3. Build cancellation-aware native transports and deadline orchestration.
4. Implement health admission and parity/adapter tests.
5. Wire catalogs and build dependencies.
6. Write notes, verify, and record material deviations and observed limits.

## Open decisions and unknowns

Runtime simulator availability is an environment check. Future streaming limits,
credential injection, maintenance status, and session behavior need separate
contracts. None blocks the bounded health exemplar.

## Deviation record

- OkHttp was planned at 5.5.0. Its Android AAR requires compile SDK 37;
  the existing AGP/API 36 consumer rejected it. Pin 5.4.0, verified through
  API 36 AAR validation, host tests, and Android assembly.
- Add a separate Swift loopback TCP test file. Synthetic URLProtocol failures
  did not preserve received headers in the observed callback path; a real
  truncated HTTP body establishes status/header retention.
- Kotlin coroutine debug stack-trace recovery can copy ordinary exceptions.
  Preserve application propagation and original cause; custom defect tests
  prove the client does not classify or replace them.
- Reject invalid UTF-8 percent-encoded path segments on both platforms;
  Java URLDecoder's replacement behavior was too permissive for parity.
- Android AAPT2 failed its first daemon start. Re-running the same build passed;
  no SDK or signing workaround was introduced.

## Completion criteria

Shared fixtures pass on both platforms; native transports preserve cancellation
and defect boundaries; both catalogs compile and expose the health scenarios;
notes explain actual code, evidence, parity, and native differences.

## Observed completion

2026-10-08: shared fixtures contain 38 responses, 23 paths and 12 retry timing
cases. `make http-test` passed: Swift HTTP 13/services 2; Kotlin HTTP 12/services 2.
`make ios-generate`, `make ios-build`, `make android-build` and
`make android-test` passed. Swift host tests include loopback truncated-body and
redirect exchanges; Kotlin uses MockWebServer and observed Call.cancel events.
The iOS 26.2 iPhone 17 Pro catalog was launched and Healthy, Malformed and
Timeout states were observed. `make android-ui-test` passed three tests on API36_Test (Android 16):
existing greetings, all health scenarios, and replacement of an in-flight
request. `make kernel-test` passed Swift 7/Kotlin 9 kernel checks. `make notes-check` passed with 64 documents, 284 local links and 7 native
example labels. Whitespace checks passed for tracked and newly created files.
No live backend or physical-device behavior is established.
