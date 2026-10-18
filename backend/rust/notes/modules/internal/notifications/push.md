# One-attempt dispatch through owned ports

Claim: point-in-time eligibility and exact target checkpoints can guard a single
external attempt while preserving known rejection separately from uncertain cleanup.

Origin/evidence: 2026-10-18 Rust 1.86, Tokio 1.53, SQLx 0.8.6, disposable
PostgreSQL 14.24; the [Go walkthrough](../../../../../go/notes/modules/internal/notifications/push.md)
is the reference. Shared cases, real module composition, paused-time tests and
database faults provide execution evidence beyond source inspection. Read:

1. [Application values/ports](../../../../internal/notifications/src/app/push.rs):
   exact owner/revision lease, redacted address, typed gateway dispositions and
   partial retirement outcome. No credential, account content or arbitrary text.
2. [SendPushPreview](../../../../internal/notifications/src/app/command/send_push_preview.rs):
   read target, query Identity, recheck target, send once and conditionally retire.
3. [Eligibility consumer](../../../../internal/notifications/src/infra/capabilities/sessions.rs),
   [Identity query/port](../../../../internal/identity/src/app/query/session_eligibility.rs)
   and [provider](../../../../internal/identity/src/transport/capability/v1/session_eligibility.rs):
   strict versioned answer with Notifications as the sole caller. No foreign stores.
4. [Memory](../../../../internal/notifications/src/infra/memory/push.rs) and
   [PostgreSQL](../../../../internal/notifications/src/infra/postgres/push.rs):
   short owned operations under the same installation mutex/registry row guard;
   stale rejection is a no-op and exact rejection clears token/reservation once.
5. [Optional composition](../../../../internal/notifications/src/lib.rs):
   new_push_preview requires an explicit gateway and admitted provider. Default
   process routes/configuration select neither sending nor a worker.

Example: I at revision 1 passes the first read. If a token rotates to revision 2
during Identity's answer, the second read refuses dispatch. If rotation instead
happens while the provider is processing revision 1, an invalid-address response
can only attempt retirement of revision 1. That cleanup returns false and keeps
the replacement token. It is not safe to look up the latest revision and clear it.

Identity's SessionStatus checks absolute refresh-session lifetime, start time,
ownership and revocation. At minute 16 a short access grant can be expired while
the session remains eligible. After Identity commits revocation, dispatch refuses
even if its event has not yet reached the still-enabled Notifications registry.
The query/check/send race remains: no distributed atomicity is implied.

Rust uses an outer Result for failure to establish a definitive attempt result.
PushAttempt is an enum: Accepted or InvalidTarget carrying Result<bool, Failure>.
The nested result preserves a known rejection alongside failed cleanup, like Go's
simultaneous attempt and error. Unknown protocol strings fail before becoming a
typed PushDisposition. See [the outcome pattern](../../../patterns/known-rejection-and-unconfirmed-cleanup.md).

The five-second budget uses Tokio's monotonic Instant, independently of business
Clock. Each borrowed phase future runs with the same timeout_at deadline. Cleanup
gets its own phase boundary within that budget, so its timeout remains inside
InvalidTarget instead of erasing the established rejection. The capability keeps
its smaller two-second budget. Timeout drops the polled future; it does not spawn
a detached task or revoke a network effect already accepted elsewhere. Synchronous
blocking code must still yield/cooperate for a timer to make progress.

Verification: make backend-push-parity-test runs twenty-one shared attempts,
twenty requests, fourteen responses and six session cases in Go/Rust, plus default
route refusal in compiled APIs. [Shared store conformance](../../../../internal/notifications/tests/support/push.rs)
runs for memory/PostgreSQL, mutates during foreign phases to detect held guards,
checks uncertainty/cleanup failure and races twelve retirements for one winner.
[Deadline/partial-result tests](../../../../internal/notifications/tests/push.rs)
use paused time, observe future destruction, preserve canceled/timed-out cleanup,
and model a lost cleanup acknowledgement without resending. [Composition acceptance](../../../../cmd/server/tests/support/push.rs)
uses actual Identity/Notifications stores through the capability with cleanup
deliberately omitted. [SQL faults](../../../../cmd/server/tests/support/push_postgres.rs)
prove failed cleanup retains token/reservation and corrupt Identity state is an
error. make backend-postgres-parity-test also uses separate owner databases and
runs Go's existing PostgreSQL dispatch tests.

Gotchas: a lease is not public authentication/proof. Accepted is not delivered.
An error is not evidence that no send or cleanup happened. Repeated invocation
can send twice; no receipt, durable attempt, queue or automatic retry exists.
Explicit providers and a guarded HTTP route now have separate
[adapter acceptance](providers.md); live device acceptance and native/CLI Rust
selection remain separate. Related: [registry ownership](installations.md),
[capability mechanics](../../pkg/capability/README.md),
[SQL lifetime](../../../substrate/sqlx-transactions-and-pool-lifetime.md).
Next: read [the providers walkthrough](providers.md) for invalid-address evidence,
bounded HTTP/TLS and private key ownership.
