# Identity verification, recovery and password changes

Claim: the application names the workflows while the store owns atomic challenge,
credential, revocation and required-fact changes.

Origin: 2026-10-17 lifecycle slice. Evidence: memory/PostgreSQL conformance,
forced fact failure, actual HTTP and local SMTP/container restore acceptance.

## Read the source

1. [Recovery values](../../../../internal/identity/domain/recovery.go): canonical
   email admission, redacted values, purpose/checkpoint/expiry validation.
2. [Recovery ports](../../../../internal/identity/app/recovery.go) and named
   [commands](../../../../internal/identity/app/command): explicit store/security/
   mail dependencies, current-password verification and purpose-specific tokens.
3. [Memory recovery](../../../../internal/identity/infra/memory/recovery.go) and
   [PostgreSQL recovery](../../../../internal/identity/infra/postgres/recovery.go):
   same state transitions, atomic event allocation, principal-before-session locks.
4. [Migration 0002](../../../../migrations/identity/0002_recovery.sql): independent
   contacts/challenges and principal revision without editing the applied baseline.
5. [Mail adapter](../../../../internal/identity/infra/email/smtp.go),
   [reset queue](../../../../internal/identity/infra/email/reset_queue.go) and
   [module configuration](../../../../internal/identity/mail.go).
6. [HTTP projection](../../../../internal/identity/transport/http/v1/recovery.go),
   [producer](../../../../internal/identity/infra/events/producer.go) and
   [shared cases](../../../../tests/identity_recovery_test.go).

Conceptual: request-reset accepts a valid address without querying Identity in
the HTTP response path. Its queued command looks up the verified contact, creates
a twenty-minute challenge and sends the code. Reset consumes that code and changes
the password atomically; the user then signs in again rather than receiving an
implicit session from recovery.

## Gotchas and evidence

The checkpoint must be compared inside IssueChecked as well as challenge use.
Otherwise a delayed successful password check could issue a session after reset.
Raw tokens leave only in mail; only digests persist. Generic public reset responses
do not mean durable delivery. The queue is bounded and stopped before pools close.
SMTP's DATA acceptance is terminal even if QUIT later fails; retrying on that
failure could duplicate accepted messages.

Local tests cover wrong purpose, strict expiry, replacement, single use, stale
issuance, revocation, redaction and transaction rollback. Docker acceptance captures
mail in owned Mailpit and restores a pending challenge into an unused project.
It does not establish external mailbox delivery or native recovery UI. Contact
change, MFA and federated identities remain separate policies/adapters.

Related: [checkpoint pattern](../../../patterns/identity-challenges-and-checkpoints.md),
[behavior](../../../../../../contracts/behavior/identity-recovery.md).
Next: configure a real provider and add independently accepted product forms.
