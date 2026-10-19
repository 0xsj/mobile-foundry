import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

@Test func pendingFilesSharedAdmissionAndExactRoundTrips() throws {
    struct Row: Decodable { let name: String; let metadata: JSONValue; let content: String; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appending(path: "contracts/fixtures/pending-files/records.json")))
    #expect(rows.count == 14)
    for row in rows {
        let bytes = try #require(Data(base64Encoded: row.content))
        let result = PendingFileIntent.decode(metadata: try JSONEncoder().encode(row.metadata), bytes: bytes)
        if row.valid {
            let original = try result.get()
            #expect(try PendingFileIntent.decode(metadata: original.metadata(), bytes: original.bytes).get().matches(original))
        } else if case .success = result { Issue.record("Admitted \(row.name)") }
    }
    for data in [Data([0xff]), Data(repeating: 32, count: 16_385)] {
        if case .success = PendingFileIntent.decode(metadata: data, bytes: Data()) { Issue.record("Admitted invalid metadata") }
    }
}
@Test func pendingFilesMemoryExactGlobalSlotAndStaleRemove() async throws {
    let scope = PendingFileScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")!
    let key = "00000000-0000-4000-8000-000000000001", bytes = Data([0,255,1])
    let first = PendingFileIntent.upload(scope: scope, key: key, spec: try UploadSpec.prepare(name: "Cafe\u{301}.png", contentType: "image/png", bytes: bytes).get(), bytes: bytes)!
    let changed = PendingFileIntent.upload(scope: scope, key: key, spec: try UploadSpec.prepare(name: "Café.png", contentType: "image/png", bytes: bytes).get(), bytes: bytes)!
    #expect(first.spec.name == changed.spec.name && !first.matches(changed))
    let store = MemoryPendingFilesStore(); _ = try await store.stage(first).get(); _ = try await store.stage(first).get()
    if case .success = try await store.stage(changed) { Issue.record("Normalized original input") }
    let otherScope = PendingFileScope(authorityID: "go.other", principalID: scope.principalID, accountID: scope.accountID)!
    let other = PendingFileIntent.upload(scope: otherScope, key: key, spec: first.spec, bytes: bytes)!
    if case .success = try await store.stage(other) { Issue.record("Exceeded the global slot") }
    _ = try await store.remove(first).get(); _ = try await store.remove(first).get(); _ = try await store.stage(other).get()
    if case .success = try await store.remove(first) { Issue.record("Erased newer intent") }
    #expect(try await store.load().get()?.matches(other) == true)
}
