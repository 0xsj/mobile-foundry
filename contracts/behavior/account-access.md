# Session-bound HTTP and account Notes

Status: native seam implemented in this slice, 2026-10-10. The authority and
HTTP gallery transport are fixtures. The separate [Go integration](go-native-session.md)
now supplies real provider/server authentication and account discovery.

## Authenticated requests

AuthenticatedHTTPClient implements the existing HTTPClient port and binds one
SessionController lease. It accepts an API base and transport through a factory.
HTTPS is required by default. An explicit local-development option permits HTTP
only for localhost, 127.0.0.1 and ::1; TLS/certificate validation is unchanged.
Existing API-relative path admission and no-redirect transports apply.

Before dispatch, require the exact owner/scope/epoch lease to remain current and
unexpired. Retrieve that lease's access token, admit Bearer header syntax and add
Authorization once. Caller Authorization headers are refused case-insensitively.
Forward budget, correlation/query/body, conditional and idempotency options.
Check cancellation and the same lease after completion, including error paths.
Stale values/failures/defects become session.scope_changed cancellation values;
explicit native cancellation still propagates. Expired/stale admission sends no IO.
An accepted request may reach the server after sign-out; this gate cannot undo it.

Current responses and expected failures retain the existing HTTP behavior and
metadata. A 401 is one refused attempt: no automatic renewal, retry, replay,
vault mutation or controller sign-out. A future provider must define that policy.
Tokens/passwords stay out of the gallery's public probe report.

## Local account data

AccountNotesRepositories selects existing Memory/SQLite CRUD adapters using a
stable partition for authority + account ID. Principal is an access identity;
two principals in the same granted account share its local collection. Different
authorities/accounts remain distinct, including case and ambiguous concatenations.
The canonical key is UTF-8 `account-notes.v1\n<authority>\n<account>` and its
SHA-256 lowercase hex digest. A digest bounds filenames and preserves case on
case-insensitive filesystems. It supplies naming, not encryption or authorization.

Device databases occupy a new account namespace with the existing v1 Notes
schema. Guest Local notes are neither moved nor adopted. Memory collections live
in their factory owner. SessionNotesRepository binds a concrete account repository
to one lease and checks before/after every read/write. Work never retargets after
account changes. An already accepted write may commit to its original account;
its late result cannot populate the new account's feature.

On sign-out, replacement, expiry, renewal or owner shutdown, the account editor
closes and discards drafts, visible results, mutation feedback and delete targets.
Successful renewal creates a fresh editor lease and reloads the same collection.
Device records are retained across sign-out/reopening until explicit note deletion;
Memory records survive account switching while that source owner remains alive.
Replacing/closing the Memory source discards them. No guest migration, outbox,
cross-device sync, at-rest Notes encryption, automatic data wipe or server access
control is established. Production data retention remains a product decision.

## Catalog and evidence

Identity and session embeds Account notes only while a lease is active. It also
offers a one-attempt authenticated HTTP probe with Success/Unauthorized/Slow
fixture responses. The probe uses real request preparation/problem decoding and
labels its transport as local; it sends no network traffic. Old probe reports are
removed when the lease changes. The owned Slow probe cooperates with cancellation;
controlled tests also cover transports/writes that complete after scope closure.

Shared fixtures, controlled late-result tests, real native SQLite partitions,
native transport tests and actual editor interactions establish the seam. Native
learning notes record executed counts and limits. Provider authentication,
server-enforced tenancy, remote CRUD and physical-device/backup audits remain
separate work.
