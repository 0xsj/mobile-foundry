# Rust one-attempt push dispatch

Established 2026-10-18. Rust implements the internal foundation in
[shared dispatch behavior](push-dispatch.md) and
[identity.session.eligible.v1](../capabilities/identity-session-eligibility-v1.md).
Go remains the behavioral reference. The default has no selected cloud gateway,
preview HTTP route, worker or automatic connection from Files/inbox to sending.
[Explicit providers](rust-push-providers.md) have separate configuration/HTTP acceptance.

Notifications owns PushLease/PushAddress, PushTargets, SessionEligibility,
PushGateway and the named SendPushPreview handler. Identity owns its SessionStatus
query port, memory/PostgreSQL query and capability provider. Only Notifications
may invoke this capability. Modules import no foreign domains, stores or SQL;
process/test composition alone selects both implementations. new_push_preview
requires an explicit gateway and an admitted frozen registry provider; missing
providers or refused callers fail without a fallback.

Identity answers whether a session belongs to the principal, has started, is not
revoked and remains before its absolute expiry. Access-token expiry is irrelevant
to this trusted question; renewal retains the session identity. Missing/mismatched/
revoked/expired sessions return false. Dependency failures and corrupt persisted
sessions are errors. Credential v1/v2 and public auth responses remain unchanged.
Request/response decoders preserve the captured ordered duplicate, casing, null
and earlier type-error behavior. The response requires an explicit boolean.

A lease pins one installation/principal/session/version; it is not public auth,
possession proof or a send receipt. Dispatch reads an exact enabled/started/
unexpired target, queries Identity, then reads the exact target again before one
gateway call. The first captured address includes its binding-update timestamp.
Target reads reject retirement tombstones. Store guards/transactions end before
foreign calls; mutations during either external phase must remain possible.

One five-second monotonic budget covers all phases; capability invocation retains
its own two-second maximum. Phase futures are borrowed, bounded and dropped on
timeout, without detached work. Scope checks are cooperative and cannot preempt
blocking synchronous code. Dropping a send future cannot undo external effects.
The remaining Identity/query/check/send race stays explicit: eligibility true is
not a lease or atomic promise against later revocation/rebind/expiry.

Gateways return typed Accepted or InvalidTarget. Unknown wire dispositions fail
adapter admission through PushDisposition::parse. Acceptance changes no registry
state and proves no device delivery/display/open. Auth/config/throttle/transient/
timeout or uncertain errors do not retire or retry a target. Only definitive
invalid-address evidence permits conditional retirement of the original lease.

Retirement atomically disables/erases the exact enabled owner/version, frees its
target reservation and advances its version once. Missing/replaced/disabled
bindings are successful no-ops. Memory uses its existing mutex; PostgreSQL uses
the existing registry guard and unchanged installation history. No network call
runs inside either owner boundary. A late rejection preserves a rotated token or
rebound owner. Rolled-back writes preserve prior token/reservation/state.

Rust represents known rejection as PushAttempt::InvalidTarget containing a
Result<bool, Failure> retirement outcome. It can therefore retain definitive
rejection alongside failed/canceled/timed-out cleanup, matching Go's simultaneous
attempt and error. Outer Err means dispatch did not establish a definitive result;
it does not prove a provider did nothing. Cleanup error is unconfirmed retirement,
not a negative receipt: it may have committed. Reconcile/repeat only exact guarded
cleanup; do not resend to discover cleanup state. Accepted and InvalidTarget are
distinct enum variants, and invalid protocol dispositions never enter that enum.

Evidence: shared twenty-one attempt, twenty request, fourteen response and six
session cases run independently against Go and Rust. They cover scope/expiry,
denial/outage, rotation during eligibility, rotation/rebind/unregister during send,
revocation, uncertainty, invalid responses and failed cleanup. Memory/PostgreSQL
conformance adds twelve competing retirements, cancellation and stale no-ops.
Rust tests use paused time for total/capability budgets, canceled cleanup and
future destruction; lost cleanup acknowledgement preserves known rejection and
does not resend. Real Identity/store/capability composition refuses a still-enabled
registry target after authority revocation, including after short access expiry.

Disposable PostgreSQL 14.24 verifies guarded cleanup rollback/token reservation,
cleanup-only reconciliation and corrupt-session errors. Separate Identity and
Notifications databases run the same dispatch and revocation-lag acceptance.
Go's existing memory/PostgreSQL dispatch conformance also runs. Compiled default
Go/Rust APIs expose no push-preview route in deny or owned-Identity mode and shut
down with private headers absent from logs. No applied SQL changes.

Run make backend-push-parity-test, make rust-check rust-test and
make backend-postgres-parity-test. These checks use injected gateways only.
No real provider keys, live cloud/device acceptance, dispatch receipts, durable
attempts/queue, fanout, preference policy, retention, exactly-once guarantee or
independent native/CLI Rust selection is included. Rust remains a foundation
candidate; see [parity inventory](../../backend/rust/PARITY.md) and
[learning notes](../../backend/rust/notes/modules/internal/notifications/push.md).
