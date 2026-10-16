import Foundation
import FoundryData
import FoundryServices
import FoundryKernel
import SQLite3
import Testing

private struct RepositoryFixture: Decodable {
    struct InvalidUpdate: Decodable { let id: String; let title: String; let code: String }
    let createTitles: [String]; let updateTitle: String; let invalidUpdates: [InvalidUpdate]; let invalidDeleteIDs: [String]; let missingID: String
}
private func fixture() throws -> RepositoryFixture {
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    return try JSONDecoder().decode(RepositoryFixture.self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/local-notes/repository.json")))
}
private func tempURL() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent("foundry-data-\(UUID().uuidString)/notes.sqlite") }
private func remove(_ url: URL) { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }

@Test func sharedCommandsAndMemoryRepositoryPreserveCRUDOrderAndOwnedSnapshots() async throws {
    let f = try fixture(), repo = InMemoryNotesRepository()
    for row in f.invalidUpdates {
        guard case .failure(let failure) = UpdateNote.make(id: row.id, title: row.title) else { Issue.record("Invalid edit admitted"); continue }
        #expect(failure.meta.code == row.code)
    }
    for id in f.invalidDeleteIDs {
        guard case .failure(let failure) = DeleteNote.make(id: id) else { Issue.record("Invalid deletion admitted"); continue }
        #expect(failure.meta.code == "notes.invalid_id")
    }
    var created: [Note] = []
    for title in f.createTitles { created.append(try await repo.create(CreateNote.make(title: title).get()).get()) }
    let before = try await repo.list().get()
    _ = try await repo.update(UpdateNote.make(id: created[0].id, title: f.updateTitle).get()).get()
    _ = try await repo.delete(DeleteNote.make(id: created[1].id).get()).get()
    #expect(before == created)
    #expect(try await repo.list().get().map(\.title) == [f.updateTitle] + Array(f.createTitles.dropFirst(2)))
}
@Test func sqliteCRUDSurvivesNewRepositoryHandlesWithStableIDsAndInsertionOrder() async throws {
    let url = tempURL(); defer { remove(url) }
    let f = try fixture(), repo = SQLiteNotesRepository(url: url)
    #expect(try await repo.list().get().isEmpty)
    var created: [Note] = []
    for title in f.createTitles { created.append(try await repo.create(CreateNote.make(title: title).get()).get()) }
    #expect(try await SQLiteNotesRepository(url: url).list().get() == created)
    let updated = try await repo.update(UpdateNote.make(id: created[0].id, title: f.updateTitle).get()).get()
    #expect(updated.id == created[0].id)
    _ = try await repo.delete(DeleteNote.make(id: created[1].id).get()).get()
    let expected = [updated] + Array(created.dropFirst(2))
    #expect(try await SQLiteNotesRepository(url: url).list().get() == expected)
    guard case .failure(let deleted) = try await repo.delete(DeleteNote.make(id: created[1].id).get()),
          case .failure(let missing) = try await repo.update(UpdateNote.make(id: f.missingID, title: "Missing").get()) else { Issue.record("Missing record silently admitted"); return }
    #expect(deleted.meta.code == "notes.not_found" && missing.kind == .notFound)
    #expect(try await repo.list().get() == expected)
}
@Test func concurrentSQLiteHandlesDoNotLoseCreatesOrDuplicateIdentity() async throws {
    let url = tempURL(); defer { remove(url) }
    let first = SQLiteNotesRepository(url: url), second = SQLiteNotesRepository(url: url)
    let notes = try await withThrowingTaskGroup(of: Note.self) { group in
        for index in 0..<12 { group.addTask { try await (index % 2 == 0 ? first : second).create(CreateNote.make(title: "Idea \(index)").get()).get() } }
        var notes: [Note] = []; for try await note in group { notes.append(note) }; return notes
    }
    #expect(Set(notes.map(\.id)).count == 12)
    #expect(try await SQLiteNotesRepository(url: url).list().get().count == 12)
}
private func rawSQL(_ url: URL, _ sql: String) throws {
    var db: OpaquePointer?; #expect(sqlite3_open(url.path, &db) == SQLITE_OK); defer { sqlite3_close(db) }
    #expect(sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK)
}
@Test func existingUnversionedSchemaIsKeptRatherThanAdoptedOrReset() async throws {
    let url = tempURL(); defer { remove(url) }
    try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try rawSQL(url, "CREATE TABLE foreign_data(value TEXT); INSERT INTO foreign_data VALUES ('Keep me')")
    let before = try Data(contentsOf: url)
    guard case .failure(let failure) = try await SQLiteNotesRepository(url: url).list() else { Issue.record("Unversioned schema adopted"); return }
    #expect(failure.meta.code == "notes.storage_corrupt")
    #expect(try Data(contentsOf: url) == before)
}
@Test func unsupportedOrCorruptStoresAreRefusedWithoutResettingStoredData() async throws {
    let url = tempURL(); defer { remove(url) }
    let repo = SQLiteNotesRepository(url: url)
    let note = try await repo.create(CreateNote.make(title: "Keep me").get()).get()
    try rawSQL(url, "PRAGMA user_version = 2")
    let futureBytes = try Data(contentsOf: url)
    guard case .failure(let future) = try await SQLiteNotesRepository(url: url).list() else { Issue.record("Future schema admitted"); return }
    #expect(future.meta.code == "notes.storage_version" && future.kind == .unavailable)
    #expect(try Data(contentsOf: url) == futureBytes)
    try rawSQL(url, "PRAGMA user_version = 1")
    #expect(try await repo.list().get() == [note])
    try rawSQL(url, "UPDATE notes SET title = '   '")
    guard case .failure(let invalid) = try await repo.list() else { Issue.record("Invalid stored title admitted"); return }
    #expect(invalid.meta.code == "notes.storage_corrupt")
    let broken = Data("This is not a SQLite database".utf8); try broken.write(to: url)
    guard case .failure(let corrupt) = try await SQLiteNotesRepository(url: url).list() else { Issue.record("Corrupt file admitted"); return }
    #expect(corrupt.meta.code == "notes.storage_corrupt" && corrupt.kind == .unavailable)
    #expect(try Data(contentsOf: url) == broken)
}
@Test func fileFailuresAndPreCanceledWritesKeepThePriorStorageUntouched() async throws {
    let url = tempURL(); defer { remove(url) }
    let repo = SQLiteNotesRepository(url: url), command = try CreateNote.make(title: "Canceled").get()
    let task = Task { withUnsafeCurrentTask { $0?.cancel() }; return try await repo.create(command) }
    do { _ = try await task.value; Issue.record("Cancellation swallowed") } catch { #expect(error is CancellationError) }
    #expect(!FileManager.default.fileExists(atPath: url.path))
    try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let blocker = url.deletingLastPathComponent().appendingPathComponent("blocker")
    try Data("Keep this file".utf8).write(to: blocker)
    let invalidURL = blocker.appendingPathComponent("notes.sqlite")
    guard case .failure(let failure) = try await SQLiteNotesRepository(url: invalidURL).create(command) else { Issue.record("Blocked path admitted"); return }
    #expect(failure.meta.code == "notes.storage_unavailable")
    #expect(try String(contentsOf: blocker, encoding: .utf8) == "Keep this file")
}
