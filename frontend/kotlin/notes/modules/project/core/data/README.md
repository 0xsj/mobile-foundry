# Data walkthrough

core:data implements Android device storage behind service-owned domain ports without depending on UI or HTTP.

## Live local observation

Claim: a cold path-invalidation Flow can publish native SQLite snapshots while
preserving operation-scoped helpers. Origin: observation, 2026-10-10. Read
[registry](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/NotesChanges.kt),
[adapter](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesRepository.kt)
and [native checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/data/NotesObservationTest.kt).

callbackFlow registers before its initial signal and unregisters in awaitClose.
Pending signals are conflated. Each signal maps to a scoped IO list call, and
equal result snapshots are suppressed. Successful CRUD invalidates inside IO
after commit, independently of caller acknowledgement; missing/failed writes
do not signal. Native opening/refusal policies remain unchanged.

Account factories expose ObservableNotesRepository while the exact-lease CRUD
facade remains narrow. No schema/path change or migration is introduced. The
three new native cases exercise cross-instance independent readers and CRUD,
registration/burst convergence/cold noncollection, and corrupt-file preservation
followed by a fresh read after explicit test-owned replacement. Existing native
guest/account cases also run; see [app evidence](../../app/README.md#live-notes).

Observation holds no helper between reads. The registry covers this process and
database name/path only; external SQL, aliases and cross-process work need a new
policy. Read [Flow mechanics](../../../../substrate/flow-invalidation-and-lifecycle-collection.md)
and [shared ownership](../../../../../../../notes/patterns/current-snapshots-and-subscription-lifetime.md).
Next: when would a real product need Room or a shared remote cache?

## Origin and source reading order

The first durable-data slice, 2026-10-10, implements
[local Notes behavior](../../../../../../../contracts/behavior/local-notes.md).

1. [Module](../../../../../project/core/data/build.gradle.kts): Android native dependency boundary.
2. [Repository port and memory adapter](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesRepository.kt): admitted CRUD commands and snapshots.
3. [SQLite adapter](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesRepository.kt): IO dispatch, helper scope, schema/refusal and decoding.
4. [Native database checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/data/LocalNotesRepositoryTest.kt) and [shared fixtures](../../../../../../../contracts/fixtures/local-notes/repository.json).
5. [Catalog consumer](../../app/README.md#durable-local-notes).

## Walkthrough and example

Construction retains application context and a filename without opening storage.
Each access runs on Dispatchers.IO under a mutex, checks cancellation, opens a
scoped helper and executes one statement. onCreate refuses existing user tables;
upgrade/downgrade callbacks refuse unknown versions. A custom corruption handler
keeps damaged data. Helper/cursor use blocks close resources before return.

List admits every row into an unmodifiable snapshot in insertion order. Create
uses a UUID and ContentValues. Update preserves ID/position; delete uses bound
ID selection. Missing IDs return not_found. Native storage exceptions become
typed failures; cancellation is not swallowed. Separate helpers share native
locking, not the repository's instance mutex.

```kotlin
// Conceptual: domain consumers need no Android database objects.
val repository: NotesRepository = SQLiteNotesRepository(applicationContext)
val reader: NotesService = repository
val snapshot = reader.list()
```

## Verification, gotchas and next

Five native database cases use actual Android SQLite: shared memory/command
fixtures, CRUD/fresh handles/Unicode/NUL, concurrent instances, corrupt/future/
invalid rows, and pre-canceled/file/unversioned-schema failures. Host checks in
`make data-test` cover Services and feature state rather than Android SQLite.
Native UI and process observations are recorded in the app walkthrough.

That first slice supplies no destructive migration, automatic replay, shared observation,
backup or remote sync is added. Recovery policy must be considered below SQL,
including native opening and corruption callbacks.
Read [helper/recovery mechanics](../../../../substrate/sqlite-helpers-and-nondestructive-recovery.md),
[shared lifetime reasoning](../../../../../../../notes/patterns/local-commits-and-observation-lifetime.md)
and [construction](../../../../../../../docs/blueprints/local-notes.md).
Next: how should released migration steps be tested, and when would Room or a
shared observation owner improve an actual multi-screen requirement?

## Account collections

Claim: a stable authority/account partition can retain data while one session
lease gates each operation. Origin: account access, 2026-10-10. Read
[factory/partition](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/AccountNotesRepositories.kt),
[lease facade](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SessionNotesRepository.kt)
and [native checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/data/AccountNotesRepositoryTest.kt).
The module now consumes Session while Services stays independent.

The retained factory caches a repository per authority/account digest. Memory
records survive account switches only within that factory lifetime. Device mode
uses a new private filename namespace with the existing v1 schema, leaving guest
data separate. The canonical UTF-8 tuple includes a version and delimiters;
SHA-256 lowercase hex bounds the name and preserves case distinctions. Principal
does not change an account's data partition.

SessionNotesRepository binds one concrete repository and one lease, called on
the same UI executor as the session. It checks coroutine activity and current
access before/after every CRUD suspension and on exception paths. Old writes
never retarget; a committed old write cannot publish into the replacement feature.
Raw repository access is a trusted composition capability, not authentication.

Native fixture/SQLite evidence and actual account-editor interactions are recorded
in [the app handoff](../../app/README.md#account-access). Four JVM owner/gate/probe
checks in [AccountAccessTest](../../../../../project/app/src/test/java/dev/mobilefoundry/catalog/ui/session/AccountAccessTest.kt)
pass alongside existing app regressions. Hashing supplies naming only; no Notes
encryption, remote tenancy, automatic wipe, backup audit or synchronization follows.
Read [digest/transport mechanics](../../../../substrate/message-digest-and-authenticated-okhttp.md)
and [shared ownership](../../../../../../../notes/patterns/account-partitions-and-leased-work.md).
Next: which retention policy and connected collection should a provider expose?

## Durable pending Notes

Claim: a service-owned recovery port can use native disk transactions without
turning local CRUD into remote sync. Origin: pending Notes, 2026-10-17. Read the
[adapter](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLitePendingNotesStore.kt) and [real-file tests](../../../../../project/core/data/src/androidTest/kotlin/dev/mobilefoundry/data/SQLitePendingNotesTest.kt), then
[construction/evidence](../../../../../../../docs/blueprints/pending-notes.md).

One slot per deployment/principal/account retains the original command and either
pending, a confirmed receipt, or a known refusal. Stage saves before HTTP and
compares exact replay before terminal CAS. Settle matches the original command
again. Transactions close before network work; capacity is bounded and corrupt/
future data is kept and refused. This adapter is selected by the connected product
overlay, while the catalog retains its earlier transient owner.

Four native cases cover reopen/terminal replacement and stale confirmation, all
scope dimensions/capacity, forced settlement rollback/corruption/future schemas,
and independent-instance contention. Failed storage blocks writes until reload.
No automatic replay, read cache, multi-item ordering or erasure policy is supplied.
Next: what explicit retention/migration rule does a real product require?

## Read-only remote snapshots — 2026-10-17

Claim: optional read-cache eviction belongs in a different database from durable pending commands. Origin: connected read-cache slice. Read [adapter](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesReadCache.kt) and [native checks](../../../../../project/core/data/src/androidTest/kotlin/dev/mobilefoundry/data/SQLiteNotesReadCacheTest.kt).

The cache marker/schema refuses unknown or corrupt stores, including a journal file accidentally used as a cache. A transaction admits/replaces one bounded payload, preserves newer timestamps and evicts oldest snapshots above capacity. Scope predicates bind all three tuple fields. Load checks the indexed timestamp against the admitted payload; expiry or a future clock timestamp removes only that scoped entry.

Gotchas: the 24-hour window controls usability with lazy cleanup, not scheduled erasure. Private backup-excluded storage is not an offline authentication grant. Later pages/cursors and automatic write replay remain outside this capability.

Actual verification and versions are tracked in [construction/evidence](../../../../../../../docs/blueprints/notes-read-cache.md). Continue with [checkpoint reasoning](../../../../../../../notes/patterns/cached-content-and-command-checkpoints.md). Next: what product requirement would justify offline startup or a multi-page synchronization cursor?

## Atomic Files journal — 2026-10-19

[SQLitePendingFilesStore](../../../../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLitePendingFilesStore.kt) persists one metadata/blob row in a separate marked SQLite database. Each short transaction owns compare/stage/remove; none contains HTTP. The adapter preserves future/foreign/corrupt stores, uses FULL synchronous rollback commits and private backup-excluded storage. Removal compares exact input and cannot clear a newer intent.

IO dispatch and a Mutex serialize one instance; SQLite transactions serialize independent instances. The refusing corruption handler prevents default deletion. Payload reads use 256 KiB substr chunks inside the transaction so a full 10 MiB row never enters CursorWindow. Four disk tests cover full-limit reopen/delete CAS, competing instances, failed-removal rollback and schema/corruption refusal. Row removal is not secure erasure of free pages, and the byte limit is not a total heap/file-size promise. Read [the shared reasoning](../../../../../../../notes/patterns/atomic-byte-staging-before-transport.md) and [actual verification](../../../../../../../docs/blueprints/connected-files.md).
