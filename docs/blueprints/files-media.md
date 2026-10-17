# Files and media foundation

The first Files slice adds account-owned uploads and immutable content behind
Store/Objects ports, with named application operations and independent wire DTOs.
Read [behavior](../../contracts/behavior/files-media.md),
[HTTP](../../contracts/http/files-v1.md) and
[integration facts](../../contracts/events/files-v1.md).

## Run and use

From the repository root, run AUTH_MODE=identity make go-dev and register through
the existing Identity API. Open Catalog → Go identity and accounts in either
native app, connect to the API, sign in and scroll to Account files. Choose a
photo, upload it, then Download and edit preview. Delete removes its API access.
Android emulator/device loopback requires the usual adb reverse mapping; the
owned native acceptance runner creates/removes its own mapping only for device
checks. HTTPS is required outside explicitly selected local HTTP.

The photo picker grants access to the selected photo. Original selected bytes
are uploaded; no claim is made that EXIF/location metadata is stripped. The server
checks byte size/digest, not image semantics. Existing PhotoDecoder bounds decoded
previews and supplies the same image editor used by the camera workflow.

For durable local operation, start PostgreSQL, run migrations explicitly and
select STORAGE=postgres AUTH_MODE=identity. FILES_DATABASE_URL inherits DATABASE_URL;
FILES_DIRECTORY defaults to .data/files relative to the API process working
folder. Retain that private directory and the database together. A missing byte
store must fail reads/completion rather than pretend that metadata alone is a
usable file. No S3 service or cloud credentials are required for this slice.

## Source reading order

1. Files domain privately owns pending → available/deleted transitions.
2. Its application owns Store, Objects and AccountAccess, with RequestUpload,
   UploadContent, CompleteUpload, DeleteFile and focused queries.
3. Memory/PostgreSQL adapters share the fingerprint/restore encoding. Store Apply
   performs replay, version/existence guards and state/receipt/fact commit atomically.
4. Objects implements memory and a private directory; external effects remain
   outside metadata transactions. Delete commits inaccessibility before cleanup.
5. Module composition wires account capability access, file storage and outbox
   workers. Audit consumes selected versioned facts through its own adapter.
6. Swift/Kotlin Services owns the native reader/writer contracts and HTTP adapters;
   session-bound HTTP supplies binary support. Catalog owners retain account-bound
   retry drafts and reuse the camera decoder/editor for verified downloaded bytes.

Read [the Go walkthrough](../../backend/go/notes/modules/internal/files/README.md)
and [the object lifecycle pattern](../../backend/go/notes/patterns/metadata-and-object-lifecycles.md).

## Evidence — 2026-10-17

Go formatting/import/vet/build/race checks and compiled smoke pass. Files
conformance verifies concurrent replay, changed-key refusal, CAS, scoped receipts,
tombstones, cancellation and failed receipt/outbox rollback. Object checks cover
exact-byte admission, concurrent immutable publication, detached snapshots,
filesystem reopen and repeatable deletion. Authenticated HTTP checks verify the
lifecycle, cross-principal refusal and selected File facts arriving in Audit.

Disposable PostgreSQL 14.24 checks pass, including immutable migration guards,
rollback and compiled API SIGKILL recovery. Pending content survives a first
crash, completion and its receipt survive a second, and deletion closes download
access after restart. This does not establish host power-loss recovery, backup
restoration, failover or shared-filesystem behavior.

Real URLSession/OkHttp checks pass for raw non-UTF8 byte transfer and dropped
request/completion acknowledgements recovered through receipts/replay. Swift/Kotlin
HTTP/session regression checks and both catalogs build. Owner-specific tests cover
retry identity/bytes through renewal and stale picker results after a scope change;
two cases pass on each platform. No automated system photo
picker gestures, new physical-device GPU budget or background transfer is claimed.

CLI 0.5.0 includes Files in the full Go backend selection and ignores private local
object data in generated git configuration. Independently renamed outputs are
retained under /private/tmp/mobile-files-starter-hk73dwex (media-api and media-final).
The former passes memory/race/runner checks; the final media-final copy is checked
with its own memory/PostgreSQL and generated-runner workflows. Generated native
shells remain independently composed, without this catalog's authenticated screens.
