# Rust session-bound installations

Established 2026-10-18. Rust implements the registry subset of
[installation behavior](installations.md) and [HTTP v1](../http/installations-v1.md),
using Go as the reference. Notifications owns the domain, application ports,
named command/query handlers, memory/PostgreSQL adapters and Identity receiving
adapter. No Identity crate, foreign store or foreign SQL is imported.

The edge resolves a current owned Identity session through credential v2;
request JSON cannot choose principal, session or expiry. An independent canonical
i1 proof authorizes one installation. Only its namespaced SHA-256 digest is
retained. Opaque printable delivery tokens identify targets; they are neither
authentication nor installation proof. Provider/route admission bounds metadata
without attesting that APNs/FCM issued a token. Public views omit all credentials,
digests and principal/session identifiers. Secret/spec/mutation/snapshot formatting
is redacted; owned Secret strings zeroize on drop. This does not erase every
HTTP, allocator, database or library copy.

Create uses caller installation/registration IDs and expected version zero.
Update requires the current strong ETag, valid proof and a new registration ID,
even for unchanged input. There are no historical receipts or automatic retries:
after uncertainty, read the current view before deciding on a new mutation.
Proof plus checkpoint explicitly permits cross-session/principal rebinding.
Proof-authorized reads expose current_binding without exposing another owner.
Unregister requires the current principal/session; a settled disabled binding
returns its existing state even with an older positive precondition.

Proof/CAS, revoked-session admission and unique target ownership share one
commit. Another live installation's target conflicts without changing either
binding. Successful reclamation of an expired target disables/erases the prior
target in the same commit. Registered describes stored enabled metadata, not
current send eligibility; there is no expiry timer or foreign Session lookup.

The notifications.identity adapter accepts only Identity revoked v1 with
sign_out/refresh_reuse, or revoked v2 with password_change/password_reset.
Envelope/payload actor/version must agree and Account scope is absent. Each
retirement atomically disables matching current bindings, clears raw token and
target key, retains a principal/session tombstone, and records the exact existing
revocation fingerprint. Identical replay is a no-op; changed input conflicts.
An old event cannot disable a rebound installation; a tombstone prevents delayed
registration of the retired session, even when no installation existed initially.
Identity and Notifications commit independently, so retirement is eventual.

Memory uses the existing Notifications mutex and 10000-entry limits for registry,
tombstones and installation markers. PostgreSQL uses the existing owner pool and
installation_guard row; no network/capability call runs under that guard. The
byte-identical existing Notifications 0002 history is now selected by adapters;
0003/0004 remain artifacts for unsupported push behavior. Disabled rows, proof
digests, tombstones and markers remain retained. Raw tokens are plaintext in the
private store, with no added envelope encryption or retention policy.

HTTP supplies POST collection and GET/HEAD/PUT/DELETE item, one proof header,
sanitized views, ETags/Location, strict 16 KiB ordered JSON and existing deadlines.
Query strings including empty query, Idempotency-Key and If-None-Match are refused.
Missing/malformed proofs are 400; valid wrong proof and missing record share 404.
Missing mutation precondition is 428. DTO string duplicates preserve last value,
null no-op and earlier wrong-type refusal, including captured field case folding.

Evidence: ten proof vectors, twenty route/token/target cases, twenty-one versioned
event/fingerprint cases and forty-two HTTP cases run against both implementations.
Real API flows cover rotation/rebinding, uniqueness, unregister, renewal and all
four retirement reasons through private local SMTP where needed. Rust memory and
PostgreSQL share concurrent checkpoint/target/revocation conformance, cancellation
and expiry reclamation. Disposable PostgreSQL faults separately reject cleanup,
tombstone and marker writes and fail a claim after prior-target cleanup. Retained
tokens/rows/markers remain unchanged on failure. Crash/handoff acceptance kills
pending v1/v2 producers, continues in the other language, replays Go/Rust markers
and preserves a newly rebound registration under late delivery.

Run make backend-installations-parity-test, make rust-check rust-test and
make backend-postgres-parity-test. The observed database is disposable local
PostgreSQL 14.24; no retained Compose data, real OS tokens or cloud provider is
used. Full Unicode/MIME/path equivalence, COMMIT-wire-loss/failover, session-send
eligibility, external push/providers/jobs, retention, native Rust connection and
independent CLI extraction remain separate. Rust remains a foundation candidate;
see [parity inventory](../../backend/rust/PARITY.md) and
[learning walkthrough](../../backend/rust/notes/modules/internal/notifications/installations.md).
