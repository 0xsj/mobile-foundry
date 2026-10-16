@testable import FoundryCatalog
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
    var loseAcknowledgement = true
    var failReadAfterWrite = false
    var failRead = false
    init(_ scope: SessionScope) { self.scope = scope }
    func configure(lost: Bool, failAfterWrite: Bool = false) { loseAcknowledgement = lost; failReadAfterWrite = failAfterWrite }
    func allowReads() { failRead = false }
    func page(after: String?,limit: Int) async throws -> AppResult<RemoteNotesPage> { failRead ? .failure(.unavailable(.init(message:"Read failed"))) : .success(.init(notes:notes,nextAfter:nil)) }
    func get(_ noteID: String) async throws -> AppResult<VersionedNote> { .failure(NotesRepositoryFailure.missing) }
    func execute(_ command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> {
        executions += 1
        if let receipt = receipts[command.commandID] { return .success(receipt) }
        let id = UUID().uuidString.lowercased()
        let receipt = NoteWriteReceipt(commandID:command.commandID,accountID:scope.accountID,principalID:scope.principalID,action:.create,noteID:id,title:command.title,version:1)!
        notes.append(VersionedNote(id:id,title:command.title,version:1)!); receipts[command.commandID] = receipt; failRead = failReadAfterWrite
        return loseAcknowledgement ? .failure(.unavailable(.init(message:"Lost acknowledgement"))) : .success(receipt)
    }
    func receipt(for command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> { receipts[command.commandID].map { .success($0) } ?? .failure(NotesRepositoryFailure.missing) }
}
@MainActor private func idle(_ model: RemoteNotesCatalogModel) async throws {
    for _ in 0..<100 { if !model.isBusy { return }; try await Task.sleep(for:.milliseconds(5)) }; Issue.record("Remote owner did not settle")
}
@MainActor @Test func remoteOwnerRetainsExactCommandThroughRenewalAndChecksReceiptWithoutReplay() async throws {
    let repo = ReceiptRepository(scope()); let model = RemoteNotesCatalogModel(); model.bind(repo,scope:scope()); try await idle(model)
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
    let model = RemoteNotesCatalogModel();model.bind(repo,scope:scope());try await idle(model)
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
    func get(_ noteID: String) async throws -> AppResult<VersionedNote> { .failure(NotesRepositoryFailure.missing) }
    func receipt(for command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> { .failure(NotesRepositoryFailure.missing) }
    func execute(_ command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt> { self.command = command;return await withCheckedContinuation { continuation = $0 } }
    func finish() { let r = NoteWriteReceipt(commandID:command!.commandID,accountID:scope().accountID,principalID:scope().principalID,action:.create,noteID:UUID().uuidString.lowercased(),title:command!.title,version:1)!;continuation?.resume(returning:.success(r));continuation = nil }
}
@MainActor @Test func remoteLateAcknowledgementCannotPublishAfterAccountReplacement() async throws {
    let old = LateReceiptRepository(); let model = RemoteNotesCatalogModel();model.bind(old,scope:scope());try await idle(model)
    model.title = "Old target";model.save()
    for _ in 0..<100 { if await old.waiting { break };try await Task.sleep(for:.milliseconds(5)) }
    let target = scope("00000000-0000-4000-8000-000000000099");model.bind(ReceiptRepository(target),scope:target);try await idle(model)
    await old.finish();try await Task.sleep(for:.milliseconds(10))
    #expect(model.notes.isEmpty && model.lastReceipt == nil && model.pending == nil && model.title.isEmpty)
    model.bind(old,scope:scope());try await idle(model);#expect(model.pending != nil) // Original target retains recovery evidence.
}
