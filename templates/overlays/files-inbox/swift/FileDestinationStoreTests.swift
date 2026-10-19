@testable import ProductApp
import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Testing

private actor DestinationReaderStub: RemoteFileDetailReader {
    var response: AppResult<RemoteFile>
    let payload: Data
    let holdRead: Bool
    let holdDownload: Bool
    var reads = 0
    var downloads = 0
    var readGate: CheckedContinuation<Void, Never>?
    var downloadGate: CheckedContinuation<Void, Never>?
    init(_ response: AppResult<RemoteFile>, payload: Data = Data([1, 2, 3]), holdRead: Bool = false, holdDownload: Bool = false) {
        self.response = response; self.payload = payload; self.holdRead = holdRead; self.holdDownload = holdDownload
    }
    func get(id: String) async throws -> AppResult<RemoteFile> {
        reads += 1
        if holdRead { await withCheckedContinuation { readGate = $0 } }
        return response
    }
    func download(_ file: RemoteFile) async throws -> AppResult<Data> {
        downloads += 1
        if holdDownload { await withCheckedContinuation { downloadGate = $0 } }
        return .success(payload)
    }
    func held(_ download: Bool = false) -> Bool { download ? downloadGate != nil : readGate != nil }
    func release() { readGate?.resume(); readGate = nil; downloadGate?.resume(); downloadGate = nil }
}
@Suite @MainActor struct FileDestinationStoreTests {
    private var scope: SessionScope { SessionScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")! }
    private func notice(account: String? = nil, principal: String? = nil) -> InboxNotification {
        InboxNotification(id: "00000000-0000-4000-8000-000000000001", accountID: account ?? scope.accountID, principalID: principal ?? scope.principalID,
            fileID: "00000000-0000-4000-8000-000000000004", occurredAt: Date(timeIntervalSince1970: 1_000), createdAt: Date(timeIntervalSince1970: 1_001), readAt: nil)!
    }
    private func file(id: String? = nil, account: String? = nil, status: FileStatus = .available) throws -> RemoteFile {
        let spec = try UploadSpec.prepare(name: "Actual.png", contentType: "image/png", bytes: Data([1, 2, 3])).get()
        return RemoteFile(id: id ?? notice().fileID, accountID: account ?? scope.accountID, ownerID: scope.principalID, spec: spec, status: status,
            version: status == .pendingUpload ? 1 : status == .available ? 2 : 3)!
    }
    private func idle(_ model: FileDestinationStore) async throws {
        for _ in 0..<200 { if !model.busy { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Detail did not settle")
    }
    private func held(_ reader: DestinationReaderStub, download: Bool = false) async throws {
        for _ in 0..<200 { if await reader.held(download) { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Detail read was not held")
    }
    @Test func scopedOpenReadsMetadataBeforeExplicitVerifiedDownload() async throws {
        let reader = DestinationReaderStub(.success(try file())); let model = FileDestinationStore()
        model.bind(reader, scope: scope); #expect(await reader.reads == 0)
        model.open(notice(account: "00000000-0000-4000-8000-000000000099")); #expect(!model.presented)
        model.open(notice(principal: "00000000-0000-4000-8000-000000000099")); #expect(!model.presented)
        model.open(notice()); try await idle(model)
        #expect(model.file?.spec.name == "Actual.png" && model.bytes == nil && model.failure == nil)
        #expect(await reader.reads == 1); #expect(await reader.downloads == 0)
        model.download(); try await idle(model); #expect(model.bytes == Data([1, 2, 3]))
        model.close(); #expect(!model.presented && model.file == nil && model.bytes == nil)
    }
    @Test func sameScopeRenewalAndReplacementRejectNoncooperativeMetadata() async throws {
        let old = DestinationReaderStub(.success(try file()), holdRead: true); let model = FileDestinationStore()
        model.bind(old, scope: scope); model.open(notice()); try await held(old)
        let replacement = DestinationReaderStub(.success(try file(status: .pendingUpload)))
        model.bind(replacement, scope: scope); #expect(!model.presented)
        model.open(notice()); try await idle(model); await old.release(); try await Task.sleep(for: .milliseconds(20))
        #expect(model.file?.status == .pendingUpload && !model.busy)
        model.download(); #expect(await replacement.downloads == 0)
        model.bind(nil, scope: nil); #expect(!model.presented && model.file == nil)
    }
    @Test func missingDeniedAndMismatchedMetadataRemainDistinctFailures() async throws {
        let model = FileDestinationStore()
        let failures: [Failure] = [.notFound(.init(message: "Missing", code: "files.not_found")), .forbidden(.init(message: "Denied"))]
        for failure in failures {
            model.bind(DestinationReaderStub(.failure(failure)), scope: scope); model.open(notice()); try await idle(model)
            #expect(model.failure?.kind == failure.kind && model.file == nil && model.bytes == nil)
        }
        for value in [try file(id: "00000000-0000-4000-8000-000000000099"), try file(account: "00000000-0000-4000-8000-000000000099"), try file(status: .deleted)] {
            model.bind(DestinationReaderStub(.success(value)), scope: scope); model.open(notice()); try await idle(model)
            #expect(model.file == nil && model.failure?.meta.code == "files.invalid_remote_response")
        }
    }
    @Test func closedDownloadCannotRepublishAndCorruptBytesNeverPreview() async throws {
        let reader = DestinationReaderStub(.success(try file()), holdDownload: true); let model = FileDestinationStore()
        model.bind(reader, scope: scope); model.open(notice()); try await idle(model); model.download(); try await held(reader, download: true)
        model.close(); await reader.release(); try await Task.sleep(for: .milliseconds(20))
        #expect(!model.presented && model.bytes == nil && model.file == nil)
        let corrupt = DestinationReaderStub(.success(try file()), payload: Data([9]))
        model.bind(corrupt, scope: scope); model.open(notice()); try await idle(model); model.download(); try await idle(model)
        #expect(model.bytes == nil && model.failure?.meta.code == "files.invalid_remote_response")
    }
}
