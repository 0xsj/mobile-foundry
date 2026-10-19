# Rust backend learning notes

Native connection follow-up, 2026-10-19: CLI 0.20.0 explicitly selects this API
for Daily through rust-connected. Read [generated execution evidence](../../../docs/blueprints/rust-connected-starter.md)
and [the CLI walkthrough](../../../notes/modules/cli/README.md#rust-native-connection--2026-10-19).
Native ports, lease policy and SQLite recovery remain shared with Go-connected.
This follow-up supersedes earlier native-unregistered statements for that
selected capability; other shells/features and full wire equivalence remain separate.

CLI handoff, 2026-10-18: Rust is a **standalone backend starter** through 0.19.0.
Read [construction/evidence](../../../docs/blueprints/rust-starter.md),
[the CLI walkthrough](../../../notes/modules/cli/README.md) and
[Cargo closure reasoning](../../../notes/techniques/cargo-closure-and-independent-starters.md).
A renamed independent output passes locked checks/tests/smoke, its actual Make
runner, disposable PostgreSQL fault/process-loss/restart and a freshly built
Docker/Caddy/mail/backup flow. Earlier follow-ups below describe the pre-extraction
candidate; this handoff supersedes their CLI-unregistered statements. Native
connected Rust selection, public TLS/hosting and external delivery remain separate.

Operations follow-up, 2026-10-18: read [portable ingress/runtime](modules/infra/production/README.md),
[fixed-label metrics](modules/pkg/telemetry/README.md),
[operational drivers](modules/scripts/README.md),
[paired backups](patterns/quiesced-database-and-object-backups.md) and
[Compose allocation](substrate/compose-address-allocation.md).
Seventeen shared proxy cases and actual Go/Rust metrics/source-gate processes
pass. Rust image/disposable Docker checks exercise actual edge/WebSockets, local
SMTP, private metrics, quiesced DB/object backup and unused-project restore.
Read [healthcheck](modules/cmd/healthcheck/README.md) and
[explicit pruning](modules/cmd/maintenance/README.md) for the tools. Public TLS/
hosting, external delivery and Rust CLI/native selection remain separate.

Realtime follow-up, 2026-10-18: read [the socket owner](modules/internal/realtime/README.md),
[the bounded hub](modules/pkg/broadcast/README.md),
[subscribe before refetch](patterns/subscribe-before-refetch.md) and
[owned upgrades](language/owned-upgrades-and-cancel-safe-join.md).
Eight shared hub scenarios/38 operations and fifteen HTTP admissions complement
actual Go/Rust Notes hints, renewal/sign-out, 16-second connections, capacity and
open-socket shutdown. PostgreSQL has the same committed-write/lifecycle phase.
These are local recoverable hints; deployment/fanout and Rust CLI/native selection
remain separate.

Rust retained-jobs follow-up, 2026-10-18: read [job ownership](modules/internal/notifications/jobs.md),
[owned polling](modules/pkg/jobs/README.md) and
[send markers/explicit replacement](patterns/durable-send-markers-and-explicit-replacement.md).
57 shared transitions, memory/HTTP/fault/lifecycle checks, PostgreSQL rollback/
capacity/concurrency and owned SIGKILL recovery pass. Compiled Rust→Go→Rust
workers preserve outcomes/lineage and pass 21 shared HTTP guards without cloud
requests. Defaults remain off; retention, live delivery and Rust CLI/native
selection remain separate.

Observed 2026-10-18. Rust now has a twenty-four-crate workspace with runtime/Health,
shared HTTP admission, memory/PostgreSQL Identity/Account/Audit/Notes/Files/Notifications, capabilities and committed delivery. CLI extraction and
native connected Rust selection remain unregistered.

Provider follow-up, 2026-10-18: read [private providers](modules/internal/notifications/providers.md)
and [HTTP transport defaults](substrate/reqwest-provider-boundaries.md).
APNs/FCM adapters and guarded manual preview now pass 32 shared response/21 HTTP
cases, local TLS/JWT/cache/cancellation checks and explicit compiled selection.
The HTTP suite also passes with separate PostgreSQL Identity/Notifications owners.
Default is off; live delivery and native/CLI Rust selection remain separate.
Retained jobs have their own acceptance above. Run make backend-push-providers-parity-test.

Push foundation follow-up, 2026-10-18: read [the dispatch walkthrough](modules/internal/notifications/push.md)
and [known rejection with unconfirmed cleanup](patterns/known-rejection-and-unconfirmed-cleanup.md).
Optional target/session/gateway ports and Identity's eligibility capability now
pass twenty-one attempt/twenty request/fourteen response/six session shared cases.
Paused deadlines preserve partial outcomes without detached futures; memory/
PostgreSQL races, cleanup rollback, corrupt-state refusal and separate owner
databases pass. Compiled defaults keep sending off. Explicit providers have
separate acceptance above; durable jobs and native/CLI Rust selection remain
separate. Run make backend-push-parity-test.

Installations follow-up, 2026-10-18: read [the registry walkthrough](modules/internal/notifications/installations.md),
then [rebinding and retirement tombstones](patterns/rebinding-and-retirement-tombstones.md).
Shared ten-proof/twenty-spec/twenty-one-event/forty-two-HTTP checks and real API
rotation/rebind/renewal/unregister plus all four session retirement reasons pass.
Memory/PostgreSQL conformance, cleanup/tombstone/marker/reclamation rollback,
six separate databases and killed v1/v2 producer handoff preserve current bindings.
Eligibility/conditional dispatch has separate acceptance above. Cloud providers/
jobs and native/CLI Rust products remain separate.

Notifications follow-up, 2026-10-18: read [the inbox walkthrough](modules/internal/notifications/README.md),
[consumed suppression and snapshots](patterns/consumed-suppression-and-inbox-snapshots.md)
and [causal delivery fences](techniques/causal-delivery-fences.md). Shared
twenty-two-event/four-fingerprint/forty-three-HTTP conformance and actual Go/Rust
upload/inbox/read/settings flows pass. PostgreSQL fault, six-database selection
and killed pending-delivery handoff preserve decisions, first read and settings.
Installations has its own acceptance above; push and native/CLI Rust products
remain separate.

Files follow-up, 2026-10-18: read [the module walkthrough](modules/internal/files/README.md),
[logical deletion/cleanup](patterns/logical-deletion-before-object-cleanup.md) and
[owned blocking filesystem jobs](language/owned-blocking-filesystem-jobs.md).
Memory/PostgreSQL metadata, historical receipts, immutable bytes and independent
Audit now pass 24 specs/four fingerprints/thirteen events/40 HTTP cases plus real
binary/HEAD, authorization/pagination and restart handoff. The private directory
is retained with the database; cleanup/commit uncertainty stays explicit.
Rust in-app Notifications has the separate acceptance above; cloud adapters and
native/CLI selection remain separate.

Recovery follow-up, 2026-10-18: read [the Identity walkthrough](modules/internal/identity/recovery.md),
[checkpoint races](patterns/checkpointed-recovery.md),
[private SMTP acceptance](substrate/private-smtp-and-acceptance.md) and
[owned joining](language/owned-polling-and-cancellation-safe-join.md).
Verified contacts, reset/change, closed v1/v2 Audit admission and private SMTP now
pass shared twenty-email/fifteen-event/twenty-one-HTTP checks, real Go/Rust flows,
local TLS/STARTTLS and PostgreSQL rollback/killed-process code handoff.
The anonymous reset queue is bounded/ephemeral; live provider delivery and native/
CLI Rust selection remain separate. Run `make backend-recovery-parity-test`.

Notes follow-up, 2026-10-18: read [Notes ownership](modules/internal/notes/README.md),
[historical receipts before current state](patterns/historical-receipts-before-current-state.md),
then [Go JSON fingerprint bytes](substrate/go-json-command-fingerprints.md).
Memory/PostgreSQL Notes now passes shared policy/digest/47-HTTP conformance,
concurrent replay/CAS and cross-language restart/historical receipt recovery.
Notes remains an example domain; local broadcast now has separate acceptance
above. Native/CLI Rust connection remains unregistered.

PostgreSQL follow-up, 2026-10-18: read [shared mechanics](modules/pkg/persistence/postgres/README.md),
[explicit migrations](modules/cmd/migrate/README.md), [artifact provenance](modules/migrations/README.md),
[borrowing executors](language/borrowed-transaction-executors.md),
[SQLx transaction/pool lifetime](substrate/sqlx-transactions-and-pool-lifetime.md), then
[restart/handoff acceptance](techniques/durable-restart-and-language-handoff.md).
`make backend-postgres-parity-test` passes against isolated PostgreSQL 14.24,
including rollback/races, five-attempt exhaustion, separate owner databases,
SIGKILL restart and Go↔Rust core state/inbox replay. Default workspace tests
ignore this owned database test; run the explicit suite for durable evidence.
Files and recovery have separate acceptance above; a generated Rust starter
remains unregistered. Recovery
has its own acceptance above. Notes
receipts have the additional acceptance above.

Audit follow-up: read [independent Audit](modules/internal/audit/README.md),
[delivery mechanics](modules/pkg/events/README.md),
[shared edge credentials](modules/pkg/principal/README.md),
[commit/replay boundaries](patterns/state-outbox-and-inbox-boundaries.md) and
[owned polling/joining](language/owned-polling-and-cancellation-safe-join.md).
Producer state/outbox and consumer evidence/inbox now commit separately through
stable-ID replay. Memory lifetime still does not establish durable recovery; PostgreSQL acceptance below does.

Account follow-up: read [Account ownership](modules/internal/account/README.md),
[versioned invocation](modules/pkg/capability/README.md),
[consuming builders](language/consuming-builders-and-immutable-registries.md) and
[personal mapping/write CAS](patterns/personal-mapping-and-write-cas.md).
The first real inter-module question now crosses consumer-owned ports and the
credential v2 contract. v1 retains its original shape. Required local facts now
map to envelopes consumed independently by Audit.

Identity follow-up: start with [the module walkthrough](modules/internal/identity/README.md),
then [committed security refusals](patterns/committed-security-refusals.md),
[owned secrets/scope adaptation](language/owned-secrets-and-scope-adapters.md) and
[blocking KDF ownership](substrate/argon2-and-blocking-lifetime.md).
Explicit identity mode now runs register/sign-in/renew/sign-out/me. Memory facts
and matching outbox records are atomically retained; memory delivery has a separate PostgreSQL follow-up below.

Admission follow-up, 2026-10-18: business Clock/ID ports, fallible request IDs,
bounded JSON/typed header admission and the common response encoder are now
implemented. Start with [wire admission before the domain](patterns/wire-admission-before-domain.md),
then [newtypes/status ownership](language/newtypes-and-transport-errors.md) and
[decoder compatibility](substrate/json-admission-compatibility.md).

Read in this order:

1. [Toolchain, workspace and HTTP runtime](substrate/cargo-and-http-runtime.md).
2. [Result, ownership and async trait ports](language/results-and-owned-async-ports.md).
3. [Kernel](modules/pkg/kernel/README.md), then [HTTP boundary](modules/pkg/httpx/README.md).
4. [Health query/adapter](modules/internal/health/README.md), then
   [process and server](modules/internal/server/README.md).
5. [Readiness and owned drain](patterns/readiness-and-owned-drain.md).
6. [Behavioral parity](concepts/behavioral-parity.md) and
   [shared fixture technique](techniques/shared-runtime-fixtures.md).

Verification: Rust 1.86 format/Clippy/build, workspace behavioral tests, and shared
corpora of eleven public failures, eleven identifiers, twenty-six JSON/media
cases, twenty-seven header cases and twenty-seven real HTTP cases pass in each
language. Listener tests exercise normal and forced drain;
the process runner checks SIGTERM/closed listeners and sensitive-input absence
from logs. Notes checks validate structure/labels, not Rust execution.

Identity adds 27 shared policy cases, PHC/token vectors, 17 live wire cases,
session lifecycle/source-IP gate process phases, and eight Rust tests including
concurrency, atomic failures and canceled-caller KDF ownership. A ninth test now
verifies credential v1/v2 shape, session evidence, input rules and revocation. Run
`make backend-identity-parity-test` for its separate memory acceptance.

Account adds seven shared name cases, forty HTTP fixtures and actual Go/Rust
bootstrap/CAS concurrency, owner pagination, renewal/revocation, default denial,
private-log and shutdown phases. Five Account tests and two capability tests
cover domain admission, required-fact atomicity, malformed provider evidence,
caller/failure boundaries and owned deadline cleanup. Run
`make backend-account-parity-test` for this separate memory acceptance.

Audit adds 33 shared event admissions, seven strict access responses and 24 HTTP
cases, plus actual Go/Rust causal metadata, scoped paging, Identity lifecycle,
private logs and shutdown phases. Rust tests cover atomic mapping failure,
independent consumers, lost acknowledgment, concurrent inbox replay, exhausted
readiness, timeout/cancellation and cancellation-safe worker joining. Identity
now has ten tests and Account six; Events has five, Audit four, and process
composition one delivery test. Run `make backend-audit-parity-test`.

The [parity inventory](../PARITY.md) lists the deliberately incomplete routing,
network-budget, full DTO compatibility and future module scope. Next questions:
how should a selected native product exercise Rust receipts and recovery endpoints?
What must an independent generated Rust starter include before product readiness?
