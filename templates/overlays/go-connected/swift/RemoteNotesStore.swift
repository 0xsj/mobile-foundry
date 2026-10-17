import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Observation

/// App-owned presentation around an injected journal and remote repository.
/// Persist before HTTP; restore/read never automatically resends a saved command.
@MainActor @Observable final class RemoteNotesStore {
    var title = ""
    private(set) var editing: VersionedNote?
    private(set) var notes: [VersionedNote] = []
    private(set) var nextAfter: String?
    private(set) var isBusy = false
    private(set) var loaded = false
    private(set) var recoveryLoaded = false
    private(set) var cachedAtMilliseconds: Int64?
    private(set) var cacheFailure: Failure?
    private(set) var failure: Failure?
    private(set) var pending: RemoteNoteWrite?
    private(set) var lastReceipt: NoteWriteReceipt?
    @ObservationIgnored private let cache: any NotesReadCache
    @ObservationIgnored private let now: () -> Int64
    @ObservationIgnored private let journal: any PendingNotesStore
    @ObservationIgnored private var scope: PendingNotesScope?
    @ObservationIgnored private var lastScope: PendingNotesScope?
    @ObservationIgnored private var repository: (any RemoteNotesRepository)?
    @ObservationIgnored private var record: PendingNoteRecord?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var dirty = false
    @ObservationIgnored private var cacheAccessRefused = false
    init(journal: any PendingNotesStore = MemoryPendingNotesStore(), cache: any NotesReadCache = MemoryNotesReadCache(),
         now: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.journal = journal; self.cache = cache; self.now = now
    }
    var canWrite: Bool { repository != nil && loaded && recoveryLoaded && !isBusy && pending == nil }

    func bind(_ repository: (any RemoteNotesRepository)?, scope session: SessionScope?) {
        generation += 1; task?.cancel(); task = nil
        dirty = false; cacheAccessRefused = false
        scope = session.flatMap { PendingNotesScope(authorityID: $0.authorityID, principalID: $0.principalID, accountID: $0.accountID) }
        self.repository = repository
        cachedAtMilliseconds = nil; cacheFailure = nil
        notes = []; loaded = false; recoveryLoaded = false; isBusy = false; failure = nil; lastReceipt = nil; nextAfter = nil; pending = nil; record = nil
        if let scope { if scope != lastScope { title = ""; editing = nil }; lastScope = scope }
        if repository != nil { refresh() }
    }
    func edit(_ note: VersionedNote) { guard canWrite else { return }; editing = note; title = note.title }
    func newDraft() { guard canWrite else { return }; editing = nil; title = "" }
    func save() {
        guard canWrite else { return }
        let id = UUID().uuidString.lowercased()
        let answer = editing.map { RemoteNoteWrite.update(commandID: id, noteID: $0.id, title: title, expectedVersion: $0.version) }
            ?? RemoteNoteWrite.create(commandID: id, title: title)
        switch answer { case .failure(let value): failure = value.publicInfo(); case .success(let command): dispatch(command) }
    }
    func delete(_ note: VersionedNote) {
        guard canWrite else { return }
        if case .success(let command) = RemoteNoteWrite.delete(commandID: UUID().uuidString.lowercased(), noteID: note.id, expectedVersion: note.version) { dispatch(command) }
    }
    private func dispatch(_ command: RemoteNoteWrite) {
        let replacing = record?.command.commandID
        run { repo, scope in
            let answer = try await self.journal.stage(command, scope: scope, replacing: replacing)
            try Task.checkCancellation()
            guard let staged = self.accept(answer) else { return }
            self.apply(staged)
            if staged.status == .pending { try await self.send(command, repo: repo, scope: scope, checkOnly: false) }
        }
    }
    func refresh(after: String? = nil) {
        run { repo, scope in
            guard try await self.restore(scope) else { return }
            if !self.loaded && self.notes.isEmpty { try await self.showSavedRead(scope) }
            try await self.read(repo, scope: scope, after: after)
        }
    }
    /// Coalesce hints during reads/writes; never cancel or replay an accepted command.
    func invalidate() {
        dirty = true
        if !isBusy { dirty = false; refresh() }
    }
    func recover(checkOnly: Bool) {
        guard let command = pending else { return }
        run { repo, scope in
            guard try await self.restore(scope) else { return }
            guard let record = self.record, record.matches(command) else {
                self.recoveryLoaded = false; self.failure = PendingNotesFailure.conflict; return
            }
            if record.status == .pending { try await self.send(command, repo: repo, scope: scope, checkOnly: checkOnly) }
            else { try await self.read(repo, scope: scope) }
        }
    }
    private func restore(_ scope: PendingNotesScope) async throws -> Bool {
        let answer = try await journal.load(scope); try Task.checkCancellation()
        switch answer {
        case .failure(let value): recoveryLoaded = false; loaded = false; notes = []; nextAfter = nil; cachedAtMilliseconds = nil; failure = value.publicInfo(); return false
        case .success(let saved):
            recoveryLoaded = true; apply(saved)
            if let pending, pending.action != .delete { title = pending.title }
            return true
        }
    }
    private func send(_ command: RemoteNoteWrite, repo: any RemoteNotesRepository, scope: PendingNotesScope, checkOnly: Bool) async throws {
        let answer = try await (checkOnly ? repo.receipt(for: command) : repo.execute(command))
        try Task.checkCancellation()
        switch answer {
        case .success(let receipt):
            guard receipt.accountID == scope.accountID, receipt.principalID == scope.principalID, receipt.confirms(command) else {
                failure = .internalError(.init(message: "The write receipt did not match this command.")); return
            }
            lastReceipt = receipt
            let persisted = try await journal.confirm(command, scope: scope, receipt: receipt); try Task.checkCancellation()
            guard let confirmed = accept(persisted) else { return }
            apply(confirmed)
            if command.action != .delete || editing?.id == command.noteID { title = ""; editing = nil }
            try await read(repo, scope: scope)
        case .failure(let value):
            failure = value.publicInfo()
            if !checkOnly, let code = value.meta.code, let reason = PendingNoteRejection(rawValue: code) {
                let persisted = try await journal.reject(command, scope: scope, reason: reason); try Task.checkCancellation()
                guard let refused = accept(persisted) else { return }
                apply(refused); loaded = false
            }
        }
    }
    private func accept(_ answer: AppResult<PendingNoteRecord>) -> PendingNoteRecord? {
        switch answer {
        case .success(let saved): recoveryLoaded = true; return saved
        case .failure(let value): recoveryLoaded = false; failure = value.publicInfo(); return nil
        }
    }
    private func apply(_ saved: PendingNoteRecord?) {
        record = saved; pending = saved?.status == .pending ? saved?.command : nil
        if let receipt = saved?.receipt { lastReceipt = receipt }
        else if pending == nil || lastReceipt?.confirms(pending!) != true { lastReceipt = nil }
        if let reason = saved?.rejection {
            failure = reason == .versionConflict
                ? .conflict(.init(message: "The saved change was refused because this note changed. Refresh and review your edit.", code: reason.rawValue))
                : .notFound(.init(message: "The saved change was refused because this note is missing. Refresh your notes.", code: reason.rawValue))
        }
    }
    func forgetSavedRead() {
        run { _, scope in
            do {
                let answer = try await self.cache.remove(scope); try Task.checkCancellation()
                switch answer {
                case .success:
                    self.cacheFailure = nil
                    if self.cachedAtMilliseconds != nil { self.notes = []; self.nextAfter = nil; self.cachedAtMilliseconds = nil }
                case .failure(let failure): self.cacheFailure = failure.publicInfo()
                }
            } catch is CancellationError { throw CancellationError() }
            catch { try Task.checkCancellation(); self.cacheFailure = NotesCacheFailure.storage }
        }
    }
    private func showSavedRead(_ scope: PendingNotesScope) async throws {
        guard !cacheAccessRefused else { return }
        do {
            let answer = try await cache.load(scope, nowMilliseconds: now()); try Task.checkCancellation()
            switch answer {
            case .success(let snapshot):
                guard let snapshot, snapshot.scope == scope, snapshot.isUsable(at: now()), snapshot.matches(record) else { return }
                notes = snapshot.notes; nextAfter = nil; loaded = false; cachedAtMilliseconds = snapshot.savedAtMilliseconds
            case .failure(let value): cacheFailure = value.publicInfo()
            }
        } catch is CancellationError { throw CancellationError() }
        catch { try Task.checkCancellation(); cacheFailure = NotesCacheFailure.storage }
    }
    private func read(_ repo: any RemoteNotesRepository, scope: PendingNotesScope, after: String? = nil) async throws {
        let answer = try await repo.page(after: after, limit: 20); try Task.checkCancellation()
        switch answer {
        case .success(let page):
            notes = page.notes; nextAfter = page.nextAfter; loaded = true; cachedAtMilliseconds = nil; cacheAccessRefused = false
            if after == nil, let snapshot = NotesReadSnapshot(scope: scope, notes: page.notes, savedAtMilliseconds: now(), journal: record) {
                do {
                    let saved = try await cache.save(snapshot); try Task.checkCancellation()
                    if case .failure(let value) = saved { cacheFailure = value.publicInfo() } else { cacheFailure = nil }
                } catch is CancellationError { throw CancellationError() }
                catch { try Task.checkCancellation(); cacheFailure = NotesCacheFailure.storage }
            }
        case .failure(let value):
            failure = value.publicInfo(); loaded = false; notes = []; nextAfter = nil; cachedAtMilliseconds = nil
            if value.kind == .unavailable || value.kind == .timeout { try await showSavedRead(scope) }
            else if value.kind == .unauthenticated || value.kind == .forbidden {
                cacheAccessRefused = true
                do {
                    let removed = try await cache.remove(scope); try Task.checkCancellation()
                    if case .failure(let value) = removed { cacheFailure = value.publicInfo() }
                } catch is CancellationError { throw CancellationError() }
                catch { try Task.checkCancellation(); cacheFailure = NotesCacheFailure.storage }
            }
        }
    }
    private func run(_ action: @escaping @MainActor (any RemoteNotesRepository, PendingNotesScope) async throws -> Void) {
        guard !isBusy, let repo = repository, let scope else { return }
        isBusy = true; failure = nil; let current = generation
        task = Task {
            defer {
                if current == generation {
                    isBusy = false; task = nil
                    if dirty { dirty = false; refresh() }
                }
            }
            do { try await action(repo, scope) }
            catch is CancellationError {}
            catch {
                guard current == generation, !Task.isCancelled else { return }
                recoveryLoaded = false; loaded = false; notes = []; nextAfter = nil; cachedAtMilliseconds = nil
                failure = .internalError(.init(message: "Saved changes could not complete. Reload recovery before trying again."))
            }
        }
    }
}
