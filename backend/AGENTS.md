# Backend development

Go is the reference owned API. Supabase and Firebase are optional provider
profiles with local infrastructure; they do not yet implement application
contracts. Keep provider SDK adapters in their owning native platform.

Rust independently mirrors the shared behavior and module ownership with idiomatic
Rust, and is a registered standalone CLI backend through 0.19.0. Follow its
[local instructions](rust/AGENTS.md), [starter contract](../contracts/behavior/rust-starter.md)
and [parity inventory](rust/PARITY.md). Preserve independent generated acceptance;
The [native Rust selection](../contracts/behavior/rust-connected-starter.md) has
its own generated acceptance; full Go wire equivalence remains separate.

## Go boundaries

The [runtime foundation](../contracts/behavior/go-runtime.md) is implemented.
Keep health/runtime checks separate from authentication, persistence and provider
conformance. `go-check` and `go-test` at the repository root verify this profile.
Go is registered as a standalone CLI backend after independent extraction,
memory/PostgreSQL and generated runner acceptance. Preserve the selected source
closure and product module/Compose identity. Co-located native shells do not
establish connected native acceptance.

Use `cmd/server` for process composition, `pkg` for reusable primitives, and
`internal/<module>` for business capabilities. Mirror Bento's `domain`,
`app/command`, `app/query`, `infra/<adapter>`, and
`transport/<protocol>/<version>` layout. Module-local composition belongs in
`module.go` and `build.go` when implemented.

Domain code has no infrastructure dependencies. Application code defines its
ports; adapters implement them. Modules own their data and communicate through
explicit interfaces or integration contracts rather than another module's
store. Keep Go's idiomatic `(value, error)` and `error` returns for anticipated
failures; do not add a generic Result wrapper or use panic for routine failures.

Follow the [capability catalog](go/CAPABILITIES.md) and
[module boundary policy](../contracts/behavior/module-boundaries.md).
Use consumer-owned application ports and versioned capability adapters for
synchronous answers; committed integration facts with outbox/inbox for
independent reactions; broadcast/Realtime for recoverable live signals.
Domains never invoke another module or share its store. Keep local/remote
validation and caller policy consistent. Dependency failures are not successful
negative answers. Add contracts and acceptance evidence with each implemented
seam. Assembly now registers capabilities/subscriptions, verifies requirements,
freezes registries and starts owned workers. A bounded in-process broadcast hub and
native-bearer WebSocket edge now implement [recoverable hints](../contracts/behavior/realtime.md).
Remote module routing and cross-process broadcast adapters remain separate.

The [Account/Audit memory slice](../contracts/behavior/account-audit.md) is
implemented. Preserve state/outbox and evidence/inbox atomicity, write-time
version guards and failure-versus-negative answers across future providers.
Development credentials are opt-in fixtures, not an Identity authority.
Use `make smoke` in `go` for compiled memory loopback checks and
`make integration-test` for the disposable PostgreSQL suite. The
[persistence contract](../contracts/behavior/postgres-persistence.md) now defines
owned pools/schemas, explicit migrations, transactional outbox/inbox, delivery
leases and cursor allocation. Never reset the retained local Compose volume in
tests. Stop all workers before closing any module resources. API startup checks
migrations but never applies them. The [Identity/session slice](../contracts/behavior/identity-sessions.md)
now supplies explicit owned authentication behind application/security/store ports.
Keep principal/session identity separate from Account scope. Consumed refresh reuse
is a committed revocation followed by credential refusal, not a rolled-back write.
Identity Audit facts omit account IDs and all credential/profile content; preserve
existing Account fingerprints when extending evidence. Never claim production
identity completeness, database failover or connected/generated starter readiness
from this execution.

The [Account scope slice](../contracts/behavior/account-bootstrap.md) implements
repeatable personal bootstrap, owner-only listing and guarded selection. Keep
mapping/account/outbox atomic; ordinary creates never implicitly become personal.
Selection is device-owned, not a mutable principal-global default. A native
restore/renewal must validate the stored account rather than silently retarget it.

The [remote Notes slice](../contracts/behavior/remote-notes.md) adds owner-scoped
data through AccountAccess and atomic command receipts. Scope receipts by account,
principal and command; authorize replays; preserve the v1 fingerprint encoding.
Replay precedes CAS/existence. Keep Notes SQL separate from Account/Identity stores.
Notes publishes lossy dirty hints after confirmed store success; these are not
durable integration facts. Daily's connected native selection retains one pending
command per original scope, with explicit recovery rather than automatic sync.

The [core identity lifecycle](../contracts/behavior/identity-recovery.md) adds
verified recovery contacts, single-use checkpointed challenges and password
mutation. Compare credential checkpoints under the principal lock, serialize
session issuance with password mutation, and commit challenge consumption,
revocation and required facts atomically. Preserve v1 revocation reasons;
password-related reasons use the separate v2 event contract. Anonymous reset
lookup/mail runs after bounded queue admission, independently of public response
timing. Never log addresses, challenges or SMTP credentials.

The [portable operational profile](../contracts/behavior/backend-operations.md)
keeps API/database/metrics private behind an explicit trusted proxy. Migrations
remain explicit; close sockets and workers before pools. Select one API replica
for the local hub. Backup database and private objects under API quiescence and
verify restores only into unused owned namespaces. Maintenance currently prunes
expired challenges; broader retention/erasure needs owner-specific product policy.

Read [Bento conventions](../../bento/backend/CONVENTIONS.md) and
[module authoring](../../bento/backend/MODULE-AUTHORING.md) when implementing
the first module. Carry the relevant principles over deliberately; Bento's
existing database baseline and domain inventory do not become this backend's
implementation automatically.

## Provider configuration

Keep Supabase's upstream release provenance in `supabase/UPSTREAM.md`. Review
image and configuration changes together. Firebase Compose runs the Local
Emulator Suite with a demo project; cloud provisioning and deployment are
separate tasks. Keep rules and indexes versioned, and define scoped access with
the first connected workflow.

## Learning and verification

Follow [the notes workflow](../docs/NOTES.md). Each profile owns a `notes` index
and Bento's six categories; module notes mirror paths from that profile's root.
Record actual checks and important runtime limits. Run `make notes-check` at
the repository root after note changes. Use each provider's `make check` for
Compose validation and `make up` when runtime startup must be verified.

The [Files/Media slice](../contracts/behavior/files-media.md) owns canonical file
metadata, versions, optional command receipts and committed lifecycle facts.
Memory/PostgreSQL metadata and memory/private-directory Objects adapters are
implemented. Delete tombstones precede external cleanup; receipt lookup proves
metadata commit only. Preserve original keys/versions across cleanup failures.
No object SDK, Files state or foreign SQL enters another module's core. Audit
receives admitted File facts through its own adapter; do not expose names, types,
hashes or content there. Retain database and private object directory together.

The [Notifications slice](../contracts/behavior/notifications.md) receives completed
Files facts through its own adapter. Keep item/inbox/preference decisions atomic
and account/principal scoped. Same-event input conflicts are failures; muted source
markers remain applied. Backend in-app delivery does not establish native/push
integration, installation lifecycle or retention.

The [installation registry](../contracts/behavior/installations.md) owns session-bound target metadata under Notifications. Identity credential v1 remains principal-only; v2 supplies trusted session/expiry to the edge. Never accept session ownership from request JSON. Keep proofs hashed, token views private, and cleanup/source-marker/tombstone atomic. No Identity SQL or aggregate imports enter Notifications.

Optional [push dispatch](../contracts/behavior/push-dispatch.md) uses a trusted
internal preview handler, exact target leases and identity.session.eligible.v1.
The negative session answer differs from dependency failure. Short access expiry
differs from absolute session expiry. Finish owned store operations before a
foreign call/send, and guard invalid-address cleanup against the original owner/
version. No gateway is selected by default. [Explicit provider selection](../contracts/behavior/push-providers.md)
adds real APNs/FCM adapters and a guarded manual HTTP preview; local TLS tests do
not prove live cloud delivery. Preserve check/send and no-receipt limits, refuse
arbitrary endpoints/redirects, sanitize transport causes, and isolate acceptance
processes from host provider settings. Provider keys remain outside source copies.

Notifications' [queued previews](../contracts/behavior/push-jobs.md) require a
second explicit runtime selection with durable PostgreSQL. Retain immutable
owner/version identity and job-ID replay; use atomic fenced claim/begin/finish
operations. Only confirmed sending-marker commit permits a gateway call. Unknown
post-marker results remain uncertain and never become automatic retries. Bound
preflight backoff, claims, lifetime and capacity, and keep raw provider details out
of job state. Preserve matching memory/PostgreSQL rules and deliberate process-kill
evidence. New migrations are immutable; runtime checks never apply them implicitly.

[Recovery](../contracts/behavior/push-job-recovery.md) retains source evidence and
admits a new child under the same installation/principal/session. Fresh replacements
prove the selected current revision; exact replay retains its original input.
Cancellation serializes with Begin, and a committed marker always wins refusal.
Keep root enqueue separate from replacement lineage, one child per source, explicit
duplicate-risk acknowledgement and old-row migration/rollback/reopen evidence.
