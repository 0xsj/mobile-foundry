import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private let pendingScope = PendingNotesScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")!
private let pendingID = "00000000-0000-4000-8000-000000000001"
@Test func pendingRecordSharedAdmissionAndExactRoundTrips() throws {
    struct Row: Decodable { let name: String; let record: JSONValue; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let data = try Data(contentsOf: root.appending(path: "contracts/fixtures/pending-notes/records.json"))
    for row in try JSONDecoder().decode([Row].self, from: data) {
        let answer = PendingNoteRecord.decode(try JSONEncoder().encode(row.record), scope: pendingScope)
        if row.valid {
            let record = try answer.get(); let restored = try PendingNoteRecord.decode(record.encoded(), scope: pendingScope).get()
            #expect(restored.matches(record.command)); #expect(restored.status == record.status)
        } else if case .success = answer { Issue.record("Admitted \(row.name)") }
    }
    let valid = try PendingNoteRecord(scope: pendingScope, command: RemoteNoteWrite.create(commandID: pendingID, title: "Original").get()).encoded()
    let utf16 = String(data: valid, encoding: .utf8)!.data(using: .utf16LittleEndian)!
    for invalid in [Data([0xff]), Data(repeating: 32, count: 16_385), utf16] {
        if case .success = PendingNoteRecord.decode(invalid, scope: pendingScope) { Issue.record("Admitted malformed storage") }
    }
}
@Test func pendingMemoryUsesExactCommandCASAndScopeCapacity() async throws {
    let store = MemoryPendingNotesStore(maxScopes: 1)
    let command = try RemoteNoteWrite.create(commandID: pendingID, title: "Cafe\u{301}\0").get()
    let different = try RemoteNoteWrite.create(commandID: pendingID, title: "Café\0").get()
    #expect(command.title == different.title) // Native semantic equality is unsuitable for command identity.
    let staged = try await store.stage(command, scope: pendingScope, replacing: nil).get()
    #expect(!staged.matches(different)); #expect(try await store.stage(command, scope: pendingScope, replacing: "stale").get().matches(command))
    if case .success = try await store.stage(different, scope: pendingScope, replacing: pendingID) { Issue.record("Changed input admitted") }
    let other = PendingNotesScope(authorityID: "go.other", principalID: pendingScope.principalID, accountID: pendingScope.accountID)!
    #expect(try await store.load(other).get() == nil)
    if case .success = try await store.stage(command, scope: other, replacing: nil) { Issue.record("Exceeded capacity") }
    let next = try RemoteNoteWrite.create(commandID: "00000000-0000-4000-8000-000000000009", title: "Next").get()
    if case .success = try await store.stage(next, scope: pendingScope, replacing: pendingID) { Issue.record("Replaced pending") }
    _ = try await store.reject(command, scope: pendingScope, reason: .versionConflict).get()
    _ = try await store.stage(next, scope: pendingScope, replacing: pendingID).get()
    if case .success = try await store.reject(command, scope: pendingScope, reason: .versionConflict) { Issue.record("Late settlement replaced newer command") }
    #expect(try await store.load(pendingScope).get()?.matches(next) == true)
}
@Test func pendingTerminalReceiptCannotChangeCreatedIdentityOrContradictRefusal() throws {
    let command = try RemoteNoteWrite.create(commandID: pendingID, title: "Original").get()
    let pending = PendingNoteRecord(scope: pendingScope, command: command)
    func receipt(_ note: String) -> NoteWriteReceipt { NoteWriteReceipt(commandID: pendingID, accountID: pendingScope.accountID, principalID: pendingScope.principalID, action: .create, noteID: note, title: command.title, version: 1)! }
    let first = receipt("00000000-0000-4000-8000-000000000004")
    let terminal = try pending.confirming(first).get()
    _ = try terminal.confirming(first).get()
    if case .success = terminal.confirming(receipt("00000000-0000-4000-8000-000000000005")) { Issue.record("Changed created identity") }
    if case .success = terminal.rejecting(.notFound) { Issue.record("Contradicted confirmation") }
    let refused = try pending.rejecting(.notFound).get()
    if case .success = refused.confirming(first) { Issue.record("Contradicted refusal") }
    if case .success = refused.rejecting(.versionConflict) { Issue.record("Changed refusal") }
}
