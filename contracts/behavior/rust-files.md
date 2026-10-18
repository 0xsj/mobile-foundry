# Rust Files and immutable content

Established 2026-10-18. Rust implements the account Files foundation through
the shared [lifecycle](files-media.md), [HTTP v1](../http/files-v1.md) and
[Files facts v1](../events/files-v1.md), with Go as the behavioral reference.
This is a tested backend subset, not a generated native/Rust starter.

Files owns a private File aggregate, canonical snapshots, named command/query
handlers and consumer-owned Store, Objects and AccountAccess ports. Account
authorization crosses account.access.v1 with caller files; no foreign store or
business crate is imported. Every operation, including receipt lookup/replay,
requires current authentication and account authorization.

Metadata policy admits 1–255 trimmed Unicode scalars, no controls/path separators,
supported declared media types, 1 byte through 10 MiB and lowercase SHA-256.
Pending v1 → available v2 requires stored content verification. Pending/available
→ deleted v2/v3 commits inaccessibility before physical cleanup. Bytes are
immutable and size/digest checked on put and get. Declared type is not format
validation, malware scanning or media processing.

Request/complete/delete have optional scoped command keys. Matching replay is
checked before current existence/CAS. It preserves historical snapshots, excludes
allocated IDs/time/request metadata from the fixed files.command.v1 fingerprint,
and appends no duplicate fact. Memory prepares metadata/receipt/envelope before
its one-lock commit; PostgreSQL locks records, claims/finalizes keyed receipts
and appends the outbox in one transaction. Its deferred receipt guard rejects
unfinished claims. Applied SQL is copied byte-for-byte from Go's Files history.

Delete cleanup remains a separate effect. A cleanup error can follow a committed
tombstone/receipt; retrying the exact deletion key repeats cleanup. Receipt reads
do not perform cleanup. A late/canceled upload can leave inaccessible orphan bytes.
Dropping a request does not prove object/database effects rolled back.

Memory metadata is bounded at 1000 records/10000 events; object memory at 64 MiB.
PostgreSQL selects its own files pool/schema and private FILES_DIRECTORY, default
.data/files. FILES_DATABASE_URL falls back to DATABASE_URL; present empty URL or
directory is invalid. Retain database and private directory together. Directory
paths derive only from admitted account/file IDs. A complete synced temporary
file is hard-linked into place without replacing an existing object; reads verify
persisted bytes. The directory is trusted private application storage.

Directory operations use at most four tracked blocking jobs. Permits/bytes stay
with the job after a caller is canceled. Normal shutdown fences HTTP, joins
delivery/reset workers, drains KDF and object jobs, then closes pools. A canceled
object close retains unjoined handles; completed joined handles are removed
immediately. Blocking filesystem calls cannot be forcibly interrupted, so the
HTTP drain budget is not a total process-shutdown guarantee.

File outboxes retain per-file order and use existing bounded owned delivery.
Audit independently admits thirteen shared event cases, retains file_id and
commits evidence/inbox atomically. Existing Account/Identity fingerprints remain
unchanged when file_id is absent. [In-app Notifications](rust-notifications.md)
now consumes completion independently; installation retirement and external push
remain separate.

HTTP admits ordered field duplicates, case folding/null no-op and signed integer
byte_size before domain policy. JSON remains bounded to 16 KiB; binary PUT to
10 MiB, independently of JSON admission. Completion/delete refuse bodies and
require strong If-Match. Downloads are authenticated no-store attachments with
nosniff, declared type and ETag; HEAD preserves headers without bytes. List cursors
bind principal/account with canonical UUID keyset order and reauthorize each page.

Evidence: twenty-four specification cases, four fixed command fingerprints,
thirteen event admissions, forty HTTP cases and an over-limit binary
case, real Go/Rust concurrent request/complete/replay, downloads/HEAD, account
isolation, scoped pagination and causal Audit output. Rust adds required-event
failure, cleanup failure/retry, corrupt-byte refusal, directory reopen and
canceled-caller/close ownership. Disposable PostgreSQL adds finalization/outbox
rollback, deferred-claim refusal and a killed Rust upload continued by Go;
Rust reads Go's completion and replays its Audit fingerprint, then Go replays
Rust's deletion and historical receipts.

Run make backend-files-parity-test and make backend-postgres-parity-test.
Limits: no cloud objects, processing/scanning, quotas/folders/trash, upload expiry,
orphan sweeping/retention, background uploads/sync, Rust live socket transport,
native-connected Rust acceptance or independently generated Rust CLI starter.
Uncaptured malformed-Unicode/MIME/router/runtime differences remain in
[the inventory](../../backend/rust/PARITY.md).

