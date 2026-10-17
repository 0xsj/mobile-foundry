# Session-bound installation registry

Implemented backend slice, 2026-10-17. Notifications owns this registry behind
Installations and InstallationProofs ports, with private domain state and focused
command/query handlers. It selects memory/PostgreSQL, not an OS/cloud SDK.

## Three independent credentials

An Identity access credential authenticates the current principal/session through
[credential resolution v2](../capabilities/identity-credential-v2.md). A client-owned
installation UUID names a local installation. A separate installation key proves
possession of its binding: i1. plus canonical unpadded base64url of 32 random bytes.
Clients must generate it cryptographically and keep it in secure local storage,
partitioned by deployment and app. Only its namespace-separated SHA-256 digest is
stored. An installation ID alone grants no access or rebinding authority.

The opaque push token is a fourth value: a delivery address, never authentication
or installation proof. The registry accepts 1–4096 printable non-space ASCII bytes
unchanged. It does not verify provider issuance or infer a fixed APNs/FCM length.
The local route policy selects apns/fcm, a 1–128 character application key and
sandbox/production; sandbox is accepted only for APNs. A future selected provider
adapter must map an allowlisted application route to actual provider credentials,
validate provider encoding and reject unsupported routes before sending.

No raw token, installation key, key digest, principal ID or session ID appears in
public installation views. Spec/snapshot/mutation diagnostic formatting is redacted.
Private routing tokens are stored in the module's protected metadata store, in
plaintext for now; at-rest envelope encryption/key management is not implemented.

## Ownership and versioning

Create admits an installation ID and registration_id, both client UUIDs. Update
requires the current ETag through If-Match and a fresh registration_id. It may
rotate a token or explicitly rebind the installation to the authenticated session.
Possession of the installation key plus the current version authorizes rebinding,
even between principals. GET requires an authenticated Identity session and the
installation key; it exposes sanitized route/version/checkpoint/times and whether
the caller owns the current binding, without identifying another owner.

Unregister requires key, current owning session and If-Match. It disables the
binding, erases its raw token and frees its target reservation atomically. Repeating
unregister on the same session's already-disabled record returns current state
without changing its version. An old session cannot unregister a newer binding;
an old version cannot remove a same-session token rotation. Wrong proof and absent
installation have the same not-found outcome. There is no bulk device listing.

Only one registered installation may reserve a (provider, application, environment,
token) target. The index uses a namespace-separated digest, not a public hash. A
conflict rolls back the whole registration. An expired conflicting registration
can be retired in the same transaction that claims its target elsewhere. Proof
loss cannot bypass a live reservation; automatic proof recovery is not supplied.

registration_id is the current accepted registration checkpoint. It is not a
historical receipt or permanent idempotency ledger. Clients use a new random UUID
for each new registration intent and never reuse an old ID after intervening
changes. Reusing the current checkpoint or a stale version returns conflict.
After uncertain acknowledgement, GET can inspect the current checkpoint/binding
and ETag; it cannot prove superseded history. There is no automatic retry/replay or
Idempotency-Key support. Registration expiry is the captured session lifetime.
registered describes metadata state; it does not attest current push eligibility.

## Domain collaboration

Notifications consumes identity.session.revoked.v1 as notifications.identity.
Its own receiver admits producer/name, omitted account scope, actor/principal,
session ID, version and reason, then calls RetireSession with owned app input.
It never imports Identity domain/application/store or joins Identity tables.

The receiver commits a fingerprinted inbox marker, session tombstone and matching
token erasure together. It records tombstones even without a current installation,
so a delayed in-flight register cannot restore a revoked session. Cleanup matches
the currently bound principal/session; a late old-session event leaves a rebound
installation alone. Same-event input is a no-op; conflicting input is a failure.
Sign-out and refresh-reuse share this path. Renewal retains the same session ID.

Credential admission and registry commit are separate boundaries. Revocation
cleanup is eventual through the existing leased Identity outbox; it is not an
atomic transaction with Identity. A future sender must recheck current session
eligibility and binding/version before external delivery, and define its remaining
check/send race. Metadata registration alone makes no immediate push suppression
or external exactly-once guarantee.

## Persistence and limits

Memory serializes changes under its owned mutex and bounds installation records,
revoked sessions and source markers to 10,000 each. PostgreSQL uses Notifications'
existing pool/schema and immutable 0002 migration. A registry-wide guard serializes
proof/CAS/target changes and revocation; this favors a clear foundation over hot-row
scaling. No capability/network call runs under that guard. Inbox delivery uses its
existing separate per-scope guards. No foreign transactions or scheduled goroutines.

Tokens are cleared on successful unregister, session retirement or conflicting
expired-target reclamation. Disabled records, proof digests, tombstones and inbox
markers are retained. Retention/erasure and active-session target discovery are
future policies. OS permission/token collection, offline unlink, provider send
queues/adapters and system notification UI remain separate capabilities.
Local-only sign-out is not a server revocation.

## Native registry ownership

Swift/Kotlin Services expose InstallationIdentity, DeliveryTarget, sanitized
InstallationRecord, InstallationVault and InstallationRegistry ports. HTTP
adapters perform one attempt through an injected exact-lease client. Reads carry
proof in X-Installation-Key; registration bodies carry the independent delivery
token. Response admission checks IDs, route policy, booleans, safe integer version,
matching ETag, real UTC timestamps and mutation intent. Private/unknown view fields
are rejected. A not-found read means no visible record; it never rotates the key.

InstallationVault serializes load-or-create and persists a cryptographically
random UUID/32-byte proof before returning it for networking. Namespace it by
deployment authority and application, independently of principal/account. Token
or environment changes reuse that identity. Catalog Device adapters use a
non-synchronizing ThisDeviceOnly Keychain item or Keystore AES-GCM/AtomicFile in
no-backup storage. Memory is an explicit process-only selection. Corrupt records,
lost encryption keys and locked/unavailable storage fail without implicit reset
or plaintext fallback. Secret-bearing model diagnostic formatting is redacted.

InstallationController owns current-state reconciliation on the UI executor.
Every bind cancels/increments its generation, clears the old record and reads
through the new lease. This includes renewal and account selection even though
the physical installation identity stays unchanged. Rebinding a client performs
no automatic POST/PUT/DELETE. Unbinding/closing drops remote presentation but
does not erase secure identity or unregister a background-capable target.

Each explicit registration uses a fresh registration UUID and the last read
revision, if any. A failed/unconfirmed mutation requires readback before another
write; there is no automatic replay, durable pending command or receipt claim.
The original target is captured for one attempt; a later token update is a new
explicit intent after readback. GET observes current state only. A stale owner
cannot publish results or begin work through a no-longer-current HTTP lease.

Sign-out invalidates native work and invokes existing Identity revocation; its
server event retires matching registrations. Native sign-out sends no delayed
DELETE under an old lease. Failed/offline revocation does not promise immediate
server retirement; absolute session expiry remains relevant. The proof stays
available so a later login can explicitly rebind. This registry does not request
notification permission, obtain OS tokens, attest provider tokens or send push.
