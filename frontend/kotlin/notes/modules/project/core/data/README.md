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
