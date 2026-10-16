# Data walkthrough

FoundryData implements device storage behind service-owned domain ports without depending on UI or HTTP.

## Live local observation

Claim: process-local invalidation coordinates repository instances without keeping
database handles open between snapshots. Origin: observation, 2026-10-10.
Read [registry](../../../../packages/FoundryData/Sources/FoundryData/NotesChanges.swift),
[adapter](../../../../packages/FoundryData/Sources/FoundryData/SQLiteNotesRepository.swift)
and [tests](../../../../packages/FoundryData/Tests/FoundryDataTests/SQLiteObservationTests.swift).

The registry registers a bounded path listener before the initial read. Committed
CRUD signals trigger scoped list calls; duplicate results are suppressed. Signals
arriving during a read remain pending for a subsequent read. The outer snapshot
stream cancels its producer on termination, and the producer defers registry
removal. NotesSubscription additionally provides explicit cancellation when never
iterated or exited early. Failures remain values while defects terminate the source.

Fourteen Data cases pass: the eleven previous cases plus native cross-instance
readers/CRUD/independent cancellation, registration and burst convergence, and
corrupt-file refusal/unused subscription cleanup. Account factories now expose
the composed observable capability; lease-bound CRUD remains unchanged. Schema v1
and all guest/account paths are preserved. Native handles stay operation-scoped.

Invalidations cover this process and standardized admitted paths, not aliases,
external SQL or background/remote changes. Restart rereads the file; no automatic
repair/retry follows. Read [native stream mechanics](../../../substrate/async-stream-buffering-and-subscription-cleanup.md)
and [shared ownership](../../../../../../notes/patterns/current-snapshots-and-subscription-lifetime.md).
Next: when should a shared projection reduce repeated native reads?

## Origin and source reading order

The first durable-data slice, 2026-10-10, implements
[local Notes behavior](../../../../../../contracts/behavior/local-notes.md).

1. [Manifest](../../../../packages/FoundryData/Package.swift): Services/Kernel dependencies and native SQLite linkage.
2. [Repository port and memory adapter](../../../../packages/FoundryServices/Sources/FoundryServices/NotesRepository.swift): admitted CRUD commands and snapshots.
3. [SQLite adapter](../../../../packages/FoundryData/Sources/FoundryData/SQLiteNotesRepository.swift): operation scope, schema refusal, statements and decoding.
4. [Disk/domain checks](../../../../packages/FoundryData/Tests/FoundryDataTests/SQLiteNotesRepositoryTests.swift) and [shared fixtures](../../../../../../contracts/fixtures/local-notes/repository.json).
5. [Catalog consumer](../../apps/FoundryCatalog/README.md#durable-local-notes).

## Walkthrough and example

Construction stores a private URL without IO. Each actor-isolated operation
checks cancellation, creates the parent, opens a handle, admits schema and
executes its statement. Version zero is initialized only without existing user
tables; version one is used; other versions are refused. Initialization rolls
back on failure. Handles/statements close before the result leaves the actor.

List orders by insertion position and admits every row. Create binds a UUID and
preserved title. Update changes title only; delete targets one ID. Missing IDs
return not_found. Bound text lengths and decoded byte counts preserve embedded
NUL. Expected storage failures return AppResult; cancellation/defects propagate.

```swift
// Conceptual: this adapter can satisfy a consumer's narrower read port.
let repository: any NotesRepository = SQLiteNotesRepository(url: privateURL)
let reader: any NotesService = repository
let snapshot = try await reader.list()
```

## Verification, gotchas and next

`make data-test` passes all six Data tests and nine existing Services tests.
Real-file checks cover CRUD/fresh handles, ordered identity, Unicode/NUL,
concurrent instances, malformed/unknown/unversioned stores and pre-cancellation.
Both native consumers build; iOS owner checks are recorded in its app walkthrough.
That first slice supplies no disk observer, later migration, backup or remote sync.
The schema is not a general migration/repair engine and busy work is not replayed.

Read [actor/handle mechanics](../../../substrate/sqlite-actors-and-scoped-handles.md),
[shared lifetime reasoning](../../../../../../notes/patterns/local-commits-and-observation-lifetime.md)
and [construction](../../../../../../docs/blueprints/local-notes.md).
Next: what extra contract would a second live reader need, and how should v2
be tested against an actual released v1 file?

## Account collections

Claim: the same repository port serves durable account collections when storage
identity and session access lifetime are composed separately. Origin: account
access, 2026-10-10. Read
[factory/partition](../../../../packages/FoundryData/Sources/FoundryData/AccountNotesRepositories.swift),
[lease facade](../../../../packages/FoundryData/Sources/FoundryData/SessionNotesRepository.swift)
and [checks](../../../../packages/FoundryData/Tests/FoundryDataTests/AccountNotesTests.swift).
The manifest now consumes Session; Services remains independent.

The factory owns a per-authority/account repository cache, preserving Memory
records across account changes. Device mode names v1 SQLite files with a canonical
SHA-256 tuple; principal is excluded from data ownership. A different member of
the same granted account shares its collection, while authority/case/delimiter
differences stay distinct. Guest SQLite is unchanged and never adopted.

SessionNotesRepository binds the selected concrete target and one lease. All
CRUD methods check cancellation/current access before and after suspension,
including error paths. Late writes can commit only in their original target;
stale feedback cannot reach the replacement owner. Sign-out keeps records and
successful renewal reloads them under a fresh editor lease.

All eleven native package cases pass: six prior SQLite/command cases plus shared
partition fixtures, native account CRUD/reopen/guest preservation, Memory factory
lifetime, all-method lease closure and accepted-write/late-error races. Tests use
actual temporary SQLite files and controlled repository gates, not a backend.
Hashing is naming only. Notes encryption, released migrations, backup audits,
automatic wipe, remote tenancy and synchronization remain separate work.
Read [CryptoKit mechanics](../../../substrate/cryptokit-partitions-and-authenticated-urlsession.md)
and [shared ownership](../../../../../../notes/patterns/account-partitions-and-leased-work.md).
Next: which device-retention policy and remote collection should a real account own?
