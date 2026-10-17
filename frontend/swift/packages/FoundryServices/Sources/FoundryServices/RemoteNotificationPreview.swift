import Foundation

/// A generic navigation hint, never session/account authority or server-rendered content.
public struct RemoteNotificationPreview: Equatable, Sendable, Identifiable {
    public let id: String
    public init?(id: String) { guard RemoteServiceAdmission.uuid(id) else { return nil }; self.id = id }
    public var payload: [String: String] { ["version": "1", "kind": "push_preview", "preview_id": id] }
    public static func decode(_ payload: [String: String]) -> Self? {
        guard Set(payload.keys) == Set(["version", "kind", "preview_id"]), payload["version"] == "1",
              payload["kind"] == "push_preview", let id = payload["preview_id"] else { return nil }
        return Self(id: id)
    }
}

/// Kind participates in identity: a remote preview cannot consume/deduplicate a local one.
public enum NotificationOpen: Equatable, Sendable, Identifiable {
    case local(LocalNotificationPreview)
    case remote(RemoteNotificationPreview)
    public var previewID: String { switch self { case .local(let value): value.id; case .remote(let value): value.id } }
    public var isRemote: Bool { if case .remote = self { true } else { false } }
    public var id: String { (isRemote ? "push_preview:" : "local_preview:") + previewID }
}
