import CryptoKit
import Foundation
import FoundryKernel

public enum FileStatus: String, Sendable { case pendingUpload = "pending_upload", available, deleted }
public struct UploadSpec: Equatable, Sendable {
    public let name: String
    public let contentType: String
    public let byteSize: Int
    public let sha256: String
    public init?(name: String, contentType: String, byteSize: Int, sha256: String) {
        guard name == name.trimmingCharacters(in: .whitespacesAndNewlines), (1...255).contains(name.unicodeScalars.count),
              !name.contains("/"), !name.contains("\\"), name.rangeOfCharacter(from: .controlCharacters) == nil,
              ["image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf", "application/octet-stream"].contains(contentType),
              (1...10 * 1024 * 1024).contains(byteSize), sha256.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else { return nil }
        self.name = name; self.contentType = contentType; self.byteSize = byteSize; self.sha256 = sha256
    }
    public static func prepare(name: String, contentType: String, bytes: Data) -> AppResult<UploadSpec> {
        guard !bytes.isEmpty, bytes.count <= 10 * 1024 * 1024,
              let spec = UploadSpec(name: name.trimmingCharacters(in: .whitespacesAndNewlines), contentType: contentType, byteSize: bytes.count, sha256: digest(bytes)) else { return .failure(FilesAdmission.invalid) }
        return .success(spec)
    }
    public func matches(_ bytes: Data) -> Bool { bytes.count == byteSize && Self.digest(bytes) == sha256 }
    private static func digest(_ bytes: Data) -> String { SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined() }
}
public struct RemoteFile: Equatable, Identifiable, Sendable {
    public let id: String
    public let accountID: String
    public let ownerID: String
    public let spec: UploadSpec
    public let status: FileStatus
    public let version: Int64
    public init?(id: String, accountID: String, ownerID: String, spec: UploadSpec, status: FileStatus, version: Int64) {
        guard FilesAdmission.uuid(id), FilesAdmission.uuid(accountID), FilesAdmission.uuid(ownerID),
              status == .pendingUpload && version == 1 || status == .available && version == 2 || status == .deleted && (version == 2 || version == 3) else { return nil }
        self.id = id; self.accountID = accountID; self.ownerID = ownerID; self.spec = spec; self.status = status; self.version = version
    }
}
public struct FilesPage: Sendable {
    public let files: [RemoteFile]; public let nextCursor: String?
    public init(files: [RemoteFile], nextCursor: String?) { self.files = files; self.nextCursor = nextCursor }
}
public struct FileReceipt: Sendable {
    public let key: String; public let action: String; public let principalID: String; public let file: RemoteFile
    public init(key: String, action: String, principalID: String, file: RemoteFile) { self.key = key; self.action = action; self.principalID = principalID; self.file = file }
}
public protocol RemoteFilesReader: Sendable {
    func page(cursor: String?, limit: Int) async throws -> AppResult<FilesPage>
    func download(_ file: RemoteFile) async throws -> AppResult<Data>
    func receipt(key: String) async throws -> AppResult<FileReceipt>
}
public protocol RemoteFilesWriter: Sendable {
    func requestUpload(_ spec: UploadSpec, key: String) async throws -> AppResult<FileReceipt>
    func uploadContent(_ file: RemoteFile, bytes: Data) async throws -> AppResult<Void>
    func complete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt>
    func delete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt>
}
public protocol RemoteFilesRepository: RemoteFilesReader, RemoteFilesWriter {}
enum FilesAdmission {
    static func uuid(_ value: String) -> Bool { value != "00000000-0000-0000-0000-000000000000" && value.range(of: "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$", options: .regularExpression) != nil }
    static let invalid = Failure.invalid(.init(message: "Check the file specification, command key and account.", code: "files.invalid_input"), fields: [:])
    static func key(_ value: String) -> Bool { value.range(of: "^[A-Za-z0-9._:-]{1,128}$", options: .regularExpression) != nil }
}
