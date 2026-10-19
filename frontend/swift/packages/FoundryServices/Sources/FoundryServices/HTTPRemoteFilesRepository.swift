import Foundation
import FoundryHTTP
import FoundryKernel

/// Account-bound, one-attempt file operations. Compose with an exact session lease.
public struct HTTPRemoteFilesRepository: RemoteFilesRepository, RemoteFileDetailReader {
    private let client: any HTTPClient
    public let accountID: String
    public let principalID: String
    public init(client: any HTTPClient, accountID: String, principalID: String) { self.client = client; self.accountID = accountID; self.principalID = principalID }
    private var admitted: Bool { FilesAdmission.uuid(accountID) && FilesAdmission.uuid(principalID) }
    private var path: String { "accounts/\(accountID)/files" }
    public func get(id: String) async throws -> AppResult<RemoteFile> {
        guard admitted, id.count == 36, FilesAdmission.uuid(id) else { return .failure(FilesAdmission.invalid) }
        return try await request(.GET, path: path + "/\(id)", options: .init()).flatMap { entity in
            guard entity.metadata.status == 200, let file = decode(entity.body), file.id == id,
                  file.status != .deleted, entity.metadata.etag == "\"\(file.version)\"" else { return .failure(invalidResponse) }
            return .success(file)
        }
    }
    public func page(cursor: String? = nil, limit: Int = 20) async throws -> AppResult<FilesPage> {
        guard admitted, (1...100).contains(limit), cursor == nil || (!cursor!.isEmpty && cursor!.count <= 512) else { return .failure(FilesAdmission.invalid) }
        var query = [QueryItem("limit", String(limit))]; if let cursor { query.append(QueryItem("cursor", cursor)) }
        return try await request(.GET, path: path, options: .init(query: query)).flatMap { entity in
            guard entity.metadata.status == 200, let body = entity.body.object, body["account_id"]?.string == accountID,
                  case .array(let rows) = body["files"], rows.count <= limit else { return .failure(invalidResponse) }
            var files: [RemoteFile] = []; var prior = ""
            for row in rows { guard let f = decode(row), f.status != .deleted, f.id > prior else { return .failure(invalidResponse) }; files.append(f); prior = f.id }
            let next: String?
            switch body["next_cursor"] { case .null: next = nil; case .string(let value) where !value.isEmpty && value.count <= 512 && value != cursor && files.count == limit: next = value; default: return .failure(invalidResponse) }
            return .success(FilesPage(files: files, nextCursor: next))
        }
    }
    public func requestUpload(_ spec: UploadSpec, key: String) async throws -> AppResult<FileReceipt> {
        guard admitted, FilesAdmission.key(key) else { return .failure(FilesAdmission.invalid) }
        let body: JSONValue = .object(["name": .string(spec.name), "content_type": .string(spec.contentType), "byte_size": .number(Double(spec.byteSize)), "sha256": .string(spec.sha256)])
        return try await request(.POST, path: path + "/uploads", options: .init(body: body, idempotencyKey: key)).flatMap { entity in
            parseReceipt(entity, key: key, action: "request_upload", status: 201).flatMap { r in
                guard r.file.spec == spec, r.file.status == .pendingUpload, r.file.ownerID == principalID else { return .failure(invalidResponse) }; return .success(r)
            }
        }
    }
    public func uploadContent(_ file: RemoteFile, bytes: Data) async throws -> AppResult<Void> {
        guard admitted, file.accountID == accountID, file.status != .deleted, file.spec.matches(bytes) else { return .failure(FilesAdmission.invalid) }
        return try await request(.PUT, path: path + "/\(file.id)/content", options: .init(binaryBody: BinaryBody(bytes, contentType: file.spec.contentType))).flatMap { entity in
            guard entity.metadata.status == 200, entity.body.object?["status"]?.string == "stored" else { return .failure(invalidResponse) }; return .success(())
        }
    }
    public func complete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt> { try await mutate(file, key: key, remove: false) }
    public func delete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt> { try await mutate(file, key: key, remove: true) }
    private func mutate(_ file: RemoteFile, key: String, remove: Bool) async throws -> AppResult<FileReceipt> {
        guard admitted, FilesAdmission.key(key), file.accountID == accountID, file.status != .deleted, remove || file.status == .pendingUpload else { return .failure(FilesAdmission.invalid) }
        return try await request(remove ? .DELETE : .POST, path: path + "/\(file.id)" + (remove ? "" : "/complete"), options: .init(ifMatch: "\"\(file.version)\"", idempotencyKey: key)).flatMap { entity in
            parseReceipt(entity, key: key, action: remove ? "delete" : "complete_upload", status: 200).flatMap { r in
                guard r.file.id == file.id, r.file.ownerID == file.ownerID, r.file.spec == file.spec, r.file.version == file.version + 1, r.file.status == (remove ? .deleted : .available) else { return .failure(invalidResponse) }; return .success(r)
            }
        }
    }
    public func receipt(key: String) async throws -> AppResult<FileReceipt> {
        guard admitted, FilesAdmission.key(key) else { return .failure(FilesAdmission.invalid) }
        return try await request(.GET, path: "receipts/files/\(key)", options: .init(query: [QueryItem("account_id", accountID)])).flatMap { parseReceipt($0, key: key, action: nil, status: 200) }
    }
    public func download(_ file: RemoteFile) async throws -> AppResult<Data> {
        guard admitted, file.accountID == accountID, file.status == .available else { return .failure(FilesAdmission.invalid) }
        try Task.checkCancellation()
        let result = try await client.requestBytes(.GET, path: path + "/\(file.id)/content", options: .init())
        try Task.checkCancellation()
        return result.flatMap { entity in
            guard entity.metadata.status == 200, entity.metadata.etag == "\"\(file.version)\"", entity.contentType == file.spec.contentType, file.spec.matches(entity.bytes) else { return .failure(invalidResponse) }; return .success(entity.bytes)
        }
    }
    private func request(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        try Task.checkCancellation(); let answer = try await client.requestEntity(method, path: path, options: options); try Task.checkCancellation(); return answer
    }
    private func parseReceipt(_ entity: Entity, key: String, action: String?, status: Int) -> AppResult<FileReceipt> {
        guard entity.metadata.status == status, let b = entity.body.object, b["key"]?.string == key, b["principal_id"]?.string == principalID,
              let actual = b["action"]?.string, ["request_upload", "complete_upload", "delete"].contains(actual), action == nil || action == actual,
              let value = b["file"], let file = decode(value), entity.metadata.etag == "\"\(file.version)\"",
              actual == "request_upload" && file.status == .pendingUpload || actual == "complete_upload" && file.status == .available || actual == "delete" && file.status == .deleted else { return .failure(invalidResponse) }
        return .success(FileReceipt(key: key, action: actual, principalID: principalID, file: file))
    }
    private func decode(_ value: JSONValue) -> RemoteFile? {
        guard let b = value.object, let id = b["id"]?.string, b["account_id"]?.string == accountID, let owner = b["owner_id"]?.string,
              let name = b["name"]?.string, let type = b["content_type"]?.string, let size = integer(b["byte_size"]), let hash = b["sha256"]?.string,
              let spec = UploadSpec(name: name, contentType: type, byteSize: Int(size), sha256: hash), let raw = b["status"]?.string, let status = FileStatus(rawValue: raw), let version = integer(b["version"]) else { return nil }
        return RemoteFile(id: id, accountID: accountID, ownerID: owner, spec: spec, status: status, version: version)
    }
    private func integer(_ value: JSONValue?) -> Int64? { guard case .number(let n) = value, n >= 1, n <= 10 * 1024 * 1024, n.rounded(.towardZero) == n else { return nil }; return Int64(n) }
    private var invalidResponse: Failure { .internalError(.init(message: "The remote Files response was invalid.", code: "files.invalid_remote_response")) }
}
