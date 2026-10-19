# Services walkthrough

## Narrow inbox extraction — 2026-10-19

Optional connected inbox copies the existing reader/writer/HTTP types and
RemoteServiceAdmission helper without Files or provider APIs. The overlaid JVM
module selects its own notifications fixture property; the catalog's broader
Gradle setup is not a generated dependency. Same-module helper declarations
remain required even though no import names their source file. Read
[generated execution](../../../../../../../docs/blueprints/connected-inbox.md)
and [CLI reasoning](../../../../../../../notes/modules/cli/README.md#optional-account-inbox--2026-10-19).

## Remote notification values — 2026-10-17

Claim: foreground receipt and an open are distinct UI-dispatcher transitions;
the complete tagged open guards consumption. Origin: generic remote-preview slice.
Read [values](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/RemoteNotificationPreview.kt),
[router](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/SystemNotifications.kt)
and [checks](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/RemoteNotificationTest.kt).

RemoteNotificationPreview uses private construction plus strict schema/UUID
admission. NotificationOpen is a sealed interface with Local/Remote data classes;
structural equality includes that case and the preview. The StateFlow owner is
confined to the UI dispatcher. recordReceipt never changes pending navigation;
consume requires the complete pending value and preserves receipt diagnostics.
The enum-like sealed representation avoids unrelated kind/string pairs.

Shared fixtures and state tests cover malformed remote fields, kind collisions,
late consumption, duplicate opens and bounded eviction. They do not prove FCM
delivery. Continue with [app transport ownership](../../app/README.md) and
[shared reasoning](../../../../../../../notes/patterns/remote-notification-admission-and-open-identity.md).

## Provider address candidate — 2026-10-17

Claim: a process-owned address candidate and UI-owned collection request remain
separate from session-bound registration. Origin: optional provider adapters.
Read [PushTokens.kt](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/PushTokens.kt),
[owner tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/PushTokenTest.kt)
and [registry handoff checks](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/InstallationTest.kt).

PushTokenStore exposes immutable StateFlow snapshots on the UI dispatcher.
Equal admitted targets do not advance the local revision; explicit invalidation
does even while empty. DeliveryTarget's diagnostic representation is redacted.
No provider SDK, auth credential, disk write or HTTP call enters this store.

The controller holds a lazy Job before starting it, serializes requests and
clears the candidate on explicit collect. Expected failures remain values;
cancellation propagates; thrown defects reach the hook and publish a generic
message. close is terminal. A later native callback can update the longer-lived
store, while a closed controller cannot expose it as a usable target or publish
late action failures. This avoids treating cancellation as provider cancellation.

The catalog snapshots the candidate into InstallationController on a separate
button action. The handoff test rotates a target during uncertain acknowledgement:
the sent address stays original, registry refresh never replays, and the rotated
address needs another explicit action. Auth unbinding leaves provider state
intact. See [ownership reasoning](../../../../../../../notes/patterns/provider-addresses-and-registration-intents.md).

Evidence: host owner/handoff and native adapter/UI checks in
[setup/evidence](../../../../../../../docs/blueprints/push-tokens.md). These do
not prove live FCM issuance, durable sync, cloud sending or generated connected
acceptance. Next: sender eligibility and version-guarded invalidation.

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

## Connected inbox — 2026-10-17

Claim: typed account-bound repositories can expose settled inbox actions without
adding command receipts or provider dependencies to reusable UI.

Origin: native Notifications slice; read [ports and snapshots](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotificationInbox.kt),
[HTTP admission](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPNotificationInboxRepository.kt) and
[the shared recovery pattern](../../../../../../../notes/patterns/inbox-refresh-and-setting-uncertainty.md).

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
The shared fixture file is an explicit Gradle test input, so corpus changes
invalidate cached test results rather than silently reusing an older pass.

[Shared response fixtures](../../../../../../../contracts/fixtures/notifications/pages.json) execute in both
platform suites. The real Go Files acceptance now also waits for its upload-ready
item, deliberately drops read/preference acknowledgements, reads committed state
and checks another account's empty/default inbox. This observes actual HTTP through
URLSession/OkHttp; it does not establish system push or native screen gestures.

Use: [catalog owner](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/notifications/NotificationInboxCatalogModel.kt).
Related: [Notifications behavior](../../../../../../../contracts/behavior/notifications.md).
Next: which additional audience/type needs a new explicit producer contract?

## Installation registry — 2026-10-17

Claim: a session-scoped registry client can manage a persistent installation
identity without importing session or provider SDK types into Services.

Origin: native registry slice. Read [InstallationRegistry.kt](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/InstallationRegistry.kt), [HTTPInstallationRegistry.kt](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPInstallationRegistry.kt), [InstallationController.kt](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/InstallationController.kt) in that order,
then [InstallationTest.kt](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/InstallationTest.kt), [GoNativeInstallationsTest.kt](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/GoNativeInstallationsTest.kt) and
[the shared response corpus](../../../../../../../contracts/fixtures/installations/records.json).

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

The owner is confined to the UI dispatcher; MutableStateFlow publishes immutable snapshots. Coroutine cancellation and a captured generation guard completions independently. Private constructors plus @ConsistentCopyVisibility keep data-class copy from bypassing route/record admission.

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

Related: [identity and reconciliation pattern](../../../../../../../notes/patterns/installation-identity-and-current-state-reconciliation.md).
Next: how should an app present a desired OS token while an earlier revision is
still unconfirmed?

## System notification preview — 2026-10-17

Claim: an OS notification port can remain independent of provider tokens, account
inbox state and native navigation APIs.

Origin: the local system-notification slice. Read
[SystemNotifications.kt](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/SystemNotifications.kt), [SystemNotificationController.kt](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/SystemNotificationController.kt), [SystemNotificationTest.kt](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/SystemNotificationTest.kt), then
[shared payload admission](../../../../../../../contracts/fixtures/system-notifications/payloads.json) and
[the behavior contract](../../../../../../../contracts/behavior/system-notifications.md).

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

The controller/router are confined to the UI dispatcher. Immutable StateFlow snapshots expose state; native coroutine jobs and recent IDs stay private. Cancellation plus generation admission protects a disposed owner even when a fake/native operation completes non-cooperatively.

Verification: host checks exercise the shared corpus, explicit permission action,
blocked posting after settings change, no automatic replay, current-ID open
consumption and late-response suppression after close. The OS tests and their
limits are separate in [the construction guide](../../../../../../../docs/blueprints/system-notifications.md).
This package never imports APNs/FCM, starts an auth request or changes an inbox
preference. Related: [navigation-hint reasoning](../../../../../../../notes/patterns/device-notifications-and-navigation-hints.md).
Next: what current account/session admission does a remote inbox destination need?

## Pending Notes models and port

Claim: recovery identity belongs to admitted command input and original scope,
not a token or screen lifetime. Origin: pending Notes, 2026-10-17. Read
[models/port/Memory](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/PendingNotes.kt),
[shared cases](../../../../../../../contracts/fixtures/pending-notes/records.json) and
[construction](../../../../../../../docs/blueprints/pending-notes.md).

The immutable record checks exact input and consistent pending/confirmed/rejected
state. Its owned versioned JSON envelope admits only expected fields and models,
strict UTF-8 and bounded bytes. Memory implements the same replay, terminal-CAS,
capacity and stale-settlement rules as disk while remaining deliberately transient.
Three service cases cover shared admission/round-trip, exact-input
replay/CAS/scope/capacity and immutable terminal results. Disk and generated-product checks are separate evidence.
Next: keep future queue ordering/retention outside this single-slot contract.

## Read-only remote snapshots — 2026-10-17

Claim: a bounded saved page can be an optional capability without changing remote CRUD or command receipts. Origin: connected read-cache slice. Read [snapshot/port](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesReadCache.kt) and [shared vectors](../../../../../../../contracts/fixtures/notes-read-cache/snapshots.json).

The immutable snapshot admits distinct IDs, a saved timestamp and the journal command/status checkpoint. PendingNotesScope supplies the already-admitted deployment/principal/account tuple; no session authority enters this port. Memory supplies parity/eviction/expiry checks. SQLite is selected only at composition.

Gotchas: the 24-hour window controls usability with lazy cleanup, not scheduled erasure. Private backup-excluded storage is not an offline authentication grant. Later pages/cursors and automatic write replay remain outside this capability.

Actual verification and versions are tracked in [construction/evidence](../../../../../../../docs/blueprints/notes-read-cache.md). Continue with [checkpoint reasoning](../../../../../../../notes/patterns/cached-content-and-command-checkpoints.md). Next: what product requirement would justify offline startup or a multi-page synchronization cursor?

## Connected Files selection (2026-10-19)

The optional Files source closure selects RemoteFiles and its HTTP repository,
plus the existing real binary transfer/receipt test. That test also observes
Notifications delivery, so Notifications decoder/helper/fixtures are selected
without selecting the Activity inbox screen. The exact-lease HTTP client stays
one-attempt; app-owned recovery performs completion receipt lookup before bytes
and repeats original deletion for external cleanup. See the product module's
Files section for selected sources, independent execution and ephemeral limits.

## Durable Files input and port — 2026-10-19

[PendingFiles](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/PendingFiles.kt) admits a separate original scope/key/action/specification and immutable payload, without importing Session. Metadata encodes an owned strict envelope; upload bytes verify size/hash, while delete records retain the observed nondeleted file/version and no bytes. Exact encoded metadata avoids Unicode semantic equality replacing input. The global memory adapter and disk seam expose load/stage/remove with exact compare semantics.

Read [staging reasoning](../../../../../../../notes/patterns/atomic-byte-staging-before-transport.md) and [the behavior contract](../../../../../../../contracts/behavior/pending-files.md). Shared fourteen-case fixtures and the global-slot/stale-remove checks exercise both implementations. Current generated execution and limits live in [construction evidence](../../../../../../../docs/blueprints/connected-files.md).

## Current file lookup — 2026-10-19

Claim: a read-only destination must resolve current metadata rather than reuse a receipt or scan a bounded first page. CLI 0.26.0 adds RemoteFileDetailReader in [RemoteFiles](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/RemoteFiles.kt) and implements it in [HTTPRemoteFilesRepository](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HTTPRemoteFilesRepository.kt). It adds no command or receipt authority to the destination. GET validates canonical ID before transport, then requested identity, account, nondeleted status and strong version ETag; existing download verification remains intact.

[Adapter tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/GoNativeFilesTest.kt) exercise seven response scenarios and malformed targets with no outgoing request. The real API flow now reads pending and available metadata and verifies deleted metadata returns not-found against each selected backend. Read [the pattern](../../../../../../../notes/patterns/notice-targets-and-current-authority.md) and [generated evidence](../../../../../../../docs/blueprints/connected-media.md). A notice alone is never current access evidence.
