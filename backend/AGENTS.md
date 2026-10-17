# Backend development

Go is the reference owned API. Supabase and Firebase are optional provider
profiles with local infrastructure; they do not yet implement application
contracts. Keep provider SDK adapters in their owning native platform.

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
freezes registries and starts owned workers. Broadcast/remote adapters remain planned.

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
No Notes Audit/Realtime facts or native durable queue are established yet.

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
