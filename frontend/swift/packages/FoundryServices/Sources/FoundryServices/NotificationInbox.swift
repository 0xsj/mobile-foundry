import Foundation
import FoundryKernel

/// An admitted account/principal snapshot. English display copy belongs to the app.
public struct InboxNotification: Equatable, Identifiable, Sendable {
    public let id: String
    public let accountID: String
    public let principalID: String
    public let fileID: String
    public let occurredAt: Date
    public let createdAt: Date
    public let readAt: Date?
    public var isUnread: Bool { readAt == nil }
    public init?(id: String, accountID: String, principalID: String, fileID: String, occurredAt: Date, createdAt: Date, readAt: Date?) {
        guard [id, accountID, principalID, fileID].allSatisfy(RemoteServiceAdmission.uuid),
              [occurredAt, createdAt].allSatisfy(RemoteServiceAdmission.date), readAt.map(RemoteServiceAdmission.date) ?? true else { return nil }
        self.id = id; self.accountID = accountID; self.principalID = principalID; self.fileID = fileID
        self.occurredAt = occurredAt; self.createdAt = createdAt; self.readAt = readAt
    }
}
public struct NotificationInboxPage: Sendable {
    public let items: [InboxNotification]
    public let unreadCount: Int64
    public let nextCursor: String?
    public init(items: [InboxNotification], unreadCount: Int64, nextCursor: String?) { self.items = items; self.unreadCount = unreadCount; self.nextCursor = nextCursor }
}
public protocol NotificationInboxReader: Sendable {
    func page(cursor: String?, limit: Int, unreadOnly: Bool) async throws -> AppResult<NotificationInboxPage>
    func mediaEnabled() async throws -> AppResult<Bool>
}
public protocol NotificationInboxWriter: Sendable {
    func markRead(_ item: InboxNotification) async throws -> AppResult<InboxNotification>
    func setMediaEnabled(_ enabled: Bool) async throws -> AppResult<Bool>
}
public protocol NotificationInboxRepository: NotificationInboxReader, NotificationInboxWriter {}

enum InboxAdmission {
    static let invalid = Failure.invalid(.init(message: "Check the inbox account, item and page request.", code: "notifications.invalid_input"), fields: [:])
}
