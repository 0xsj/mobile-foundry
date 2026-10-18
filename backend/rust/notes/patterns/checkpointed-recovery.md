# Recovery checkpoints serialize credential changes

Claim: an expiring single-use code also needs a credential checkpoint, checked
under the same serialization boundary as session issuance and password mutation.

Origin/evidence: 2026-10-18 [Identity recovery transitions](../../internal/identity/src/infra/postgres/recovery.rs),
matching [memory implementation](../../internal/identity/src/infra/memory/recovery.rs)
and [shared store conformance](../../internal/identity/tests/support/recovery.rs).
Observed memory/PostgreSQL races and actual Go/Rust restart handoff pass.

An application password check observes a snapshot. Hashing the replacement takes
time; another mutation can commit while that work runs. A code's random digest
proves possession but does not prove it was authorized against the current
credentials. Persisting the observed password hash with the challenge lets the
store compare it again immediately before consuming the code.

For example, change-password and sign-in both observe H1. Change acquires the
principal lock first, installs H2, consumes challenges and revokes sessions.
Sign-in then takes the lock and refuses its stale H1. In the opposite order,
sign-in may issue a session, but the following password change revokes it. Both
orders leave no active session issued from H1 after the change commits.

```rust
// Conceptual: the store owns this complete serialized transition.
let principal = lock_principal(transaction, principal_id).await?;
require_checkpoint(principal.password_hash(), observed_hash)?;
consume_challenges(transaction, principal_id).await?;
replace_password_and_revoke_sessions(transaction, principal_id).await?;
append_required_facts(transaction, principal_id).await?;
commit(transaction).await?;
```

The actual adapter locks principal before challenges/sessions. A candidate code
lookup only discovers the principal; it is reread/locked and checked afterward.
Challenge replacement, expiry and consumption must all agree with that locked
view. Memory prepares all revocation/fact allocations before modifying its state;
SQL rolls back the complete transaction if any required fact fails.

Gotchas: this is not optimistic checking without a lock. Code expiry uses the
injected business clock, not an HTTP timeout. A lost commit acknowledgment remains
uncertain; no automatic retry or command receipt is invented. The password hash
checkpoint is private credential material and never belongs in events or logs.

Actual use: email verification, reset and authenticated change in
[the module walkthrough](../modules/internal/identity/recovery.md). Run
`make backend-recovery-parity-test` and `make backend-postgres-parity-test`.
Related: [committed refusals](committed-security-refusals.md),
[state/outbox boundaries](state-outbox-and-inbox-boundaries.md).
Next: which additional revocations require a new versioned event rather than
extending a closed existing vocabulary?
