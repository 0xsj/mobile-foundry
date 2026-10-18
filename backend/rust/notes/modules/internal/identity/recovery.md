# Checkpointed recovery and owned mail work

Claim: recovery can reuse Identity's credential authority while keeping code
consumption, password replacement, session revocation and facts in one store commit.

Origin/evidence: 2026-10-18 implementation on Rust 1.86; shared email/event/HTTP
fixtures, actual Go/Rust local-SMTP flows and isolated PostgreSQL 14.24 rollback,
process-kill and cross-language challenge consumption. Read
[behavior and limits](../../../../../../contracts/behavior/rust-recovery.md).

Read these sources in order:

1. [Recovery domain values](../../../../internal/identity/src/domain/recovery.rs):
   canonical ASCII email, redacted Contact/Challenge and twenty-minute validity.
2. [Recovery ports](../../../../internal/identity/src/app/recovery.rs) and
   [commands](../../../../internal/identity/src/app/command/recovery.rs):
   authenticate current password, stage a hash checkpoint, persist before mail,
   and delegate transitions to the owned store. Mailer carries no SMTP types;
   ResetRequests keeps HTTP admission independent of the selected queue adapter.
3. [Memory transitions](../../../../internal/identity/src/infra/memory/recovery.rs)
   and [PostgreSQL transitions](../../../../internal/identity/src/infra/postgres/recovery.rs):
   prepare facts before memory mutation; lock principal before challenges/sessions
   in SQL. Both compare the exact current password hash and consume codes atomically.
4. [Queue](../../../../internal/identity/src/infra/reset_queue.rs): bounded
   anonymous admission, serial lookup/mail, stop signal, retained join handle.
5. [SMTP](../../../../internal/identity/src/infra/email.rs) and
   [HTTP recovery edge](../../../../internal/identity/src/transport/http/v1/recovery.rs):
   private configuration/TLS and endpoint-specific ordered DTO admission.
6. [Lifecycle envelopes](../../../../internal/identity/src/infra/events.rs) and
   [Audit receiver](../../../../internal/audit/src/infra/events.rs): separate v1/v2
   reason vocabularies, no code/address/hash in integration facts.

Example: a reset code records hash H1. A user changes their password to H2 before
redeeming that code. The store compares H1 with current H2 under the principal
lock and refuses the code. A concurrent old-password sign-in also compares H1
under that lock; it cannot create a surviving session after the change. Checking
only in the command before entering the store would leave both races open.

The public reset handler canonicalizes and enqueues without an address lookup.
A 202 means admission to an ephemeral queue, independent of downstream lookup/
mail outcome. The worker can run concurrently with response delivery. It is not
a durable mail outbox, and an API crash can lose accepted work. Shutdown cancels
held async mail, joins the worker, then waits for owned blocking KDF calculations
before closing pools. Cancellation after a database/SMTP effect remains uncertain.

Verification: `make backend-recovery-parity-test` exercises twenty email policy,
fifteen event and twenty-one HTTP cases plus real contact/change/reset flows.
[Store conformance](../../../../internal/identity/tests/support/recovery.rs)
runs for memory and PostgreSQL. [Fault tests](../../../../internal/identity/tests/recovery.rs)
prove pre-commit failure leaves state intact; [queue tests](../../../../internal/identity/tests/reset_queue.rs)
hold mail while filling capacity and observe work dropped/joined on close.
`make backend-postgres-parity-test` consumes Rust/Go-created codes after restart
and checks rollback/reuse under an injected outbox failure.

Gotchas: mail follows challenge commit, so delivery failure does not undo the
staged code. Re-request replaces it. Password changes revoke sessions; interrupted
responses require explicit sign-in, not automatic replay. Contact replacement,
durable delivery and scheduled pruning remain separate. Notifications now has
an independently verified [retirement consumer](../notifications/installations.md).
Related: [checkpoint races](../../../patterns/checkpointed-recovery.md),
[SMTP boundary](../../../substrate/private-smtp-and-acceptance.md),
[owned joining](../../../language/owned-polling-and-cancellation-safe-join.md).
Next: which independently generated native product should exercise this profile?
