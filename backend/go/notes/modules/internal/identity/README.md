# Identity orchestration and owned session state

Core lifecycle follow-up, 2026-10-17: continue with
[verification/recovery/password changes](recovery.md) and
[credential checkpoints](../../../patterns/identity-challenges-and-checkpoints.md).
Memory/PostgreSQL conformance and local SMTP/container recovery extend the earlier
session evidence below. Contact changes, MFA/OIDC/passkeys, distributed abuse
controls and native recovery forms remain separate.

## Session eligibility follow-up — 2026-10-17

Claim: Notifications can ask whether a bound session remains live without using
an access credential or importing Identity state. Origin: push-dispatch foundation.
Read [the query](../../../../internal/identity/app/query/session_eligibility.go),
[versioned provider](../../../../internal/identity/transport/capability/v1/session_eligibility.go),
[memory lookup](../../../../internal/identity/infra/memory/session_eligibility.go),
[PostgreSQL lookup](../../../../internal/identity/infra/postgres/session_eligibility.go)
and [shared checks](../../../../tests/push_test.go).

The query validates principal/session IDs and uses Identity's clock. Stores check
the owned session's principal and Renewable predicate, which covers start,
revocation and absolute expiry without requiring a live short access token. This
does not renew anything. Missing/wrong/expired/revoked is false; storage failure
or corrupt restoration is an error. Only notifications can invoke the capability;
API callers remain on credential v1/v2. The response contains only a boolean.

Memory/PostgreSQL conformance covers short access expiry, renewal, absolute
expiry and revocation. The composed dispatch check leaves Notifications cleanup
lagging after a real Identity revocation and still blocks the next attempt.
This answer is point-in-time, not a lease that closes the later provider race.
See [contract](../../../../../../contracts/capabilities/identity-session-eligibility-v1.md)
and [observed evidence](../../../../../../docs/blueprints/push-dispatch.md).
Next: preserve this distinction when adding real outbound providers.

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
now compose that scope. Verification/recovery and generated backend acceptance
are supplied by later follow-ups, not by this initial session run. OAuth/passkeys,
distributed abuse control and broader retention remain separate.
No Identity PostgreSQL 18.6 run is claimed from the earlier Account/Audit slice.

Related: [behavior](../../../../../../contracts/behavior/identity-sessions.md),
[principal/session/account distinction](../../../patterns/principal-session-and-account-boundaries.md)
and [security substrate](../../../substrate/argon2-and-opaque-session-credentials.md).
Next: remote account data and generated connected acceptance, preserving the
implemented native account/rotation checkpoint semantics.

## Session evidence follow-up — 2026-10-17

Claim: session-bound consumers need admitted session identity without breaking existing principal-only capability clients.

Origin: installation registry slice. Read [ResolveSession](../../../../internal/identity/app/query/resolve_session.go), [the v2 provider](../../../../internal/identity/transport/capability/v2/handler.go) and [the v2 contract](../../../../../../contracts/capabilities/identity-credential-v2.md). Memory/PostgreSQL expose AccessSession while retaining the original Access method. v1 still serializes exactly principal_id; the edge resolver selects v2 and carries trusted session ID/absolute expiry. No caller supplies those values to a domain route. Installation HTTP acceptance invokes both versions, refuses a non-edge capability caller, renews without changing binding identity and observes event-driven retirement.

The shared Principal session fields are optional for sessionless fixture adapters. This does not turn fixtures into Identity sessions. The expiry describes the refresh family, not the short-lived access credential. Future push eligibility must still consult current authority rather than treating captured metadata as a live session lookup. Related: [installation ownership](../notifications/installations.md).
