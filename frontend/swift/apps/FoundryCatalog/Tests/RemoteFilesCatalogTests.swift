@testable import FoundryCatalog
import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Testing

private actor FileRepositoryStub: RemoteFilesRepository {
    let account: SessionScope
    var keys: [String] = []
    var bytes = Data()
    init(_ account: SessionScope) { self.account = account }
    func page(cursor: String?, limit: Int) async throws -> AppResult<FilesPage> { .success(FilesPage(files: [], nextCursor: nil)) }
    func requestUpload(_ spec: UploadSpec, key: String) async throws -> AppResult<FileReceipt> {
        keys.append(key)
        if keys.count == 1 { return .failure(.unavailable(.init(message: "Acknowledgement lost"))) }
        let file = RemoteFile(id: "00000000-0000-4000-8000-000000000004", accountID: account.accountID, ownerID: account.principalID, spec: spec, status: .pendingUpload, version: 1)!
        return .success(FileReceipt(key: key, action: "request_upload", principalID: account.principalID, file: file))
    }
    func uploadContent(_ file: RemoteFile, bytes: Data) async throws -> AppResult<Void> { self.bytes = bytes; return .success(()) }
    func complete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt> {
        .success(FileReceipt(key: key, action: "complete_upload", principalID: account.principalID, file: RemoteFile(id: file.id, accountID: file.accountID, ownerID: file.ownerID, spec: file.spec, status: .available, version: 2)!))
    }
    func delete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt> { .failure(.unavailable(.init(message: "Unused"))) }
    func download(_ file: RemoteFile) async throws -> AppResult<Data> { .success(bytes) }
    func receipt(key: String) async throws -> AppResult<FileReceipt> { .failure(.notFound(.init(message: "Unused"))) }
}
@Suite @MainActor struct RemoteFilesCatalogTests {
    private var account: SessionScope { SessionScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")! }
    private func idle(_ model: RemoteFilesCatalogModel) async throws {
        for _ in 0..<200 { if !model.busy { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Files owner did not settle")
    }
    @Test func retryRetainsExactBytesAndKeyThroughRenewal() async throws {
        let repo = FileRepositoryStub(account); let model = RemoteFilesCatalogModel(); model.bind(repo, scope: account); try await idle(model)
        let bytes = Data([0, 255, 1, 128]); let spec = try UploadSpec.prepare(name: "photo.png", contentType: "image/png", bytes: bytes).get()
        model.upload(spec: spec, bytes: bytes, ticket: model.selectionTicket); try await idle(model)
        #expect(model.pending && !model.canUpload)
        model.bind(nil, scope: nil); model.bind(repo, scope: account); try await idle(model)
        #expect(model.pending); model.retry(); try await idle(model)
        let keys = await repo.keys; #expect(keys.count == 2 && keys[0] == keys[1])
        #expect(await repo.bytes == bytes); #expect(!model.pending && model.canUpload)
    }
    @Test func stalePickerResultCannotUploadToReplacementScope() async throws {
        let repo = FileRepositoryStub(account); let model = RemoteFilesCatalogModel(); model.bind(repo, scope: account); try await idle(model)
        let ticket = model.selectionTicket
        let other = SessionScope(authorityID: account.authorityID, principalID: account.principalID, accountID: "00000000-0000-4000-8000-000000000099")!
        let replacement = FileRepositoryStub(other); model.bind(replacement, scope: other); try await idle(model)
        let bytes = Data([1]); let spec = try UploadSpec.prepare(name: "photo.png", contentType: "image/png", bytes: bytes).get()
        model.upload(spec: spec, bytes: bytes, ticket: ticket); try await idle(model)
        #expect(await repo.keys.isEmpty); #expect(await replacement.keys.isEmpty); #expect(!model.pending)
    }
}
