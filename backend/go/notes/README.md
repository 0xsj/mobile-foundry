# Go backend learning notes

Realtime conformance follow-up, 2026-10-18: Go now shares
[hub scenarios and HTTP admission cases](../../../contracts/fixtures/backend-realtime/README.md)
with Rust. Actual APIs verify ready/refetch, committed Notes hints/replays,
failed-CAS silence, scope isolation, renewal/sign-out, capacity and open-socket
shutdown; the disposable PostgreSQL runner adds durable-store flows. Go production
behavior is unchanged. Read [Rust ownership and limits](../../rust/notes/modules/internal/realtime/README.md)
for the independent implementation and handshake-response differences.

Rust retained-jobs follow-up, 2026-10-18: read [job ownership](../../rust/notes/modules/internal/notifications/jobs.md),
[owned polling](../../rust/notes/modules/pkg/jobs/README.md) and
[send markers/explicit replacement](../../rust/notes/patterns/durable-send-markers-and-explicit-replacement.md).
57 shared transitions, memory/HTTP/fault/lifecycle checks, PostgreSQL rollback/
capacity/concurrency and owned SIGKILL recovery pass. Compiled Rust→Go→Rust
workers preserve outcomes/lineage and pass 21 shared HTTP guards without cloud
requests. Defaults remain off; retention, live delivery and Rust CLI/native
selection remain separate.

Rust provider follow-up, 2026-10-18: repository-only
[shared response/HTTP cases](../../../contracts/fixtures/backend-push-providers/README.md)
and [runner](../../../scripts/push_providers_parity.py) pass local APNs/FCM protocols,
guarded HTTP and compiled default/selected startup in both languages. PostgreSQL
HTTP additionally uses separate Rust owners. Go production code is unchanged;
the CLI excludes the two repository-fixture tests from generated sources. Read
[the Rust walkthrough](../../rust/notes/modules/internal/notifications/providers.md).

Rust push foundation follow-up, 2026-10-18: repository-only
[shared cases](../../../contracts/fixtures/backend-push/README.md) and
[runner](../../../scripts/push_parity.py) now verify dispatch checkpoints, session
eligibility, mutation races, partial cleanup outcomes and default sending-off
behavior in both implementations. Go's existing memory/PostgreSQL dispatch tests
also pass. Go production/extraction sources are unchanged. Read
[the Rust walkthrough](../../rust/notes/modules/internal/notifications/push.md).

Rust installations follow-up, 2026-10-18: repository-only
[shared corpus](../../../contracts/fixtures/backend-installations/README.md) and
[real API runner](../../../scripts/installations_parity.py) now verify proof/target
digests, registration checkpoints, rebind/rotation and all four retirement reasons.
Disposable PostgreSQL rejects each atomic retirement write, exercises separate
owner databases and resumes killed pending v1/v2 events across Go/Rust without
clearing newer bindings. Go production/extraction sources are unchanged. Read
[the Rust walkthrough](../../rust/notes/modules/internal/notifications/installations.md).

Rust Notifications follow-up, 2026-10-18: the repository-only
[shared corpus](../../../contracts/fixtures/backend-notifications/README.md) and
[real API runner](../../../scripts/notifications_parity.py) now check in-app Files
delivery, first-read/settings, scoped pages and refusal policy in both languages.
Disposable PostgreSQL verifies late rollback, six databases, killed pending
delivery and Go/Rust fingerprints for suppressed/delivered sources. Go production
and extraction code is unchanged. Read
[the Rust walkthrough](../../rust/notes/modules/internal/notifications/README.md).

Rust Files follow-up, 2026-10-18: repository-only
[shared corpus](../../../contracts/fixtures/backend-files/README.md) and
[actual API runner](../../../scripts/files_parity.py) now verify policy, fingerprints,
wire admission, immutable transfers, lifecycle receipts and Audit in both languages.
Disposable PostgreSQL continues a killed Rust upload in Go, reads/replays it in
Rust and replays Rust's deletion in Go using retained private bytes. Go production
and extraction sources are unchanged. Read
[the Rust walkthrough](../../rust/notes/modules/internal/files/README.md).

Rust recovery follow-up, 2026-10-18: repository-only
[policy/event fixtures](../../../contracts/fixtures/backend-recovery/README.md)
and [real API runner](../../../scripts/recovery_parity.py) now check canonical
email, verification/reset/change, session revocations and private local SMTP in
both languages. Disposable PostgreSQL acceptance consumes Rust/Go-created codes
across a process kill and checks retained Audit evidence. Go production behavior
is unchanged. Read [the Rust recovery walkthrough](../../rust/notes/modules/internal/identity/recovery.md).

Rust Notes follow-up, 2026-10-18: the repository-only
[Notes parity checks](../parity/notes_test.go) and
[process runner](../../../scripts/notes_parity.py) verify shared title policy,
fingerprint bytes and 47 HTTP cases. Disposable PostgreSQL acceptance also
replays actual Go/Rust receipts after restart/deletion. Go production behavior
is unchanged. Read [the Rust walkthrough](../../rust/notes/modules/internal/notes/README.md).

Rust PostgreSQL follow-up, 2026-10-18: the repository-only
[durability runner](../../../scripts/postgres_parity.py) now verifies Rust→Go→Rust
core state/session and Audit fingerprint replay in one disposable PostgreSQL 14.24
database. Exact Account/Identity/Audit migration bytes are checked before execution.
Go production/extraction sources are unchanged. This does not establish Rust
generated-product parity; Notifications/Files/recovery have separate evidence
above. Read
[the Rust transaction walkthrough](../../rust/notes/modules/pkg/persistence/postgres/README.md).

Rust Audit follow-up, 2026-10-18: the repository-only
[shared admission checks](../parity/audit_test.go) and
[Audit process runner](../../../scripts/audit_parity.py) verify expected event,
access-response and HTTP behavior in both implementations. Rust now has memory
state/outbox and evidence/inbox commits, stable-ID replay and owned worker drain;
its PostgreSQL and recovery acceptance now have separate follow-ups above. Read
[the Rust walkthrough](../../rust/notes/modules/internal/audit/README.md).

Rust Account conformance follow-up, 2026-10-18: the repository-only
[Account corpus check](../parity/account_test.go) and
[process runner](../../../scripts/account_parity.py) execute shared name/wire
expectations plus real bootstrap/CAS concurrency, owner pagination and credential
renewal/revocation against both APIs. Go production behavior and extraction
remain unchanged. Read [Rust's Account walkthrough](../../rust/notes/modules/internal/account/README.md)
for the credential v2 seam; the Audit follow-up above now supplies independent
memory delivery there.

Connected-foundation follow-up, 2026-10-17: read
[bounded hints](patterns/bounded-live-hints.md),
[credential checkpoints](patterns/identity-challenges-and-checkpoints.md),
[Realtime](modules/internal/realtime/README.md),
[Identity recovery](modules/internal/identity/recovery.md), then
[containers/backups](substrate/portable-container-and-quiesced-backups.md) and
[setup/evidence](../../../docs/blueprints/backend-connected-foundations.md).
The backend now implements native-bearer WebSockets, core identity lifecycle and
portable Docker operations. Native socket consumers/recovery forms, broker fanout,
external provider delivery and product-specific retention remain separate.

Recovery follow-up, 2026-10-17: read
[preserved outcomes](patterns/recovery-preserves-the-original-outcome.md),
[explicit boolean presence](language/explicit-json-boolean-presence.md),
[module flow](modules/internal/notifications/recovery.md), then
[setup/evidence](../../../docs/blueprints/push-job-recovery.md).
Current owners can cancel before sending or create one linked replacement with
explicit acknowledgement when the source is uncertain. Cross-session operations,
retention/erasure and live provider delivery remain separate.

Queued-preview follow-up, 2026-10-17: read
[send markers and recovery](patterns/send-markers-and-conservative-recovery.md),
[Notifications jobs](modules/internal/notifications/jobs.md),
[generic polling](modules/pkg/jobs/README.md), then
[setup and verification](../../../docs/blueprints/push-jobs.md).
Memory/PostgreSQL state, guarded HTTP, forced rollback and killed-worker recovery
now distinguish safe preflight retries from an uncertain external effect. Live
provider delivery, fanout/retention and connected native CLI UI remain separate.

Provider follow-up, 2026-10-17: read
[failure evidence](patterns/provider-errors-and-address-retirement.md),
[HTTP/signing/caches](substrate/provider-http-and-jwt.md), then
[adapter walkthrough](modules/internal/notifications/providers.md) and
[setup/evidence](../../../docs/blueprints/push-providers.md).
Local TLS protocols and guarded HTTP pass; live cloud/device acceptance and
native remote opens remain separate. CLI 0.9.0 selects only source and examples,
prunes private credentials and keeps sending disabled without explicit setup.

Push follow-up, 2026-10-17: start with
[snapshots and external effects](patterns/push-snapshots-and-external-effects.md),
then [Notifications orchestration](modules/internal/notifications/push.md), the
Identity eligibility section and [construction/evidence](../../../docs/blueprints/push-dispatch.md).
Memory/PostgreSQL ordering/rollback and independent CLI 0.8.0 acceptance pass.
The gateway remains injected; cloud sending, durable retries and native remote
opens are not established by these checks.

Installation registry follow-up, 2026-10-17: read [construction](../../../docs/blueprints/installations.md), [the module walkthrough](modules/internal/notifications/installations.md) and [session-bound target retirement](patterns/session-bound-delivery-targets.md). Source race/HTTP and PostgreSQL 14.24 rollback/crash recovery pass. The backend registry retains hashed installation proof and clears tokens on matching retirement; native token collection and external delivery remain pending.

Notifications follow-up, 2026-10-17: start with
[the module walkthrough](modules/internal/notifications/README.md) and
[delivery decisions and replay](patterns/notification-decisions-and-replay.md).
Memory and PostgreSQL 14.24 conformance, forced transaction failures and blocked
consumer/SIGKILL recovery pass. Read state and preferences survive process restart.
Native catalog inbox consumers now exist; OS token collection, APNs/FCM and retention remain pending.

Follow [the shared notes workflow](../../../docs/NOTES.md). Runtime/shared packages
and Identity/Account/Audit memory/PostgreSQL modules are implemented. Notes distinguish
source boundaries, verified execution, and future provider behavior.

Use `modules/<source-path>` for walkthroughs relative to the Go root, such as
`modules/pkg/errors/` or `modules/internal/identity/app/command/`. Put
transferable Go mechanics in `language`, architectural approaches in `patterns`,
domain distinctions in `concepts`, verification methods in `techniques`, and
versioned tool or dependency findings in `substrate`.

## Reading order

1. [Backend setup and layout](../README.md) and [runtime contract](../../../contracts/behavior/go-runtime.md).
2. [Process startup and drain](modules/cmd/server/README.md).
3. [Go interfaces, errors and contexts](language/interfaces-errors-and-contexts.md).
4. [Assembly and cleanup](modules/pkg/module/README.md), then
   [ports and composition](patterns/ports-and-module-composition.md).
   Its 2026-10-17 Bento comparison records matching boundaries and remaining
   handler/error, HTTP and PostgreSQL configuration differences. Architecture
   checks passed; this comparison is source inspection, not fresh runtime acceptance.
   Its follow-up traces the implemented access-capability and Account/Identity
   fact-to-Audit paths, and distinguishes local wiring from future transports.
   Its integration-events section traces the proposed Identity/Account → Audit
   seam against Bento's actual producer, receiving and outbox/inbox design.
   The client-realtime section separates durable delivery, ephemeral broadcast
   and a selectable WebSocket gateway, including native lifecycle limits.
   Its synchronous-capabilities section follows a consumer-owned port through
   local/remote routing and explains failure versus a negative answer. Read the
   [capability catalog](../CAPABILITIES.md) and
   [established boundary policy](../../../contracts/behavior/module-boundaries.md)
   alongside it; the implemented memory subset is described in the module notes below.
5. [Failure projection](modules/pkg/errors/README.md).
6. [HTTP admission and responses](modules/pkg/httpx/README.md), then
   [Health query/transport](modules/internal/health/README.md).
7. [Account values and atomic facts](modules/internal/account/README.md), then
   [Audit receiving/access](modules/internal/audit/README.md).
8. [Local capabilities](modules/pkg/capability/README.md) and
   [outbox delivery/worker lifetime](modules/pkg/events/README.md).
9. [PostgreSQL mechanics](modules/pkg/persistence/postgres/README.md),
   [explicit deployment](modules/cmd/migrate/README.md), then
   [transactions, leases and committed cursors](patterns/transactions-leases-and-committed-cursors.md).
10. [Toolchain history](substrate/go-runtime-toolchain.md) and
    [pgx/PostgreSQL execution](substrate/pgx-and-postgresql.md).
11. [Identity orchestration](modules/internal/identity/README.md),
    [principal/session/account distinction](patterns/principal-session-and-account-boundaries.md),
    then [Argon2/opaque credentials](substrate/argon2-and-opaque-session-credentials.md).
12. [Idempotent bootstrap and owned discovery](patterns/idempotent-bootstrap-and-owned-discovery.md),
    then Account's latest source walkthrough. Compare a stable personal mapping,
    live UUID listing and device-owned selection with generic write receipts.

13. Read [Notes commands/queries and receipts](modules/internal/notes/README.md),
then [shared acknowledgement recovery](../../../notes/patterns/command-receipts-and-uncertain-writes.md).
Memory/PostgreSQL conformance, atomic receipt-finalization rollback, CAS/replay
and killed API recovery now pass. Notes stays a replaceable exemplar. Daily now
selects a single-slot durable pending journal; backend extraction and lossy live
hints are implemented. Multi-item sync and durable Notes facts remain separate.

15. [Selecting domains for mobile products](patterns/mobile-capability-selection.md)
    separates candidate installation/notification/media/purchase ownership from
    native lifecycle and per-domain synchronization. This is proposed selection
    guidance from 2026-10-17, not implemented capability coverage.

16. [Files lifecycle](modules/internal/files/README.md), then
    [metadata and object lifecycles](patterns/metadata-and-object-lifecycles.md).
    Trace replay/CAS/outbox atomicity separately from immutable bytes and deletion
    cleanup. The native catalogs use real binary HTTP; cloud storage is separate.

## Files follow-up — 2026-10-17

Files memory/PostgreSQL conformance and forced receipt/outbox rollback pass.
Private-directory immutable writes/reopen and compiled PostgreSQL API SIGKILL
recovery pass on isolated local PostgreSQL 14.24. File facts reach Audit without
foreign implementation imports. Native URLSession/OkHttp transfers and receipt
recovery pass; both catalogs build and each platform passes two file-owner tests.
CLI 0.5.0's media-final output passes independent checks/race/smoke, PostgreSQL
recovery and generated Make-runner auth/Files acceptance. Cloud adapters,
background transfer, retention jobs and generated native connectivity remain open.

## Current coverage — 2026-10-13

Reading step 14: [Go source-copy verification](../../../notes/techniques/go-source-copy-verification.md)
and [CLI walkthrough](../../../notes/modules/cli/README.md). Independently generated
Go APIs now pass imported-module policy, memory/PostgreSQL and runner acceptance;
CLI 0.4.0 marks Go standalone ready. Native connected profiles remain separate.

Go 1.27.1: formatting, AST import checks, vet, build and race-enabled tests pass.
Runtime tests use real loopback HTTP, readiness failures, graceful active-request
draining and forced timeout. A compiled-process smoke check verifies live/ready,
metadata, HEAD, 404/405 problems, JSON logging and SIGTERM exit 0. Invalid log
configuration exits 1 before listening. Root `make notes-check` validates links
and mirrored paths separately.

Account/Audit checks additionally cover canonical names/restoration, atomic
memory state/outbox, concurrent CAS, private fact projection, capability admission/
caller/deadline policy, dependency failure versus denial, concurrent Audit replay,
scoped pagination and delivery acknowledgment loss. Worker checks cover bounded
deadline retries, cancellation and joining. `make check test smoke` passes;
compiled processes verify opt-in credentials, owner HTTP/evidence, stale versions,
private JSON logs, SIGTERM and expected memory loss after restart.

PostgreSQL execution now additionally checks explicit migration guards,
concurrent schema bootstrap, state/outbox and evidence/inbox rollback, leases,
stale acknowledgment, durable replay, cursor isolation, cancellation, poison
quarantine and dependency failure versus absence. Compiled processes verify
forced API crash/recovery, persisted evidence and other-principal denial.
`make integration-test` establishes database evidence separately from the normal
suite, which skips database cases without a disposable URL/flag.
PostgreSQL 18.6 Docker runs passed; the final full rerun used local PostgreSQL 14.24
because Docker's VM became read-only. Compose configuration validates separately.

Identity now implements real password/session credentials, a versioned resolver
and principal-scoped Audit. Security/domain/HTTP and memory/PostgreSQL conformance
checks cover expiry, rotation races, reuse revocation, stable absolute lifetime,
rollback/leases, scope/cursor admission and credential privacy. Compiled API checks
observe issued sessions after SIGKILL, durable renewal/revocation/sign-out, recovered
Audit facts and no raw credentials in owned rows/events/logs. This Identity batch
ran against local PostgreSQL 14.24; no new Identity 18.6 run is claimed.

Account scope now passes twelve-way bootstrap contention on both adapters,
one creation fact, no ordinary-account adoption/orphans, current renamed state,
owner-filtered UUID pagination and cancellation. HTTP verifies foreign cursors,
HEAD and guarded selection. PostgreSQL checks retained 0001 migration upgrade,
mapping/state/outbox rollback and account/owner FK rejection. Compiled processes
recover the personal mapping and list/select workspaces through actual Identity
credentials after SIGKILL. This full rerun used isolated local PostgreSQL 14.24.

Native Go adapters now compose the public Identity/Account contracts, with real
URLSession/OkHttp acceptance and separate catalog/native-vault observations.
Read [the cross-platform checkpoint pattern](../../../notes/patterns/deployment-authorities-and-rotation-checkpoints.md).
These new native checks use an owned memory API; they do not replace PostgreSQL
recovery evidence or establish a generated connected profile.

Remote routing remains pending. Realtime and generated Daily + go-connected now
have their own later evidence in the follow-ups above; the earlier historical
checks do not establish those capabilities. Remote Notes and standalone Go CLI
generation are implemented. Next: select native live/recovery UI and product policy.
Database failover, role isolation,
operational replay/retention and actual network-cut commit uncertainty remain
unverified. Documentation checks validate links and mirrored paths separately.

## Rust runtime conformance follow-up

2026-10-18: [shared fixtures](../../../contracts/fixtures/backend-runtime/README.md)
now check Go's public projection and actual canonical HTTP behavior alongside
Rust. Read [the Rust notebook](../../rust/notes/README.md) for ownership choices
and [scope/gaps](../../rust/PARITY.md). The admission follow-up adds 26 JSON/media,
27 version/key and 11 identifier cases plus generator-failure privacy checks.
These stay in the repository-only parity package; no Go production behavior or
CLI source selection changed.

Core Identity follow-up: [shared fixtures](../../../contracts/fixtures/backend-identity/README.md)
now compare policy, PHC/token vectors, credential DTO admission and actual
register/sign-in/me/renew/reuse/sign-out/source-gate behavior with Rust. The Go
implementation and extracted source closure are unchanged. Rust's retained memory
facts do not establish its Audit delivery or persistence parity.
