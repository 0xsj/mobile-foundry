# Core Identity verification and credential recovery

This extends [Identity/session ownership](identity-sessions.md). Existing sign-in,
renewal and profile responses remain unchanged. Memory and PostgreSQL implement
the same application ports. Login is not inferred to be a verified email address.

## Contact and challenge boundaries

Email uses a canonical lowercase ASCII mailbox policy (maximum 254 bytes, local
part at most 64, DNS labels at most 63). Authenticated `POST /v1/identity/email/request`
accepts email and current password. Reauthentication must succeed before staging a
verification challenge and sending mail. `POST /v1/identity/email/verify` accepts
the code; `GET /v1/identity/email` returns the caller's verified contact/status.
The first verified address is unique and retained; changing/removing an established
contact is a separate sensitive workflow, not an implicit request side effect.

A challenge is scoped to principal and purpose, contains only a SHA-256 token
digest and credential-hash checkpoint, and expires twenty minutes after creation.
Raw codes use independent 256-bit random secrets and distinct e1./p1. prefixes.
Verification and reset purposes cannot substitute for each other. Only one
challenge per principal/purpose remains; a new admitted request replaces it.
Consumption is single-use and serializes with replacement and password mutation.
Expired, replaced, consumed and stale-checkpoint codes are refused without mutation.

`POST /v1/identity/password/request-reset` admits email into a bounded 64-entry
process queue independent of address existence. A generic 202 means only admitted
work; it does not promise delivery. Lookup/storage/SMTP occur after the response,
avoiding a synchronous existence/delivery timing oracle. Unknown addresses and
worker failures never change the response body; worker failures log only public
kind/code. Existing IP admission plus global queue capacity bound work. The queue
is ephemeral, serial, has a ten-second work budget and stops before pools close.
Distributed abuse controls/per-recipient delivery policy remain deployment work.

SMTP is selected through a private IDENTITY_MAIL_CONFIG file. Production permits
verified TLS or STARTTLS, never plaintext or certificate-check bypass. Explicit
development selection permits a local capture service. Message content contains
purpose/code, not a caller-controlled redirect URL. Transport deadlines and four
concurrent sends bound the adapter; SMTP DATA acceptance is not proof of delivery.
A lost send response is not automatically replayed. No sender is selected by default.

## Password changes and atomicity

`POST /v1/identity/password/reset` accepts code/password. Authenticated
`POST /v1/identity/password/change` accepts current_password/new_password.
Current password/checkpoint validation is repeated inside the store transition.
A successful mutation atomically changes the password hash, consumes all outstanding
challenges, revokes every unrevoked session and appends required lifecycle facts.
Clients must sign in again. Existing access/refresh credentials are refused.

Sign-in issues only against the exact password hash it verified. Checked issuance
and credential mutation serialize on the principal, so an old-password check cannot
create a surviving session after reset. PostgreSQL locks principal before sessions/
challenges; memory uses one owned lock. Required event failure rolls back contact,
password, revocation and token consumption together. No cross-module transaction
or foreign SQL is involved. Contact/password principal revisions have their own
monotonic stream; session versions keep their existing stream semantics.

Audit consumes redacted email.verified/password.changed/password.recovered facts.
Password-caused session revocations use [v2](../events/identity-recovery-v1.md),
leaving v1 reason admission intact. Notifications independently consumes v2 into
its existing tombstone/token-retirement use case. No email, password, hash, token
or digest is present in integration facts or public audit evidence.

## Storage, operations and scope

Identity 0002 and Audit 0004 are additive migrations; startup checks them and never
applies them. Bounded maintenance removes only expired challenge rows. Contacts,
credential history, Audit and command receipts are not implicitly pruned/erased.

These are core password-based lifecycle flows, not complete identity/product
policy. Passkeys/MFA/OIDC, email change, administrative recovery, account deletion,
[native lifecycle forms](native-identity-live.md) are selected by Daily go-connected; live external mail delivery requires explicit provider acceptance.
Tests distinguish injected mail, local SMTP capture, PostgreSQL rollback/reopen and
public provider acceptance. See [construction/evidence](../../docs/blueprints/backend-connected-foundations.md).
