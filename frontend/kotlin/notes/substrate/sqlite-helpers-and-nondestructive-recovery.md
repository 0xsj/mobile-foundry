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
