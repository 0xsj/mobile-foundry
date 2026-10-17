# SQLite actors and scoped handles

An actor can own synchronous SQLite work while returning domain values and closing C handles within each operation.

## Origin and versions

Observed 2026-10-10 with Swift 6.2.3, Xcode 26.2, macOS 26.3 and the iOS 26.2
simulator; package minimums remain iOS 17/macOS 14. The adapter imports the SDK's
SQLite3 module and links sqlite3. The OS supplies its SQLite implementation;
this slice does not pin a bundled database engine version.

## What and why

The repository actor is separate from the MainActor screen owner. Its methods
perform synchronous open/schema/statement/close work without suspension between
dispatch and acknowledgement. Actor isolation serializes an instance; SQLite
locking coordinates distinct instances. This is bounded catalog work, not an
established throughput budget for a large database.

`defer` closes a connection and finalizes statements on success or failure.
No OpaquePointer escapes the operation or becomes an observable UI property.
Expected native failures become kernel values; cancellation remains thrown.

SQLite documents automatic transactions around statements and the locking
behavior of BEGIN IMMEDIATE. Initialization uses that transaction plus rollback
on refusal; single-statement CRUD uses implicit transactions.
[Transaction reference](https://www.sqlite.org/lang_transaction.html).
The application owns user_version and admits only its known version.
[PRAGMA reference](https://www.sqlite.org/pragma.html#pragma_user_version).

## Example

```swift
// Excerpt: native binding inside SQLiteNotesRepository.execute.
let status = value.withCString {
    sqlite3_bind_text(statement, Int32(index + 1), $0,
        Int32(value.utf8.count),
        unsafeBitCast(-1, to: sqlite3_destructor_type.self))
}
```

An explicit UTF-8 byte count preserves an embedded NUL. SQLITE_TRANSIENT asks
SQLite to copy the bytes before withCString ends. Reads use column_bytes and
UTF-8 decoding rather than a zero-terminated String initializer.
[Binding reference](https://www.sqlite.org/c3ref/bind_blob.html).

## Gotchas

An actor does not interrupt a synchronous native statement when its task is
canceled. Check before dispatch, then treat a lost acknowledgement as uncertain.
Do not await while a native transaction or borrowed text pointer is owned.
Only known storage failures are projected; raw file paths/native messages are
not public copy. Missing v1 tables are refused, not recreated implicitly.

## Used in and actual checks

[Adapter](../../packages/FoundryData/Sources/FoundryData/SQLiteNotesRepository.swift)
and [six disk/domain tests](../../packages/FoundryData/Tests/FoundryDataTests/SQLiteNotesRepositoryTests.swift)
pass through `make data-test`. Checks use real temporary files, fresh handles,
concurrent instances, NUL/Unicode, unversioned/future/corrupt stores and pre-cancel.
The iOS consumer builds and its three new state-owner checks pass within 67
hosted app cases. Fresh-handle and hosted tests are not an iOS process-kill,
physical-device, power-loss or storage-pressure experiment.

## Related and next

[Data walkthrough](../modules/packages/FoundryData/README.md),
[observable service owners](../language/swift-observable-stores-and-service-protocols.md)
and [local observation lifetime](../../../../notes/patterns/local-commits-and-observation-lifetime.md).
Next: when should a dedicated storage executor replace cooperative actor work,
and what independent evidence would justify connection pooling or WAL?

## Pending journal follow-up — 2026-10-17

Claim: the actor can compare/stage/settle a recovery slot inside a short native
transaction without carrying its C pointers across HTTP. The pending adapter
uses BEGIN IMMEDIATE, DELETE journaling and FULL synchronous, and verifies numeric
PRAGMA settings. Four real-file tests pass on macOS: reopen/terminal CAS, all scope
partitions/capacity, rollback/corruption/schema refusal and competing instances.
See [Data](../modules/packages/FoundryData/README.md#durable-pending-notes).

Swift 6 rejected a closure that captured both an actor-isolated checking helper
and an OpaquePointer inside withUnsafeBytes/withCString: “sending statement risks
causing data races.” Return only the C status from the synchronous borrowed-buffer
closure, then check it outside. This follows the earlier text-binding shape and
keeps self out of the pointer closure. No unchecked Sendable annotation is needed.

```swift
// Excerpt: SQLitePendingNotesStore.write borrows bytes only for the bind call.
let status = bytes.withUnsafeBytes { raw in
    sqlite3_bind_blob(statement, 4, raw.baseAddress, Int32(raw.count),
        unsafeBitCast(-1, to: sqlite3_destructor_type.self))
}
try check(status, SQLITE_OK)
```

No transaction spans await/network work; cancellation can still arrive after
commit, so the owner reloads rather than assuming rollback. These checks establish
process/file behavior, not measured sudden-power-loss durability or flash budgets.

## Separate read cache — 2026-10-17

Claim: short native transactions can bound an advisory cache without weakening journal retention. Origin: account read-cache slice on the same native toolchains; independent OS/device evidence is recorded in [construction/evidence](../../../../docs/blueprints/notes-read-cache.md). Read [the adapter](../../packages/FoundryData/Sources/FoundryData/SQLiteNotesReadCache.swift).

The actor keeps statements/borrowed bytes inside one operation. A second schema marker prevents accidental cache operations on the pending journal file. Timestamp/partition metadata is checked against the strict decoded payload before use; SQLite eviction runs in the same transaction as insertion. The owner checks task cancellation again after the actor returns.

```swift
// Excerpt: SQLiteNotesReadCache.load makes expiry cleanup scope-local.
guard let snapshot = try read(db, scope) else { return nil }
guard snapshot.isUsable(at: nowMilliseconds) else {
    try remove(db, scope); return nil
}
```

A custom corruption/refusal path keeps stored bytes rather than inventing an empty cache. Optional cache failure returns a separate warning; journal storage keeps its stricter write gate. Lazy expiry is not scheduled secure deletion, and these tests do not establish power-loss or flash-throughput budgets. Next: should an actual product require eager account erasure or OS-backed encryption beyond container protection?
