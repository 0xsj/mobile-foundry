# Rust retained push jobs and explicit recovery

Established 2026-10-18. Rust implements [queued previews](push-jobs.md) and
[owned recovery](push-job-recovery.md) through Notifications-owned ports, memory/
PostgreSQL adapters and an explicitly selected polling worker. Go remains the
behavioral reference. The [provider preview](rust-push-providers.md) remains a
separate one-attempt endpoint; inbox events do not automatically enqueue jobs.

`PUSH_JOBS_ENABLED` is exactly true/false and defaults false. True requires
PostgreSQL, owned Identity and private PUSH_CONFIG_FILE. Providers alone expose
no job route or worker. The existing immutable Notifications 0003/0004 histories
remain byte-identical to Go; migrations are applied explicitly and checked at
startup. Memory supplies conformance behavior, not process durability.

Jobs store immutable ID, installation/principal/session/revision and optional
replacement lineage/acknowledgement. They store no address, possession proof,
credentials, account data or arbitrary message. Exact same intent replays retained
state before a fresh target check; changed intent conflicts. Root enqueue cannot
replay a replacement as an unrelated root. Ten thousand retained rows bound
admission, with existing replays available at capacity. Execution lasts fifteen
minutes; terminal evidence is retained without an implicit retention/erasure policy.

Domain transitions admit queued, leased, sending, accepted, invalid_target,
blocked, failed, uncertain, expired and canceled with closed result-code/state
combinations. A due claim has a random token, thirty-second lease and incremented
attempt count. Memory serializes under its mutex; PostgreSQL locks one due row
with SKIP LOCKED. No transaction spans a capability or provider call.

RunPushJob uses the existing eligibility/target rechecks. A gateway decorator
must confirm the fenced sending-marker commit before invoking the selected
provider. An unconfirmed marker never authorizes a send. Pre-marker failures can
back off 1/2/4/8 seconds, to five claimed attempts; target/session refusal blocks.
After the marker, errors become uncertain/provider_unconfirmed, never automatic
retries. Expired sending leases become uncertain/worker_lost. Known rejection
retains invalid_target even if cleanup is unconfirmed. Exact claim/state/lease
checks prevent stale settlement from changing replacement work.

Cancellation is legal only for queued/leased work and serializes with Begin;
if Begin wins it conflicts. Repeated cancellation preserves timestamps. Explicit
replacement admits one direct child per eligible terminal source, under the same
installation/principal/session. Known-unsent failed/blocked/expired/canceled work
requires false acknowledgement; uncertain work requires true. The child can pin
a newly selected current revision, but never edits the source or descendants.
Replay preserves child ID, source, revision and acknowledgement. PostgreSQL's
existing foreign key/partial unique index reinforces lineage and one-child rules.

Selected HTTP supports enqueue/status/cancel/replacement/readback under
`/v1/installations/{id}/push-jobs`. Current Identity credentials, one installation
proof and current binding are always required. Enqueue/replacement also require
strong If-Match; GET/HEAD/cancel reject it and any body. Queries, Idempotency-Key
and If-None-Match are refused. Replacement has exactly job_id and required boolean
accept_possible_duplicate, with strict casing/duplicate/null admission. Root
enqueue retains the captured Go string case/duplicate/null policy. Responses use
the unchanged eight-field projection, no ETag and no-store; no private lease or
raw dependency information is exposed.

The reusable jobs package owns serial 100ms polling, cancellation, health and join
handles. It contains no notification policy. Stop cancels the Scope but lets the
bounded runner settle; it does not drop the send to make shutdown appear finished.
Settlement has a fresh independent two-second budget. Claim/send budgets are
separate five-second bounds; canceled futures cannot undo committed or external
effects. Main fences/joins job and event workers before Identity/provider/pool
closure. Canceling a close future retains its handle for another join. Drop abort
is a fallback, not proof of completed settlement or joining.

Evidence: `make backend-push-jobs-parity-test` runs 57 shared transitions in both
languages, Go's existing job/recovery checks and Rust memory/fault/lifecycle tests.
`make backend-postgres-parity-test` adds common store conformance, twelve-way
claims/replacements and cancellation/Begin races, marker/result/child rollback,
retained-capacity replay, and SIGKILL after a marker plus simulated file effect.
A fresh connection recovers worker_lost without another effect. Actual compiled
Rust → Go → Rust APIs run 21 shared HTTP cases, selected workers and retained
outcome/lineage handoff. Their deliberately unsupported APNs address is refused
locally before network; this proves no cloud delivery. Positive cancel/replacement
HTTP is additionally exercised with real memory Identity and no started
worker. Default compiled APIs keep all queue routes absent.

Limits: no new provider-result retry classifier, fanout, live Apple/Google delivery,
administrative/cross-session recovery, retention/erasure, native queue UI or Rust
CLI registration. Explicit replacement can duplicate an earlier uncertain alert;
no exactly-once claim follows from a local marker. Rust remains a foundation
candidate. Read [inventory](../../backend/rust/PARITY.md) and
[walkthrough](../../backend/rust/notes/modules/internal/notifications/jobs.md).
