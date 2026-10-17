# Files HTTP v1

Bearer authentication and current account-owner access are required throughout.
The API prefix is /v1; A and F below are canonical account/file UUIDs. Files is
[owned by its module](../behavior/files-media.md), not an object-store proxy URL.

| Method/path | Input | Success |
| --- | --- | --- |
| POST /accounts/A/files/uploads | JSON name, content_type, byte_size, sha256; optional Idempotency-Key | 201 receipt with pending file, ETag 1, Location metadata path |
| PUT /accounts/A/files/F/content | Exact raw bytes and declared Content-Type; maximum 10 MiB | 200 JSON status=stored |
| POST /accounts/A/files/F/complete | Bodyless; If-Match; optional Idempotency-Key | 200 receipt with available file |
| GET /accounts/A/files/F | No query | 200 current nondeleted metadata with ETag |
| GET /accounts/A/files | limit 1–100 (default 20), optional cursor | 200 account_id, files, next_cursor or null |
| GET /accounts/A/files/F/content | No query | 200 bytes, declared type, attachment filename, ETag, no-store, nosniff |
| DELETE /accounts/A/files/F | Bodyless; If-Match; optional Idempotency-Key | 200 receipt with deleted file after byte cleanup succeeds |
| GET /receipts/files/KEY?account_id=A | Current authenticated principal and account access | 200 historical keyed receipt with ETag |

GET routes also support HEAD. Metadata/command responses are JSON; downloads are
raw binary. Metadata includes id, account_id, owner_id, name, content_type,
byte_size, sha256, status, version, created_at and updated_at. Timestamps are UTC
RFC3339. Commands return key (empty for unkeyed operations), action
(request_upload, complete_upload or delete), principal_id and nested file.
Unknown fields, extra query members, repeated query values/headers and malformed
preconditions are refused. Metadata JSON uses the shared 16 KiB decoder. Upload
content refuses If-Match and Idempotency-Key; completion/deletion refuse bodies.

Lists use stable UUID keyset order and include pending and available files.
Deleted files are absent. The cursor is an opaque, versioned canonical encoding
bound to the principal/account, not authorization. Each page checks current access.
A changing list is not a snapshot or change feed. File IDs are never storage URLs.

403 means access was denied; unavailable access providers remain failures.
404 files.not_found covers absent/deleted metadata/content. Pending content GET
is 409 files.invalid_transition. Stale mutations are 409 files.version_conflict;
key reuse with changed normalized input is 409 files.command_conflict. Missing
If-Match is 428. Declared-byte mismatch is 400 files.content_mismatch. Oversized
content is 413 http.body_too_large. Missing stored content before completion is
503 files.object_missing; corrupt persisted bytes/state are internal failures.

A keyed deletion can commit its tombstone/receipt before external cleanup fails.
Keep the original key/file/version and retry that same operation; a receipt read
proves metadata commit only. Replayed command responses are historical even after
later deletion, and do not grant current download access. Unknown commit outcomes
must not be converted into a claim of rollback.
