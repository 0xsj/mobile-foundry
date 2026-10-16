# Go Identity and sessions

Implemented 2026-10-13. This is principal authentication with owned credentials
and sessions, separate from Account ownership, membership and selection. Memory
and PostgreSQL adapters implement the same application ports.
[Account bootstrap/listing](account-bootstrap.md) is implemented separately;
[native Go adapters](go-native-session.md) are implemented against these public
contracts. Provider federation and backend CLI extraction remain later slices.

## Values and ownership

Identity owns canonical principal IDs, login/display name, credential hashes and
session state. Login trims surrounding whitespace and folds case, then requires
3–128 ASCII characters matching `[a-z0-9][a-z0-9._@+-]{2,127}`. It is a login
identifier, not a verified email address. Display name uses 1–80 Unicode code
points without controls, after trimming. Passwords retain exact supplied bytes,
require valid UTF-8, at least 12 code points and at most 256 bytes. These are
initial product policies. Domain restoration rejects noncanonical values.

Each session has its own ID, principal ID, generation, current credential
digests, creation/update times, access expiry, fixed absolute expiry and optional
revocation time. Times are UTC microseconds; generations remain positive JSON-safe
integers. Access lasts up to 15 minutes; session/refresh lifetime is 30 days from
creation. Renewal preserves session/principal identity and absolute expiry.
No access is admitted before issuance, at/after expiry, or after revocation.

Application ports own Store, Passwords and Tokens. Security, memory/PostgreSQL,
event mapping and capability routing stay in infrastructure. Identity never
imports Account's implementation or reads its tables. Registration creates a
principal only; account creation remains an explicit authenticated command.

## Credential adapters

The selected password adapter uses Argon2id with a random 16-byte salt, 32-byte
output, 19 MiB memory, two iterations and one lane. Stored PHC parameters are
strictly admitted; malformed/unsupported hashes are internal failures rather
than wrong-password answers. At most two computations run concurrently; excess
work returns 429. Cancellation is checked before/after the bounded computation;
the underlying KDF is not interrupted mid-computation. Unknown valid logins run
a dummy verification and receive the same credential refusal as wrong passwords.

Opaque access and refresh tokens contain independent 256-bit random secrets,
with purpose prefixes `a1.` and `r1.`. Only their SHA-256 digests are stored. Raw
credentials appear only in admitted requests and explicit successful grant
responses; they never belong in event/evidence/log payloads. Token parsing rejects
the wrong purpose, malformed lengths and noncanonical encodings.

A process-local edge gate admits a burst of 12 credential requests per source
IP/endpoint, replenishing one per second, with at most 1,000 keys. It uses the
connection address and does not trust forwarded headers. This is bounded local
admission, not distributed abuse protection or production capacity tuning.

## Commands and atomic writes

Registration commits principal state and `identity.principal.registered.v1`
together. Sign-in verifies the admitted password, creates a session and commits
session/current digests, its refresh-history marker and issued fact together.
Failure writes no partial state; login conflicts are distinct from storage failure.

Renewal authenticates through the supplied refresh secret and locks the current
session. A current credential atomically advances generation, replaces both
access/refresh digests, retains the predecessor marker and appends a renewed fact.
Previous access credentials stop resolving. Rotation must supply fresh digests.

A consumed refresh credential proves membership in its session family. Reuse
atomically revokes that session and appends a revoked fact with `refresh_reuse`,
then the command returns 401. This is a committed security outcome, not a failed
transaction. Two concurrent renewals using one credential admit one rotation and
one reuse revocation; the newly returned grant can therefore already be revoked.
There is no grace window or automatic credential replay.

Sign-out accepts a known refresh credential, including its retained predecessor,
and atomically revokes the session with reason `sign_out`. Repeating sign-out on
that revoked family succeeds without another fact. An unknown/malformed credential
is refused. Other sessions belonging to the principal remain independent.

Writes have no idempotent replay receipts. A lost renewal response may leave the
old refresh credential consumed; blindly replaying it revokes the session and
requires sign-in again. A COMMIT response loss retains the persistence helper's
explicit uncertainty. Do not infer rollback or silently retry these commands.

## Authentication and Audit seams

`AUTH_MODE=identity` explicitly selects the Identity module and its
[credential capability](../capabilities/identity-credential-v1.md). The API's
Resolver adapter invokes that capability as caller `api`. The provider hashes
the access credential, loads current session state and admits the principal only
if the session is active. Missing providers, database failures, timeouts and
malformed decisions remain failures; they never become an anonymous success.

Authorization happens at request admission. Revocation blocks subsequent
resolution; it does not undo an already admitted operation. Account retains its
owner policy. `AUTH_MODE=deny` remains the default. Existing paired development
credentials select fixture mode when AUTH_MODE is omitted; explicit modes cannot
mix fixture credentials with real Identity. Identity endpoints are registered only
when that module is selected.

Audit's independent `audit.identity` receiving adapter validates
[lifecycle facts](../events/identity-v1.md) into principal-scoped evidence. Identity
events omit account ID. Evidence stores principal/session IDs, action, version,
time, causal metadata and admitted revocation reason; no login, profile name,
password, credential hash or bearer secret. `GET /v1/principals/me/audit` reads only
the authenticated subject and uses a subject-bound keyset cursor. Account evidence
remains account-scoped and retains its existing access capability.

Audit migration `0002` extends its owned table without changing applied `0001`.
Empty new evidence fields are omitted from fingerprint encoding, preserving
existing Account inbox identities. Both scopes share commit-ordered append
positions; arrival order is not global business order.

## Storage and lifecycle

PostgreSQL Identity owns its schema/pool, principals, sessions, refresh history
and leased outbox. Foreign keys stay within Identity. `IDENTITY_DATABASE_URL`
overrides DATABASE_URL and is required when Identity/PostgreSQL is selected.
The migrator includes Identity when that URL is configured. Startup checks
embedded histories; no implicit schema changes or resets occur.

Session writes and required facts share a transaction. One worker owns one store,
claims one record per poll and uses five-second recoverable leases, the existing
two-second delivery budget/five-failure development policy, stable event identity
and Audit inbox deduplication. Exhausted/quarantined records fail readiness.
Assembly stops all workers before closing any pool.

Memory admits at most 1,000 principals, 10,000 sessions, 100,000 refresh markers
and 10,000 outbox records; all are lost on exit. PostgreSQL retains committed
credentials, revocations/history and delivery state across API exits. Neither
adapter prunes records or supplies administrative dead-letter repair yet.

## Evidence and limits

`make check test smoke` passes static boundaries, vet/build, race-enabled
security/HTTP/domain/memory checks and the existing denying/fixture process smoke.
The disposable PostgreSQL suite additionally runs the same store conformance
cases, atomic Identity rollback and lease recovery. Compiled processes verify
real registration/sign-in, Account access, API SIGKILL, restored sessions,
rotation/reuse, persisted revocation/sign-out, scoped Audit and secret-free
database snapshots/logs. This batch ran against local PostgreSQL 14.24; previous
Account/Audit PostgreSQL 18.6 evidence does not establish an Identity run on 18.6.

Email verification/recovery, credential changes, account membership,
MFA/passkeys/OIDC, session management UI, distributed throttling, pruning,
deployment TLS, external-provider conformance and native Go adapters remain
separate work. This API is not an OAuth/OIDC authorization server or a complete
production identity service. Fixed `go.identity.v1` wire authority naming needs
deployment/endpoint scoping in the future native adapter.
