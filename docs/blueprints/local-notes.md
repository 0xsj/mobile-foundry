# Local persistence construction

Authority: [behavior](../../contracts/behavior/local-notes.md),
[organization](../ORGANIZATION.md), [architecture](../../ARCHITECTURE.md).

1. Compose a CRUD NotesRepository from the existing read/create ports. Admit
   update/delete commands at the domain boundary, reusing ID/title rules.
2. Put the memory repository in Services and native SQLite adapters in
   FoundryData/core:data. Keep domain values independent of rows/SDK objects.
3. Initialize a version-one schema transactionally. Use insertion position,
   stable unique ID, preserved title and parameter-bound SQL. Close native
   handles per operation; run storage off the UI thread.
4. Refuse unknown versions and malformed data. Preserve the database on failure,
   including Android native corruption callbacks. Add explicit future migrations
   only when the next schema is specified.
5. Compose Device storage and Memory in the app. Keep the existing injected
   HTTP/read and Forms galleries intact. Both new adapters implement the same
   local feature port; a future remote integration can consume narrower ports.
6. Own query/mutation phases, draft, editing identity and result generations in
   the feature. Guard admission synchronously, confirm deletion, reload after
   commit and preserve its receipt if a later read fails.
7. Exercise actual files, fresh handles, concurrent access and failure retention.
   Check native form/source/navigation behavior and process reopening. Record
   each platform's actual observations and limits in the learning notes.

## Try the slice

Open **Studio → Open catalog → Local notes**. Keep Device storage selected,
create two notes, edit the first, cancel a deletion and then confirm it. Close
the catalog or relaunch the app and return: saved records keep their IDs/order.
Memory demonstrates the same screen while retaining only transient feature data.
Source replacement discards a Swift draft/owner; Android retains source owners
only within the current navigation entry. Switching source during work is blocked.

`make data-test` runs Swift disk/domain checks and the Kotlin services/app state
tests. `make data-ui-test` requires a booted Android device/emulator and runs
native SQLite and Local notes UI checks. `make ios-test` includes the hosted iOS
CRUD/state-owner checks. `make notes-check` validates links/example labels only.

## Use the seam

```swift
// Conceptual: app composition supplies one private database URL.
let repository: any NotesRepository = SQLiteNotesRepository(url: databaseURL)
let reads: any NotesService = repository
let creates: any NoteCreator = repository
let result = try await creates.create(CreateNote.make(title: title).get())
```

```kotlin
// Conceptual: app composition supplies context; expected failures remain values.
val repository: NotesRepository = SQLiteNotesRepository(context)
val reads: NotesService = repository
val creates: NoteCreator = repository
when (val result = creates.create(command)) {
    is Outcome.Ok -> refreshLocalSnapshot()
    is Outcome.Err -> showExpectedFailure(result.error)
}
```

The catalog is a reference consumer, not a universal notes application. Explicit
reload after mutation is sufficient for its single feature owner. A future shared
observation stream or multi-account repository needs its own lifetime/scope
contract; do not introduce it implicitly through this adapter.
