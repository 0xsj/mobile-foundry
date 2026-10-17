# Queued generic push previews

Follow-up: [owned recovery](push-job-recovery.md) adds cancel/replacement/readback
operations under the same optional selection. Its new child is an explicit intent;
it does not change automatic retry or uncertainty semantics below.

Notifications owns an optional queue for the existing [provider preview](push-providers.md).
It adds retained execution state and conservative recovery behind application-owned
ports. It does not turn inbox events into push, choose an audience or establish
device delivery. The synchronous preview route remains an independent one-attempt API.

## Selection and admission

PUSH_JOBS_ENABLED is exactly true/false, default false. Runtime selection requires
STORAGE=postgres, AUTH_MODE=identity and PUSH_CONFIG_FILE. Provider selection alone
does not start a worker or expose queue routes. Invalid selection refuses startup.
Memory implements matching behavior for conformance, with no process durability.
Apply immutable Notifications migration 0003 explicitly before upgrading the API.

POST /v1/installations/{installationID}/push-jobs takes strict JSON {job_id:UUID},
current auth, exactly one secure X-Installation-Key and If-Match. It proves current
installation ownership before entering the command. Queries, foreign binding,
unknown body fields, Idempotency-Key and If-None-Match are refused. A fresh job
requires that exact active installation principal/session/version. The command
stores no token, proof, credentials, arbitrary content or account selector.

Job ID is also the remote payload preview_id. Its installation/principal/session/
version are immutable. Same ID and same intent return retained state before a
fresh target check; changed input conflicts. This remains true after target
retirement or execution expiry, provided current HTTP auth/proof/binding still
authorize the caller. An enqueue commit can be unconfirmed; repeat the exact
intent or inspect status rather than substituting a new ID.

POST returns 202 with the current job, including for a terminal replay. GET
/v1/installations/{installationID}/push-jobs/{jobID} requires the current owning
session and installation proof; absent/foreign jobs are not returned. GET rejects
If-Match. Projection contains exactly job_id, status, attempts, result_code,
retired, created_at, updated_at and expires_at. It exposes no lease token or
owner/session IDs, has no registry ETag and is no-store. 202 does not promise the
job is still queued when read or that an alert was delivered.

Ten thousand retained jobs per store bound admission; existing replays remain
available at the cap. Execution expires fifteen minutes after creation. Terminal
rows are retained, not silently deleted/reused. Production retention, erasure,
admission quotas and an administrative redrive policy require deliberate follow-up.

## Execution and retry boundary

The module-owned worker polls serially at 100ms. A transaction claims at most one
due job with a random fencing token and thirty-second lease, increasing attempts.
PostgreSQL uses owned row locking with SKIP LOCKED; memory uses its mutex. Every
begin/settlement checks the exact token, expected state and unexpired lease.
No transaction or mutex spans Identity eligibility or a provider request.

The existing dispatch handler checks the original target, asks Identity through
identity.session.eligible.v1 and rechecks the target. A gateway decorator then
commits sending before delegating one provider attempt. Only a confirmed marker
permits that call. A marker commit with unknown acknowledgement never permits a
send; it may conservatively leave an uncertain job even if nothing was sent.
The existing check/send race remains; this is not an atomic transaction with
Identity, the registry or a remote provider.

| State/result | Recovery |
| --- | --- |
| queued | Claim when due; expire at its execution deadline. |
| leased, no sending marker | An expired lease can be reclaimed with a new token. Old tokens cannot begin/settle. |
| Target/session ineligible | blocked, no provider call or retargeting. |
| Preflight/marker failure before confirmed sending | Retry after 1/2/4/8 seconds, at most five claimed attempts; then failed. Fenced state prevents an unconfirmed committed marker being requeued. |
| Provider accepted and settlement committed | accepted; it is provider acceptance only. |
| Definitive invalid address | invalid_target; conditional original-binding cleanup runs once. Cleanup error retains cleanup_unconfirmed and never resends. |
| Any error/invalid response after confirmed sending | uncertain; no automatic resend. |
| sending lease expires after process loss/unconfirmed settlement | uncertain/worker_lost; no automatic resend. |

Result codes are owned constants rather than raw dependency messages. Provider
credentials/throttling/transport errors share the conservative post-marker rule;
the existing gateway does not prove safe retry phases for every failure. There
is no provider-result retry classifier, exactly-once send claim or automatic
manual redrive. A new explicit job ID is a new request and may duplicate an
earlier uncertain preview. Claim loss can discard a late provider acknowledgement.

Shutdown cancels cooperative work and waits for bounded settlement before closing
module pools. It may settle with a fresh two-second context, but never detach
another provider send. A failed settlement remains recoverable from the marker.
Jobs stay Notifications-owned; pkg/jobs supplies only polling/lifecycle mechanics.

## Verification boundary

Memory/PostgreSQL conformance covers immutable replay, scope/version changes,
backoff/exhaustion, concurrent claims, expiry, fencing, unconfirmed Begin, provider
errors and lost settlement. Forced PostgreSQL failures verify marker/result
rollback. A separate process is SIGKILLed after the committed marker and simulated
external effect; a fresh adapter recovers without another effect. HTTP checks use
real Identity/proof/version admission with an injected gateway. None of these
establish live cloud/device delivery, fanout, a broker or connected native CLI UI.
