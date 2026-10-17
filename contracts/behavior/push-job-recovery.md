# Owned push-job recovery

This extends [queued previews](push-jobs.md) with explicit cancellation and linked
replacement. It is a current-installation-owner API, not cross-user administration,
a scheduler or evidence that an earlier uncertain send did not happen. The same
PUSH_JOBS_ENABLED/provider/Identity/PostgreSQL selection applies; default is off.
Apply immutable Notifications migration 0004 before upgrading an existing API.

## Cancellation

POST /v1/installations/{installationID}/push-jobs/{jobID}/cancel requires current
Identity auth and installation proof. It admits no body, If-Match, query,
Idempotency-Key or If-None-Match. The current installation must still belong to
that session, and the job must match installation/principal/session. Foreign or
absent jobs are not returned. Registry version is not a job cancellation token.

queued or leased may atomically become canceled/user_canceled, clearing its claim.
Cancellation and Begin serialize on the same job state: if cancellation wins,
the worker cannot commit sending or settle/requeue the old claim. If Begin wins,
cancellation conflicts. It does not retract an in-flight provider request. An
expired leased claim remains cancelable while no sending marker exists.

A repeated canceled request returns its retained job without changing timestamps.
Other terminal states and sending conflict. HTTP 200 uses the unchanged eight-field
job projection, no-store and no ETag. An unconfirmed write is read back or repeated
against that same job; a failure is not proof that sending was prevented.

## Replacement

POST /v1/installations/{installationID}/push-jobs/{jobID}/replacement requires
current auth/proof plus If-Match and exactly two JSON members: job_id (a fresh
canonical UUID) and accept_possible_duplicate (a required boolean). Missing/null,
unknown or case-variant names, duplicate members, wrong types, self-replacement,
queries and replay headers fail admission.

| Source status | Required acknowledgement | Behavior |
| --- | --- | --- |
| failed, blocked, expired, canceled | false | New intent for known-unsent terminal work. |
| uncertain | true | New intent with an explicit possible-duplicate acknowledgement. |
| queued, leased, sending, accepted, invalid_target | Either | Conflict; not eligible for replacement. |

The new job has its own ID, execution lifetime, attempt count and immutable target
revision. Source and child must have the same installation/principal/session;
current active binding/version is checked for a fresh child. A new revision under
the same owner can be selected explicitly, without editing the source. Sign-in or
rebinding to a different session does not grant recovery of the former session's
jobs. Worker eligibility/target rechecks still apply independently at execution.

The child stores retry_of and the acknowledgement as private immutable intent
fields. The original outcome, timestamps, address revision and uncertainty are
preserved. Each source admits at most one direct replacement, enforced atomically
by both stores and a PostgreSQL partial unique index. The database reference stays
inside Notifications. Neither operation silently affects descendants.

The same child ID, source, target revision and acknowledgement return retained
state before a fresh target check, including after execution/cancellation. Changing
any immutable input conflicts. A different child ID for an already-replaced source
conflicts and directs the caller to read its existing replacement. Plain enqueue
cannot replay a replacement as a root job and erase its lineage.

POST returns 202 with the existing eight-field job projection, including for a
terminal replay. GET at the same /replacement path uses auth/proof, no body or
If-Match, and returns the direct child or 404. It is an owned readback after an
unconfirmed request, not a source-state rewrite. If a retained child itself later
needs recovery, another explicit operation targets that child, with its own policy.

## Atomicity, migration and limits

Replacement admission, capacity check, source rule and insertion share one owned
critical section/transaction. Failed insertion cannot reserve a child or mutate
the source. Same-ID replay precedes admission capacity. The existing ten-thousand
retained-row cap applies to roots and children together. Cancellation/Begin use
the same mutex or row lock; no foreign capability/provider call is inside either.

Migration 0004 extends status/code vocabulary and adds nullable lineage plus a
default-false acknowledgement. Existing root intents/outcomes remain intact.
Terminal evidence/lineage are retained; erasure and pruning need an explicit
policy accounting for related rows. No automatic redrive, provider-result retry
classification, operational roles, cross-session access or live delivery claim is
introduced. An explicit replacement can duplicate a previous uncertain alert.
