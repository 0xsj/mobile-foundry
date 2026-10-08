# Services walkthrough

HealthService owns endpoint selection and successful domain admission above the HTTP port.
NotesService adds a provider-independent domain port with memory and HTTP implementations.

## Origin

The 2026-10-08 slice follows Bento's health live/ready services.
[HTTP and health contract](../../../../../../contracts/behavior/http.md) controls admission; this walkthrough mirrors the services package/module.

## Reading order

1. [HealthService source](../../../../packages/FoundryServices/Sources/FoundryServices/HealthService.swift), from injected HTTPClient through live/ready to admission.
2. [HTTP walkthrough](../FoundryHTTP/README.md), for request and problem handling below this service.
3. [Health service tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/HealthServiceTests.swift) and [Shared response/health cases](../../../../../../contracts/fixtures/http/responses.json), for independent admitted/rejected shapes.
4. [Notes service port and adapters](../../../../packages/FoundryServices/Sources/FoundryServices/NotesService.swift), then [notes tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/NotesServiceTests.swift) and [shared notes fixtures](../../../../../../contracts/fixtures/notes/responses.json).

## Walkthrough

`live` selects `health/live`; `ready` selects `health/ready`. Each requests a GET
entity and passes options through. Expected failures return unchanged. A success
must contain a root JSON object whose status is the string `ok`. Array, null,
missing, wrong or numeric status is internal/http.invalid_response with IDs
retained. Extra response fields are discarded.

The admitted Health exposes status, IDs and an optional version from ETag.
The catalog never reads arbitrary wire JSON. There is no native session
singleton, repository, maintenance endpoint or HTTP catch in this service.
Structured cancellation and defects keep propagating to the caller.

## Verification and limits

The notes port returns ordered, uniquely identified domain values and no HTTP
options. Memory owns a snapshot; HTTP admits the complete `notes` array before
returning any values. Invalid items or duplicate IDs refuse the whole response
with notes.invalid_response and retained diagnostic IDs. Expected HTTP failures,
cancellation and defects keep their existing semantics. See [the notes contract](../../../../../../contracts/behavior/notes-query.md)
and [protocol/store mechanics](../../../language/swift-observable-stores-and-service-protocols.md).

Two host tests passed on this platform through `make http-test`: shared health
shapes and live endpoint, plus ready endpoint and failure metadata propagation.
The consumer app compiled. These are injected exchanges; they do not establish
a backend deployment or provider health semantics.

The 2026-10-08 notes slice adds three passing package tests: all 12 shared
admission cases plus memory parity, domain bounds/owned snapshots, and defect/
cancellation propagation. Five service package tests pass in total. Feature
state is checked separately in the catalog's iOS test target.

## Gotchas

HTTP success is necessary but insufficient for a successful domain result.
A 204 is a valid empty HTTP entity but invalid Health. An ETag is opaque; this
slice never interprets or manufactures a version number from it.

## Questions for the next session

- Why should unknown successful fields be discarded?
- What endpoint behavior belongs in a service rather than the client?
- Which layer will own session renewal when an operation is unauthenticated?

## Related

[Transport, service, screen](../../../../../../notes/patterns/transport-service-and-screen.md) and [Catalog walkthrough](../../apps/FoundryCatalog/README.md).
