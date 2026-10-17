# Single-use challenges and credential checkpoints

Claim: a recovery token must change credentials and consume its authority in the
same transaction, while session issuance must compare the credential it verified.

Origin: core identity lifecycle slice, 2026-10-17. Evidence:
[shared conformance and rollback cases](../../tests/identity_recovery_test.go),
[HTTP cases](../../tests/identity_recovery_http_test.go) and
[versioned receiving checks](../../tests/identity_recovery_events_test.go).

## What and why

Verification and recovery use different token purposes. Only a digest is stored;
the challenge retains principal, canonical mailbox, purpose, expiry and the
password hash checkpoint. Replacing a challenge invalidates the previous digest.
Verification consumes its challenge and commits the contact plus required fact.
Password reset/change also invalidate outstanding challenges, revoke all sessions
and append required facts atomically. A failed fact allocation rolls back all of
that work, leaving the original token usable rather than half consumed.

Checking a password outside the transaction saves a database lock during Argon2,
but it introduces a race. SignIn therefore passes the verified hash to
IssueChecked. The store locks the principal and compares the checkpoint before
issuing the session. Password mutation takes the same principal lock before
session/challenge locks. An old-password verification cannot issue a surviving
session after a password change wins.

## Example

Conceptual: sign-in verifies hash H1, then reset commits H2 and revokes sessions.
The delayed sign-in attempts IssueChecked(H1); it sees H2 under the principal
lock and refuses. If issuance wins first, reset subsequently revokes that session.

## Mail and response timing

The anonymous request route admits a normalized address into a bounded ephemeral
queue. Lookup and SMTP happen after the public 202, including for unknown addresses.
This removes synchronous SMTP latency as an account-existence signal. Acceptance
does not promise mail delivery: shutdown or capacity can prevent work, and callers
can explicitly request again. Production SMTP requires TLS or STARTTLS; secrets
and addresses are redacted. The first verified address is retained; changing a
contact is a separate workflow with separate threat and notification policy.

## Gotchas and actual use

Password revocation introduces reasons outside v1's closed vocabulary. Producer
and independent consumers use identity.session.revoked.v2 for those reasons;
v1 keeps sign_out/refresh_reuse. Domain rules may accept both while wire adapters
reject wrong-schema reasons. No email, token or password hash enters the facts.

Memory/PostgreSQL execute the same single-use, expiry, replacement, stale issuance,
all-session revocation and rollback cases. The local Docker flow additionally
consumes actual SMTP-captured codes and restores a pending challenge. This is
core lifecycle coverage, not MFA, OIDC/passkeys, email change or distributed abuse
control. Native recovery forms remain unselected.

Related: [Identity walkthrough](../modules/internal/identity/recovery.md),
[fact contract](../../../../contracts/events/identity-recovery-v1.md).
Next: choose a real SMTP provider and product recovery UI; define per-recipient
abuse controls and contact-change policy before extending those workflows.
