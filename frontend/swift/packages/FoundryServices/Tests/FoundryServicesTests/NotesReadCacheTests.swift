import Foundation
import FoundryHTTP
import FoundryServices
import Testing

private let cacheScope = PendingNotesScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")!
@Test func readCacheSharedAdmissionAndJournalCheckpoint() throws {
    struct Row: Decodable { let name: String; let snapshot: JSONValue; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appending(path: "contracts/fixtures/notes-read-cache/snapshots.json")))
    for row in rows {
        let answer = NotesReadSnapshot.decode(try JSONEncoder().encode(row.snapshot), scope: cacheScope)
        if row.valid {
            let value = try answer.get(); let decoded = try NotesReadSnapshot.decode(value.encoded(), scope: cacheScope).get()
            #expect(decoded.notes == value.notes && decoded.savedAtMilliseconds == value.savedAtMilliseconds)
        } else if case .success = answer { Issue.record("Admitted \(row.name)") }
    }
    let command = try RemoteNoteWrite.create(commandID: "00000000-0000-4000-8000-000000000001", title: "Original").get()
    let pending = PendingNoteRecord(scope: cacheScope, command: command)
    let value = NotesReadSnapshot(scope: cacheScope, notes: [], savedAtMilliseconds: 1_800_000_000_000, journal: pending)!
    let restored = try NotesReadSnapshot.decode(value.encoded(), scope: cacheScope).get()
    #expect(restored.matches(pending)); #expect(!restored.matches(nil))
    #expect(!restored.matches(try pending.rejecting(.notFound).get()))
    for invalid in [Data([0xff]), Data(repeating: 32, count: 65_537), try value.encoded().base64EncodedString().data(using: .utf16)!] {
        if case .success = NotesReadSnapshot.decode(invalid, scope: cacheScope) { Issue.record("Admitted invalid storage") }
    }
}
@Test func memoryReadCacheExpiresEvictsAndNeverRegresses() async throws {
    let cache = MemoryNotesReadCache(maxScopes: 1); let at: Int64 = 1_800_000_000_000
    func snapshot(_ scope: PendingNotesScope, _ time: Int64) -> NotesReadSnapshot { NotesReadSnapshot(scope: scope, notes: [], savedAtMilliseconds: time, journal: nil)! }
    _ = try await cache.save(snapshot(cacheScope, at)).get()
    _ = try await cache.save(snapshot(cacheScope, at - 1)).get()
    #expect(try await cache.load(cacheScope, nowMilliseconds: at).get()?.savedAtMilliseconds == at)
    let other = PendingNotesScope(authorityID: "go.other", principalID: cacheScope.principalID, accountID: cacheScope.accountID)!
    #expect(try await cache.load(other, nowMilliseconds: at).get() == nil)
    _ = try await cache.save(snapshot(other, at + 1)).get()
    #expect(try await cache.load(cacheScope, nowMilliseconds: at + 1).get() == nil)
    #expect(try await cache.load(other, nowMilliseconds: at + 1 + NotesReadSnapshot.lifetimeMilliseconds).get() == nil)
    _ = try await cache.save(snapshot(cacheScope, at)).get()
    #expect(try await cache.load(cacheScope, nowMilliseconds: at - 1).get() == nil)
    _ = try await cache.save(snapshot(cacheScope, at)).get(); _ = try await cache.remove(cacheScope).get()
    #expect(try await cache.load(cacheScope, nowMilliseconds: at).get() == nil)
}
