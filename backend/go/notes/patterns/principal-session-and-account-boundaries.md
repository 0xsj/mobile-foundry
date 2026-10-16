# Principal, session and account boundaries

Authentication identity, credential lifetime and data ownership are separate
values even when one product initially has one person and one personal account.

Origin: Go Identity implementation, 2026-10-13, compared with the existing
[native scope pattern](../../../../notes/patterns/session-scope-and-credential-lifetime.md)
and [Account/Audit construction](../../../../docs/blueprints/account-audit.md).

## What and why

Identity answers which principal a credential admits. Session state determines
whether a particular credential is current, expired or revoked. Account answers
who owns/belongs to a data scope. A session can be revoked while the principal
and accounts remain intact; a principal can have multiple independent sessions.

The Go grant therefore contains principal/session identity and credentials,
without inventing a selected account. Registration does not call another module's
store or sneak personal-account creation into an Identity transaction. The
[Account scope contract](../../../../contracts/behavior/account-bootstrap.md) now
defines explicit bootstrap/listing and guarded selection. The next native adapter
must compose that result into SessionAuthority's account-bound grant.

Conceptual example: sign in → admit principal/session → obtain/select an Account
through its contract → construct a native scope grant. A failed account lookup
must remain a failure; it cannot be replaced with the principal ID as account ID.
This is the proposed native integration sequence, not an implemented adapter.

## Applying the distinction to Audit

An Identity registration/sign-out fact may occur without an account. The generic
envelope permits omitted account ID; Account receivers still require their
contract's real account ID. Audit has an explicit principal scope for Identity
facts and retains its existing Account scope for account activity.

The [Identity receiving adapter](../../internal/audit/infra/events/identity.go)
validates its own wire types and creates Audit's evidence without importing
Identity implementations. The [personal query](../../internal/audit/app/query/principal.go)
reads only the trusted principal. Its cursor binds that subject; the Account
query continues to use the consumer-owned AccountAccess port. No credential or
profile data belongs in the evidence projection.

Adding optional evidence fields also affects deduplication encoding. Omitting
empty new fields preserves the original Account fingerprint shape; a
[compatibility check](../../internal/audit/infra/postgres/fingerprint_test.go)
protects existing inbox records. Append positions remain serialized through
commit, across both evidence scopes.

## Evidence, gotchas and actual use

The real HTTP suite signs in two principals and observes cross-account denial,
subject-bound cursor refusal and selected credential-free Audit facts. PostgreSQL
acceptance observes credentials and revocations across API restarts. These prove
the backend boundary; the native catalogs still use their existing authorities.

Revocation blocks subsequent credential resolution, not a request already
admitted earlier. A lost refresh response is also a write-uncertainty problem:
strict one-use replay revokes the session. The wire authority label is a protocol
label; future native issuer identity must also bind the configured deployment.
Do not claim OAuth compliance or connected starter readiness from these mechanics.

Related: [Identity module](../modules/internal/identity/README.md),
[Audit](../modules/internal/audit/README.md),
[committed delivery/cursors](transactions-leases-and-committed-cursors.md).
Account now performs synchronous explicit bootstrap through its own Directory
port. See [repeatable bootstrap](idempotent-bootstrap-and-owned-discovery.md).
Next: define native recovery when a stored account is unavailable, without
silently selecting another account during restoration.
