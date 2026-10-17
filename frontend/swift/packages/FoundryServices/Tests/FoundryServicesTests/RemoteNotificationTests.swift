import Foundation
import Testing
import FoundryServices

@Test func remoteNotificationSharedAdmission() throws {
    struct Row: Decodable { let name: String; let payload: [String: String]; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/system-notifications/remote-payloads.json")))
    for row in rows {
        #expect((RemoteNotificationPreview.decode(row.payload) != nil) == row.valid, "\(row.name)")
        if row.valid { #expect(LocalNotificationPreview.decode(row.payload) == nil) }
    }
}

@Test @MainActor func remoteReceiptDoesNotNavigateAndKindGuardsConsumption() {
    let router = NotificationOpenRouter()
    let remote = RemoteNotificationPreview(id: UUID().uuidString.lowercased())!
    let local = LocalNotificationPreview(id: remote.id)!
    router.recordReceipt(remote)
    #expect(router.pending == nil && router.lastOpened == nil && router.lastReceived == remote)
    router.receive(local); router.receive(remote); router.consume(.local(local))
    #expect(router.pending == .remote(remote))
    router.consume(.remote(remote)); router.receive(remote); router.receive(local)
    #expect(router.pending == nil && router.lastOpened == .remote(remote) && router.lastReceived == remote)
    // Bounded process memory permits an old ID again after eviction, without claiming durable dedupe.
    for _ in 0..<32 { router.receive(RemoteNotificationPreview(id: UUID().uuidString.lowercased())!) }
    router.receive(remote); #expect(router.pending == .remote(remote))
}
