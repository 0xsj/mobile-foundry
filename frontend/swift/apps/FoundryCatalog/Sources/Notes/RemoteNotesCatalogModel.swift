import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Observation

/// App-owned drafts and recovery. Pending commands survive lease renewal in this owner.
@MainActor @Observable final class RemoteNotesCatalogModel {
    var title = ""
    private(set) var editing: VersionedNote?
    private(set) var notes: [VersionedNote] = []
    private(set) var hasMore = false
    private(set) var isBusy = false
    private(set) var loaded = false
    private(set) var failure: Failure?
    private(set) var pending: RemoteNoteWrite?
    private(set) var lastReceipt: NoteWriteReceipt?
    @ObservationIgnored private var scope: SessionScope?
    @ObservationIgnored private var lastScope: SessionScope?
    @ObservationIgnored private var repository: (any RemoteNotesRepository)?
    @ObservationIgnored private var retained: [SessionScope: RemoteNoteWrite] = [:]
    @ObservationIgnored private var task: Task<Void,Never>?
    @ObservationIgnored private var generation = 0
    var canWrite: Bool { repository != nil && loaded && !isBusy && pending == nil }
    func bind(_ repository: (any RemoteNotesRepository)?, scope: SessionScope?) {
        generation += 1; task?.cancel(); task = nil
        self.repository = repository; self.scope = scope; notes = []; loaded = false; isBusy = false; failure = nil; lastReceipt = nil; hasMore = false
        pending = scope.flatMap { retained[$0] }
        if let scope { if scope != lastScope { title = ""; editing = nil }; lastScope = scope }
        if repository != nil { refresh() }
    }
    func edit(_ note: VersionedNote) { guard canWrite else { return }; editing = note; title = note.title }
    func newDraft() { guard canWrite else { return }; editing = nil; title = "" }
    func save() {
        guard canWrite else { return }
        let id = UUID().uuidString.lowercased()
        let answer = editing.map { RemoteNoteWrite.update(commandID:id,noteID:$0.id,title:title,expectedVersion:$0.version) } ?? RemoteNoteWrite.create(commandID:id,title:title)
        switch answer { case .failure(let value): failure = value.publicInfo(); case .success(let command): dispatch(command) }
    }
    func delete(_ note: VersionedNote) {
        guard canWrite else { return }
        if case .success(let command) = RemoteNoteWrite.delete(commandID:UUID().uuidString.lowercased(),noteID:note.id,expectedVersion:note.version) { dispatch(command) }
    }
    private func dispatch(_ command: RemoteNoteWrite) { guard let scope else { return }; retained[scope] = command; pending = command; recover(checkOnly:false) }
    func refresh() { run { repo in await self.read(repo) } }
    func recover(checkOnly: Bool) {
        guard let command = pending else { return }
        run { repo in
            do {
                let answer = try await (checkOnly ? repo.receipt(for:command) : repo.execute(command))
                try Task.checkCancellation()
                switch answer {
                case .success(let receipt):
                    guard let scope = self.scope, receipt.accountID == scope.accountID, receipt.principalID == scope.principalID, receipt.confirms(command) else { self.failure = .internalError(.init(message:"The write receipt did not match this command.")); return }
                    self.lastReceipt = receipt; if let scope = self.scope { self.retained[scope] = nil }; self.pending = nil
                    if command.action != .delete || self.editing?.id == command.noteID { self.title = ""; self.editing = nil }
                    await self.read(repo)
                case .failure(let value):
                    self.failure = value.publicInfo()
                    // A version/missing refusal from execute cannot coexist with a committed replay.
                    if !checkOnly && ["notes.version_conflict","notes.not_found"].contains(value.meta.code ?? "") {
                        if let scope = self.scope { self.retained[scope] = nil }; self.pending = nil; self.loaded = false
                    }
                }
            } catch is CancellationError {} catch { guard !Task.isCancelled else { return }; self.failure = .internalError(.init(message:"The write acknowledgement could not be admitted. Check its receipt.")) }
        }
    }
    private func read(_ repo: any RemoteNotesRepository) async {
        do { let answer = try await repo.page(after:nil,limit:20); try Task.checkCancellation()
            switch answer { case .success(let page): notes = page.notes; hasMore = page.nextAfter != nil; loaded = true
            case .failure(let value): failure = value.publicInfo(); loaded = false }
        } catch is CancellationError {} catch { guard !Task.isCancelled else { return }; failure = .internalError(.init(message:"Remote notes could not be loaded.")); loaded = false }
    }
    private func run(_ action: @escaping @MainActor (any RemoteNotesRepository) async -> Void) {
        guard !isBusy, let repo = repository else { return }
        isBusy = true; failure = nil; let current = generation
        task = Task { await action(repo); if current == generation { isBusy = false; task = nil } }
    }
}
