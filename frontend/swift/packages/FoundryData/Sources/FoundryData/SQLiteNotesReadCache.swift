import Foundation
import FoundryKernel
import FoundryServices
import SQLite3

/// Separate backup-excluded advisory cache. Unknown/corrupt databases are kept;
/// expiry and oldest-snapshot eviction never touch the pending-command journal.
public actor SQLiteNotesReadCache: NotesReadCache {
    private let url: URL
    private let maxScopes: Int
    public init(url: URL, maxScopes: Int = 32) {
        precondition(url.isFileURL && (1...32).contains(maxScopes)); self.url = url.standardizedFileURL; self.maxScopes = maxScopes
    }
    public func load(_ scope: PendingNotesScope, nowMilliseconds: Int64) async throws -> AppResult<NotesReadSnapshot?> {
        try access { db in
            guard let snapshot = try read(db, scope) else { return nil }
            guard snapshot.isUsable(at: nowMilliseconds) else { try remove(db, scope); return nil }
            return snapshot
        }
    }
    public func save(_ snapshot: NotesReadSnapshot) async throws -> AppResult<Void> {
        try access { db in
            let bytes = try snapshot.encoded()
            guard bytes.count <= NotesReadSnapshot.maxBytes else { throw NotesCacheFailure.invalid }
            // Refuse an unreadable target row rather than silently repairing it on save.
            if let current = try read(db, snapshot.scope), current.savedAtMilliseconds > snapshot.savedAtMilliseconds { return }
            let statement = try prepare(db, "INSERT INTO notes_cache(authority,principal,account,saved_at,payload) VALUES (?,?,?,?,?) ON CONFLICT(authority,principal,account) DO UPDATE SET saved_at=excluded.saved_at,payload=excluded.payload WHERE excluded.saved_at>=notes_cache.saved_at")
            defer { sqlite3_finalize(statement) }; try bind(snapshot.scope, statement)
            try check(sqlite3_bind_int64(statement, 4, snapshot.savedAtMilliseconds), SQLITE_OK)
            try check(bytes.withUnsafeBytes { sqlite3_bind_blob(statement, 5, $0.baseAddress, Int32($0.count), unsafeBitCast(-1, to: sqlite3_destructor_type.self)) }, SQLITE_OK)
            try check(sqlite3_step(statement), SQLITE_DONE)
            try exec(db, "DELETE FROM notes_cache WHERE (authority,principal,account) IN (SELECT authority,principal,account FROM notes_cache ORDER BY saved_at DESC,authority,principal,account LIMIT -1 OFFSET \(maxScopes))")
        }
    }
    public func remove(_ scope: PendingNotesScope) async throws -> AppResult<Void> { try access { try remove($0, scope) } }
    private func remove(_ db: OpaquePointer, _ scope: PendingNotesScope) throws {
        let statement = try prepare(db, "DELETE FROM notes_cache WHERE authority=? AND principal=? AND account=?")
        defer { sqlite3_finalize(statement) }; try bind(scope, statement); try check(sqlite3_step(statement), SQLITE_DONE)
    }
    private func access<T>(_ operation: (OpaquePointer) throws -> T) throws -> AppResult<T> {
        try Task.checkCancellation()
        do {
            do {
                var directory = url.deletingLastPathComponent()
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
                var values = URLResourceValues(); values.isExcludedFromBackup = true; try directory.setResourceValues(values)
                #if os(iOS)
                try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
                #endif
            } catch { throw NotesCacheFailure.storage }
            var handle: OpaquePointer?
            let opened = sqlite3_open_v2(url.path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
            defer { if let handle { sqlite3_close(handle) } }
            try check(opened, SQLITE_OK); guard let db = handle else { throw NotesCacheFailure.storage }
            try check(sqlite3_busy_timeout(db, 2_000), SQLITE_OK)
            try exec(db, "PRAGMA journal_mode=DELETE"); try exec(db, "PRAGMA synchronous=FULL")
            guard try scalar(db, "PRAGMA synchronous") == 2, try scalar(db, "PRAGMA busy_timeout") == 2_000 else { throw NotesCacheFailure.storage }
            try exec(db, "BEGIN IMMEDIATE")
            var committed = false
            defer { if !committed { _ = sqlite3_exec(db, "ROLLBACK", nil, nil, nil) } }
            try schema(db); try Task.checkCancellation()
            let answer = try operation(db)
            try exec(db, "COMMIT"); committed = true
            return .success(answer)
        } catch let failure as Failure { return .failure(failure) }
    }
    private func schema(_ db: OpaquePointer) throws {
        let version = try scalar(db, "PRAGMA user_version")
        if version == 0 {
            guard try scalar(db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'") == 0 else { throw NotesCacheFailure.invalid }
            try exec(db, "CREATE TABLE cache_store (kind TEXT NOT NULL PRIMARY KEY)")
            try exec(db, "INSERT INTO cache_store(kind) VALUES ('notes-read-cache.v1')")
            try exec(db, "CREATE TABLE notes_cache (authority TEXT NOT NULL, principal TEXT NOT NULL, account TEXT NOT NULL, saved_at INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(authority,principal,account)) WITHOUT ROWID")
            try exec(db, "PRAGMA user_version=1")
        } else if version != 1 { throw NotesCacheFailure.schema }
        let statement = try prepare(db, "SELECT kind FROM cache_store"); defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW, let kind = sqlite3_column_text(statement, 0), String(cString: kind) == "notes-read-cache.v1", sqlite3_step(statement) == SQLITE_DONE else { throw NotesCacheFailure.invalid }
    }
    private func read(_ db: OpaquePointer, _ scope: PendingNotesScope) throws -> NotesReadSnapshot? {
        let statement = try prepare(db, "SELECT payload,saved_at FROM notes_cache WHERE authority=? AND principal=? AND account=?")
        defer { sqlite3_finalize(statement) }; try bind(scope, statement)
        let status = sqlite3_step(statement); if status == SQLITE_DONE { return nil }; try check(status, SQLITE_ROW)
        let length = Int(sqlite3_column_bytes(statement, 0))
        guard sqlite3_column_type(statement, 0) == SQLITE_BLOB, length > 0, length <= NotesReadSnapshot.maxBytes,
              sqlite3_column_type(statement, 1) == SQLITE_INTEGER, let bytes = sqlite3_column_blob(statement, 0) else { throw NotesCacheFailure.invalid }
        let snapshot = try NotesReadSnapshot.decode(Data(bytes: bytes, count: length), scope: scope).get()
        guard snapshot.savedAtMilliseconds == sqlite3_column_int64(statement, 1) else { throw NotesCacheFailure.invalid }
        return snapshot
    }
    private func bind(_ scope: PendingNotesScope, _ statement: OpaquePointer) throws {
        for (index, value) in [scope.authorityID, scope.principalID, scope.accountID].enumerated() {
            let status = value.withCString { sqlite3_bind_text(statement, Int32(index + 1), $0, Int32(value.utf8.count), unsafeBitCast(-1, to: sqlite3_destructor_type.self)) }
            try check(status, SQLITE_OK)
        }
    }
    private func scalar(_ db: OpaquePointer, _ sql: String) throws -> Int {
        let statement = try prepare(db, sql); defer { sqlite3_finalize(statement) }; try check(sqlite3_step(statement), SQLITE_ROW)
        return Int(sqlite3_column_int64(statement, 0))
    }
    private func prepare(_ db: OpaquePointer, _ sql: String) throws -> OpaquePointer {
        var statement: OpaquePointer?
        let status = sqlite3_prepare_v2(db, sql, -1, &statement, nil)
        if status != SQLITE_OK { if let statement { sqlite3_finalize(statement) }; try check(status, SQLITE_OK) }
        guard let statement else { throw NotesCacheFailure.invalid }; return statement
    }
    private func exec(_ db: OpaquePointer, _ sql: String) throws { try check(sqlite3_exec(db, sql, nil, nil, nil), SQLITE_OK) }
    private func check(_ status: Int32, _ expected: Int32) throws {
        guard status != expected else { return }
        if [SQLITE_CORRUPT, SQLITE_NOTADB, SQLITE_ERROR].contains(status & 255) { throw NotesCacheFailure.invalid }
        throw NotesCacheFailure.storage
    }
}
