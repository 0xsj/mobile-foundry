import Foundation
import FoundryKernel
import FoundryServices
import SQLite3

/// One atomic private slot contains metadata AND bytes. No network inside a transaction.
/// Unreadable/foreign stores are preserved; compare exact input before removing anything.
public actor SQLitePendingFilesStore: PendingFilesStore {
    private let url: URL
    public init(url: URL) { precondition(url.isFileURL); self.url = url.standardizedFileURL }
    public func load() async throws -> AppResult<PendingFileIntent?> { try access { try read($0) } }
    public func stage(_ intent: PendingFileIntent) async throws -> AppResult<Void> {
        try access { db in
            if let current = try read(db) {
                guard current.matches(intent) else { throw PendingFilesFailure.conflict }; return
            }
            let statement = try prepare(db, "INSERT INTO pending_files(slot,metadata,content) VALUES (1,?,?)")
            defer { sqlite3_finalize(statement) }
            for (index, bytes) in [try intent.metadata(), intent.bytes].enumerated() {
                if bytes.isEmpty { try check(sqlite3_bind_zeroblob(statement, Int32(index + 1), 0), SQLITE_OK) }
                else {
                    let status = bytes.withUnsafeBytes { raw in sqlite3_bind_blob(statement, Int32(index + 1), raw.baseAddress, Int32(raw.count), unsafeBitCast(-1, to: sqlite3_destructor_type.self)) }
                    try check(status, SQLITE_OK)
                }
            }
            try check(sqlite3_step(statement), SQLITE_DONE)
        }
    }
    public func remove(_ intent: PendingFileIntent) async throws -> AppResult<Void> {
        try access { db in
            guard let current = try read(db) else { return }
            guard current.matches(intent) else { throw PendingFilesFailure.conflict }
            try exec(db, "DELETE FROM pending_files WHERE slot=1")
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
            } catch { throw PendingFilesFailure.storage }
            var handle: OpaquePointer?
            let opened = sqlite3_open_v2(url.path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
            defer { if let handle { sqlite3_close(handle) } }
            try check(opened, SQLITE_OK); guard let db = handle else { throw PendingFilesFailure.storage }
            try check(sqlite3_busy_timeout(db, 2_000), SQLITE_OK)
            try exec(db, "PRAGMA journal_mode=DELETE"); try exec(db, "PRAGMA synchronous=FULL")
            guard try scalar(db, "PRAGMA synchronous") == 2, try scalar(db, "PRAGMA busy_timeout") == 2_000 else { throw PendingFilesFailure.storage }
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
            guard try scalar(db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'") == 0 else { throw PendingFilesFailure.invalid }
            try exec(db, "CREATE TABLE pending_store (kind TEXT NOT NULL PRIMARY KEY)")
            try exec(db, "INSERT INTO pending_store(kind) VALUES ('pending-files.v1')")
            try exec(db, "CREATE TABLE pending_files (slot INTEGER PRIMARY KEY CHECK(slot=1), metadata BLOB NOT NULL, content BLOB NOT NULL)")
            try exec(db, "PRAGMA user_version=1")
        } else if version != 1 { throw PendingFilesFailure.schema }
        let statement = try prepare(db, "SELECT kind FROM pending_store"); defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW, let kind = sqlite3_column_text(statement, 0), String(cString: kind) == "pending-files.v1",
              sqlite3_step(statement) == SQLITE_DONE else { throw PendingFilesFailure.invalid }
    }
    private func read(_ db: OpaquePointer) throws -> PendingFileIntent? {
        let statement = try prepare(db, "SELECT slot,metadata,content FROM pending_files")
        defer { sqlite3_finalize(statement) }
        let status = sqlite3_step(statement); if status == SQLITE_DONE { return nil }; try check(status, SQLITE_ROW)
        guard sqlite3_column_int64(statement, 0) == 1 else { throw PendingFilesFailure.invalid }
        var values: [Data] = []
        for (index, maximum) in [(1, 16_384), (2, 10 * 1024 * 1024)] {
            let count = Int(sqlite3_column_bytes(statement, Int32(index)))
            guard sqlite3_column_type(statement, Int32(index)) == SQLITE_BLOB, count <= maximum, index == 2 || count > 0 else { throw PendingFilesFailure.invalid }
            if count == 0 { values.append(Data()) }
            else {
                guard let raw = sqlite3_column_blob(statement, Int32(index)) else { throw PendingFilesFailure.invalid }
                values.append(Data(bytes: raw, count: count))
            }
        }
        guard sqlite3_step(statement) == SQLITE_DONE else { throw PendingFilesFailure.invalid }
        return try PendingFileIntent.decode(metadata: values[0], bytes: values[1]).get()
    }
    private func scalar(_ db: OpaquePointer, _ sql: String) throws -> Int {
        let statement = try prepare(db, sql); defer { sqlite3_finalize(statement) }; try check(sqlite3_step(statement), SQLITE_ROW)
        return Int(sqlite3_column_int64(statement, 0))
    }
    private func prepare(_ db: OpaquePointer, _ sql: String) throws -> OpaquePointer {
        var statement: OpaquePointer?
        let status = sqlite3_prepare_v2(db, sql, -1, &statement, nil)
        if status != SQLITE_OK { if let statement { sqlite3_finalize(statement) }; try check(status, SQLITE_OK) }
        guard let statement else { throw PendingFilesFailure.invalid }; return statement
    }
    private func exec(_ db: OpaquePointer, _ sql: String) throws { try check(sqlite3_exec(db, sql, nil, nil, nil), SQLITE_OK) }
    private func check(_ status: Int32, _ expected: Int32) throws {
        guard status != expected else { return }
        if [SQLITE_CORRUPT, SQLITE_NOTADB, SQLITE_ERROR].contains(status & 255) { throw PendingFilesFailure.invalid }
        throw PendingFilesFailure.storage
    }
}
