# Services walkthrough

## Remote Notes and immutable receipts

Claim: a remote repository promises versions/recoverable commands through admitted
classes, separate from local CRUD. Origin: connected Notes, 2026-10-13. Evidence:
shared fixture checks and actual OkHttp → Go acknowledgement recovery pass.
Read [values/ports](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/RemoteNotes.kt),
[HTTP adapter](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPRemoteNotesRepository.kt),
[fixture checks](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/RemoteNotesTest.kt),
and [real Go test](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/GoNativeNotesTest.kt).

Factories admit canonical UUIDs, exact titles and versions before exposing readonly
fields. VersionedNote uses a plain class: a public generated data-class copy could
bypass a private constructor's admission. Kotlin compares strings by UTF-16
content; unlike Swift's canonical equivalence, different accent spellings remain
different. The adapter validates scalar sequences (including surrogate pairing),
wire scope, receipt input, ETag and bounded pages. Lists own an unmodifiable copy.

Example — Excerpt:

```kotlin
// Excerpt: a JUnit4 method must expose a Unit/void boundary.
@Test fun realGoWriteRecoveryAndVersions(): Unit = runBlocking {
    // real IO and assertions, then explicit Unit return at the JUnit boundary
}
```

JUnit4 requires void methods. Without the Unit annotation, a final request expression
made the generated test method return AppResult and JUnit refused initialization;
the corrected real test executes. Use real runBlocking for transport deadlines,
not a virtual-time runTest clock. State-owner fakes use runTest separately.

The adapter checks coroutine cancellation around IO and does not catch defects,
renew or replay. A deliberately lost success recovers through a receipt, and the
same key cannot recreate a later-deleted note. Normal unconfigured tests skip the
real endpoint case; `make go-native-test` owns its API and executes it. Catalog
controls build and state tests pass; Android Notes gestures, physical-device
performance and durable device input are not claimed.
Used in [catalog recovery](../../app/README.md#remote-notes-recovery).
Related: [shared receipt pattern](../../../../../../../notes/patterns/command-receipts-and-uncertain-writes.md).
Next: persist a pending command without exposing mutable scope/credentials.

HealthService owns endpoint selection and successful domain admission above the HTTP port.
NotesService adds a provider-independent domain port with memory and HTTP implementations.

## Live read capability

Local-data extraction, 2026-10-10: [NotesService](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesService.kt)
now owns domain/read/Memory types, and
[HTTPNotesService](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPNotesService.kt)
owns transport admission in a separate file. Existing API/behavior remains;
11 full-module regressions pass. A reduced local build selects original CRUD/
observation ports without HTTP, with its two observation cases in generated
output. See [product local-data output](../../product/README.md#optional-local-data-output).

Claim: a native Flow capability composes with CRUD while existing read/write ports
and fakes remain narrow. Origin: observation, 2026-10-10. Read
[NotesObserver](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesObserver.kt),
[Memory publication](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesRepository.kt)
and [tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/NotesObservationTest.kt).
Coroutines is an API dependency because Flow appears in the public signature.

Memory keeps an owned immutable copy in StateFlow and publishes inside its CRUD
mutex. Collectors receive current state; equal values and slow-reader bursts may
coalesce. Each collector has independent cancellation. Returning a Flow creates
no subscriber until collection starts. Expected failures are result values;
unexpected source exceptions and cancellation propagate to their owner.

All eleven service JVM cases pass, including two live checks for independent
collectors, owned CRUD snapshots, bounded burst convergence and fresh resume.
This capability does not make existing HTTP adapters observe remote changes.
Read [Flow mechanics](../../../../substrate/flow-invalidation-and-lifecycle-collection.md)
and [Data](../data/README.md#live-local-observation).
Next: what consistency contract would a remote observable cache need?

## Mutable repository seam

The 2026-10-10 local-data slice adds
[NotesRepository, UpdateNote, DeleteNote and InMemoryNotesRepository](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesRepository.kt).
The repository composes read/create ports and adds update/delete without exposing
rows. Command factories reuse ID/title admission; update keeps identity/position,
missing update/delete returns not_found, and the memory mutex protects ordered
values while list returns an unmodifiable owned copy. Read-only callers keep
depending only on NotesService.

Existing Services host regressions pass through `make data-test`; native
[Data checks](../data/README.md) additionally exercise shared command/memory
fixtures. Memory semantics do not establish disk durability. Follow
[local observation lifetime](../../../../../../../notes/patterns/local-commits-and-observation-lifetime.md)
and the [catalog consumer](../../app/README.md#durable-local-notes).
Next: which future consumers need the aggregate CRUD port versus only reads or creates?

## Origin

The 2026-10-08 slice follows Bento's health live/ready services.
[HTTP and health contract](../../../../../../../contracts/behavior/http.md) controls admission; this walkthrough mirrors the services package/module.

## Reading order

1. [HealthService source](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HealthService.kt), from injected HTTPClient through live/ready to admission.
2. [HTTP walkthrough](../http/README.md), for request and problem handling below this service.
3. [Health service tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/HealthServiceTest.kt) and [Shared response/health cases](../../../../../../../contracts/fixtures/http/responses.json), for independent admitted/rejected shapes.
4. [Notes port and Memory](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesService.kt) and [HTTP adapter](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPNotesService.kt), then [notes tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/NotesServiceTest.kt) and [shared notes fixtures](../../../../../../../contracts/fixtures/notes/responses.json).

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

## Files binary boundary — 2026-10-17

Claim: an account-bound file reader/writer can share the existing expected-failure,
HTTP and exact-session-lease policy while carrying immutable admitted binary bytes.
Origin: Files slice; [the source](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPRemoteFilesRepository.kt) and [the behavior contract](../../../../../../../contracts/behavior/files-media.md).

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
cloud storage. Related: [object lifecycle reasoning](../../../../../../../backend/go/notes/patterns/metadata-and-object-lifecycles.md). Next: what extra ownership is needed when uploads
must survive native process termination?
