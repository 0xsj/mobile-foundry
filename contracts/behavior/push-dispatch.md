# Backend push dispatch foundation

Implemented: a trusted internal generic-preview use case and session/target/
provider seams. The core handler selects no cloud adapter or public route.
[Optional provider composition](push-providers.md) now supplies real HTTP adapters
and an explicitly selected manual preview endpoint. Neither creates a durable
queue or connects upload facts to outbound push.
Native [token collection](push-tokens.md) and the [registry](installations.md)
remain independent capabilities.

## Ownership

Notifications owns SendPushPreview, PushTargets, SessionEligibility and
PushGateway. Identity answers the consumer port through
[identity.session.eligible.v1](../capabilities/identity-session-eligibility-v1.md).
Neither core imports the other's implementation or joins foreign SQL. Memory
and PostgreSQL implement target reads/conditional retirement in Notifications.
Existing immutable migrations remain unchanged.

NewPushPreview composes the optional use case after module assembly, requiring
an explicit gateway and Identity capability. Missing dependencies are errors,
with no fixture fallback. The default API selects no gateway or send route.
Explicit provider configuration adds only the manual preview route; no worker,
timer or automatic delivery policy is supplied.

## One attempt

A trusted internal caller supplies PushLease: installation ID, principal ID,
session ID and exact observed installation version, plus a preview UUID. IDs
are canonical/nonzero; the version is a positive safe integer. This snapshot
reference is not authentication or public installation proof. No caller-supplied
title, body, account data or inbox content enters the preview gateway.

The handler reads a matching enabled/started/unexpired target, asks Identity
whether its session is eligible, then reads the exact target again. Changed
owner/version, disabled/expired targets or an applied revocation tombstone stop
dispatch. Dependency errors differ from a negative session answer. The gateway
is called once with the captured address and preview ID. The full attempt has
a five-second context budget; the capability retains its two-second budget.

Each store operation ends before the foreign query or send. No network call
runs under a store mutex, transaction or registry guard. Current tokens are read
at dispatch time rather than embedded in jobs/events/logs. Secret-bearing
address/lease diagnostic representations are redacted.

The Identity query blocks revocation already visible at the authority even when
outbox cleanup has not reached Notifications. It cannot close the remaining
query/check/send race. Revocation, rebind or expiry can happen after the last
check. Do not claim atomicity with Identity/provider, immediate suppression or
exactly-once delivery.

## Outcomes and retirement

A gateway must allowlist application/provider/environment credentials, validate
provider encoding, honor context and attempt once. It distinguishes acceptance
from definitive invalid-address rejection. Throttling, auth/config errors,
transient failures and uncertain/time-out acknowledgements are errors, never
invalid-address evidence.

Acceptance changes no registry metadata and proves no display/receipt/open.
Unknown dispositions are internal response failures. Errors cause no automatic
retry or retirement.

A definitive invalid-address response calls RetirePushTarget with the original
lease. The store atomically disables/erases the token, frees its reservation and
advances the version only if ID/principal/session/version still match an enabled
record. Missing, replaced or disabled records are successful no-ops. A late
rejection never removes a new token or another session binding. Concurrent or
repeated cleanup changes the matching version at most once.

PostgreSQL uses the existing registry guard. Token erasure, reservation, version
and disabled state commit together. A statement failure that rolls back preserves
the prior target/reservation. A lost commit acknowledgement can still mean cleanup
committed: an error reports unconfirmed retirement, not proof of unchanged state.
After definitive rejection the attempt can retain invalid_target with a cleanup
error; Retired=false alongside that error is not a negative receipt. Reconcile or
repeat only guarded cleanup, never resend just to discover its outcome. No
event/outbox/inbox marker is invented for this non-durable preview attempt.

## Continuation and limits

No receipt, persisted attempt/result, dispatch idempotency or application retry
exists. Repeated invocation can send twice. Lost acknowledgement cannot prove
whether a provider accepted. Cleanup failure after rejection must eventually
be retried separately from sending; a future durable worker must preserve that
distinction.

Real integration still needs selected provider adapters/credentials and live
acceptance, native remote payload/open admission, recipient/preference policy,
durable jobs/fanout, bounded retries and retention. Business domains must not
send from a transaction. A future Notifications job must commit with its delivery
decision and capture the intended binding, not silently retarget an installation
after session handoff. See [construction/evidence](../../docs/blueprints/push-dispatch.md).
