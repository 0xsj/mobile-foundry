# Cancel and replace owned push-preview jobs

The backend now supports explicit recovery without resetting an earlier outcome.
Read [behavior](../../contracts/behavior/push-job-recovery.md),
[replacement reasoning](../../backend/go/notes/patterns/recovery-preserves-the-original-outcome.md)
and [the module walkthrough](../../backend/go/notes/modules/internal/notifications/recovery.md).
These are owner-scoped API operations; no native recovery UI or administrator role
is selected by this slice.

## Upgrade and use

Keep the [queue selection](push-jobs.md) unchanged. Existing databases need pending
migrations applied explicitly, including 0004_push_job_recovery.sql. Runnable from
backend/go after configuring the selected database:

```sh
make migrate migrate-check
```

To cancel queued/unstarted work, send POST to its /cancel path with the current
access credential and X-Installation-Key, and no body or If-Match. Read or repeat
that same operation if acknowledgement is lost. A sending conflict does not imply
the provider has accepted or delivered; it means the safe cancellation boundary
has passed.

Conceptual replacement request for an uncertain source (possible duplicate accepted):

```http
POST /v1/installations/INSTALLATION_UUID/push-jobs/SOURCE_JOB_UUID/replacement
Authorization: Bearer ACCESS_CREDENTIAL
X-Installation-Key: INSTALLATION_PROOF
If-Match: "CURRENT_INSTALLATION_REVISION"
Content-Type: application/json

{"job_id":"NEW_CANONICAL_UUID","accept_possible_duplicate":true}
```

For failed/blocked/expired/canceled sources, explicitly use false. Accepted,
invalid-target and active jobs cannot be replaced through this route. A fresh
replacement proves the current active target; subsequent same-ID replay retains
the original child revision and acknowledgement. GET that /replacement path with
auth/proof and no If-Match to inspect the retained child. Do not invent another
child ID to recover a lost response. The original source remains unchanged, and
canceling it again does not cancel its replacement.

## Verify locally

Runnable from the repository root:

```sh
make go-check go-test
make -C backend/go smoke
make starter-check cli-test notes-check
```

Runnable from backend/go with local PostgreSQL tools; only disposable test data is owned:

```sh
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin python3 -B scripts/postgres_integration.py
```

Memory/PostgreSQL checks cover both cancellation/send-marker orderings, source
eligibility, acknowledged uncertainty, retained identity, changed inputs, foreign
owners, twelve-way replacement contention and lost acknowledgements. HTTP checks
use real Identity/proof and an injected gateway, with the existing public DTO.
Forced database constraints prove cancellation/insertion rollback. A separate
owned test database starts with migrations 0001–0003 and retained root jobs, then
applies 0004 and checks outcome preservation and new constraints. Reopened stores
read the same lineage and canceled receipts.

## Observed evidence — 2026-10-17

Source architecture/build/race/smoke and final focused recovery tests pass. The
disposable PostgreSQL 14.24 suite passes cancellation/send races, immutable source
and child replay, twelve-way replacement contention, lost-acknowledgement readback,
forced cancel/insertion rollback, reopened-store receipts and the pre-0004 upgrade.
Old queued/accepted/uncertain roots retain their outcome, timestamp and empty lineage;
new self-reference/acknowledgement constraints reject invalid rows. Prior queued
send-marker SIGKILL and module/process recovery checks remain passing.

All twenty-eight CLI tests and inventory/notes checks pass. CLI 0.11.0 independently
generated `/private/tmp/mobile-push-recovery-starter-2QauZT/recovery-verified`, using
`github.com/example/recovery-verified/backend`. Its own check/race/smoke and
disposable PostgreSQL suite pass, including the new recovery/upgrade cases. The
external generated Make-runner driver also passes auth/account/Notes/Files/inbox,
installation proof/rebind/unregister, renewal and sign-out. Acceptance clears host
provider/worker settings even when inherited true with an unreadable private path.

The first concurrently loaded generated race run hit the existing server-drain
test's two-second deadline. An isolated full rerun passed; that server test and
its timeout were not changed by this slice. No cloud credentials or device sends
were used. Native code/profile selection is unchanged, so native builds were not
rerun and connected native starter readiness is not promoted.

Standalone Go readiness is preserved through CLI 0.11.0. Live cloud delivery,
cross-session operations, retention/erasure, provider-result retries and native
queue/recovery UI remain separate. The new API supplies explicit owner recovery;
it does not turn an uncertain outcome into proof that sending failed.
