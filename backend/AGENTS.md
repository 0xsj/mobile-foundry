# Backend development

Go is the reference owned API. Supabase and Firebase are optional provider
profiles with local infrastructure; they do not yet implement application
contracts. Keep provider SDK adapters in their owning native platform.

## Go boundaries

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
