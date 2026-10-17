# Services walkthrough

## Remote notification values — 2026-10-17

Claim: receiving an admitted push value does not request navigation; consuming
an open requires its complete tagged identity. Origin: generic remote-preview slice.
Read [values](../../../../packages/FoundryServices/Sources/FoundryServices/RemoteNotificationPreview.swift),
[router](../../../../packages/FoundryServices/Sources/FoundryServices/SystemNotifications.swift)
and [checks](../../../../packages/FoundryServices/Tests/FoundryServicesTests/RemoteNotificationTests.swift).

RemoteNotificationPreview validates the exact string schema and canonical UUID.
NotificationOpen is an enum with associated local/remote values; equality retains
the case, while its Identifiable key combines kind/UUID for SwiftUI observation.
The main-actor observable router separately records receipt, pending navigation
and the last consumed open. Associated values keep invalid case/data combinations
out of this representation. A raw UUID would lose that distinction.

Shared fixtures cover remote admission. State tests cover foreground no-navigation,
same-UUID local/remote races, duplicate opens and eviction from the bounded window.
These do not prove OS delivery. Follow [app flow and acceptance](../../apps/FoundryCatalog/README.md)
and [the shared reasoning](../../../../../../notes/patterns/remote-notification-admission-and-open-identity.md).

## Provider address candidate — 2026-10-17

Claim: a process-local provider address and a screen-owned collection request
have separate lifetimes from the session-bound installation owner.
Origin: optional APNs/FCM collection. Read [PushTokens.swift](../../../../packages/FoundryServices/Sources/FoundryServices/PushTokens.swift),
[owner tests](../../../../packages/FoundryServices/Tests/FoundryServicesTests/PushTokenTests.swift)
and [registry handoff checks](../../../../packages/FoundryServices/Tests/FoundryServicesTests/InstallationTests.swift).

PushTokenStore is @MainActor/@Observable. Its admitted DeliveryTarget is redacted
in diagnostics, and its revision orders local changes. Explicit invalidation
advances even an empty store; an older completion must not resurrect a refused
address. Repeated equal targets do not advance the revision. Nothing is persisted
by this package and no callback has a reference to an installation registry.

PushTokenPort publishes its admitted candidate before returning success. The
controller owns only busy/failure and its cancellable Task. Construction does
not collect; collect clears the old candidate, serializes screen requests and
reports only public failures. A thrown defect reaches its original reporting
hook. close makes the owner terminal; a later native callback may update the
process store, but cannot publish request state into that closed screen.

The catalog's separate registration button snapshots target into the existing
InstallationController. Tests rotate the candidate during a write with a lost
acknowledgement: the first write retains its original address, refresh does not
resend, and only another explicit action writes the new address. Unbinding auth
does not destroy the process address. Read [the shared pattern](../../../../../../notes/patterns/provider-addresses-and-registration-intents.md).

Evidence: host owner/handoff checks and native tests recorded in
[setup/evidence](../../../../../../docs/blueprints/push-tokens.md). Live APNs/FCM
issuance, automatic registration/sync and generated connected selection are not
established. Next: provider selection/eligibility and guarded invalid-token cleanup.

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

## Connected inbox — 2026-10-17

Claim: typed account-bound repositories can expose settled inbox actions without
adding command receipts or provider dependencies to reusable UI.

Origin: native Notifications slice; read [ports and snapshots](../../../../packages/FoundryServices/Sources/FoundryServices/NotificationInbox.swift),
[HTTP admission](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPNotificationInboxRepository.swift) and
[the shared recovery pattern](../../../../../../notes/patterns/inbox-refresh-and-setting-uncertainty.md).

The reader exposes scoped pages and a media preference; the writer marks an item
read or assigns the preference. The HTTP adapter is one-attempt and expects an
exact-lease AuthenticatedHTTPClient from composition. No SDK, renewal, receipt key
or implicit retry enters these ports. The wire does not repeat the principal on
each item; the repository carries its bound identity and the authenticated server
checks row ownership. Pages must repeat the current account.

Response admission rejects unknown type/category, malformed IDs/calendar times,
missing read_at, duplicate rows, inconsistent unread filters/counts and invalid
cursor shapes. Preserve server order because its commit sequence is intentionally
not in item DTOs. MarkRead validates the original item/file/times and preserves a
known first-read time. A setting acknowledgement must echo the requested boolean.
Public failure metadata and native cancellation remain distinct.

[Shared response fixtures](../../../../../../contracts/fixtures/notifications/pages.json) execute in both
platform suites. The real Go Files acceptance now also waits for its upload-ready
item, deliberately drops read/preference acknowledgements, reads committed state
and checks another account's empty/default inbox. This observes actual HTTP through
URLSession/OkHttp; it does not establish system push or native screen gestures.

Use: [catalog owner](../../../../apps/FoundryCatalog/Sources/Notifications/NotificationInboxCatalogModel.swift).
Related: [Notifications behavior](../../../../../../contracts/behavior/notifications.md).
Next: which additional audience/type needs a new explicit producer contract?

## Installation registry — 2026-10-17

Claim: a session-scoped registry client can manage a persistent installation
identity without importing session or provider SDK types into Services.

Origin: native registry slice. Read [InstallationRegistry.swift](../../../../packages/FoundryServices/Sources/FoundryServices/InstallationRegistry.swift), [HTTPInstallationRegistry.swift](../../../../packages/FoundryServices/Sources/FoundryServices/HTTPInstallationRegistry.swift), [InstallationController.swift](../../../../packages/FoundryServices/Sources/FoundryServices/InstallationController.swift) in that order,
then [InstallationTests.swift](../../../../packages/FoundryServices/Tests/FoundryServicesTests/InstallationTests.swift), [GoNativeInstallationsTests.swift](../../../../packages/FoundryServices/Tests/FoundryServicesTests/GoNativeInstallationsTests.swift) and
[the shared response corpus](../../../../../../contracts/fixtures/installations/records.json).

InstallationIdentity owns a canonical UUID and canonical i1 proof. Its formatter
is redacted, as is DeliveryTarget; neither secret enters controller snapshots.
InstallationVault offers only serialized load-or-create, so callers cannot
accidentally treat a corrupt record as absence or clear proof on sign-out.
InstallationRegistry is the replaceable read/register/unregister port. The HTTP
adapter receives an exact-lease client from app composition and preserves proof,
CAS and one-attempt semantics without owning renewal.

The response decoder checks the sanitized field set, matching ETag/version,
UUIDs, route policy, booleans and timestamp ordering. Register validates the
accepted checkpoint, route, binding and next revision; unregister validates the
disabled revision and preserved metadata. GET not-found does not create/reset a
remote record or rotate local proof.

@MainActor serializes observable presentation. @ObservationIgnored excludes ports, proof and task handles from rendering dependencies. A captured generation plus cancellation checks admits completions; a canceled task cannot undo an accepted remote mutation.

The controller first loads/persists identity, then reads the registry. A storage
failure prevents networking. Each explicit registration captures a fresh UUID
and target once. A failed mutation leaves writes blocked until GET succeeds.
A new bind, including renewal/account switch, cancels/invalidates the old work,
clears presentation and reads again without automatic registration. Unbind does
not erase the vault. There is no hidden token retry queue or receipt history.

Verification: shared corpus admission and three owner cases pass on both platforms;
the held old-lease response contains a different record, proving it cannot replace
the new binding's state. Real Go HTTP checks cover lost acknowledgement,
session handoff, unchanged binding through renewal, settled unregister, target
replacement and asynchronous sign-out retirement. These are host/state checks,
not catalog gestures, real provider delivery or generated connected acceptance.

Related: [identity and reconciliation pattern](../../../../../../notes/patterns/installation-identity-and-current-state-reconciliation.md).
Next: how should an app present a desired OS token while an earlier revision is
still unconfirmed?

## System notification preview — 2026-10-17

Claim: an OS notification port can remain independent of provider tokens, account
inbox state and native navigation APIs.

Origin: the local system-notification slice. Read
[SystemNotifications.swift](../../../../packages/FoundryServices/Sources/FoundryServices/SystemNotifications.swift), [SystemNotificationController.swift](../../../../packages/FoundryServices/Sources/FoundryServices/SystemNotificationController.swift), [SystemNotificationTests.swift](../../../../packages/FoundryServices/Tests/FoundryServicesTests/SystemNotificationTests.swift), then
[shared payload admission](../../../../../../contracts/fixtures/system-notifications/payloads.json) and
[the behavior contract](../../../../../../contracts/behavior/system-notifications.md).

NotificationAccess separates authorization from delivery settings; notGranted
intentionally avoids promising another OS prompt. Quiet delivery can still be
admitted. SystemNotificationPort exposes read, explicit permission request,
post/clear preview and open settings. Native adapters recheck current access at
post time. The controller never prompts on refresh, never retries an OS write,
and calls its defect hook without exposing private exception content to UI.
Closing prevents further publication but cannot undo a post already accepted by
an OS. A later owner can explicitly clear the same owned slot.

LocalNotificationPreview admits exactly version/kind/canonical UUID. It carries
no account authority or remote content. NotificationOpenRouter holds the latest
pending preview and a bounded 32-ID duplicate window in process memory. consume
must match the pending ID; an old consume cannot erase a newer intent. Repeated
opens within the window do not add another route. No durable receipt/history or
cross-process exactly-once behavior follows.

@MainActor keeps action/open ownership serial; @Observable publishes the snapshot while @ObservationIgnored excludes native ports and task handles. The port is also UI-isolated because permission requests belong to a presentation owner. Native delegate callbacks transfer only Sendable preview values into that owner.

Verification: host checks exercise the shared corpus, explicit permission action,
blocked posting after settings change, no automatic replay, current-ID open
consumption and late-response suppression after close. The OS tests and their
limits are separate in [the construction guide](../../../../../../docs/blueprints/system-notifications.md).
This package never imports APNs/FCM, starts an auth request or changes an inbox
preference. Related: [navigation-hint reasoning](../../../../../../notes/patterns/device-notifications-and-navigation-hints.md).
Next: what current account/session admission does a remote inbox destination need?
