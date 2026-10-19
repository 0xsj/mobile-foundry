import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Observation

struct FileRecoveryReview: Identifiable, Sendable {
    fileprivate let generation: Int
    fileprivate let key: String
    let name: String
    let deleting: Bool
    var id: String { key }
    var consequence: String { deleting
        ? "This only forgets this app's retry information. The server may already have deleted the file, and byte cleanup may still be unfinished. Forgetting does not finish cleanup or undo deletion."
        : "This discards the retained photo and retry information from this app. The upload may already exist on the server. Forgetting does not cancel or delete it." }
}

/// One original intent in the injected journal. Bind/restore read only; retry is explicit.
@MainActor @Observable final class FilesStore {
    private(set) var files: [RemoteFile] = []
    private(set) var busy = false
    private(set) var pending = false
    private(set) var blockedByOtherScope = false
    private(set) var ready = false
    private(set) var failure: Failure?
    private(set) var nextCursor: String?
    private(set) var preview: Data?
    private(set) var previewName: String?
    @ObservationIgnored private var repository: (any RemoteFilesRepository)?
    @ObservationIgnored private var scope: SessionScope?
    @ObservationIgnored private var intent: PendingFileIntent?
    @ObservationIgnored private let journal: any PendingFilesStore
    private var journalReady = false
    init(journal: any PendingFilesStore = MemoryPendingFilesStore()) { self.journal = journal }
    private var recoveryScope: PendingFileScope? { scope.flatMap { PendingFileScope(authorityID: $0.authorityID, principalID: $0.principalID, accountID: $0.accountID) } }
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var task: Task<Void, Never>?
    var selectionTicket: Int { generation }
    var canEdit: Bool { repository != nil && recoveryScope != nil && journalReady && ready && !busy && intent == nil }
    var canLoadMore: Bool { ready && !busy && nextCursor != nil && files.count < 200 }

    func bind(_ repository: (any RemoteFilesRepository)?, scope: SessionScope?) {
        generation += 1; task?.cancel(); task = nil; self.repository = repository; self.scope = scope
        files = []; busy = false; ready = false; journalReady = false; failure = nil; nextCursor = nil; closePreview()
        pending = scope != nil && intent?.scope == recoveryScope
        blockedByOtherScope = scope != nil && intent != nil && !pending
        if repository != nil && scope != nil { refresh() }
    }
    func refresh() {
        guard !busy else { return }; let expected = intent; ready = false; journalReady = false
        run({ repo -> AppResult<(PendingFileIntent?, AppResult<FilesPage>)> in
            switch try await self.journal.load() {
            case .failure(let error): return .failure(error)
            case .success(let retained):
                if let expected, let retained, !expected.matches(retained) { return .failure(PendingFilesFailure.conflict) }
                try Task.checkCancellation()
                return .success((retained, try await repo.page(cursor: nil, limit: 20)))
            }
        }) { retained, page in
            self.intent = self.intent ?? retained; self.journalReady = true
            self.pending = self.intent != nil && self.intent?.scope == self.recoveryScope
            self.blockedByOtherScope = self.intent != nil && !self.pending
            switch page {
            case .success(let page): self.files = page.files; self.nextCursor = page.nextCursor; self.ready = true
            case .failure(let error): self.failure = error.publicInfo()
            }
        }
    }
    func loadMore() {
        guard canLoadMore, let cursor = nextCursor else { return }
        run({ try await $0.page(cursor: cursor, limit: 20) }) { page in
            guard Set(self.files.map(\.id)).isDisjoint(with: page.files.map(\.id)) else {
                self.ready = false; self.failure = .internalError(.init(message: "Files changed while paging. Refresh to continue.")); return
            }
            self.files += page.files; self.nextCursor = page.nextCursor
        }
    }
    func upload(_ spec: UploadSpec, bytes: Data, ticket: Int) {
        guard ticket == generation, canEdit, let scope = recoveryScope, spec.matches(bytes) else { return }
        intent = .upload(scope: scope, key: UUID().uuidString.lowercased(), spec: spec, bytes: bytes); pending = true; retry()
    }
    func delete(_ file: RemoteFile) {
        guard canEdit, let scope = recoveryScope, file.accountID == scope.accountID, files.contains(file) else { return }
        guard let accepted = PendingFileIntent.delete(scope: scope, key: UUID().uuidString.lowercased(), file: file) else { return }
        intent = accepted; pending = true; retry()
    }
    func retry() {
        guard !busy, let intent, intent.scope == recoveryScope else { return }
        run({ repo -> AppResult<Void> in
            switch try await self.journal.stage(intent) { case .failure(let error): return .failure(error); case .success: break }
            try Task.checkCancellation() // Disk acknowledgement is required before any HTTP.
            let result: AppResult<Void>
            if let file = intent.file {
                // Repeat the original delete, not receipt-only settlement: cleanup can be unfinished.
                result = try await repo.delete(file, key: intent.key).map { _ in () }
            } else { result = try await self.resumeUpload(repo, intent) }
            switch result {
            case .failure(let error): return .failure(error)
            case .success:
                try Task.checkCancellation()
                return try await self.journal.remove(intent)
            }
        }) { _ in
            self.intent = nil; self.pending = false; self.closePreview(); self.refresh()
        }
    }
    private func resumeUpload(_ repo: any RemoteFilesRepository, _ intent: PendingFileIntent) async throws -> AppResult<Void> {
        let scope = intent.scope, key = intent.key, spec = intent.spec
        switch try await repo.receipt(key: key + ".complete") {
        case .success(let receipt):
            guard receipt.key == key + ".complete", receipt.action == "complete_upload", receipt.principalID == scope.principalID,
                  receipt.file.accountID == scope.accountID, receipt.file.ownerID == scope.principalID,
                  receipt.file.spec == spec, receipt.file.status == .available else { return .failure(Self.invalidReceipt) }
            return .success(())
        case .failure(.notFound): break
        case .failure(let error): return .failure(error)
        }
        try Task.checkCancellation()
        switch try await repo.requestUpload(spec, key: key + ".request") {
        case .failure(let error): return .failure(error)
        case .success(let receipt):
            try Task.checkCancellation()
            switch try await repo.uploadContent(receipt.file, bytes: intent.bytes) {
            case .failure(let error): return .failure(error)
            case .success:
                try Task.checkCancellation()
                return try await repo.complete(receipt.file, key: key + ".complete").map { _ in () }
            }
        }
    }
    func reviewPending() -> FileRecoveryReview? {
        guard !busy, let intent, intent.scope == recoveryScope else { return nil }
        return .init(generation: generation, key: intent.key, name: intent.spec.name, deleting: intent.deleting)
    }
    func forgetLocalRecovery(_ review: FileRecoveryReview) {
        guard !busy, review.generation == generation, let intent, intent.scope == recoveryScope, review.key == intent.key else { return }
        run({ _ in try await self.journal.remove(intent) }) { _ in
            // Only an acknowledged exact removal releases local responsibility.
            self.generation += 1; self.intent = nil; self.pending = false; self.ready = false
            self.failure = nil; self.closePreview(); self.refresh()
        }
    }
    func open(_ file: RemoteFile) {
        guard ready, !busy, file.status == .available, file.accountID == scope?.accountID, files.contains(file) else { return }
        closePreview()
        run({ try await $0.download(file) }) { bytes in self.preview = bytes; self.previewName = file.spec.name }
    }
    func closePreview() { preview = nil; previewName = nil }
    func importFailed(ticket: Int) {
        guard ticket == generation else { return }
        failure = .invalid(.init(message: "Choose a supported photo up to 10 MiB."), fields: [:])
    }
    private static var invalidReceipt: Failure { .internalError(.init(message: "The upload receipt did not match the retained photo.")) }
    private func run<T: Sendable>(_ operation: @escaping @MainActor (any RemoteFilesRepository) async throws -> AppResult<T>, receive: @escaping @MainActor (T) -> Void) {
        guard !busy, let repository, scope != nil else { return }; let current = generation
        busy = true; failure = nil
        task = Task {
            do {
                let result = try await operation(repository); try Task.checkCancellation()
                guard current == generation else { return }
                busy = false; task = nil
                switch result { case .success(let value): receive(value); case .failure(let error): failure = error.publicInfo() }
            } catch {
                guard current == generation else { return }
                busy = false; task = nil
                if !(error is CancellationError) { failure = .internalError(.init(message: "Files could not complete. Keep the pending operation and retry explicitly.")) }
            }
        }
    }
}
