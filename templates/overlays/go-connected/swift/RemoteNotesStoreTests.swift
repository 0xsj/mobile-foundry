@testable import ProductApp
import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Testing

private func scope(_ account: String = "00000000-0000-4000-8000-000000000002") -> SessionScope {
    SessionScope(authorityID:"go.test",principalID:"00000000-0000-4000-8000-000000000003",accountID:account)!
}
private actor ReceiptRepository: RemoteNotesRepository {
    let scope: SessionScope
    var notes: [VersionedNote] = []
    var receipts: [String:NoteWriteReceipt] = [:]
    var executions = 0
    var readFailure: Failure?
    func setReadFailure(_ failure: Failure?) { readFailure = failure }
    func seed() { notes = [VersionedNote(id: "00000000-0000-4000-8000-000000000004", title: "Saved snapshot", version: 1)!] }
    var reads = 0
    var loseAcknowledgement = true
    var failReadAfterWrite = false
    var failRead = false
    init(_ scope: SessionScope) { self.scope = scope }
    func configure(lost: Bool, failAfterWrite: Bool = false) { loseAcknowledgement = lost; failReadAfterWrite = failAfterWrite }
    func allowReads() { failRead = false }
    func page(after: String?,limit: Int) async throws -> AppResult<RemoteNotesPage> { reads += 1; if let readFailure { return .failure(readFailure) }; return failRead ? .failure(.unavailable(.init(message:"Read failed"))) : .success(.init(notes:notes,nextAfter:nil)) }
    func get(_ noteID: String) async throws -> AppResult<VersionedNote> { .failure(.notFound(.init(message: "Not found"))) }
    func execute(_ command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> {
        executions += 1
        if let receipt = receipts[command.commandID] { return .success(receipt) }
        let id = UUID().uuidString.lowercased()
        let receipt = NoteWriteReceipt(commandID:command.commandID,accountID:scope.accountID,principalID:scope.principalID,action:.create,noteID:id,title:command.title,version:1)!
        notes.append(VersionedNote(id:id,title:command.title,version:1)!); receipts[command.commandID] = receipt; failRead = failReadAfterWrite
        return loseAcknowledgement ? .failure(.unavailable(.init(message:"Lost acknowledgement"))) : .success(receipt)
    }
    func receipt(for command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> { receipts[command.commandID].map { .success($0) } ?? .failure(.notFound(.init(message: "Not found"))) }
}
@MainActor private func idle(_ model: RemoteNotesStore) async throws {
    for _ in 0..<100 { if !model.isBusy { return }; try await Task.sleep(for:.milliseconds(5)) }; Issue.record("Remote owner did not settle")
}
@MainActor @Test func remoteOwnerRetainsExactCommandThroughRenewalAndChecksReceiptWithoutReplay() async throws {
    let repo = ReceiptRepository(scope()); let model = RemoteNotesStore(); model.bind(repo,scope:scope()); try await idle(model)
    model.title = "Retained"; model.save(); model.save(); try await idle(model)
    let command = try #require(model.pending); #expect(await repo.executions == 1 && !model.canWrite)
    model.bind(nil,scope:nil); model.bind(repo,scope:scope()); try await idle(model)
    #expect(model.pending?.commandID == command.commandID)
    model.recover(checkOnly:true); try await idle(model)
    #expect(model.pending == nil && model.canWrite && model.notes.count == 1 && model.title.isEmpty)
    #expect(await repo.executions == 1 && model.lastReceipt?.confirms(command) == true)
}
@MainActor @Test func remoteCommitReceiptSurvivesFailedReadAndRefreshDoesNotRepeatWrite() async throws {
    let repo = ReceiptRepository(scope()); await repo.configure(lost:false,failAfterWrite:true)
    let model = RemoteNotesStore();model.bind(repo,scope:scope());try await idle(model)
    model.title = "Committed";model.save();try await idle(model)
    #expect(model.pending == nil && model.lastReceipt != nil && !model.canWrite && model.failure?.kind == .unavailable)
    await repo.allowReads();model.refresh();try await idle(model)
    #expect(model.canWrite && model.notes.count == 1 && model.lastReceipt != nil && model.failure == nil)
    #expect(await repo.executions == 1)
}
private actor LateReceiptRepository: RemoteNotesRepository {
    var continuation: CheckedContinuation<AppResult<NoteWriteReceipt>,Never>?
    var command: RemoteNoteWrite?
    var waiting: Bool { continuation != nil }
    func page(after: String?,limit: Int) async throws -> AppResult<RemoteNotesPage> { .success(.init(notes:[],nextAfter:nil)) }
    func get(_ noteID: String) async throws -> AppResult<VersionedNote> { .failure(.notFound(.init(message: "Not found"))) }
    func receipt(for command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> { .failure(.notFound(.init(message: "Not found"))) }
    func execute(_ command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> { self.command = command;return await withCheckedContinuation { continuation = $0 } }
    func finish() { let r = NoteWriteReceipt(commandID:command!.commandID,accountID:scope().accountID,principalID:scope().principalID,action:.create,noteID:UUID().uuidString.lowercased(),title:command!.title,version:1)!;continuation?.resume(returning:.success(r));continuation = nil }
}
@MainActor @Test func remoteLateAcknowledgementCannotPublishAfterAccountReplacement() async throws {
    let old = LateReceiptRepository(); let model = RemoteNotesStore();model.bind(old,scope:scope());try await idle(model)
    model.title = "Old target";model.save()
    for _ in 0..<100 { if await old.waiting { break };try await Task.sleep(for:.milliseconds(5)) }
    let target = scope("00000000-0000-4000-8000-000000000099");model.bind(ReceiptRepository(target),scope:target);try await idle(model)
    await old.finish();try await Task.sleep(for:.milliseconds(10))
    #expect(model.notes.isEmpty && model.lastReceipt == nil && model.pending == nil && model.title.isEmpty)
    model.bind(old,scope:scope());try await idle(model);#expect(model.pending != nil) // Original target retains recovery evidence.
}

private actor UncertainJournal: PendingNotesStore {
    let underlying = MemoryPendingNotesStore()
    var failLoad = false
    func refuseLoad() { failLoad = true }
    var loseStage = false
    var failConfirm = false
    init(loseStage: Bool = false, failConfirm: Bool = false) { self.loseStage = loseStage; self.failConfirm = failConfirm }
    func load(_ scope: PendingNotesScope) async throws -> AppResult<PendingNoteRecord?> { if failLoad { return .failure(PendingNotesFailure.invalid) }; return try await underlying.load(scope) }
    func stage(_ command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?) async throws -> AppResult<PendingNoteRecord> {
        let result = try await underlying.stage(command, scope: scope, replacing: replacing)
        if loseStage { loseStage = false; return .failure(PendingNotesFailure.storage) }; return result
    }
    func confirm(_ command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt) async throws -> AppResult<PendingNoteRecord> {
        if failConfirm { failConfirm = false; return .failure(PendingNotesFailure.storage) }
        return try await underlying.confirm(command, scope: scope, receipt: receipt)
    }
    func reject(_ command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection) async throws -> AppResult<PendingNoteRecord> { try await underlying.reject(command, scope: scope, reason: reason) }
}
@MainActor @Test func newRemoteOwnerRestoresWithoutSendingAndReceiptNotFoundKeepsIntent() async throws {
    let journal = UncertainJournal(loseStage: true); let repo = ReceiptRepository(scope())
    let first = RemoteNotesStore(journal: journal); first.bind(repo, scope: scope()); try await idle(first)
    first.title = "Not sent"; first.save(); try await idle(first)
    #expect(!first.recoveryLoaded); #expect(await repo.executions == 0)
    let second = RemoteNotesStore(journal: journal); second.bind(repo, scope: scope()); try await idle(second)
    let original = try #require(second.pending); #expect(original.title == "Not sent" ); #expect(await repo.executions == 0)
    second.recover(checkOnly: true); try await idle(second)
    #expect(second.pending?.commandID == original.commandID ); #expect(await repo.executions == 0)
    await repo.configure(lost: false); second.recover(checkOnly: false); try await idle(second)
    #expect(second.pending == nil && second.lastReceipt?.confirms(original) == true ); #expect(await repo.executions == 1)
}
@MainActor @Test func failedReceiptPersistenceRequiresReloadAndSurvivesNewOwner() async throws {
    let journal = UncertainJournal(failConfirm: true); let repo = ReceiptRepository(scope()); await repo.configure(lost: false)
    let first = RemoteNotesStore(journal: journal); first.bind(repo, scope: scope()); try await idle(first)
    first.title = "Remote committed"; first.save(); try await idle(first)
    let original = try #require(first.pending)
    #expect(first.lastReceipt?.confirms(original) == true && !first.recoveryLoaded && !first.canWrite)
    first.title = "Unsafe next"; first.save(); #expect(await repo.executions == 1)
    let second = RemoteNotesStore(journal: journal); second.bind(repo, scope: scope()); try await idle(second)
    #expect(second.pending?.commandID == original.commandID ); #expect(await repo.executions == 1)
    second.recover(checkOnly: true); try await idle(second)
    #expect(second.pending == nil && second.lastReceipt?.confirms(original) == true ); #expect(await repo.executions == 1)
    let third = RemoteNotesStore(journal: journal); third.bind(repo, scope: scope()); try await idle(third)
    #expect(third.lastReceipt?.confirms(original) == true && third.canWrite ); #expect(await repo.executions == 1)
}

@MainActor @Test func liveHintsCoalesceBehindUnconfirmedWriteWithoutReplaying() async throws {
    let repo = ReceiptRepository(scope()); let model = RemoteNotesStore()
    model.bind(repo, scope: scope()); try await idle(model)
    model.title = "Unconfirmed live write"; model.save()
    for _ in 0..<100 { model.invalidate() }
    try await idle(model)
    #expect(await repo.executions == 1); #expect(await repo.reads == 2)
    #expect(model.pending != nil && model.notes.count == 1 && !model.canWrite)
}

@MainActor @Test func savedReadSurvivesNewOwnerButNeverEnablesWritesAndCanBeForgotten() async throws {
    let repo = ReceiptRepository(scope()), cache = MemoryNotesReadCache(); await repo.seed()
    let first = RemoteNotesStore(cache: cache); first.bind(repo, scope: scope()); try await idle(first)
    #expect(first.loaded && first.cachedAtMilliseconds == nil)
    await repo.setReadFailure(.unavailable(.init(message: "Connection unavailable")))
    let reopened = RemoteNotesStore(cache: cache); reopened.bind(repo, scope: scope()); try await idle(reopened)
    #expect(reopened.notes.first?.title == "Saved snapshot" && reopened.cachedAtMilliseconds != nil && !reopened.loaded && !reopened.canWrite)
    reopened.title = "Must not send"; reopened.save(); #expect(await repo.executions == 0)
    reopened.forgetSavedRead(); try await idle(reopened); #expect(reopened.notes.isEmpty && reopened.cachedAtMilliseconds == nil)
    reopened.refresh(); try await idle(reopened); #expect(reopened.notes.isEmpty)
    await repo.setReadFailure(nil); reopened.refresh(); try await idle(reopened)
    #expect(reopened.canWrite && reopened.cachedAtMilliseconds == nil && reopened.notes.count == 1)
}
@MainActor @Test func savedReadNeverCrossesScopeOrSignedOutBoundary() async throws {
    let cache = MemoryNotesReadCache(), repo = ReceiptRepository(scope()); await repo.seed()
    let model = RemoteNotesStore(cache: cache); model.bind(repo, scope: scope()); try await idle(model)
    let foreign = SessionScope(authorityID: "go.other", principalID: scope().principalID, accountID: scope().accountID)!
    let offline = ReceiptRepository(foreign); await offline.setReadFailure(.timeout(.init(message: "Offline")))
    model.bind(offline, scope: foreign); try await idle(model); #expect(model.notes.isEmpty && model.cachedAtMilliseconds == nil)
    model.bind(nil, scope: nil); #expect(model.notes.isEmpty && !model.canWrite)
}
@MainActor @Test func changedJournalPreventsPreMutationSnapshotResurrection() async throws {
    let cache = MemoryNotesReadCache(), journal = MemoryPendingNotesStore(), repo = ReceiptRepository(scope())
    await repo.seed(); await repo.configure(lost: false, failAfterWrite: true)
    let first = RemoteNotesStore(journal: journal, cache: cache); first.bind(repo, scope: scope()); try await idle(first)
    first.title = "New commit"; first.save(); try await idle(first)
    #expect(first.lastReceipt != nil && first.notes.isEmpty && first.cachedAtMilliseconds == nil && !first.canWrite)
    let reopened = RemoteNotesStore(journal: journal, cache: cache); reopened.bind(repo, scope: scope()); try await idle(reopened)
    #expect(reopened.lastReceipt != nil && reopened.notes.isEmpty && reopened.cachedAtMilliseconds == nil)
    #expect(await repo.executions == 1)
}
private actor BrokenReadCache: NotesReadCache {
    func load(_ scope: PendingNotesScope, nowMilliseconds: Int64) async throws -> AppResult<NotesReadSnapshot?> { throw NSError(domain: "Owned storage failure", code: 1) }
    func save(_ snapshot: NotesReadSnapshot) async throws -> AppResult<Void> { .failure(NotesCacheFailure.storage) }
    func remove(_ scope: PendingNotesScope) async throws -> AppResult<Void> { .failure(NotesCacheFailure.storage) }
}
@MainActor @Test func optionalCacheFailureCannotBlockFreshReadsOrConfirmedWrites() async throws {
    let repo = ReceiptRepository(scope()); await repo.configure(lost: false)
    let model = RemoteNotesStore(cache: BrokenReadCache()); model.bind(repo, scope: scope()); try await idle(model)
    #expect(model.canWrite && model.recoveryLoaded && model.cacheFailure != nil && model.failure == nil)
    model.title = "Still writable"; model.save(); try await idle(model)
    #expect(model.canWrite && model.pending == nil && model.lastReceipt != nil && model.notes.count == 1 && model.cacheFailure != nil)
}
@MainActor @Test func authorizationRefusalHidesAndRemovesSavedRead() async throws {
    let repo = ReceiptRepository(scope()), cache = MemoryNotesReadCache(); await repo.seed()
    let first = RemoteNotesStore(cache: cache); first.bind(repo, scope: scope()); try await idle(first)
    await repo.setReadFailure(.forbidden(.init(message: "No membership")))
    let denied = RemoteNotesStore(cache: cache); denied.bind(repo, scope: scope()); try await idle(denied)
    #expect(denied.notes.isEmpty && denied.cachedAtMilliseconds == nil && !denied.canWrite)
    await repo.setReadFailure(.unavailable(.init(message: "Offline")))
    let later = RemoteNotesStore(cache: cache); later.bind(repo, scope: scope()); try await idle(later)
    #expect(later.notes.isEmpty && later.cachedAtMilliseconds == nil)
}
private actor DelayedReadCache: NotesReadCache {
    var continuation: CheckedContinuation<AppResult<NotesReadSnapshot?>, Never>?
    var waiting: Bool { continuation != nil }
    func load(_ scope: PendingNotesScope, nowMilliseconds: Int64) async throws -> AppResult<NotesReadSnapshot?> { await withCheckedContinuation { continuation = $0 } }
    func save(_ snapshot: NotesReadSnapshot) async throws -> AppResult<Void> { .success(()) }
    func remove(_ scope: PendingNotesScope) async throws -> AppResult<Void> { .success(()) }
    func finish(_ snapshot: NotesReadSnapshot) { continuation?.resume(returning: .success(snapshot)); continuation = nil }
}
@MainActor @Test func lateCacheReadCannotPublishAfterDetach() async throws {
    let cache = DelayedReadCache(), repo = ReceiptRepository(scope())
    let model = RemoteNotesStore(cache: cache); model.bind(repo, scope: scope())
    for _ in 0..<100 { if await cache.waiting { break }; try await Task.sleep(for: .milliseconds(5)) }
    model.bind(nil, scope: nil)
    let original = PendingNotesScope(authorityID: scope().authorityID, principalID: scope().principalID, accountID: scope().accountID)!
    await cache.finish(NotesReadSnapshot(scope: original, notes: [VersionedNote(id: UUID().uuidString.lowercased(), title: "Old account", version: 1)!], savedAtMilliseconds: Int64(Date().timeIntervalSince1970 * 1000), journal: nil)!)
    try await Task.sleep(for: .milliseconds(10)); #expect(model.notes.isEmpty && model.cachedAtMilliseconds == nil)
    #expect(await repo.reads == 0)
}

private actor RetainedReadCache: NotesReadCache {
    let underlying = MemoryNotesReadCache()
    func load(_ scope: PendingNotesScope, nowMilliseconds: Int64) async throws -> AppResult<NotesReadSnapshot?> { try await underlying.load(scope, nowMilliseconds: nowMilliseconds) }
    func save(_ snapshot: NotesReadSnapshot) async throws -> AppResult<Void> { try await underlying.save(snapshot) }
    func remove(_ scope: PendingNotesScope) async throws -> AppResult<Void> { .failure(NotesCacheFailure.storage) }
}
@MainActor @Test func knownRefusalSuppressesRetainedCacheUntilFreshAuthorizationSucceeds() async throws {
    let repo = ReceiptRepository(scope()), cache = RetainedReadCache(); await repo.seed()
    let model = RemoteNotesStore(cache: cache); model.bind(repo, scope: scope()); try await idle(model)
    await repo.setReadFailure(.forbidden(.init(message: "No membership"))); model.refresh(); try await idle(model)
    #expect(model.cacheFailure != nil && model.notes.isEmpty)
    await repo.setReadFailure(.unavailable(.init(message: "Offline"))); model.refresh(); try await idle(model)
    #expect(model.notes.isEmpty && model.cachedAtMilliseconds == nil && !model.canWrite)
    await repo.setReadFailure(nil); model.refresh(); try await idle(model); #expect(model.canWrite)
    await repo.setReadFailure(.timeout(.init(message: "Offline"))); model.refresh(); try await idle(model)
    #expect(model.notes.count == 1 && model.cachedAtMilliseconds != nil && !model.canWrite)
}
@MainActor @Test func unreadableJournalHidesEarlierSnapshotAndBlocksReadFallback() async throws {
    let repo = ReceiptRepository(scope()), cache = MemoryNotesReadCache(), journal = UncertainJournal(); await repo.seed()
    let model = RemoteNotesStore(journal: journal, cache: cache); model.bind(repo, scope: scope()); try await idle(model)
    await repo.setReadFailure(.unavailable(.init(message: "Offline"))); model.refresh(); try await idle(model)
    #expect(model.cachedAtMilliseconds != nil)
    await journal.refuseLoad(); model.refresh(); try await idle(model)
    #expect(model.notes.isEmpty && model.cachedAtMilliseconds == nil && !model.recoveryLoaded && !model.canWrite)
    #expect(await repo.reads == 2)
}
