# Account files and media

Implemented first slice, 2026-10-17: Go metadata and immutable bytes, Swift/Kotlin
HTTP ports and a gallery/editor example in the Go session catalogs. This is a
local Files foundation, not a complete media-processing or synchronization system.

## Ownership

Files owns metadata, a private aggregate, versions and internal lifecycle facts.
Its application owns AccountAccess, Store and Objects ports and a named handler
per operation. Account access uses `account.access.v1` through a consumer adapter;
current owner-only policy admits reads and writes. Membership/contributor rules
need an explicit extension before introducing read-only members.

Metadata has canonical IDs, creator, account, name, content type, declared byte
size and lowercase SHA-256 digest. Names normalize by trimming on creation and
are 1–255 Unicode code points without controls or path separators. Restore rejects
noncanonical representations. Bytes are 1 byte through 10 MiB. Supported types:
JPEG, PNG, WebP, HEIC, HEIF, PDF and application/octet-stream. Content type is a
client declaration, not server-side format validation or malware scanning.

## Lifecycle and recovery

A requested upload creates pending_upload version 1. Authenticated PUT content
must exactly match its declared size/digest/type. Bytes are immutable; repeating
identical content is allowed. Completion verifies storage and atomically changes
pending to available version 2. Download requires current account access and an
available record. Deletion commits a deleted tombstone (version 2 or 3) before
removing bytes; it never leaves available metadata pointing to bytes it removed.
All mutations retain their original account and authenticated actor.

Request, completion and deletion accept optional Idempotency-Key values of 1–128
letters/digits or ._:-. Successful keyed writes atomically save a fingerprint,
historical response receipt and required outbox message with metadata. Replays
precede CAS/existence, require current authorization and reject changed input.
Fingerprints use the fixed files.command.v1 encoding and exclude allocated IDs,
time and request metadata. A replay does not append another fact. Completion and
deletion require a strong If-Match. Byte PUT is intrinsically repeatable for the
same declared bytes and does not take a command key or If-Match.

A deletion receipt proves the logical deletion commit. Physical byte cleanup is
a separate effect: failure can return an error after that commit. Retrying the
same deletion key repeats cleanup. Receipt GET alone does not execute cleanup.
Metadata and object storage are never claimed to be one transaction. A crash or
late upload can leave inaccessible orphan objects; automatic retention/orphan
sweeping is future work. Pending uploads/tombstones/receipts currently have no
retention policy. Do not promise storage erasure from an inaccessible file alone.

## Providers and communication

Memory metadata guards state/receipt/outbox with one mutex. PostgreSQL owns the
files schema and pool, locks current records, and uses atomic receipt claims
with a deferred finalization constraint. Its outbox uses leased, per-file ordered
delivery. Memory delivery preserves the same per-file sequence. Audit consumes
versioned Files facts through its own receiving adapter and commits evidence and
inbox together. Facts carry IDs, versions and times, never names/types/digests,
content bytes, storage keys or URLs.

Objects has memory and private-directory adapters. The directory uses admitted
IDs for names and atomically links complete temporary writes without replacing
existing content; reads verify the persisted content digest. Retain both the
PostgreSQL database and FILES_DIRECTORY across restarts. The directory is private
trusted application storage, not a shared untrusted upload directory. Memory is
bounded at 1,000 metadata records, 10,000 facts and 64 MiB of object bytes.

## Native and HTTP boundaries

[HTTP contract](../http/files-v1.md) owns wire projection and scoped opaque cursors.
Downloads are authenticated, no-store attachments with nosniff. No public bucket,
presigned URL, range transfer, external URL fetch or transparent redirect is used.

Native RemoteFilesReader/Writer ports are independent of Notes and provider SDKs.
HTTP adapters make one attempt through the exact session lease, preserve failures
and validate scope, receipt intent, versions, type, size and SHA-256. HTTP supports
an explicit binary body mutually exclusive with JSON; transports bound response
buffering to 16 MiB and the Files binary decoder to 10 MiB. Existing JSON routes
retain their smaller JSON admission. Cancellation and credential renewal remain
owned by HTTP/session, with no automatic upload replay or token renewal.

The Go session catalog uploads selected gallery photos up to 10 MiB and opens
verified downloaded photos through the existing bounded PhotoDecoder/image editor.
Edits are local previews; rendered export/re-upload is unimplemented. Picker
results from replaced scope generations are discarded. Pending operations retain
exact bytes/keys/account per owner lifetime through renewal; leaving/relaunching
loses this in-memory retry state. This is not a native durable queue.

## Verification and remaining scope

Memory/PostgreSQL conformance and process crash checks, real URLSession/OkHttp
binary transfer/receipt recovery, account isolation and native builds have passed.
Read [construction and evidence](../../docs/blueprints/files-media.md). CLI 0.5.0
copies Files with the full Go backend; generated native auth/data composition
remains separate. Cloud object adapters, background uploads, upload expiration,
processing/thumbnails, folders/trash, quotas, scanning and offline sync are future
selected slices, not implicit promises of this contract.
