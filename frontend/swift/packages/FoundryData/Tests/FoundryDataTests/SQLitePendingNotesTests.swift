import Foundation
import FoundryKernel
import FoundryServices
import FoundryData
import SQLite3
import Testing

private let journalScope = PendingNotesScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")!
private func journalCommand(_ title: String = "Cafe\u{301}\0🧱") throws -> RemoteNoteWrite { try RemoteNoteWrite.create(commandID: UUID().uuidString.lowercased(), title: title).get() }
private func journalURL() -> URL { FileManager.default.temporaryDirectory.appending(path: UUID().uuidString).appending(path: "journal.sqlite") }
private func sql(_ url: URL, _ text: String) throws {
    var db: OpaquePointer?; #expect(sqlite3_open(url.path, &db) == SQLITE_OK); defer { sqlite3_close(db) }
    #expect(sqlite3_exec(db, text, nil, nil, nil) == SQLITE_OK)
}
private func receipt(_ command: RemoteNoteWrite) -> NoteWriteReceipt { NoteWriteReceipt(commandID: command.commandID, accountID: journalScope.accountID, principalID: journalScope.principalID, action: .create, noteID: "00000000-0000-4000-8000-000000000004", title: command.title, version: 1)! }
@Test func sqlitePendingSurvivesReopenAndTerminalCASProtectsNewerIntent() async throws {
    let url = journalURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let first = SQLitePendingNotesStore(url: url); let command = try journalCommand()
    _ = try await first.stage(command, scope: journalScope, replacing: nil).get()
    let reopened = SQLitePendingNotesStore(url: url)
    #expect(try await reopened.load(journalScope).get()?.matches(command) == true)
    let confirmed = try await reopened.confirm(command, scope: journalScope, receipt: receipt(command)).get()
    #expect(confirmed.status == .confirmed)
    #expect(try await SQLitePendingNotesStore(url: url).load(journalScope).get()?.receipt?.confirms(command) == true)
    let next = try journalCommand("Next")
    if case .success = try await first.stage(next, scope: journalScope, replacing: nil) { Issue.record("Missing CAS admitted") }
    _ = try await first.stage(next, scope: journalScope, replacing: command.commandID).get()
    if case .success = try await reopened.confirm(command, scope: journalScope, receipt: receipt(command)) { Issue.record("Stale confirm admitted") }
    #expect(try await reopened.load(journalScope).get()?.matches(next) == true)
}
@Test func sqlitePendingPartitionsEveryScopeDimensionAndEnforcesCapacity() async throws {
    let url = journalURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let store = SQLitePendingNotesStore(url: url, maxScopes: 4); let command = try journalCommand()
    let variants = [journalScope,
        PendingNotesScope(authorityID: "go.other", principalID: journalScope.principalID, accountID: journalScope.accountID)!,
        PendingNotesScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000099", accountID: journalScope.accountID)!,
        PendingNotesScope(authorityID: "go.test", principalID: journalScope.principalID, accountID: "00000000-0000-4000-8000-000000000099")!]
    for scope in variants { #expect(try await store.load(scope).get() == nil); _ = try await store.stage(command, scope: scope, replacing: nil).get() }
    let excess = PendingNotesScope(authorityID: "go.excess", principalID: journalScope.principalID, accountID: journalScope.accountID)!
    if case .success = try await store.stage(command, scope: excess, replacing: nil) { Issue.record("Exceeded capacity") }
    for scope in variants { #expect(try await store.load(scope).get()?.matches(command) == true) }
}
@Test func sqlitePendingRollbackKeepsOriginalAndCorruptStoresAreNeverReset() async throws {
    let url = journalURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let store = SQLitePendingNotesStore(url: url); let command = try journalCommand()
    _ = try await store.stage(command, scope: journalScope, replacing: nil).get()
    try sql(url, "CREATE TRIGGER fail_settle BEFORE UPDATE ON pending_notes BEGIN SELECT RAISE(ABORT,'owned failure'); END")
    if case .success = try await store.confirm(command, scope: journalScope, receipt: receipt(command)) { Issue.record("Failed transaction admitted") }
    #expect(try await store.load(journalScope).get()?.status == .pending)
    try sql(url, "DROP TRIGGER fail_settle; PRAGMA user_version=99")
    if case .success = try await store.load(journalScope) { Issue.record("Future schema admitted") }
    try sql(url, "PRAGMA user_version=1")
    #expect(try await store.load(journalScope).get()?.matches(command) == true)
    try sql(url, "UPDATE pending_notes SET payload=x'ff'")
    if case .success = try await store.load(journalScope) { Issue.record("Corrupt payload admitted") }
    let invalidURL = journalURL(); defer { try? FileManager.default.removeItem(at: invalidURL.deletingLastPathComponent()) }
    try FileManager.default.createDirectory(at: invalidURL.deletingLastPathComponent(), withIntermediateDirectories: true)
    let original = Data("not a database".utf8); try original.write(to: invalidURL)
    if case .success = try await SQLitePendingNotesStore(url: invalidURL).load(journalScope) { Issue.record("Corrupt file admitted") }
    #expect(try Data(contentsOf: invalidURL) == original)
}
@Test func sqlitePendingIndependentInstancesSerializeCompetingStages() async throws {
    let url = journalURL(); defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    let a = SQLitePendingNotesStore(url: url), b = SQLitePendingNotesStore(url: url)
    _ = try await a.load(journalScope).get()
    let c1 = try journalCommand("One"), c2 = try journalCommand("Two")
    async let r1 = a.stage(c1, scope: journalScope, replacing: nil)
    async let r2 = b.stage(c2, scope: journalScope, replacing: nil)
    let results = try await [r1, r2]
    #expect(results.filter { if case .success = $0 { true } else { false } }.count == 1)
    let record = try #require(try await a.load(journalScope).get())
    #expect(record.matches(c1) || record.matches(c2))
}
