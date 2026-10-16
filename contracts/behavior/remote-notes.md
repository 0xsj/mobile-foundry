# Account-scoped remote Notes and write receipts

Status: Implemented 2026-10-13 in Go memory/PostgreSQL and native Swift/Kotlin
ports/adapters. Notes remains a replaceable reference domain.

## Ownership and authorization

Notes owns records and committed command receipts. Domain values have no IO;
application command/query handlers depend on their own Store and AccountAccess
ports. A Notes capability adapter calls `account.access.v1` as caller `notes`.
Account owns the current owner-only policy; Notes reads and writes require that
scope access. Denial is 403, including absent/foreign accounts. Dependency failure
is propagated rather than converted into an empty list or denied answer.
Identity authenticates the HTTP caller. Neither body nor query can set the actor.
Authorization runs before every read, write, replay and receipt lookup. Receipts
are private to `(account, principal, command)`; no account store, foreign schema,
or mutable principal-global selection is shared with Notes.

## Remote ports and HTTP

Native `RemoteNotesReader`, `RemoteNotesWriter`, and their composed
`RemoteNotesRepository` expose bounded pages, single-record reads, admitted writes
and receipt lookup. `VersionedNote`, `RemoteNoteWrite`, and `NoteWriteReceipt` are
owned values. Local `NotesRepository`, SQLite and Memory retain their earlier
unversioned/insertion-order contract; a provider cannot silently omit remote CAS
or pretend to supply receipts. `HTTPRemoteNotesRepository` owns wire admission
and accepts an injected HTTPClient; it owns no credential/session/renewal logic.

| Method/path relative to `/v1` | Input | Success |
| --- | --- | --- |
| GET `accounts/{account}/notes` | Optional `limit` (default 20, 1–100), `after` canonical note UUID | 200 `{account_id, notes:[{id,title,version}], next_after:UUID|null}` |
| GET `accounts/{account}/notes/{note}` | None | 200 `{id,title,version}` and strong ETag |
| POST `accounts/{account}/notes` | `{title}`, required Idempotency-Key canonical UUID; no If-Match | 201 immutable receipt and historical ETag |
| PATCH `accounts/{account}/notes/{note}` | `{title}`, required command key and If-Match | 200 immutable receipt and historical ETag |
| DELETE `accounts/{account}/notes/{note}` | Bodyless, required command key and If-Match | 200 immutable deletion receipt and historical ETag |
| GET `accounts/{account}/note-receipts/{command}` | Original account and command ID | 200 committed receipt and its historical ETag |

Receipt fields: `command_id`, `account_id`, `principal_id`, `action`
(`create`, `update`, `delete`), `note_id`, `title`, `version`. Delete's title is
the empty string; its version is the removed record's version plus one. It is
commit evidence, not a claim that the record still exists. Mutation Location
points to the receipt. Replays return the same evidence and original success
status even after later edits/deletion; metadata/request IDs can differ.

IDs are lowercase canonical nonzero UUIDs. Titles follow CreateNote admission:
preserve exact supplied Unicode scalars, whitespace and NUL, at most 200 code
points, with at least one character other than space/tab/CR/LF. Native Kotlin
also refuses unpaired UTF-16 surrogates. Go's common JSON decoder retains its
documented last-key-wins and Unicode replacement behavior on malformed JSON
string escapes; admitted native clients emit valid Unicode. Missing/null title
is invalid. Unknown body fields and invalid/repeated/unknown query fields fail.

Pages are live ascending UUID order with exclusive `after`, not creation order
or a snapshot. The continuation is the last returned UUID only when another
record was observed. It is a position, not an authorization token; every page
is filtered and authorized by the target account. There is no automatic full
collection download. Native admission checks page scope, bounds, strict order,
record/title/version shapes and continuation consistency.

Versions start at one; admitted integers are at most `2^53-1`. Update/delete
require the observed strong ETag (missing 428, malformed 400). A stale write is
409/notes.version_conflict, an absent record 404/notes.not_found. Concurrent
commands use store-level CAS; equal-title updates still increment version.

## Commit, replay and uncertain acknowledgement

The caller generates and retains an admitted command before dispatch, together
with its original issuer/principal/account, operation, target, title and expected
version. Each successful command commits exactly one receipt alongside its state
change. Matching replay is checked before record existence/version, so it cannot
recreate a deleted note or overwrite a newer edit. A committed key with changed
input returns 409/notes.command_conflict. Rejected/rolled-back commands do not
leave successful receipts or reserved keys.

Memory serializes with a mutex and loses state on process exit. PostgreSQL owns
the `notes` schema/pool and an explicit numbered migration. It claims a unique
receipt row inside a ReadCommitted transaction; a competing insert waits, then
a separate statement reads the winner. The v1 fingerprint is SHA-256 over fixed
JSON field encoding of the admitted command, excluding generated create IDs.
The record change and completed receipt share that transaction. A deferred
constraint checks the final row and refuses a committed incomplete claim.
Titles and receipt JSON use bytea to preserve NUL; persisted values are validated
when restored. Receipts survive deletion and API restart. This slice adds no
receipt TTL, erasure workflow or pruning policy; changing retention changes replay
guarantees and must be designed explicitly. Existing migrations are immutable.

Cancellation, a lost response and `postgres.commit_unknown` do not establish
rollback. Explicit receipt lookup can confirm the original write. A 404 receipt
means no committed receipt was observed at that moment; the first request could
still be in flight. Explicit retry uses the exact same command, original scope
and expected version. Never generate a replacement key to recover an uncertain
write. There is no automatic retry, 401 renewal, resubmission or network rollback.

Native receipt admission checks exact command/action/target/version/title,
account/principal and ETag. Swift compares title UTF-8 bytes for confirmation,
so canonical Unicode equivalence cannot falsely acknowledge different input.
Cancellation and unexpected defects propagate at ports.

## Catalog ownership

Studio → Open catalog → Go identity and accounts composes the adapter with an
HTTPClient bound to the exact current lease. Each app owns drafts, captured
versions, request cancellation and receipt feedback above UI controls. Duplicate
actions are refused synchronously. Delete requires confirmation. A confirmed
receipt survives failed follow-up reads; refresh does not repeat the write.
An unconfirmed command offers Check write receipt and Retry original write.
Pending commands remain in a per-scope map across renewal/account changes in
this catalog owner. Only the matching active scope can recover them. Canceled
or noncooperative late work cannot publish into a replacement scope.
Definitive version/missing refusals from execute clear that pending command and
require refresh; receipt-not-found does not clear it. Confirmation layouts are
keyed by account scope. The gallery shows the first twenty notes; the port
supports pagination. Route closure/process death discards transient drafts and
pending-command input. This is **not a durable device outbox or offline sync**.

## Acceptance and limits

Memory/PostgreSQL provider checks exercise twelve concurrent matching creates,
one CAS winner, changed-input refusal, Unicode/NUL, historical replay after
deletion, scope isolation, cancellation and injected receipt-finalization failure.
Compiled PostgreSQL API kill/restart checks prove receipt/state survival and
matching replay. Native shared fixtures check malformed/foreign receipts;
URLSession/OkHttp real Go checks deliberately discard a successful acknowledgement
and recover it through lookup, then exercise update/conflict/delete and account
isolation. Catalog state tests exercise renewal, duplicate admission, post-commit
read failure and late acknowledgement after scope replacement.

An iPhone 17 Pro / iOS 26.2 interaction case observes remote create, process
relaunch with restored workspace/Keychain, read/edit/confirmed delete, and
sign-out persistence. Android Notes UI gestures/relaunch were not run in this
batch; its build, host state tests and actual OkHttp acceptance are separate evidence.

Notes facts are not yet selected into Audit or Realtime. There is no cross-device
live observation, merge policy, background scheduler, durable native queue,
Supabase/Firebase implementation, production capacity/retention claim, or
independently generated connected starter in this slice.
