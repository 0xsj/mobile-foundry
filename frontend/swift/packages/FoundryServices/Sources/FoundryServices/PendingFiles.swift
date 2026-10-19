import Foundation
import FoundryHTTP
import FoundryKernel

/// Original deployment/actor/account, independent of expiring credentials.
public struct PendingFileScope: Equatable, Sendable {
    public let authorityID: String
    public let principalID: String
    public let accountID: String
    public init?(authorityID: String, principalID: String, accountID: String) {
        guard !authorityID.isEmpty, authorityID.utf8.count <= 200,
              authorityID.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || [45,46,95].contains($0) }),
              principalID.utf8.count == 36, accountID.utf8.count == 36,
              FilesAdmission.uuid(principalID), FilesAdmission.uuid(accountID) else { return nil }
        self.authorityID = authorityID; self.principalID = principalID; self.accountID = accountID
    }
}

/// One original command plus bounded immutable bytes. Metadata never contains credentials.
public struct PendingFileIntent: Sendable {
    public let scope: PendingFileScope
    public let key: String
    public let spec: UploadSpec
    public let file: RemoteFile?
    public let bytes: Data
    public var deleting: Bool { file != nil }
    public static func upload(scope: PendingFileScope, key: String, spec: UploadSpec, bytes: Data) -> Self? {
        guard key.utf8.count == 36, FilesAdmission.uuid(key), spec.matches(bytes) else { return nil }
        return .init(scope: scope, key: key, spec: spec, file: nil, bytes: bytes)
    }
    public static func delete(scope: PendingFileScope, key: String, file: RemoteFile) -> Self? {
        guard key.utf8.count == 36, FilesAdmission.uuid(key), file.id.utf8.count == 36, file.spec.sha256.utf8.count == 64,
              file.accountID == scope.accountID, file.ownerID == scope.principalID,
              file.status != .deleted else { return nil }
        return .init(scope: scope, key: key, spec: file.spec, file: file, bytes: Data())
    }
    public func matches(_ other: Self) -> Bool {
        // Swift's semantic String equality normalizes Unicode; persisted intent must not.
        (try? metadata()) == (try? other.metadata()) && bytes == other.bytes
    }
    public func metadata() throws -> Data {
        let file: JSONValue = self.file.map { .object(["id": .string($0.id), "status": .string($0.status.rawValue), "version": .number(Double($0.version))]) } ?? .null
        let value = JSONValue.object([
            "format": .string("pending-files.v1"), "key": .string(key), "action": .string(deleting ? "delete" : "upload"),
            "scope": .object(["authority_id": .string(scope.authorityID), "principal_id": .string(scope.principalID), "account_id": .string(scope.accountID)]),
            "spec": .object(["name": .string(spec.name), "content_type": .string(spec.contentType), "byte_size": .number(Double(spec.byteSize)), "sha256": .string(spec.sha256)]), "file": file
        ])
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]; return try encoder.encode(value)
    }
    public static func decode(metadata: Data, bytes: Data) -> AppResult<Self> {
        guard !metadata.isEmpty, metadata.count <= 16_384, bytes.count <= 10 * 1024 * 1024,
              String(data: metadata, encoding: .utf8) != nil,
              let root = (try? JSONDecoder().decode(JSONValue.self, from: metadata))?.object,
              Set(root.keys) == ["format", "key", "action", "scope", "spec", "file"], root["format"]?.string == "pending-files.v1",
              let key = root["key"]?.string, let s = root["scope"]?.object, Set(s.keys) == ["authority_id", "principal_id", "account_id"],
              let authority = s["authority_id"]?.string, let principal = s["principal_id"]?.string, let account = s["account_id"]?.string,
              let scope = PendingFileScope(authorityID: authority, principalID: principal, accountID: account),
              let p = root["spec"]?.object, Set(p.keys) == ["name", "content_type", "byte_size", "sha256"],
              let name = p["name"]?.string, let type = p["content_type"]?.string, let size = integer(p["byte_size"], maximum: 10 * 1024 * 1024), let digest = p["sha256"]?.string,
              let spec = UploadSpec(name: name, contentType: type, byteSize: size, sha256: digest) else { return .failure(PendingFilesFailure.invalid) }
        let value: Self?
        switch root["action"]?.string {
        case "upload":
            guard root["file"] == .null else { return .failure(PendingFilesFailure.invalid) }
            value = upload(scope: scope, key: key, spec: spec, bytes: bytes)
        case "delete":
            guard bytes.isEmpty, let f = root["file"]?.object, Set(f.keys) == ["id", "status", "version"],
                  let id = f["id"]?.string, let status = f["status"]?.string.flatMap(FileStatus.init(rawValue:)),
                  let version = integer(f["version"], maximum: 2), let file = RemoteFile(id: id, accountID: account, ownerID: principal, spec: spec, status: status, version: Int64(version)) else { return .failure(PendingFilesFailure.invalid) }
            value = delete(scope: scope, key: key, file: file)
        default: value = nil
        }
        return value.map(AppResult.success) ?? .failure(PendingFilesFailure.invalid)
    }
    private static func integer(_ value: JSONValue?, maximum: Int) -> Int? {
        guard case .number(let value) = value, value.isFinite, value >= 1, value <= Double(maximum), value.rounded(.towardZero) == value else { return nil }; return Int(value)
    }
}

/// One global slot per product installation. Compare exact input on stage/remove;
/// transactions contain local state only, never HTTP. Remove is deliberately idempotent when absent.
public protocol PendingFilesStore: Sendable {
    func load() async throws -> AppResult<PendingFileIntent?>
    func stage(_ intent: PendingFileIntent) async throws -> AppResult<Void>
    func remove(_ intent: PendingFileIntent) async throws -> AppResult<Void>
}
public actor MemoryPendingFilesStore: PendingFilesStore {
    private var intent: PendingFileIntent?
    public init() {}
    public func load() async throws -> AppResult<PendingFileIntent?> { .success(intent) }
    public func stage(_ value: PendingFileIntent) async throws -> AppResult<Void> {
        if let intent, !intent.matches(value) { return .failure(PendingFilesFailure.conflict) }; intent = value; return .success(())
    }
    public func remove(_ value: PendingFileIntent) async throws -> AppResult<Void> {
        if let intent, !intent.matches(value) { return .failure(PendingFilesFailure.conflict) }; intent = nil; return .success(())
    }
}
public enum PendingFilesFailure {
    public static let invalid = Failure.unavailable(.init(message: "File recovery data is unreadable. It has been kept; new file changes are blocked.", code: "pending_files.invalid_store"))
    public static let schema = Failure.unavailable(.init(message: "File recovery needs a supported store version. Existing data has been kept.", code: "pending_files.schema"))
    public static let storage = Failure.unavailable(.init(message: "File recovery could not be saved or loaded. Retry without resetting its store.", code: "pending_files.storage"))
    public static let conflict = Failure.conflict(.init(message: "Another original file operation is retained. Reload recovery before changing it.", code: "pending_files.conflict"))
}
