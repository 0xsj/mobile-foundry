# SQLite helpers and nondestructive recovery

Native database opening, version callbacks and corruption recovery are part of the adapter's behavior, not just its SQL statements.

## Origin and versions

Observed 2026-10-10 with Kotlin 2.3.20, AGP 9.0.1, JDK 17, compile SDK 36/minimum
24, Compose BOM 2026.03.01 and Material3 1.4.0. Native checks ran on
API36_Test/Android 16 in an isolated emulator. SQLite is supplied by Android;
no bundled engine or Room dependency is introduced.

## What and why

SQLiteOpenHelper opens lazily and routes schema versions through native
callbacks. Opening/upgrading may block, so the adapter runs on Dispatchers.IO.
Its mutex serializes one repository; SQLite coordinates separate helpers.
Helper and cursor `use` blocks scope native handles to an operation.
Version callbacks can throw to refuse an unsupported change transactionally.
[SQLiteOpenHelper reference](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper).

The AOSP default corruption handler contains database-deletion paths. Supplying
an owned DatabaseErrorHandler avoids inheriting that recovery policy.
[AOSP implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/database/DefaultDatabaseErrorHandler.java).
The inspected mirror is archived; the API36 execution test, rather than that
historical source alone, verifies retention for this adapter on the tested OS.

## Example

```kotlin
// Excerpt: SQLiteNotesRepository.NotesOpenHelper supplies a refusing handler.
private class NotesOpenHelper(context: Context, name: String) :
    SQLiteOpenHelper(context, name, null, 1,
        DatabaseErrorHandler { throw InvalidStoredNote() })
```

The surrounding adapter maps that refusal to a typed unavailable failure with
deliberate recovery copy. It does not delete the file or invent an empty list.
ContentValues and selection arguments bind supplied values; list returns an
unmodifiable owned copy after complete domain admission.

## Fast text feedback

The first native rapid-key experiment produced only `Loc` from a longer title
despite passing SetText replacement tests. Log inspection subsequently showed
Gboard activating an internal handwriting editor; this observation alone does
not isolate a product defect. Closing the panel did not make shell injection
reliable; it reactivated its internal editor. WORKING: that OS/IME interaction
needs a controlled keyboard/device comparison before attributing a cause.
The retained regression injects a burst through Compose's key pipeline and
checks IME-action command admission. It does not claim OS window routing coverage.
[Focus/key test guidance](https://developer.android.com/develop/ui/compose/touch-input/focus/testing-focus).

Local notes uses the state-based LabeledTextField overload with a transient TextFieldState
owned by its ViewModel. Native input edits that state directly; snapshotFlow
updates the domain draft for feedback. Save flushes the current text synchronously
before command admission, so collection ordering cannot drop the final edit.
Programmatic edit/reset updates the field explicitly; no collected stale String
is written back over a newer native edit. A host check saves a native edit before
the draft observer runs. Immediate String feedback and early native-state
attempts did not resolve the panel's key capture; their failed experiments are
not credited as proof of a field-level cause. Other consumers/soft keyboards
remain separate checks.
[Compose input guidance](https://developer.android.com/develop/ui/compose/text/user-input).

## Gotchas and actual use

[Adapter](../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesRepository.kt)
and [native database checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/data/LocalNotesRepositoryTest.kt)
exercise actual Android SQLite, including byte-preserved damaged files,
unversioned tables, future versions, concurrent creates and fresh helpers.
Host JVM tests cannot establish Android SQLite behavior.

Cancellation after a native statement may suppress its result after it committed.
No automatic write replay is supplied. Process relaunch was observed separately:
an app-created note survived force-stop, PID 2117 → 2563, with a blank draft;
the test record was then deleted through confirmation. Native keyboard/layout
checks remain limited to the tested emulator; TalkBack, other IMEs, devices,
backup/restore and power-loss behavior remain unobserved.

## Related and next

[Data walkthrough](../modules/project/core/data/README.md),
[StateFlow mechanics](../language/kotlin-service-interfaces-and-stateflow.md)
and [local observation lifetime](../../../../notes/patterns/local-commits-and-observation-lifetime.md).
Next: which explicit migrations preserve released data, and when should multiple
screen owners share database observation instead of separate reloads?

## Pending journal follow-up — 2026-10-17

Claim: SQL configuration needs native execution checks as well as compilation.
The pending adapter opens a short-lived SQLiteDatabase on Dispatchers.IO, supplies
a refusing corruption handler and compares/stages/settles under one transaction.
It uses DELETE journaling and FULL synchronous, checking their selected values.
Read [Data](../modules/project/core/data/README.md#durable-pending-notes).

On Pixel_9_Pro / Android 15, execSQL("PRAGMA busy_timeout=2000") failed with
“Queries can be performed using SQLiteDatabase query or rawQuery methods only.”
The pragma returns a row. Consuming it with rawQuery and checking 2000 fixes the
actual native failure. The four native recovery cases then pass: reopen/terminal
CAS, scope partitions/capacity, rollback/corruption/future schema and competing
instances. A successful JVM build could not establish any of those database facts.

```kotlin
// Excerpt: consume the selected timeout from the result-bearing PRAGMA.
db.rawQuery("PRAGMA busy_timeout=2000", null).use {
    if (!it.moveToFirst() || it.getLong(0) != 2_000L) throw InvalidStore()
}
```

The journal's UTF-8 decoder also uses CodingErrorAction.REPORT: the ordinary
ByteArray.toString(Charsets.UTF_8) replacement behavior could turn corrupt bytes
into a different admitted note title. Shared record cases plus malformed bytes
inside valid JSON verify refusal. These are Android 15/native-file and JVM codec
observations, not claims about power-loss durability or automatic offline sync.
[SQLite PRAGMA reference](https://sqlite.org/pragma.html#pragma_busy_timeout).

## Separate read cache — 2026-10-17

Claim: short native transactions can bound an advisory cache without weakening journal retention. Origin: account read-cache slice on the same native toolchains; independent OS/device evidence is recorded in [construction/evidence](../../../../docs/blueprints/notes-read-cache.md). Read [the adapter](../../project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesReadCache.kt).

Dispatchers.IO owns native operations under the adapter mutex; SQLite still coordinates distinct instances. Cursor use closes before eviction deletes begin. The implementation uses update/insert and bound scope arguments rather than requiring a newer SQLite UPSERT syntax across supported Android versions. The owner checks coroutine cancellation after every storage suspension.

```kotlin
// Excerpt: SQLiteNotesReadCache.load makes expiry cleanup scope-local.
val snapshot = read(db, scope)
if (snapshot != null && !snapshot.isUsable(nowMilliseconds)) {
    remove(db, scope); null
} else snapshot
```

A custom corruption/refusal path keeps stored bytes rather than inventing an empty cache. Optional cache failure returns a separate warning; journal storage keeps its stricter write gate. Lazy expiry is not scheduled secure deletion, and these tests do not establish power-loss or flash-throughput budgets. Next: should an actual product require eager account erasure or OS-backed encryption beyond container protection?
