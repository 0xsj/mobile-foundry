# Registration is not session issuance

Claim: a swappable registration seam stays recoverable when identity creation,
credential issuance and account bootstrap remain separate outcomes.
Origin: native registration slice, 2026-10-17. Evidence: shared admission vectors,
real Go adapter checks and generated product execution recorded in
[construction/evidence](../../docs/blueprints/native-registration.md).

## What and why

Creating a principal establishes an identity record. Signing in proves knowledge
of its credential and issues a session. Account bootstrap grants the personal
workspace through the Account boundary. A registration profile proves neither
authenticated access nor an account grant. Separate return types make that
distinction visible to the compiler and keep the registration port free of vault
and account-store dependencies.

```text
Conceptual: separate explicit effects, with separate failure boundaries.
register -> profile confirmation -> user chooses sign-in -> session -> Personal
    |
    +-> lost reply -> creation uncertain -> user tries sign-in
```

Keeping sign-in explicit avoids retaining the new password across another
remote operation or confusing a failed account bootstrap with failed registration.
The form clears its password on submit and navigation passes only the admitted
login. The user re-enters their password through the existing secure session path.

Cancellation only stops the owner's interest in a reply. The server may already
have created the principal. Invalidate the owner generation and check the session
epoch after suspension; do not let an old registration result publish into a
newly authenticated session. A duplicate-login reply is not proof that the
requester owns that identity and cannot authorize automatic sign-in.

## Gotchas and alternatives

This backend rejects registration idempotency keys. Treating the form like the
receipt-backed Notes journal would store a password without providing a matching
server recovery protocol. Instead, show uncertainty and offer an explicit sign-in
path. The test proxy counts attempts/commits while retaining no registration
password or request body. A single attempt does not imply exactly-once creation.

Platform string defaults are another seam: trim sets, UTF-16 code units, Unicode
scalars and UTF-8 byte counts differ. The command owns normalization, the password
retains original bytes, and fixtures pin the native subset. The HTTP adapter admits
exact canonical profile fields, rather than silently accepting transformed input.

## Actual use, related and next

Daily's Swift/Kotlin products select HTTPIdentityRegistration; tests inject
immediate, failed, mismatched and suspended services through IdentityRegistration.
The service never calls SessionController. The product calls its existing
GoSessionAuthority only after the user chooses sign-in. Read
[the behavior boundary](../../contracts/behavior/native-registration.md),
[intent before effects](persist-intent-before-external-effect.md),
[connected composition](connected-products-and-composition.md) and
[cached content checkpoints](cached-content-and-command-checkpoints.md).
Next: does a particular product need invitations, mandatory verified contact or
approval before a personal account is available, and which module owns that rule?
