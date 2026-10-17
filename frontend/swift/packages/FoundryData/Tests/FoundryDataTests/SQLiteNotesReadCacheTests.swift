import Foundation
import FoundryServices
import FoundryData
import SQLite3
import Testing

private let readScope = PendingNotesScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")!
private let readTime: Int64 = 1_800_000_000_000
private func readURL() -> URL { FileManager.default.temporaryDirectory.appending(path: UUID().uuidString).appending(path: "cache.sqlite") }
private func snapshot(_ scope: PendingNotesScope = readScope, at: Int64 = readTime) -> NotesReadSnapshot {
    NotesReadSnapshot(scope: scope, notes: [VersionedNote(id: "00000000-0000-4000-8000-000000000004", title: "Cafe\u{301}\0🧱", version: 1)!], savedAtMilliseconds: at, journal: nil)!
}
private func cacheSQL(_ url: URL, _ sql: String) throws {
    var db: OpaquePointer?; #expect(sqlite3_open(url.path, &db) == SQLITE_OK); defer { sqlite3_close(db) }
    #expect(sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK)
}
@Test func sqliteReadCacheReopensPartitionsExpiresAndForgets() async throws {
    let url = readURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let cache = SQLiteNotesReadCache(url: url)
    _ = try await cache.save(snapshot()).get()
    let reopened = SQLiteNotesReadCache(url: url)
    #expect(try await reopened.load(readScope, nowMilliseconds: readTime).get()?.notes == snapshot().notes)
    let variants = [PendingNotesScope(authorityID: "go.other", principalID: readScope.principalID, accountID: readScope.accountID)!,
        PendingNotesScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000099", accountID: readScope.accountID)!,
        PendingNotesScope(authorityID: "go.test", principalID: readScope.principalID, accountID: "00000000-0000-4000-8000-000000000099")!]
    for other in variants { #expect(try await reopened.load(other, nowMilliseconds: readTime).get() == nil) }
    #expect(try await reopened.load(readScope, nowMilliseconds: readTime + NotesReadSnapshot.lifetimeMilliseconds).get() == nil)
    _ = try await cache.save(snapshot()).get()
    #expect(try await reopened.load(readScope, nowMilliseconds: readTime - 1).get() == nil)
    _ = try await cache.save(snapshot()).get(); _ = try await reopened.remove(readScope).get()
    #expect(try await cache.load(readScope, nowMilliseconds: readTime).get() == nil)
}
@Test func sqliteReadCacheBoundsEvictsAndDoesNotRegress() async throws {
    let url = readURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let cache = SQLiteNotesReadCache(url: url, maxScopes: 1)
    _ = try await cache.save(snapshot()).get(); _ = try await cache.save(snapshot(at: readTime - 1)).get()
    #expect(try await cache.load(readScope, nowMilliseconds: readTime).get()?.savedAtMilliseconds == readTime)
    let other = PendingNotesScope(authorityID: "go.other", principalID: readScope.principalID, accountID: readScope.accountID)!
    _ = try await cache.save(snapshot(other, at: readTime + 1)).get()
    #expect(try await cache.load(readScope, nowMilliseconds: readTime + 1).get() == nil)
    #expect(try await cache.load(other, nowMilliseconds: readTime + 1).get() != nil)
}
@Test func sqliteReadCacheRefusesUnknownCorruptAndJournalFilesWithoutReset() async throws {
    let url = readURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let cache = SQLiteNotesReadCache(url: url); _ = try await cache.save(snapshot()).get()
    try cacheSQL(url, "PRAGMA user_version=99")
    if case .success = try await cache.load(readScope, nowMilliseconds: readTime) { Issue.record("Admitted future schema") }
    try cacheSQL(url, "PRAGMA user_version=1")
    #expect(try await cache.load(readScope, nowMilliseconds: readTime).get() != nil)
    try cacheSQL(url, "UPDATE notes_cache SET saved_at=saved_at+1")
    if case .success = try await cache.load(readScope, nowMilliseconds: readTime) { Issue.record("Admitted inconsistent timestamp") }
    try cacheSQL(url, "UPDATE notes_cache SET payload=x'ff'")
    if case .success = try await cache.load(readScope, nowMilliseconds: readTime) { Issue.record("Admitted corrupt payload") }
    if case .success = try await cache.save(snapshot()) { Issue.record("Silently replaced corrupt payload") }
    if case .success = try await cache.load(readScope, nowMilliseconds: readTime) { Issue.record("Corrupt payload was reset") }
    let corrupt = url.deletingLastPathComponent().appending(path: "corrupt.sqlite"), original = Data("not a database".utf8)
    try original.write(to: corrupt)
    if case .success = try await SQLiteNotesReadCache(url: corrupt).load(readScope, nowMilliseconds: readTime) { Issue.record("Admitted corrupt database") }
    #expect(try Data(contentsOf: corrupt) == original)
    let journalURL = url.deletingLastPathComponent().appending(path: "journal.sqlite")
    let command = try RemoteNoteWrite.create(commandID: UUID().uuidString.lowercased(), title: "Keep me").get()
    _ = try await SQLitePendingNotesStore(url: journalURL).stage(command, scope: readScope, replacing: nil).get()
    if case .success = try await SQLiteNotesReadCache(url: journalURL).remove(readScope) { Issue.record("Opened a journal as cache") }
    #expect(try await SQLitePendingNotesStore(url: journalURL).load(readScope).get()?.matches(command) == true)
}
