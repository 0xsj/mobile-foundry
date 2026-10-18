# Core memory Identity

PostgreSQL follow-up, 2026-10-18: the [owned SQL adapter](../../../../internal/identity/src/infra/postgres.rs)
now implements the same application ports. Read [shared transaction mechanics](../../pkg/persistence/postgres/README.md)
and [restart/handoff acceptance](../../../techniques/durable-restart-and-language-handoff.md).
`make backend-postgres-parity-test` verifies actual durable behavior separately
from the memory acceptance below. Module composition selects the adapter without
foreign repositories or SQL/domain coupling.

Claim: an owned Identity module can preserve Go's credential/session behavior
while keeping domain policy, application calls and selected adapters separate.

Origin/evidence: 2026-10-18 implementation, ten Rust Identity tests, shared
policy/security fixtures and executed Go/Rust memory API acceptance.
Read [the behavior scope](../../../../../../contracts/behavior/rust-identity.md)
and [wire contract](../../../../../../contracts/http/identity-sessions-v1.md).

Source reading order:

1. [Domain](../../../../internal/identity/src/domain/mod.rs): canonical Login,
   DisplayName, exact Password, redacted Secret, microsecond Timestamp,
   Principal/Session restoration and rotation/revocation rules.
2. [Ports](../../../../internal/identity/src/app/ports.rs),
   [commands](../../../../internal/identity/src/app/command/mod.rs) and
   [Me query](../../../../internal/identity/src/app/query/mod.rs): application-owned
   Store/Passwords/Tokens/Scope/Gate, injected time/IDs, named command behavior.
3. [Memory](../../../../internal/identity/src/infra/memory.rs): one lock prepares
   fact IDs and checks scope before atomically changing indexes, state, facts and
   [mapped integration envelopes](../../../../internal/identity/src/infra/events.rs).
4. [Security](../../../../internal/identity/src/infra/security.rs): strict
   Go-compatible PHC parameters, purpose-checked opaque tokens, bounded CPU and
   socket-source admission. [Composition](../../../../internal/identity/src/lib.rs)
   selects these adapters; HTTP sees an AttemptGate port rather than constructing it.
5. [HTTP v1](../../../../internal/identity/src/transport/http/v1/mod.rs) and
   [ordered input decoder](../../../../internal/identity/src/transport/http/v1/input.rs):
   profile/grant projection, explicit string DTO policy and request Scope adapter.
6. [Credential v1](../../../../internal/identity/src/transport/capability/v1.rs),
   [v2](../../../../internal/identity/src/transport/capability/v2.rs) and
   ResolveSession: Identity verifies access and returns trusted principal/session
   evidence. Account receives only that versioned answer, never the Store.

Registration validates policy, hashes and allocates a principal, then commits
principal plus a retained fact. It issues no session/account. Sign-in performs
dummy verification for an unknown valid login, checks the observed password
hash again under the store lock, and commits session/digest indexes plus fact.
Access only resolves currently active credentials; renewal preserves family and
absolute expiry. A reused predecessor is a committed revocation followed by 401.

For example, two renewals with one refresh can yield one successful grant and
one refusal that has already invalidated that grant. The
[application tests](../../../../internal/identity/tests/application.rs) assert
this outcome and its fact sequence. They also inject fact-allocation failure and
observe no partial registration/session, exercise registration conflict and
unknown-login dummy work. These tests use an explicitly test-only password
adapter; [security tests](../../../../internal/identity/tests/security.rs) and
the process runner use the real KDF/token adapters.

Verification: 27 policy cases, shared PHC/token vectors, 17 wire cases, real
register/sign-in/me/rotate/reuse/sign-out/isolation flows and a real source-IP
attempt limit pass against Go/Rust. Rust additionally covers time/restoration,
private formatting and controlled canceled-caller KDF work. Run
`make backend-identity-parity-test` from the repository root.

Gotchas: local fact records share a commit with matching integration envelopes;
independent Audit delivery does not turn them into durable evidence or consumer
receipts. Credentials, outbox and Audit disappear on restart.
Account and credential v1/v2 now have [separate scope/evidence](../../../../../../contracts/behavior/rust-account.md).
PostgreSQL is covered above; [recovery adapters](recovery.md) now extend this
module through checkpointed store transitions and a private Mailer port.
Malformed Unicode repair, some MIME/router behavior, full network budgets and
independent CLI/native acceptance remain gaps. Related:
[committed refusals](../../../patterns/committed-security-refusals.md),
[blocking KDF ownership](../../../substrate/argon2-and-blocking-lifetime.md) and
[owned secret lifetimes](../../../language/owned-secrets-and-scope-adapters.md).
Next: read how recovery adds v2 revocation reasons while keeping v1 closed in
[the recovery walkthrough](recovery.md).
