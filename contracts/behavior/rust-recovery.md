# Rust core Identity recovery

Established 2026-10-18. Rust implements the core backend flows from
[Identity recovery](identity-recovery.md) through its own domain values,
command handlers, RecoveryStore/Mailer ports, memory/PostgreSQL stores,
owned reset queue and HTTP adapter. Existing grant/profile shapes are unchanged.

The explicit ASCII email policy is independent of Login. Authenticated email
request verifies the current password and stages a twenty-minute challenge.
Codes use separate e1./p1. prefixes and independent random 256-bit secrets; only
digests and a password-hash checkpoint enter challenge storage. One challenge
per principal/purpose remains. Verify/reset checks purpose, current digest,
checkpoint and time under the same owned transition. Replacement, expiry,
single-use consumption and password mutation cannot revive an old code.

The verified contact is canonical, unique and retained. Changing/removing it
needs a separate workflow. Password changes/reset consume all outstanding
challenges and revoke all unrevoked sessions with their required facts in one
commit. Checked session issuance compares the exact verified password hash under
the same principal lock. A completed old-password check cannot create a surviving
session after mutation. PostgreSQL locks principal before challenges/sessions;
memory prepares all state/facts before its one-lock commit.

All six shared routes are selected under `AUTH_MODE=identity`: GET/HEAD email;
POST email/request, email/verify, password/request-reset, password/reset and
password/change. Actor identity comes from access credentials, never request JSON.
Endpoint DTOs preserve ordered string duplicates, case folding and null no-op.
No mutation has command receipts or automatic replay. A response interrupted
after commit remains uncertain; clients must sign in again after password changes.

Public request-reset validates and enqueues an address without lookup. Generic
202 means ephemeral admission only. A serial owned worker performs lookup and
mail outside the response path, with capacity 64 and a ten-second work budget.
Unknown addresses and downstream failures do not change the accepted response.
Logs contain only public failure kind/code. Queue-full is a rate-limit failure;
the queue is not a durable delivery outbox. The HTTP source gate still applies.

Mail defaults to disabled. `IDENTITY_MAIL_CONFIG` explicitly selects a private
bounded JSON file with Address/From/Username/Password/Mode (lowercase aliases also
accepted). tls/starttls validate server certificates and names using the system
trust store and permit TLS 1.2/1.3. Production refuses development plaintext;
explicit development permits local capture. Four concurrent sends and a five-
second total budget bound SMTP work. Replies are bounded. DATA acknowledgment
is acceptance; the adapter closes without depending on QUIT or resending. Lost
acceptance remains uncertain. No address, raw code, password or SMTP credentials
appear in events/Audit or public diagnostics.

New principal revision facts use email.verified/password.changed/password.recovered
v1. Password-caused revocations use session.revoked.v2; v1 remains closed to
sign_out/refresh_reuse. Audit admits both schemas into canonical principal evidence
while checking each reason vocabulary. Existing fingerprints stay unchanged.
Notifications now independently consumes both revocation versions through
[its installation registry](rust-installations.md); token retirement has its
own acceptance rather than sharing Identity's transaction.

Identity 0002/Audit 0004 were already in the copied migration history; no applied
SQL is edited. RecoveryStore exposes bounded expired-challenge pruning; no Rust
maintenance command or periodic pruning schedule is selected. Shutdown drains
HTTP, fences delivery/reset workers, joins them, drains owned KDF work and closes
pools. A canceled queue close retains its join handle until a later owned close.

Acceptance: twenty shared email cases, fifteen new event cases, twenty-one HTTP
cases and actual Go/Rust local-SMTP flows. Rust checks queue capacity/drop/join,
purpose separation, fact-failure rollback, replacement/expiry, password/session
atomicity and checked-issuance races. PostgreSQL checks failed verification/reset
rollback and unconsumed challenge recovery across process kill and Go/Rust handoff.
Local TLS/STARTTLS acceptance checks matching hostname and refuses a mismatch.

Limits: no live mail-provider/device acceptance, contact change, MFA/passkeys/OIDC,
admin recovery, account deletion, distributed abuse policy, durable mail delivery,
Rust native connection or independently generated Rust starter. Read
[the inventory](../../backend/rust/PARITY.md) before selecting a product profile.
