# Services walkthrough

HealthService owns endpoint selection and successful domain admission above the HTTP port.
NotesService adds a provider-independent domain port with memory and HTTP implementations.

## Origin

The 2026-10-08 slice follows Bento's health live/ready services.
[HTTP and health contract](../../../../../../../contracts/behavior/http.md) controls admission; this walkthrough mirrors the services package/module.

## Reading order

1. [HealthService source](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HealthService.kt), from injected HTTPClient through live/ready to admission.
2. [HTTP walkthrough](../http/README.md), for request and problem handling below this service.
3. [Health service tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/HealthServiceTest.kt) and [Shared response/health cases](../../../../../../../contracts/fixtures/http/responses.json), for independent admitted/rejected shapes.
4. [Notes port and adapters](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesService.kt), then [notes tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/NotesServiceTest.kt) and [shared notes fixtures](../../../../../../../contracts/fixtures/notes/responses.json).

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
options. Memory copies its seed list and exposes an unmodifiable snapshot.
HTTP validates every item and unique ID before publishing its unmodifiable
domain list. Bad shapes refuse the complete response with notes.invalid_response
and retained IDs. See [the notes contract](../../../../../../../contracts/behavior/notes-query.md)
and [interface/StateFlow mechanics](../../../../language/kotlin-service-interfaces-and-stateflow.md).

Two host tests passed on this platform through `make http-test`: shared health
shapes and live endpoint, plus ready endpoint and failure metadata propagation.
The consumer app compiled. These are injected exchanges; they do not establish
a backend deployment or provider health semantics.

The 2026-10-08 notes slice adds three passing host tests: all 12 shared admission
cases plus memory parity, domain bounds/owned snapshots, and defect/cancellation
propagation. Five service tests pass in total. Feature state is checked separately
by the catalog's ViewModel unit tests.

## Create-note write seam

The 2026-10-08 [forms contract](../../../../../../../contracts/behavior/forms-mutations.md)
adds [CreateNote and NoteCreator](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/CreateNote.kt).
The private constructor and companion factory admit raw text before provider
work. The separate fun interface keeps read-only consumers independent of writes.

[InMemoryNoteCreator](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/InMemoryNoteCreator.kt)
implements both ports, with a mutex protecting append/list and an unmodifiable
copy for each list snapshot. [HTTPNoteCreator](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPNoteCreator.kt)
posts the admitted title and validates a note envelope. Invalid success shapes
preserve response IDs in an internal failure; expected failures pass through.
Server-normalized valid titles are accepted.

[Create tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/CreateNoteTest.kt)
add four passing host tests: ten shared title cases, thirteen responses/request
shape, concurrent unique appends/owned snapshots, and cancellation/defects.
Nine service tests pass in `make android-test`. This is instance-local memory
and injected transport evidence, not server idempotency or persistence.
See [constructor, mutex and IME mechanics](../../../../substrate/compose-form-focus-and-ime.md).

## Gotchas

HTTP success is necessary but insufficient for a successful domain result.
A 204 is a valid empty HTTP entity but invalid Health. An ETag is opaque; this
slice never interprets or manufactures a version number from it.

## Questions for the next session

- Why should unknown successful fields be discarded?
- What endpoint behavior belongs in a service rather than the client?
- Which layer will own session renewal when an operation is unauthenticated?

## Related

[Transport, service, screen](../../../../../../../notes/patterns/transport-service-and-screen.md) and [Catalog walkthrough](../../app/README.md).
