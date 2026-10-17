# Services walkthrough

## Remote Notes and immutable receipts

Claim: a remote repository promises versions and recoverable commands explicitly,
without changing the local CRUD contract. Origin: connected Notes, 2026-10-13.
Evidence: shared receipt fixtures and real URLSession → Go acceptance pass.
Read [RemoteNotes values/ports](../../../../packages/FoundryServices/Sources/FoundryServices/RemoteNotes.swift),
[HTTP adapter](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPRemoteNotesRepository.swift),
[fixture tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/RemoteNotesTests.swift),
then [real acknowledgement recovery](../../../../packages/FoundryServices/Tests/FoundryServicesTests/GoNativeNotesTests.swift).

The admitted command exposes readonly fields; factories require a caller-retained
UUID and observed version. Reader/writer ports stay separate, with a composed port
where both are needed. HTTP admission checks scope/input/ETag and historical
receipt shape before producing values. Swift String equality is canonically
equivalent, so receipt confirmation uses UTF-8 elementsEqual to distinguish
different scalar spellings. The shared fixture compares Café with Cafe plus a
combining accent and refuses the altered acknowledgement.

Example — Excerpt:

```swift
// Excerpt: NoteWriteReceipt.confirms compares the preserved command title.
title.utf8.elementsEqual(command.title.utf8)
```

Cancellation is checked before and after IO. A dependency may ignore cancellation;
its committed response must still be refused by the abandoned owner. The port
does not catch unexpected defects, renew tokens or replay writes. The real Go
test discards a successful response, finds its receipt, replays the same key,
then exercises CAS/delete and historical recovery. Its unconfigured ordinary
package run is skipped; `make go-native-test` supplies the owned endpoint and
executes it. Current full-package fixtures pass; physical-device cost, durable
pending input and independent connected extraction are separate work.
Used in [catalog recovery](../../apps/FoundryCatalog/README.md#remote-notes-recovery).
Related: [shared receipt pattern](../../../../../../notes/patterns/command-receipts-and-uncertain-writes.md).
Next: make pending input survive process death without weakening scope checks.

HealthService owns endpoint selection and successful domain admission above the HTTP port.
NotesService adds a provider-independent domain port with memory and HTTP implementations.

## Live read capability

Local-data extraction, 2026-10-10: [NotesService](../../../../packages/FoundryServices/Sources/FoundryServices/NotesService.swift)
now owns the domain/read/memory types, and
[HTTPNotesService](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPNotesService.swift)
owns transport admission in its own file. Public API/behavior is unchanged; all
11 full-module regressions pass. A reduced local manifest selects the original
ports/CRUD/observation source without HTTP; its two Memory observation cases pass
in independent output. See [product local-data output](../../apps/ProductApp/README.md#optional-local-data-output).

Claim: observation composes with CRUD without expanding the narrower service ports.
Origin: native observation, 2026-10-10. Read
[NotesObserver and NotesSubscription](../../../../packages/FoundryServices/Sources/FoundryServices/NotesObserver.swift),
[Memory publication](../../../../packages/FoundryServices/Sources/FoundryServices/NotesRepository.swift)
and [observation tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/NotesObservationTests.swift).

Each Memory subscription registers in the actor, receives a present current
snapshot and follows successful mutations. Update suppresses identical content;
bounded buffering coalesces slow-reader bursts. Array values keep prior snapshots
owned. Explicit subscription cancellation finishes the stream and its termination
handler removes the continuation from the actor. Callers defer cancellation even
on early loop exit. SQLite supplies the same port in Data.

All eleven service tests pass, including two observation checks for independent
subscribers/cleanup, CRUD snapshots and burst convergence/fresh resume. Existing
HTTP fixtures remain injected and do not acquire live behavior automatically.
Read [stream mechanics](../../../substrate/async-stream-buffering-and-subscription-cleanup.md)
and [Data](../FoundryData/README.md#live-local-observation).
Next: which provider genuinely supports current snapshots rather than just reads?

## Mutable repository seam

The 2026-10-10 local-data slice adds
[NotesRepository, UpdateNote, DeleteNote and InMemoryNotesRepository](../../../../packages/FoundryServices/Sources/FoundryServices/NotesRepository.swift).
The repository composes read/create ports and adds update/delete without exposing
rows. Command factories reuse ID/title admission; update keeps identity/position,
missing update/delete returns not_found, and the memory actor returns snapshots.
Read-only callers can continue depending only on NotesService.

The six [Data disk/domain checks](../FoundryData/README.md) include shared command
fixtures and memory CRUD behavior; all nine existing Services tests also pass.
Memory semantics do not establish disk durability. Follow
[local observation lifetime](../../../../../../notes/patterns/local-commits-and-observation-lifetime.md)
and the [catalog consumer](../../apps/FoundryCatalog/README.md#durable-local-notes).
Next: which future consumers need the aggregate CRUD port versus only reads or creates?

## Origin

The 2026-10-08 slice follows Bento's health live/ready services.
[HTTP and health contract](../../../../../../contracts/behavior/http.md) controls admission; this walkthrough mirrors the services package/module.

## Reading order

1. [HealthService source](../../../../packages/FoundryServices/Sources/FoundryServices/HealthService.swift), from injected HTTPClient through live/ready to admission.
2. [HTTP walkthrough](../FoundryHTTP/README.md), for request and problem handling below this service.
3. [Health service tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/HealthServiceTests.swift) and [Shared response/health cases](../../../../../../contracts/fixtures/http/responses.json), for independent admitted/rejected shapes.
4. [Notes service port and Memory](../../../../packages/FoundryServices/Sources/FoundryServices/NotesService.swift) and [HTTP adapter](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPNotesService.swift), then [notes tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/NotesServiceTests.swift) and [shared notes fixtures](../../../../../../contracts/fixtures/notes/responses.json).

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

## Create-note write seam

The 2026-10-08 [forms contract](../../../../../../contracts/behavior/forms-mutations.md)
adds [CreateNote and NoteCreator](../../../../packages/FoundryServices/Sources/FoundryServices/CreateNote.swift).
The private command initializer forces raw text through admission before a
provider sees it. The separate write protocol leaves the existing read-only
port source-compatible; a read consumer need not implement writes.

[InMemoryNoteCreator](../../../../packages/FoundryServices/Sources/FoundryServices/InMemoryNoteCreator.swift)
is an actor implementing both ports. Its cancellation check precedes a
non-suspending append, and list returns an array value snapshot.
[HTTPNoteCreator](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPNoteCreator.swift)
maps the admitted title to POST notes and admits the returned note envelope.
HTTP success with a missing/invalid note is internal with IDs retained;
expected HTTP failures pass through. A valid server-normalized title is accepted.

[Create tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/CreateNoteTests.swift)
add four passing tests: ten shared title cases, thirteen shared responses plus
request shape, concurrent unique creates/snapshots, and cancellation/defects.
Nine service tests now pass through `swift test --package-path
frontend/swift/packages/FoundryServices`. This proves instance-local behavior
and injected wire admission, not deployed writes or safe replay.
See [command/actor and focus mechanics](../../../substrate/swiftui-form-focus-and-submit.md).

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

## Files binary boundary — 2026-10-17

Claim: an account-bound file reader/writer can share the existing expected-failure,
HTTP and exact-session-lease policy while carrying immutable admitted binary bytes.
Origin: Files slice; [the source](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPRemoteFilesRepository.swift) and [the behavior contract](../../../../../../contracts/behavior/files-media.md).

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
