# Queued push-preview construction

Recovery follow-up: [owned cancellation and linked replacement](push-job-recovery.md)
now handle explicit terminal recovery without resetting the source. The conservative
automatic retry/send-marker behavior below remains unchanged.

Notifications now has retained generic preview jobs, exact claims and conservative
recovery. Read [behavior](../../contracts/behavior/push-jobs.md),
[marker reasoning](../../backend/go/notes/patterns/send-markers-and-conservative-recovery.md)
and [the module walkthrough](../../backend/go/notes/modules/internal/notifications/jobs.md).
The synchronous preview remains available independently; enabling providers alone
does not enable jobs.

## Configure and request

Complete [private provider setup](push-providers.md), retain PostgreSQL and apply
the new owned migration. Conceptual environment (use your private config path):

```properties
STORAGE=postgres
AUTH_MODE=identity
PUSH_CONFIG_FILE=/absolute/private/path/push.local.json
PUSH_JOBS_ENABLED=true
```

Runnable from backend/go after configuring the database environment:

```sh
make migrate migrate-check
make dev
```

Conceptual request using the current installation owner, secure proof and revision:

```http
POST /v1/installations/INSTALLATION_UUID/push-jobs
Authorization: Bearer ACCESS_CREDENTIAL
X-Installation-Key: INSTALLATION_PROOF
If-Match: "CURRENT_REVISION"
Content-Type: application/json

{"job_id":"CANONICAL_NONZERO_UUID"}
```

Read GET at that path plus /JOB_UUID with the same auth/proof and no If-Match.
Repeat an unconfirmed enqueue with the identical body/revision. Do not invent a
new ID to retry acknowledgement. Execution may finish before the 202 response;
the returned status is authoritative only at its snapshot. uncertain is retained
for review, not automatically replayed. accepted is no delivery/open receipt.
Preview TTL remains zero at the providers; queueing backend work does not turn it
into an offline-device backlog.

## Verify locally

Runnable from the repository root:

```sh
make go-check go-test
make -C backend/go smoke
make starter-check cli-test notes-check
```

Runnable from backend/go, with local PostgreSQL tools and no provider credentials:

```sh
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin python3 -B scripts/postgres_integration.py
```

The suite owns its disposable database and never resets retained Compose data.
Its helper executable is deliberately killed after committing sending and recording
one simulated external attempt. Recovery uses a new pool/adapter and an injected
clock, with no thirty-second test sleep. Forced constraints separately prove
marker and settlement rollback. Real cloud credentials are never loaded by the
acceptance processes; inherited provider/worker settings are cleared.

## Observed evidence — 2026-10-17

Source formatting/import/architecture/vet/build, full Go race tests and compiled
process smoke pass. Memory/PostgreSQL cases exercise acceptance/rejection,
immutable replay/conflict, preflight retry/exhaustion, ineligible/rebound targets,
unknown marker commit, uncertain provider response, lost settlement and cleanup
failure. Twelve competing claims produce one winner. Expired claims, old-token
begin/finish and execution expiry are checked. HTTP tests use real Identity and
proof/revision guards with an injected gateway; queue selection remains off by default.

The disposable PostgreSQL 14.24 suite passes the new migration, forced marker/
settlement write rollback and helper-process SIGKILL recovery, alongside existing
outbox/inbox/account/auth/Files/installation recovery. Restored job timestamps
are normalized to UTC/microseconds after the first database run exposed a parity
mismatch. A full parallel run also hit an existing server-drain test's two-second
deadline; the subsequent full suite and generated copy passed without changing
that server test or its timeout.

All twenty-eight CLI tests and inventory checks pass. CLI 0.10.0 independently
generated `/private/tmp/mobile-push-jobs-starter-SNkXAO/verified-api`, with module
`github.com/example/verified-api/backend`. Its own check/race/smoke and disposable
PostgreSQL suite pass, including the new queue HTTP/crash/rollback cases. The
generated Make runner additionally passes auth/account/Notes/Files/inbox,
installation proof/rebind/unregister, renewal and sign-out acceptance. Inherited
PUSH_JOBS_ENABLED=true and an unreadable PUSH_CONFIG_FILE do not activate providers
inside those owned acceptance processes.

Standalone Go readiness is preserved through CLI 0.10.0. No native code/profile
changed; no native build or connected-starter promotion follows from this backend
slice. Live cloud/device delivery, provider-result automatic retries, fanout,
retention/erasure/admin redrive, broker transport and native queue UI remain separate.

Primary mechanics: [PostgreSQL row locking/SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE)
and [Go context cancellation/WithoutCancel](https://pkg.go.dev/context#WithoutCancel).
