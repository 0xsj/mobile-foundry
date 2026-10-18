# Committed security refusals

Claim: an operation can commit a protective state change and then correctly
refuse the caller; that outcome must not be confused with a failed transaction.

Origin/evidence: 2026-10-18 Identity application tests and actual Go/Rust refresh
reuse acceptance. This conclusion follows the existing Go session contract.

Rotation checks a retained refresh-family marker under the owning store lock.
A current marker rotates the session. A consumed predecessor commits revocation
and a fact, then returns Rotation::Reused. The command maps that explicit result
to the public credential refusal after storage has committed. Returning a plain
error from a future transaction closure would risk rolling back the protective
change while telling the caller it had been handled.

```rust
// Excerpt: the command refuses a successfully committed security outcome.
Rotation::Reused => return Err(domain::credential_refused()),
```

The [full command](../../internal/identity/src/app/command/mod.rs) supplies context.
The application tests concurrently renew one original credential and confirm that
the returned grant can already be revoked. Repeating known-family sign-out
succeeds without creating another fact; unrelated sessions retain their authority.

Gotchas: a generic retry policy would turn an uncertain rotation into proven
reuse. A lost response is not proof that the old credential is safe to send
again. Future PostgreSQL adapters must preserve the same commit-before-refusal
meaning and atomic event write. Current retained memory facts do not establish
durable delivery or exactly-once reactions.
The selected v1 revocation envelope now reaches independent memory Audit with
sign_out or refresh_reuse reason; the actual Audit runner observes both while a
separate session retains read authority. Read [commit/replay boundaries](state-outbox-and-inbox-boundaries.md).

Used in [Identity](../modules/internal/identity/README.md). Related:
[wire admission](wire-admission-before-domain.md) and
[owned drain](readiness-and-owned-drain.md). Next: how should native restoration
present uncertainty after it cannot confirm a credential rotation?
