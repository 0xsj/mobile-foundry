import Foundation
import Testing
import FoundryKernel
import FoundryServices

@Test func systemNotificationPayloadsUseSharedAdmission() throws {
    struct Row: Decodable { let name: String; let payload: [String:String]; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self,from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/system-notifications/payloads.json")))
    for row in rows { #expect((LocalNotificationPreview.decode(row.payload) != nil) == row.valid, "\(row.name)") }
}
@Test @MainActor func systemNotificationOpenConsumesOnlyCurrentIntentAndDeduplicates() {
    let router = NotificationOpenRouter()
    let first = LocalNotificationPreview(id: UUID().uuidString.lowercased())!, next = LocalNotificationPreview(id: UUID().uuidString.lowercased())!
    router.receive(first); router.receive(next); router.consume(.local(first))
    #expect(router.pending == .local(next) && router.lastOpenedID == nil)
    router.consume(.local(next)); router.receive(first); router.receive(next)
    #expect(router.pending == nil && router.lastOpenedID == next.id)
}
@MainActor private final class DeviceNotificationProbe: SystemNotificationPort {
    var permissionRequests = 0; var posted = 0
    var allowed = false; var blockedOnPost = false
    var held: CheckedContinuation<AppResult<NotificationAccess>, Never>?
    var holdRead = false
    func access() async throws -> AppResult<NotificationAccess> {
        if holdRead { return await withCheckedContinuation { held = $0 } }
        return .success(.init(authorization: allowed ? .allowed : .notGranted, deliveryEnabled: allowed))
    }
    func requestPermission() async throws -> AppResult<NotificationAccess> { permissionRequests += 1; allowed = true; return try await access() }
    func postPreview(_ preview: LocalNotificationPreview) async throws -> AppResult<Void> {
        posted += 1; return blockedOnPost ? .failure(SystemNotificationFailures.blocked) : .success(())
    }
    func clearPreview() async throws -> AppResult<Void> { .success(()) }
    func openSettings() async throws -> AppResult<Void> { .success(()) }
}
@MainActor private func settleNotifications(_ model: SystemNotificationController) async {
    for _ in 0..<1000 { if !model.busy { return }; await Task.yield() }; Issue.record("Notification owner did not settle")
}
@Test @MainActor func systemNotificationPermissionIsExplicitAndPostingNeverRetries() async {
    let port = DeviceNotificationProbe()
    let owner = SystemNotificationController(port: port, onUnexpectedError: { _ in Issue.record("Unexpected defect") })
    owner.refresh(); await settleNotifications(owner); #expect(port.permissionRequests == 0)
    owner.postPreview(); #expect(port.posted == 0)
    owner.requestPermission(); await settleNotifications(owner); #expect(owner.canPost && port.permissionRequests == 1)
    port.blockedOnPost = true; owner.postPreview(); await settleNotifications(owner)
    #expect(owner.failure == SystemNotificationFailures.blocked && !owner.canPost && owner.lastAcceptedID == nil && port.posted == 1)
    owner.postPreview(); #expect(port.posted == 1)
    owner.refresh(); await settleNotifications(owner); port.blockedOnPost = false
    owner.postPreview(); await settleNotifications(owner); #expect(owner.lastAcceptedID != nil && port.posted == 2)
    owner.clearPreview(); await settleNotifications(owner); #expect(owner.lastAcceptedID == nil)
}
@Test @MainActor func systemNotificationClosedOwnerIgnoresLateNativeResponse() async {
    let port = DeviceNotificationProbe(); port.holdRead = true
    let owner = SystemNotificationController(port: port, onUnexpectedError: { _ in Issue.record("Unexpected defect") })
    owner.refresh()
    for _ in 0..<1000 { if port.held != nil { break }; await Task.yield() }
    owner.close(); port.held?.resume(returning: .success(.init(authorization: .allowed,deliveryEnabled: true))); port.held = nil
    await Task.yield(); #expect(owner.access == nil && !owner.busy && !owner.canPost)
}
