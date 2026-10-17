import Foundation
import FoundryHTTP
import FoundryKernel

/// One attempt on an injected, exact-lease HTTP client. No renewal or replay.
public struct HTTPNotificationInboxRepository: NotificationInboxRepository {
    private let client: any HTTPClient
    public let accountID: String
    public let principalID: String
    public init(client: any HTTPClient, accountID: String, principalID: String) { self.client = client; self.accountID = accountID; self.principalID = principalID }
    private var admitted: Bool { RemoteServiceAdmission.uuid(accountID) && RemoteServiceAdmission.uuid(principalID) }
    private var path: String { "accounts/\(accountID)/notifications" }
    public func page(cursor: String? = nil, limit: Int = 20, unreadOnly: Bool = false) async throws -> AppResult<NotificationInboxPage> {
        guard admitted, (1...100).contains(limit), cursor == nil || (!cursor!.isEmpty && cursor!.utf8.count <= 512) else { return .failure(InboxAdmission.invalid) }
        var query = [QueryItem("limit", String(limit)), QueryItem("unread", String(unreadOnly))]
        if let cursor { query.append(QueryItem("cursor", cursor)) }
        return try await request(.GET, path, .init(query: query)).flatMap { entity in
            guard entity.metadata.status == 200, let body = entity.body.object, body["account_id"]?.string == accountID,
                  case .array(let rows) = body["items"], rows.count <= limit, case .number(let count) = body["unread_count"],
                  count.isFinite, count >= 0, count <= 9_007_199_254_740_991, count.rounded(.towardZero) == count else { return .failure(invalid(entity)) }
            var items: [InboxNotification] = []; var ids: Set<String> = []
            for row in rows {
                guard let item = decode(row), ids.insert(item.id).inserted, !unreadOnly || item.isUnread else { return .failure(invalid(entity)) }
                items.append(item)
            }
            guard Int64(count) >= Int64(items.filter(\.isUnread).count) else { return .failure(invalid(entity)) }
            let next: String?
            switch body["next_cursor"] {
            case .null: next = nil
            case .string(let value) where !value.isEmpty && value.utf8.count <= 512 && value != cursor && items.count == limit: next = value
            default: return .failure(invalid(entity))
            }
            return .success(.init(items: items, unreadCount: Int64(count), nextCursor: next))
        }
    }
    public func markRead(_ item: InboxNotification) async throws -> AppResult<InboxNotification> {
        guard admitted, item.accountID == accountID, item.principalID == principalID else { return .failure(InboxAdmission.invalid) }
        return try await request(.POST, path + "/\(item.id)/read").flatMap { entity in
            guard entity.metadata.status == 200, let result = decode(entity.body), result.id == item.id, result.fileID == item.fileID,
                  result.occurredAt == item.occurredAt, result.createdAt == item.createdAt, result.readAt != nil,
                  item.readAt == nil || item.readAt == result.readAt else { return .failure(invalid(entity)) }; return .success(result)
        }
    }
    public func mediaEnabled() async throws -> AppResult<Bool> { try await preference(enabled: nil) }
    public func setMediaEnabled(_ enabled: Bool) async throws -> AppResult<Bool> { try await preference(enabled: enabled) }
    private func preference(enabled: Bool?) async throws -> AppResult<Bool> {
        guard admitted else { return .failure(InboxAdmission.invalid) }
        let options = RequestOptions(body: enabled.map { .object(["media_in_app": .bool($0)]) })
        return try await request(enabled == nil ? .GET : .PUT, "accounts/\(accountID)/notification-preferences", options).flatMap { entity in
            guard entity.metadata.status == 200, let body = entity.body.object, body["account_id"]?.string == accountID,
                  case .bool(let value) = body["media_in_app"], enabled == nil || enabled == value else { return .failure(invalid(entity)) }; return .success(value)
        }
    }
    private func request(_ method: HTTPMethod, _ path: String, _ options: RequestOptions = .init()) async throws -> AppResult<Entity> {
        try Task.checkCancellation(); let result = try await client.requestEntity(method, path: path, options: options); try Task.checkCancellation(); return result
    }
    private func decode(_ value: JSONValue) -> InboxNotification? {
        guard let b = value.object, b["type"]?.string == "files.upload_ready", b["category"]?.string == "media",
              let id = b["id"]?.string, let file = b["file_id"]?.string,
              let occurred = RemoteServiceAdmission.timestamp(b["occurred_at"]?.string), let created = RemoteServiceAdmission.timestamp(b["created_at"]?.string) else { return nil }
        let read: Date?
        switch b["read_at"] { case .null: read = nil; case .string(let text): guard let date = RemoteServiceAdmission.timestamp(text) else { return nil }; read = date; default: return nil }
        return InboxNotification(id: id, accountID: accountID, principalID: principalID, fileID: file, occurredAt: occurred, createdAt: created, readAt: read)
    }
    private func invalid(_ e: Entity) -> Failure { .internalError(.init(message: "The remote inbox response was invalid.", code: "notifications.invalid_remote_response", requestID: e.metadata.requestID, correlationID: e.metadata.correlationID)) }
}
