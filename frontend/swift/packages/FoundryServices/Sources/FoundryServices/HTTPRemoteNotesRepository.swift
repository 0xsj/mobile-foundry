import Foundation
import FoundryHTTP
import FoundryKernel

/// One attempt, no renewal/replay. Compose with a client bound to the exact session lease.
public struct HTTPRemoteNotesRepository: RemoteNotesRepository {
    private let client: any HTTPClient
    public let accountID: String
    public let principalID: String
    public init(client: any HTTPClient, accountID: String, principalID: String) { self.client = client; self.accountID = accountID; self.principalID = principalID }
    private var admitted: Bool { RemoteNotesAdmission.uuid(accountID) && RemoteNotesAdmission.uuid(principalID) }
    private var path: String { "accounts/\(accountID)/notes" }
    public func page(after: String? = nil, limit: Int = 20) async throws -> AppResult<RemoteNotesPage> {
        guard admitted, (1...100).contains(limit), after == nil || RemoteNotesAdmission.uuid(after!) else { return .failure(RemoteNotesAdmission.invalid) }
        var query = [QueryItem("limit", String(limit))]; if let after { query.append(QueryItem("after", after)) }
        return try await request(.GET, path: path, options: .init(query: query)).flatMap { entity in
            guard entity.metadata.status == 200, let body = entity.body.object, body["account_id"]?.string == accountID, case .array(let rows) = body["notes"], rows.count <= limit else { return .failure(failure(entity)) }
            var notes: [VersionedNote] = []; var prior = after ?? ""
            for row in rows { guard let value = note(row), value.id > prior else { return .failure(failure(entity)) }; notes.append(value); prior = value.id }
            let next: String?
            switch body["next_after"] { case .null: next = nil; case .string(let value) where notes.count == limit && value == prior: next = value; default: return .failure(failure(entity)) }
            return .success(RemoteNotesPage(notes: notes, nextAfter: next))
        }
    }
    public func get(_ noteID: String) async throws -> AppResult<VersionedNote> {
        guard admitted, RemoteNotesAdmission.uuid(noteID) else { return .failure(RemoteNotesAdmission.invalid) }
        return try await request(.GET, path: path + "/" + noteID, options: .init()).flatMap { entity in
            guard entity.metadata.status == 200, let n = note(entity.body), n.id == noteID, entity.metadata.etag == "\"\(n.version)\"" else { return .failure(failure(entity)) }; return .success(n)
        }
    }
    public func execute(_ command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> {
        guard admitted else { return .failure(RemoteNotesAdmission.invalid) }
        let method: HTTPMethod = command.action == .create ? .POST : command.action == .update ? .PATCH : .DELETE
        let target = path + (command.noteID.map { "/" + $0 } ?? "")
        let body: JSONValue? = command.action == .delete ? nil : .object(["title": .string(command.title)])
        let options = RequestOptions(body: body, ifMatch: command.expectedVersion.map { "\"\($0)\"" }, idempotencyKey: command.commandID)
        return try await request(method, path: target, options: options).flatMap { entity in decodeReceipt(entity, command: command, status: command.action == .create ? 201 : 200) }
    }
    public func receipt(for command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> {
        guard admitted else { return .failure(RemoteNotesAdmission.invalid) }
        return try await request(.GET, path: "accounts/\(accountID)/note-receipts/\(command.commandID)", options: .init()).flatMap { entity in decodeReceipt(entity, command: command, status: 200) }
    }
    private func request(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        try Task.checkCancellation()
        let result = try await client.requestEntity(method, path: path, options: options)
        try Task.checkCancellation()
        return result
    }
    private func decodeReceipt(_ entity: Entity, command: RemoteNoteWrite, status: Int) -> AppResult<NoteWriteReceipt> {
        guard entity.metadata.status == status, let b = entity.body.object,
              let commandID = b["command_id"]?.string, let account = b["account_id"]?.string, let principal = b["principal_id"]?.string,
              let raw = b["action"]?.string, let action = NoteWriteAction(rawValue: raw), let id = b["note_id"]?.string, let title = b["title"]?.string, let version = number(b["version"]),
              let receipt = NoteWriteReceipt(commandID: commandID, accountID: account, principalID: principal, action: action, noteID: id, title: title, version: version),
              account == accountID, principal == principalID, receipt.confirms(command), entity.metadata.etag == "\"\(version)\"" else { return .failure(failure(entity)) }
        return .success(receipt)
    }
    private func note(_ value: JSONValue) -> VersionedNote? { guard let b = value.object, let id = b["id"]?.string, let title = b["title"]?.string, let version = number(b["version"]) else { return nil }; return VersionedNote(id: id, title: title, version: version) }
    private func number(_ value: JSONValue?) -> Int64? { guard case .number(let n) = value, n >= 1, n <= Double(RemoteNotesAdmission.maxVersion), n.rounded(.towardZero) == n else { return nil }; return Int64(n) }
    private func failure(_ entity: Entity) -> Failure { .internalError(.init(message: "The remote Notes response was invalid.", code: "notes.invalid_remote_response", requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID)) }
}
