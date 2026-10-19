@testable import ProductApp
import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Testing

private actor FilesRepositoryStub: RemoteFilesRepository {
    let scope: SessionScope
    let duplicate: Bool
    var held: CheckedContinuation<AppResult<FilesPage>, Never>?
    var hold: Bool
    var completion: FileReceipt?
    var requestKeys: [String] = []
    var deleteKeys: [String] = []
    var puts = 0
    var lostComplete = true
    var bytes = Data([0, 255, 1])
    var removed = false
    var deleteConflict = false
    func conflictOnDelete() { deleteConflict = true }
    func holdPages() { hold = true }
    init(_ scope: SessionScope, hold: Bool = false, duplicate: Bool = false) { self.scope = scope; self.hold = hold; self.duplicate = duplicate }
    var spec: UploadSpec { try! UploadSpec.prepare(name: "photo.png", contentType: "image/png", bytes: bytes).get() }
    func file(_ status: FileStatus = .available) -> RemoteFile { RemoteFile(id: "00000000-0000-4000-8000-000000000001", accountID: scope.accountID, ownerID: scope.principalID, spec: spec, status: status, version: status == .pendingUpload ? 1 : status == .deleted ? 3 : 2)! }
    func page(cursor: String?, limit: Int) async throws -> AppResult<FilesPage> {
        if hold { return await withCheckedContinuation { held = $0 } }
        return .success(FilesPage(files: removed ? [] : [file()], nextCursor: duplicate && cursor == nil ? "next" : nil))
    }
    func isHeld() -> Bool { held != nil }
    func removeExternally() { removed = true }
    func release() { held?.resume(returning: .success(.init(files: [file()], nextCursor: nil))); held = nil }
    func download(_ file: RemoteFile) async throws -> AppResult<Data> { .success(bytes) }
    func receipt(key: String) async throws -> AppResult<FileReceipt> { completion.map(AppResult.success) ?? .failure(.notFound(.init(message: "No receipt"))) }
    func requestUpload(_ spec: UploadSpec, key: String) async throws -> AppResult<FileReceipt> { requestKeys.append(key); return .success(.init(key: key, action: "request_upload", principalID: scope.principalID, file: file(.pendingUpload))) }
    func uploadContent(_ file: RemoteFile, bytes: Data) async throws -> AppResult<Void> { #expect(spec.matches(bytes)); puts += 1; return .success(()) }
    func complete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt> {
        let receipt = FileReceipt(key: key, action: "complete_upload", principalID: scope.principalID, file: self.file())
        completion = receipt
        if lostComplete { lostComplete = false; return .failure(.unavailable(.init(message: "Acknowledgement lost"))) }; return .success(receipt)
    }
    func delete(_ file: RemoteFile, key: String) async throws -> AppResult<FileReceipt> {
        deleteKeys.append(key)
        if deleteConflict { return .failure(.conflict(.init(message: "Observed version changed"))) }
        removed = true
        if deleteKeys.count == 1 { return .failure(.unavailable(.init(message: "Cleanup incomplete"))) }
        return .success(.init(key: key, action: "delete", principalID: scope.principalID, file: self.file(.deleted)))
    }
}
private actor FilesJournalStub: PendingFilesStore {
    let inner = MemoryPendingFilesStore()
    var lostStage: Bool
    var removalFailures: Int
    var refuseLoad: Bool
    let holdStage: Bool
    var held: CheckedContinuation<Void, Never>?
    init(lostStage: Bool = false, removalFailures: Int = 0, refuseLoad: Bool = false, holdStage: Bool = false) {
        self.lostStage = lostStage; self.removalFailures = removalFailures; self.refuseLoad = refuseLoad; self.holdStage = holdStage
    }
    func load() async throws -> AppResult<PendingFileIntent?> {
        if refuseLoad { return .failure(PendingFilesFailure.invalid) }; return try await inner.load()
    }
    func allowLoad() { refuseLoad = false }
    func isHeld() -> Bool { held != nil }
    func release() { held?.resume(); held = nil }
    func stage(_ intent: PendingFileIntent) async throws -> AppResult<Void> {
        let result = try await inner.stage(intent)
        if holdStage { await withCheckedContinuation { held = $0 } }
        if lostStage { lostStage = false; return .failure(PendingFilesFailure.storage) }; return result
    }
    func remove(_ intent: PendingFileIntent) async throws -> AppResult<Void> {
        if removalFailures > 0 { removalFailures -= 1; return .failure(PendingFilesFailure.storage) }; return try await inner.remove(intent)
    }
}
@Suite @MainActor struct FilesStoreTests {
    private var scope: SessionScope { SessionScope(authorityID: "files.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")! }
    private var other: SessionScope { SessionScope(authorityID: scope.authorityID, principalID: scope.principalID, accountID: "00000000-0000-4000-8000-000000000099")! }
    private func idle(_ store: FilesStore) async throws {
        for _ in 0..<200 { if !store.busy { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Files owner did not settle")
    }
    @Test func lostCompletionSettlesReceiptWithoutReuploadAndNeverAutoReplays() async throws {
        let repo = FilesRepositoryStub(scope); let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store)
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: store.selectionTicket); try await idle(store)
        #expect(store.pending && !store.canEdit); #expect(await repo.puts == 1)
        store.bind(nil, scope: nil); #expect(!store.pending && store.files.isEmpty)
        store.bind(FilesRepositoryStub(other), scope: other); try await idle(store)
        #expect(store.blockedByOtherScope && !store.canEdit)
        await repo.removeExternally()
        store.bind(repo, scope: scope); try await idle(store); #expect(store.pending && store.files.isEmpty); #expect(await repo.puts == 1)
        store.retry(); try await idle(store)
        #expect(!store.pending && store.canEdit); #expect(await repo.puts == 1); #expect(await repo.requestKeys.count == 1)
        store.bind(nil, scope: nil)
    }
    @Test func deletionRetriesOriginalCommandEvenAfterLogicalDeletion() async throws {
        let repo = FilesRepositoryStub(scope); let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store)
        store.delete(try #require(store.files.first)); try await idle(store); #expect(store.pending)
        store.bind(nil, scope: nil); store.bind(repo, scope: scope); try await idle(store)
        #expect(store.files.isEmpty && store.pending); #expect(await repo.deleteKeys.count == 1)
        store.retry(); try await idle(store); let keys = await repo.deleteKeys
        #expect(keys.count == 2 && Set(keys).count == 1 && !store.pending)
        store.bind(nil, scope: nil)
    }
    @Test func lateOldScopeCompletionCannotPublish() async throws {
        let repo = FilesRepositoryStub(scope, hold: true); let store = FilesStore(); store.bind(repo, scope: scope)
        for _ in 0..<200 { if await repo.isHeld() { break }; try await Task.sleep(for: .milliseconds(5)) }; #expect(await repo.isHeld())
        store.bind(FilesRepositoryStub(other), scope: other); try await idle(store)
        await repo.release(); try await Task.sleep(for: .milliseconds(20))
        #expect(store.files.allSatisfy { $0.accountID == other.accountID }); store.bind(nil, scope: nil)
    }
    @Test func oldPickerTicketAndMismatchedBytesCannotStartAnIntent() async throws {
        let repo = FilesRepositoryStub(scope); let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store); let ticket = store.selectionTicket
        store.bind(repo, scope: scope); try await idle(store)
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: ticket)
        store.upload(await repo.spec, bytes: Data([2]), ticket: store.selectionTicket)
        #expect(!store.pending && store.canEdit); #expect(await repo.requestKeys.isEmpty)
        store.bind(nil, scope: nil)
    }
    @Test func duplicatePagesRequireRefreshAndDetachDropsPreview() async throws {
        let repo = FilesRepositoryStub(scope, duplicate: true); let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store)
        store.loadMore(); try await idle(store); #expect(!store.ready && store.files.count == 1 && !store.canEdit)
        store.refresh(); try await idle(store); #expect(store.canEdit)
        store.open(try #require(store.files.first)); try await idle(store); #expect(store.preview != nil)
        store.bind(nil, scope: nil); #expect(store.preview == nil && store.files.isEmpty)
    }
    @Test func terminalConflictRequiresExplicitAbandonmentBeforeNewCommand() async throws {
        let repo = FilesRepositoryStub(scope); await repo.conflictOnDelete()
        let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store)
        store.delete(try #require(store.files.first)); try await idle(store)
        let review = try #require(store.reviewPending()); #expect(review.deleting && review.name == "photo.png")
        #expect(store.pending && !store.canEdit) // Opening/dismissing review does not settle anything.
        store.retry(); try await idle(store); #expect(await Set(repo.deleteKeys).count == 1)
        let ticket = store.selectionTicket
        store.forgetLocalRecovery(review); #expect(store.pending && !store.canEdit)
        try await idle(store); #expect(store.canEdit && store.failure == nil && store.selectionTicket != ticket)
        store.delete(try #require(store.files.first)); try await idle(store)
        #expect(await repo.deleteKeys.count == 3); #expect(await Set(repo.deleteKeys).count == 2)
        store.bind(nil, scope: nil)
    }
    @Test func reviewCannotForgetAcrossRenewalOtherScopeOrActiveRead() async throws {
        let repo = FilesRepositoryStub(scope); let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store)
        store.delete(try #require(store.files.first)); try await idle(store)
        let old = try #require(store.reviewPending())
        store.bind(FilesRepositoryStub(other), scope: other); try await idle(store)
        #expect(store.reviewPending() == nil); store.forgetLocalRecovery(old); #expect(store.blockedByOtherScope)
        store.bind(repo, scope: scope); try await idle(store)
        store.forgetLocalRecovery(old); #expect(store.pending)
        let current = try #require(store.reviewPending()); await repo.holdPages(); store.refresh()
        #expect(store.reviewPending() == nil); store.forgetLocalRecovery(current); #expect(store.pending)
        for _ in 0..<200 { if await repo.isHeld() { break }; try await Task.sleep(for: .milliseconds(5)) }
        await repo.release(); try await idle(store); #expect(store.pending)
        store.bind(nil, scope: nil)
    }
    @Test func oldUploadReviewCannotEraseLaterDeleteAndUploadAbandonmentDoesNotReplay() async throws {
        let repo = FilesRepositoryStub(scope); let store = FilesStore(); store.bind(repo, scope: scope); try await idle(store)
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: store.selectionTicket); try await idle(store)
        let old = try #require(store.reviewPending()); #expect(!old.deleting)
        store.retry(); try await idle(store)
        store.delete(try #require(store.files.first)); try await idle(store)
        store.forgetLocalRecovery(old); #expect(store.pending)
        store.forgetLocalRecovery(try #require(store.reviewPending())); try await idle(store)
        // A new upload may be deliberately forgotten without another PUT/complete.
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: store.selectionTicket); try await idle(store)
        // The old stub receipt mismatches this new key, retaining uncertainty.
        let upload = try #require(store.reviewPending()); store.forgetLocalRecovery(upload); try await idle(store)
        #expect(!store.pending && store.canEdit); #expect(await repo.puts == 1); #expect(await repo.requestKeys.count == 1)
        #expect(await repo.deleteKeys.count == 1); store.bind(nil, scope: nil)
    }
    @Test func coldOwnerRestoresOriginalIntentWithoutAutomaticReplay() async throws {
        let repo = FilesRepositoryStub(scope), journal = MemoryPendingFilesStore(), first = FilesStore(journal: journal)
        first.bind(repo, scope: scope); try await idle(first)
        first.upload(await repo.spec, bytes: await repo.bytes, ticket: first.selectionTicket); try await idle(first); first.bind(nil, scope: nil)
        let restored = FilesStore(journal: journal); restored.bind(FilesRepositoryStub(other), scope: other); try await idle(restored)
        #expect(restored.blockedByOtherScope && !restored.canEdit && restored.reviewPending() == nil)
        restored.bind(repo, scope: scope); try await idle(restored); #expect(restored.pending); #expect(await repo.puts == 1)
        restored.retry(); try await idle(restored)
        #expect(!restored.pending && restored.canEdit); #expect(try await journal.load().get() == nil); #expect(await repo.puts == 1)
        restored.bind(nil, scope: nil)
    }
    @Test func lostStageAndFailedRemovalCannotDispatchOrReleaseOriginalIntent() async throws {
        let repo = FilesRepositoryStub(scope), journal = FilesJournalStub(lostStage: true, removalFailures: 2), store = FilesStore(journal: journal)
        store.bind(repo, scope: scope); try await idle(store)
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: store.selectionTicket); try await idle(store)
        #expect(store.pending && !store.canEdit); #expect(await repo.puts == 0)
        store.forgetLocalRecovery(try #require(store.reviewPending())); try await idle(store); #expect(store.pending)
        store.bind(repo, scope: scope); try await idle(store); #expect(await repo.puts == 0)
        store.retry(); try await idle(store); #expect(store.pending); #expect(await repo.puts == 1)
        store.retry(); try await idle(store); #expect(store.pending); #expect(try await journal.load().get() != nil)
        store.retry(); try await idle(store); #expect(!store.pending && store.canEdit); #expect(await repo.puts == 1)
        store.bind(nil, scope: nil)
    }
    @Test func unreadableJournalBlocksChangesUntilExplicitReload() async throws {
        let repo = FilesRepositoryStub(scope), journal = FilesJournalStub(refuseLoad: true), store = FilesStore(journal: journal)
        store.bind(repo, scope: scope); try await idle(store)
        #expect(!store.canEdit && !store.ready && store.failure?.meta.code == "pending_files.invalid_store")
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: store.selectionTicket); #expect(await repo.puts == 0)
        await journal.allowLoad(); store.refresh(); try await idle(store); #expect(store.canEdit); store.bind(nil, scope: nil)
    }
    @Test func cancelledNoncooperativeDiskStageCannotSendHTTPAfterDetach() async throws {
        let repo = FilesRepositoryStub(scope), journal = FilesJournalStub(holdStage: true), store = FilesStore(journal: journal)
        store.bind(repo, scope: scope); try await idle(store)
        store.upload(await repo.spec, bytes: await repo.bytes, ticket: store.selectionTicket)
        for _ in 0..<200 { if await journal.isHeld() { break }; try await Task.sleep(for: .milliseconds(5)) }; #expect(await journal.isHeld())
        store.bind(nil, scope: nil); await journal.release(); try await Task.sleep(for: .milliseconds(20)); #expect(await repo.puts == 0)
        let restored = FilesStore(journal: journal); restored.bind(repo, scope: scope); try await idle(restored)
        #expect(restored.pending && !restored.canEdit); #expect(await repo.requestKeys.isEmpty); restored.bind(nil, scope: nil)
    }

}
