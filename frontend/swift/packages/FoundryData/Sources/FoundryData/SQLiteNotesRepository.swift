import Foundation
import FoundryKernel
import FoundryServices
import SQLite3

/// Device-local SQLite adapter. Handles/statements are scoped to one actor-isolated operation.
/// No disk work in init, no destructive migration, no networking or automatic write replay.
public actor SQLiteNotesRepository: ObservableNotesRepository {
    private let url: URL
    public init(url: URL) { precondition(url.isFileURL); self.url = url.standardizedFileURL }

    public func observe() async throws -> NotesSubscription {
        try Task.checkCancellation()
        let changes = NotesChanges.shared.observe(url.path)
        let (stream, continuation) = NotesStream.makeStream(bufferingPolicy: .bufferingNewest(1))
        let task = Task {
            defer { changes.cancel() }
            do {
                var previous: AppResult<[Note]>?
                for await _ in changes.values {
                    try Task.checkCancellation()
                    let answer = try await self.list(); try Task.checkCancellation()
                    if answer != previous { continuation.yield(answer); previous = answer }
                }
                continuation.finish()
            } catch { continuation.finish(throwing: error) }
        }
        continuation.onTermination = { _ in task.cancel() }
        return NotesSubscription(values: stream, onCancel: { continuation.finish() })
    }

    public func list() async throws -> AppResult<[Note]> {
        try withDatabase { db in
            let statement = try prepare(db, "SELECT id, title FROM notes ORDER BY position ASC")
            defer { sqlite3_finalize(statement) }
            var notes: [Note] = []
            while true {
                let status = sqlite3_step(statement)
                if status == SQLITE_DONE { return notes }
                try check(status, expected: SQLITE_ROW)
                guard let note = Note(id: try text(statement, 0), title: try text(statement, 1)) else { throw Self.corrupt }
                notes.append(note)
            }
        }
    }
    public func create(_ command: CreateNote) async throws -> AppResult<Note> {
        try withDatabase(notify: true) { db in
            let note = Note(id: UUID().uuidString, title: command.title)!
            try execute(db, "INSERT INTO notes(id, title) VALUES (?, ?)", values: [note.id, note.title])
            return note
        }
    }
    public func update(_ command: UpdateNote) async throws -> AppResult<Note> {
        try withDatabase(notify: true) { db in
            try execute(db, "UPDATE notes SET title = ? WHERE id = ?", values: [command.title, command.id])
            guard sqlite3_changes(db) == 1 else { throw NotesRepositoryFailure.missing }
            return Note(id: command.id, title: command.title)!
        }
    }
    public func delete(_ command: DeleteNote) async throws -> AppResult<Void> {
        try withDatabase(notify: true) { db in
            try execute(db, "DELETE FROM notes WHERE id = ?", values: [command.id])
            guard sqlite3_changes(db) == 1 else { throw NotesRepositoryFailure.missing }
        }
    }

    private func withDatabase<T>(notify: Bool = false, _ operation: (OpaquePointer) throws -> T) throws -> AppResult<T> {
        try Task.checkCancellation()
        do {
            do { try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true) }
            catch { throw Self.unavailable }
            var handle: OpaquePointer?
            let status = sqlite3_open_v2(url.path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
            defer { if let handle { sqlite3_close(handle) } }
            try check(status, expected: SQLITE_OK)
            guard let db = handle else { throw Self.unavailable }
            try check(sqlite3_busy_timeout(db, 2_000), expected: SQLITE_OK)
            try schema(db)
            try Task.checkCancellation()
            // Single-statement CRUD uses SQLite's atomic implicit transaction; no suspension before acknowledgement.
            let value = try operation(db)
            if notify { NotesChanges.shared.committed(url.path) }
            return .success(value)
        } catch let failure as Failure { return .failure(failure) }
    }
    private func schema(_ db: OpaquePointer) throws {
        try execute(db, "BEGIN IMMEDIATE")
        var committed = false
        defer { if !committed { _ = sqlite3_exec(db, "ROLLBACK", nil, nil, nil) } }
        let statement = try prepare(db, "PRAGMA user_version")
        defer { sqlite3_finalize(statement) }
        try check(sqlite3_step(statement), expected: SQLITE_ROW)
        let version = sqlite3_column_int(statement, 0)
        sqlite3_reset(statement)
        if version == 0 {
            let tables = try prepare(db, "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
            defer { sqlite3_finalize(tables) }
            try check(sqlite3_step(tables), expected: SQLITE_ROW)
            guard sqlite3_column_int(tables, 0) == 0 else { throw Self.corrupt }
            sqlite3_reset(tables)
            // Existing unversioned data is never silently adopted or dropped.
            try execute(db, "CREATE TABLE notes (position INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE, title TEXT NOT NULL)")
            try execute(db, "PRAGMA user_version = 1")
        } else if version != 1 { throw Self.unsupported }
        try execute(db, "COMMIT"); committed = true
    }
    private func prepare(_ db: OpaquePointer, _ sql: String) throws -> OpaquePointer {
        var statement: OpaquePointer?
        let status = sqlite3_prepare_v2(db, sql, -1, &statement, nil)
        if status != SQLITE_OK { if let statement { sqlite3_finalize(statement) }; try check(status, expected: SQLITE_OK) }
        guard let statement else { throw Self.corrupt }; return statement
    }
    private func execute(_ db: OpaquePointer, _ sql: String, values: [String] = []) throws {
        let statement = try prepare(db, sql); defer { sqlite3_finalize(statement) }
        for (index, value) in values.enumerated() {
            // Explicit UTF-8 length and SQLITE_TRANSIENT preserve embedded NUL and own copied bytes.
            let status = value.withCString { sqlite3_bind_text(statement, Int32(index + 1), $0, Int32(value.utf8.count), unsafeBitCast(-1, to: sqlite3_destructor_type.self)) }
            try check(status, expected: SQLITE_OK)
        }
        try check(sqlite3_step(statement), expected: SQLITE_DONE)
    }
    private func text(_ statement: OpaquePointer, _ column: Int32) throws -> String {
        guard sqlite3_column_type(statement, column) == SQLITE_TEXT,
              let bytes = sqlite3_column_text(statement, column),
              let text = String(bytes: UnsafeBufferPointer(start: bytes, count: Int(sqlite3_column_bytes(statement, column))), encoding: .utf8) else { throw Self.corrupt }
        return text
    }
    private func check(_ status: Int32, expected: Int32) throws {
        guard status != expected else { return }
        switch status & 0xff {
        case SQLITE_BUSY, SQLITE_LOCKED: throw Failure.unavailable(.init(message: "Local notes storage is busy. Try again.", code: "notes.storage_busy"))
        case SQLITE_CORRUPT, SQLITE_NOTADB, SQLITE_ERROR, SQLITE_SCHEMA: throw Self.corrupt
        case SQLITE_CONSTRAINT: throw Failure.conflict(.init(message: "The local note could not be saved. Refresh your notes.", code: "notes.storage_conflict"))
        default: throw Self.unavailable
        }
    }
    private static var unavailable: Failure { .unavailable(.init(message: "Local notes storage is unavailable. Try again.", code: "notes.storage_unavailable")) }
    private static var corrupt: Failure { .unavailable(.init(message: "Local notes could not be read. Stored data has been kept.", code: "notes.storage_corrupt")) }
    private static var unsupported: Failure { .unavailable(.init(message: "This notes database needs a newer app version.", code: "notes.storage_version")) }
}
