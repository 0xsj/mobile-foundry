# Account and Audit HTTP v1

Implemented by the Go memory/PostgreSQL profiles. All operations require one accepted
`Authorization: Bearer <credential>`. The default resolver accepts none; explicit
development credentials are described in [behavior](../behavior/account-audit.md).
Owned credentials are selected through [Identity](identity-sessions-v1.md).
Use [runtime HTTP v1](runtime-v1.md) for problems, body admission and metadata.

| Method/path | Input | Success |
| --- | --- | --- |
| `POST /v1/accounts` | JSON `{"name":"Personal"}` | 201 account; Location; ETag `"1"` |
| `POST /v1/accounts/bootstrap` | JSON name; no query parameters | 201 newly mapped personal account, or 200 current mapped account; Location; current ETag |
| `GET /v1/accounts` | Optional `limit`/`cursor` | 200 owner-filtered account page |
| `GET /v1/accounts/{account_id}` | Canonical account ID | 200 owner account; current ETag |
| `HEAD /v1/accounts/{account_id}` | Same as GET | Same status/headers; empty body |
| `PATCH /v1/accounts/{account_id}` | JSON name; strong `If-Match: "<version>"` | 200 updated account; incremented ETag |
| `GET /v1/accounts/{account_id}/audit` | Optional `limit`/`cursor` | 200 evidence page after Account access succeeds |

Account shape: `id`, `owner_id`, `name`, `version`, `created_at`, `updated_at`.
IDs are canonical UUID strings; version is a positive JSON-safe integer;
timestamps are UTC RFC 3339 with at most six fractional digits. JSON body admits
only `name`. GoSessionAuthority and HTTPAccountDirectory now implement native
bootstrap/guarded selection/discovery; general Account mutations remain future.

Bootstrap is safe to repeat with a valid name: the first committed name wins,
and repeats return current state without renaming or emitting another fact.
Ordinary creates do not establish or replace the personal mapping. See
[bootstrap behavior](../behavior/account-bootstrap.md) for concurrency and
write-uncertainty semantics.

Account list shape: `{"accounts":[<account>],"next_cursor":null|string}`.
Limit defaults to 20 and is one canonical decimal integer from 1 to 100.
Pages sort by ascending canonical UUID, not creation time. Cursor is an unpadded
strict base64url JSON object with `version:1`, `owner_id` and an exclusive `after`
UUID, bounded to 256 encoded characters. Reject missing/empty/malformed positions,
unknown fields or versions, foreign owners, repeated/unknown parameters and bad
query encoding. Cursor contents are not authorization; current ownership is
filtered on every request. Listing is live, not a snapshot. HEAD has GET's
status/headers with an empty body.

Clients select an account through the existing owner-guarded GET by ID. There
is no global selected-account state or account-bound Identity credential. Native
restoration must validate its saved account rather than silently substituting
the personal default.

Audit shape: `{"entries":[...],"next_cursor":null|string}`. Each entry contains
`sequence`, `event_id`, `account_id`, `actor_id`, `action`, `aggregate_version`,
`occurred_at`, `recorded_at`, and optional `request_id`/`correlation_id`.
Actions are `account.created` and `account.renamed`. No account content appears.

Pages use ascending Audit append sequence, with exclusive keyset continuation.
Limit defaults to 50 and must be 1–100. A nonnull next cursor means more matching
records existed at the read. Decode it only at the transport boundary; it is a
bounded base64url object containing account ID/position, not a credential. Reject
cross-account, malformed or empty cursors, repeated/unknown parameters and
malformed query encoding. Each page rechecks current access. HEAD also works on
the Audit GET route through the shared method adapter.

| Failure | HTTP status |
| --- | --- |
| Missing/invalid credential | 401 |
| Valid principal without owner access | 403 |
| Absent account on direct read/rename | 404 |
| Invalid name/ID/body/cursor/precondition/key | 400 |
| Missing rename `If-Match` | 428 |
| Stale version at read or atomic write | 409 |
| Dependency/capacity failure | 503 |
| Unsupported method | 405 |

An Audit lookup for an unknown account receives an admitted negative access
decision and returns 403. Dependency failure retains its classification rather
than becoming 403/404. Other shared HTTP admission failures follow runtime v1.
`Idempotency-Key` is unsupported for all three writes and rejected; no generic
receipt endpoint is implied. A missing response can follow a committed write.
Only personal bootstrap has the separately specified repeat semantics.

Audit is eventually updated after Account commit. Memory loses all data on exit;
[PostgreSQL](../behavior/postgres-persistence.md) retains committed state/facts and
recovers pending delivery. [Identity](../behavior/identity-sessions.md) supplies
explicit owned authentication. Membership and native integration remain future.
