# Notes commands, scoped receipts and replaceable storage

Claim: a module can preserve immutable command evidence across storage/language
changes while keeping current authorization separate from replay identity.

Origin/evidence: 2026-10-18 Rust 1.86/SQLx 0.8.6 and the Go reference Notes module.
Read [shared behavior](../../../../../../contracts/behavior/remote-notes.md),
[Rust scope](../../../../../../contracts/behavior/rust-notes.md) and
[the shared corpus](../../../../../../contracts/fixtures/backend-notes/README.md).

Source reading order:

1. [Domain](../../../../internal/notes/src/domain/mod.rs): typed operations,
   preserved title scalars, private admitted Note/Receipt state and pure `decide`.
   An optional create target differs from the command input; expected version
   and operation determine which fields are meaningful.
2. [Ports](../../../../internal/notes/src/app/ports.rs),
   [write](../../../../internal/notes/src/app/command/mod.rs) and
   [reads](../../../../internal/notes/src/app/query/mod.rs). Every operation first
   authorizes the original account. A known create replay skips ID allocation;
   Store.apply still owns atomic replay comparison. Validate returned scope and
   command before publishing an optional lossy change hint.
3. [Account capability client](../../../../internal/notes/src/infra/capabilities.rs):
   calls account.access.v1 as caller notes, requiring an explicit boolean answer.
   No Identity/Account implementation dependency or foreign repository exists.
4. [Memory](../../../../internal/notes/src/infra/memory.rs): one mutex protects
   records and immutable input/receipt pairs. Values clone without shared mutable
   title buffers. Replay is checked before current-record lookup.
5. [PostgreSQL](../../../../internal/notes/src/infra/postgres.rs) and
   [wire/fingerprint adapter](../../../../internal/notes/src/infra/wire.rs): claim
   the composite receipt key, compare a competing committed fingerprint, lock the
   target, decide, mutate, finalize and COMMIT. bytea preserves NUL. The exact Go
   migration is [embedded locally](../../../../migrations/notes/0001_notes_receipts.sql).
6. [HTTP](../../../../internal/notes/src/transport/http/v1/mod.rs) and
   [input visitor](../../../../internal/notes/src/transport/http/v1/input.rs):
   trusted principal, canonical IDs, required command keys, strong If-Match,
   bodyless delete, immutable receipt Location/ETag and live UUID pages.
7. [Module](../../../../internal/notes/src/lib.rs) and
   [process](../../../../cmd/server/src/main.rs): select memory/PostgreSQL through
   ports; process selection verifies capabilities/migrations before admission.

Example (Conceptual): command C creates a note at version one. Command U edits
it to version two; D deletes it at version three. Repeating C returns the original
version-one title/receipt without recreating the note. Repeating U returns its
historical version-two evidence. A new command still sees the note as absent.
Current authorization applies even to C/U/D and receipt lookup.

Verification: [shared provider checks](../../../../internal/notes/tests/support/mod.rs)
run against memory and PostgreSQL: twelve matching creates, one CAS winner,
historical replay after deletion, scoped receipts, pages and cancellation.
[Application checks](../../../../internal/notes/tests/application.rs) exercise
allocation failure, authorization and hint ordering; [access checks](../../../../internal/notes/tests/access.rs)
reject malformed answers. `make backend-notes-parity-test` runs eleven title
cases, four fingerprint vectors and 47 HTTP cases plus real Go/Rust flows.
The [disposable database test](../../../../cmd/server/tests/postgres.rs) injects
finalization failure and verifies the deferred incomplete-receipt guard. The
[process runner](../../../../../../scripts/postgres_parity.py) verifies restart,
historical Go/Rust receipt replay and separate Notes database selection.

Gotchas: a receipt is evidence of one historical commit, not current record
state or present account authority. Missing receipt observation cannot settle an
in-flight write. Optional change signals are not durable events; this runtime
now selects the [local Changes adapter](../../../../internal/notes/src/infra/broadcast.rs)
and [Realtime Feed](../realtime/README.md). A lost acknowledgement can omit a hint.
No native/CLI Rust selection is registered.
Related: [historical receipts](../../../patterns/historical-receipts-before-current-state.md),
[stable JSON fingerprints](../../../substrate/go-json-command-fingerprints.md) and
[transaction lifetime](../../../substrate/sqlx-transactions-and-pool-lifetime.md).
Next: which selected native product should validate a Rust-backed connection?
