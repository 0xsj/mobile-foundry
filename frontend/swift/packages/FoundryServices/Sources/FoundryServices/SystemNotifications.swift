import Foundation
import FoundryKernel
import Observation

public enum NotificationAuthorization: String, Sendable { case notGranted, allowed, quiet }
public struct NotificationAccess: Equatable, Sendable {
    public let authorization: NotificationAuthorization
    public let deliveryEnabled: Bool
    public var canPost: Bool { authorization != .notGranted && deliveryEnabled }
    public init(authorization: NotificationAuthorization, deliveryEnabled: Bool) { self.authorization = authorization; self.deliveryEnabled = deliveryEnabled }
}
/// Local preview destination only. This value carries no account, server text or authority.
public struct LocalNotificationPreview: Equatable, Sendable, Identifiable {
    public let id: String
    public init?(id: String) { guard RemoteServiceAdmission.uuid(id) else { return nil }; self.id = id }
    public var payload: [String: String] { ["version":"1", "kind":"local_preview", "preview_id":id] }
    public static func decode(_ payload: [String: String]) -> Self? {
        guard Set(payload.keys) == Set(["version","kind","preview_id"]), payload["version"] == "1", payload["kind"] == "local_preview", let id = payload["preview_id"] else { return nil }
        return Self(id: id)
    }
}
/// UI-executor port; native permission prompts happen only on explicit request.
@MainActor public protocol SystemNotificationPort {
    func access() async throws -> AppResult<NotificationAccess>
    func requestPermission() async throws -> AppResult<NotificationAccess>
    func postPreview(_ preview: LocalNotificationPreview) async throws -> AppResult<Void>
    func clearPreview() async throws -> AppResult<Void>
    func openSettings() async throws -> AppResult<Void>
}
public enum SystemNotificationFailures {
    public static let blocked = Failure.forbidden(.init(message: "Notifications are disabled. Check notification settings, then refresh.", code: "notifications.device_blocked"))
    public static let unavailable = Failure.unavailable(.init(message: "The notification service is unavailable. Refresh before trying again.", code: "notifications.device_unavailable"))
}
/// A bounded, ephemeral inbox for untrusted OS opens. Navigation consumes by exact ID.
@MainActor @Observable public final class NotificationOpenRouter {
    public private(set) var pending: NotificationOpen?
    public private(set) var lastOpened: NotificationOpen?
    public private(set) var lastReceived: RemoteNotificationPreview?
    public var lastOpenedID: String? { lastOpened?.previewID }
    @ObservationIgnored private var recent: [NotificationOpen] = []
    public init() {}
    public func recordReceipt(_ preview: RemoteNotificationPreview) { lastReceived = preview }
    public func receive(_ preview: LocalNotificationPreview) { receive(.local(preview)) }
    public func receive(_ preview: RemoteNotificationPreview) { receive(.remote(preview)) }
    public func receive(_ preview: NotificationOpen) {
        guard !recent.contains(preview) else { return }
        recent.append(preview); if recent.count > 32 { recent.removeFirst() }
        pending = preview
    }
    public func consume(_ preview: NotificationOpen) {
        guard pending == preview else { return }; lastOpened = preview; pending = nil
    }
}
