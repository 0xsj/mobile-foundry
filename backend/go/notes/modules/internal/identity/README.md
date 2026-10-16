# Identity orchestration and owned session state

Identity can authenticate principals and own session lifecycle while Account
retains a separate ownership scope and Audit interprets its committed facts.

Origin: 2026-10-13 Go Identity slice. Source inspection, race-enabled memory/
HTTP checks and isolated PostgreSQL 14.24 acceptance. The relevant layout and
security-port boundaries follow [Bento authoring](../../../../../../../bento/backend/MODULE-AUTHORING.md),
without copying its wider Identity catalog or database baseline.

## Read the flow

1. [Domain values](../../../../internal/identity/domain/identity.go) and
   [invariant checks](../../../../internal/identity/domain/identity_test.go).
2. [Application ports](../../../../internal/identity/app/ports.go),
   [commands](../../../../internal/identity/app/command/commands.go) and
   [queries](../../../../internal/identity/app/query/queries.go).
3. [Security adapters](../../../../internal/identity/infra/security/security.go),
   then [memory](../../../../internal/identity/infra/memory/store.go) and
   [PostgreSQL](../../../../internal/identity/infra/postgres/store.go) stores.
4. [Fact mapping](../../../../internal/identity/infra/events/producer.go),
   [leased delivery](../../../../internal/identity/infra/postgres/outbox.go) and
   [owned SQL](../../../../migrations/identity/0001_principals_sessions_outbox.sql).
5. [HTTP](../../../../internal/identity/transport/http/v1/handler.go),
   [credential capability](../../../../internal/identity/transport/capability/v1/handler.go),
   its [Resolver client](../../../../internal/identity/infra/capabilities/resolver.go),
   [module](../../../../internal/identity/module.go) and
   [memory/PostgreSQL builders](../../../../internal/identity/postgres.go).

The domain knows credential digests and transitions, not Argon2, random sources,
SQL or HTTP. Commands coordinate password/token ports with atomic store operations.
The process selects Identity explicitly; Account and Audit still receive a
generic principal Resolver. Its capability client resolves credentials through
Identity's current session query without exposing the repository to handlers.

## Rotation is a transaction policy

Sign-in creates one session/current token pair and its first refresh marker.
Renewal locks the session, checks that supplied history matches the current
generation, rotates both credentials and appends a fact. The original absolute
expiry remains fixed. Keeping consumed refresh markers lets a later use identify
the same family without storing any raw credential.

Conceptual example: two renewals arrive with generation 1. The first commits
generation 2. The second acquires the lock, sees generation 1 is consumed and
commits generation 3 revocation. It returns credential refusal after that commit.
The first caller's returned grant may already be revoked; a client must not
mistake this policy for a benign concurrent refresh retry.

The Store port returns `(session, reused, error)` so committed reuse revocation
is explicit. Returning an error from inside the transaction callback would roll
back the security action. Sign-out is idempotent for an already-revoked known
family, and revocation can occur after access expiry. Grant values redact their
formatted representation; explicit HTTP projection is the place raw tokens leave.

## Evidence and limits

[Store conformance](../../../../tests/identity_conformance_test.go) runs the same
registration, expiry, concurrent rotation/reuse, sign-out and absolute-lifetime
cases against memory/PostgreSQL. The first database run caught a test fixture
reusing the same principal ID for duplicate-login admission; it now models a
fresh registration ID and the same login rather than a generator collision.
[HTTP checks](../../../../tests/identity_http_test.go) exercise actual auth,
cross-principal Account denial, prior-access invalidation, principal cursors,
safe evidence and repeated sign-out. [Database tests](../../../../tests/integration/postgres_test.go)
force outbox failure to observe principal/session/refresh rollback and lease recovery.

`make check test smoke` passes. The complete local PostgreSQL acceptance also
passes, including compiled API SIGKILL, restored sessions, rotation/reuse,
durable revocation/sign-out and raw-secret absence from rows/events/logs.
Account now has [bootstrap/discovery](../../../../../../contracts/behavior/account-bootstrap.md)
without an Identity store dependency. [Native Go adapters](../../../../../../contracts/behavior/go-native-session.md)
now compose that scope. Verification/recovery, OAuth/passkeys,
distributed abuse control, retention or generated backend acceptance is supplied.
No Identity PostgreSQL 18.6 run is claimed from the earlier Account/Audit slice.

Related: [behavior](../../../../../../contracts/behavior/identity-sessions.md),
[principal/session/account distinction](../../../patterns/principal-session-and-account-boundaries.md)
and [security substrate](../../../substrate/argon2-and-opaque-session-credentials.md).
Next: remote account data and generated connected acceptance, preserving the
implemented native account/rotation checkpoint semantics.
