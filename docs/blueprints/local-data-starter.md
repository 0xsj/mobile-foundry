# Local data starter construction specification

## Status and scope

Implemented and independently verified 2026-10-10. Add optional local-data selection to the CLI
and a working native Notes exemplar. Preserve the default UI-only starter and
the catalog's complete package builds. Go stays planned.

## Exemplar workflow

A developer generates a renamed Daily product with local-data, builds it outside
the checkout, opens Library, creates/edits/deletes records, and relaunches it to
read persisted data. A separate reader reports current collection size. Developers
replace the injected repository without changing its consumers.

## Source documents and authority

Established: [architecture](../../ARCHITECTURE.md),
[CLI](../../contracts/behavior/project-cli.md),
[local-data starter](../../contracts/behavior/local-data-starter.md),
[local Notes](../../contracts/behavior/local-notes.md),
[observation](../../contracts/behavior/notes-observation.md),
[notes workflow](../NOTES.md) and the parent implementation blueprint protocol.
Existing native adapters/ports/state patterns are established. Capability
registration and local-only build overlays are proposed by this slice. Backend
and remote synchronization are not applicable.

## Nouns, states, and invariants

Capability IDs select explicit combinations, not guessed dependency graphs.
The effective inventory owns selected files and overlaid build manifests. Notes
and commands retain their existing identity/title rules. The repository owns
commits; CRUD state owns the draft and receipt; the reader owns its subscription.
Product-owned containers define the database scope. No account/session state is
introduced. A canceled acknowledgement is never treated as rollback.

## Workflow and side-effect ordering

Load registry and inventories, admit capability selection, validate effective
closure and snapshot source bytes before creating owned output. Copy overlays,
configure identity, then write guides/provenance. Initialization does no disk work
inside native repositories. Library loads before admitting a command, validates
the draft synchronously, marks busy, commits, retains a receipt and reloads.
Observation starts independently and is canceled on inactive lifetime. Confirmed
delete captures a note; dismissal/cancel never calls the repository.

## Expected file tree

| Required owner | Files / responsibilities |
| --- | --- |
| CLI | cli/mobile_foundry/capabilities.py; profiles.py; cli.py; documents.py; version; safety/capability tests |
| Registry/inventory | mobile-foundry.starters.json; templates/profiles/native-daily-local-data.json |
| Local module overlays | templates/overlays/local-data/{swift,kotlin} build manifests/settings selecting local Services/Data closure |
| Native product overlays | Swift/Kotlin Library screen, CRUD state, live reader, composition, hosted/device tests and adjusted entry test |
| Shared source | Swift/Kotlin NotesService and separate HTTPNotesService files; local ports/adapters/tests are selected from their existing owners |
| Contracts/docs/notes | This blueprint, starter contract, CLI/setup/readiness/template docs and shared/native learning handoff |

Deferred: other shells/capabilities, backend/provider adapters, outbox and release
automation. Forbidden: implicit resetting/seed data, copying catalog roots,
credential files, source-checkout links or networking permissions.

## File specifications

`capabilities.py` owns registered combination discovery and selection. Inputs are
an admitted source root, registered base profiles and requested IDs; output is one
validated inventory/path. It refuses duplicates, unknown IDs, mismatched shells/
platform identity configuration and absent combinations before side effects.

`profiles.py` validates the effective file view: overlays replace destination
bytes for build graph checks, selected partial source/test trees remain portable,
and required fixtures/module/settings closure must be complete. It owns no copying.

`cli.py` owns the --with projection, list output and selection errors. `documents.py`
derives selected test targets/guides from the effective inventory. Generation keeps
its existing exclusive ownership/cleanup and fingerprints the selected inventory.

Native overlays own product-specific composition and presentation. CRUD and reader
owners receive ports, never instantiate SQLite. Composition alone selects the
database location and adapter. Expected failures render public kernel copy;
unexpected defects reach a private diagnostic callback. Lifetime generations reject
late results. The Library screen owns focus and delete confirmation, not commits.
Its tests exercise real adapters and state ownership with temporary test databases.

The separated HTTPNotesService files preserve the existing public type/API and
transport admission behavior. NotesService files own domain records/read ports/
memory snapshots without HTTP imports. Full package tests detect regressions.

## Cross-file dependency map

CLI → registered combination → effective inventory → copy/configuration/guides.
Native app → UI/Query/Kernel and Services/Data. Data → Services/Kernel; local
Services → Kernel and native concurrency libraries. App-owned editor/reader →
consumer ports. Catalog uses its existing full manifests and HTTP adapters.

## Transport and persistence mapping

CLI strings are admitted before output. Native fields become existing validated
commands. SQLite schema/version/locking and notifications remain unchanged. No
network transport, migration, credential namespace or remote ownership is added.

## Verification plan

Check all three platform copies with and without local-data, refusal/cleanup/source
preservation, no unwanted modules, effective overlay drift and missing fixtures.
Run full Services checks after HTTP extraction. Independently build and run both
native outputs, exercise CRUD/forms/observation/failure behavior, then terminate
and relaunch the installed products without clearing data. Verify renamed identity
and audit copied source/configuration for checkout paths. Run notes/link checks.

## Construction order

Contract/blueprint; transport source separation; registered local inventory and
effective validation; native product overlays/tests; CLI/docs projection; generated
native acceptance; learning notes and readiness evidence.

## Open decisions and unknowns

Only the explicitly registered UI + local-data combination is implemented. Future
multiple capabilities require their own collision/closure acceptance. Physical
devices, older OS, backup/restore and release signing remain unverified.

## Deviation record

Complete inventories were chosen over implicit additive overlay merging: only
registered combinations can be selected. Required capability/shared files are
checked after exclusion preflight. Local product models are editable overlays
based on the existing state patterns; ports/SQLite/notification code stay
single-source. Full modules retain account/HTTP dependencies.

The first Kotlin build exposed missing Query extension imports; the screen now
imports them. The first Android form assertion missed the native Error: prefix;
its corrected locator passes. Native form checks now use unique records and a
count relative to the existing collection. iOS UI tests attach a relaunch capture.

Final independently generated combined/Swift-only/Kotlin-only outputs build;
each iOS output passes eight native cases, each Android output 26 unit/eleven
device cases. Selected Swift packages pass 30; full Services passes eleven per
platform. Nineteen CLI checks and inventory validation pass. Android process
force-stop/PID absence/relaunch retains a real control-entered note; iOS tests
also terminate/relaunch before deletion. Captures were inspected. An emulator
System UI startup timeout was cleared before manual Android acceptance and
recorded separately from product behavior. See [readiness](../STARTER-READINESS.md).

## Completion criteria

Default UI output stays unchanged in scope; --with local-data generates portable
working products for Swift/Kotlin/both; included checks pass; observed relaunch
retains records; readiness and notes distinguish this from a connected starter.
