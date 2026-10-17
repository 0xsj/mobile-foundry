import Foundation
import FoundryKernel
import FoundryServices
import SQLite3

/// Private, backup-excluded recovery journal. Every operation owns a short transaction;
/// no transaction crosses HTTP. Corrupt/unknown stores are kept and refused.
public actor SQLitePendingNotesStore: PendingNotesStore {
    private let url: URL
    private let maxScopes: Int
    public init(url: URL, maxScopes: Int = 256) {
        precondition(url.isFileURL && maxScopes > 0); self.url = url.standardizedFileURL; self.maxScopes = maxScopes
    }
    public func load(_ scope: PendingNotesScope) async throws -> AppResult<PendingNoteRecord?> {
        try access { try read($0, scope) }
    }
    public func stage(_ command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?) async throws -> AppResult<PendingNoteRecord> {
        try access { db in
            let current = try read(db, scope)
            if let current, current.command.commandID == command.commandID {
                guard current.matches(command) else { throw PendingNotesFailure.conflict }; return current
            }
            guard current?.command.commandID == replacing, current?.status != .pending else { throw PendingNotesFailure.conflict }
            if current == nil {
                let count = try scalar(db, "SELECT count(*) FROM pending_notes")
                guard count < maxScopes else { throw PendingNotesFailure.capacity }
            }
            let record = PendingNoteRecord(scope: scope, command: command); try write(db, record); return record
        }
    }
    public func confirm(_ command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt) async throws -> AppResult<PendingNoteRecord> {
        try access { db in
            guard let current = try read(db, scope), current.matches(command) else { throw PendingNotesFailure.conflict }
            let next = try current.confirming(receipt).get(); try write(db, next); return next
        }
    }
    public func reject(_ command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection) async throws -> AppResult<PendingNoteRecord> {
        try access { db in
            guard let current = try read(db, scope), current.matches(command) else { throw PendingNotesFailure.conflict }
            let next = try current.rejecting(reason).get(); try write(db, next); return next
        }
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
            } catch { throw PendingNotesFailure.storage }
            var handle: OpaquePointer?
            let opened = sqlite3_open_v2(url.path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
            defer { if let handle { sqlite3_close(handle) } }
            try check(opened, SQLITE_OK); guard let db = handle else { throw PendingNotesFailure.storage }
            try check(sqlite3_busy_timeout(db, 2_000), SQLITE_OK)
            try exec(db, "PRAGMA journal_mode=DELETE"); try exec(db, "PRAGMA synchronous=FULL")
            guard try scalar(db, "PRAGMA synchronous") == 2, try scalar(db, "PRAGMA busy_timeout") == 2_000 else { throw PendingNotesFailure.storage }
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
            guard try scalar(db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'") == 0 else { throw PendingNotesFailure.invalid }
            try exec(db, "CREATE TABLE pending_store (kind TEXT NOT NULL PRIMARY KEY)")
            try exec(db, "INSERT INTO pending_store(kind) VALUES ('pending-notes.v1')")
            try exec(db, "CREATE TABLE pending_notes (authority TEXT NOT NULL, principal TEXT NOT NULL, account TEXT NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(authority,principal,account)) WITHOUT ROWID")
            try exec(db, "PRAGMA user_version=1")
        } else if version != 1 { throw PendingNotesFailure.schema }
        let statement = try prepare(db, "SELECT kind FROM pending_store"); defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW, let kind = sqlite3_column_text(statement, 0), String(cString: kind) == "pending-notes.v1",
              sqlite3_step(statement) == SQLITE_DONE else { throw PendingNotesFailure.invalid }
    }
    private func read(_ db: OpaquePointer, _ scope: PendingNotesScope) throws -> PendingNoteRecord? {
        let statement = try prepare(db, "SELECT payload FROM pending_notes WHERE authority=? AND principal=? AND account=?")
        defer { sqlite3_finalize(statement) }; try bind(scope, statement)
        let status = sqlite3_step(statement); if status == SQLITE_DONE { return nil }; try check(status, SQLITE_ROW)
        let length = Int(sqlite3_column_bytes(statement, 0))
        guard sqlite3_column_type(statement, 0) == SQLITE_BLOB, length > 0, length <= 16_384, let bytes = sqlite3_column_blob(statement, 0) else { throw PendingNotesFailure.invalid }
        return try PendingNoteRecord.decode(Data(bytes: bytes, count: length), scope: scope).get()
    }
    private func write(_ db: OpaquePointer, _ record: PendingNoteRecord) throws {
        let statement = try prepare(db, "INSERT INTO pending_notes(authority,principal,account,payload) VALUES (?,?,?,?) ON CONFLICT(authority,principal,account) DO UPDATE SET payload=excluded.payload")
        defer { sqlite3_finalize(statement) }; try bind(record.scope, statement)
        let bytes = try record.encoded()
        let status = bytes.withUnsafeBytes { raw in sqlite3_bind_blob(statement, 4, raw.baseAddress, Int32(raw.count), unsafeBitCast(-1, to: sqlite3_destructor_type.self)) }
        try check(status, SQLITE_OK)
        try check(sqlite3_step(statement), SQLITE_DONE)
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
        guard let statement else { throw PendingNotesFailure.invalid }; return statement
    }
    private func exec(_ db: OpaquePointer, _ sql: String) throws { try check(sqlite3_exec(db, sql, nil, nil, nil), SQLITE_OK) }
    private func check(_ status: Int32, _ expected: Int32) throws {
        guard status != expected else { return }
        if [SQLITE_CORRUPT, SQLITE_NOTADB, SQLITE_ERROR].contains(status & 255) { throw PendingNotesFailure.invalid }
        throw PendingNotesFailure.storage
    }
}
