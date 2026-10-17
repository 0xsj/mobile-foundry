import Foundation
import FoundryGraphics
import FoundryKernel
import FoundryServices
import FoundrySession
import Observation

private enum PendingFile: Sendable {
    case upload(key: String, spec: UploadSpec, bytes: Data)
    case delete(key: String, file: RemoteFile)
}
/// Catalog lifetime only. Original input is retained per account through renewal;
/// there is no durable queue or automatic background replay.
@MainActor @Observable final class RemoteFilesCatalogModel {
    private(set) var files: [RemoteFile] = []
    private(set) var busy = false
    private(set) var pending = false
    private(set) var hasMore = false
    private(set) var failure: Failure?
    var preview: RasterImage?
    @ObservationIgnored private var repository: (any RemoteFilesRepository)?
    @ObservationIgnored private var scope: SessionScope?
    @ObservationIgnored private var retained: [SessionScope: PendingFile] = [:]
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    var selectionTicket: Int { generation }
    var canUpload: Bool { repository != nil && !busy && !pending }
    func bind(_ repository: (any RemoteFilesRepository)?, scope: SessionScope?) {
        generation += 1; task?.cancel(); task = nil; self.repository = repository; self.scope = scope
        files = []; busy = false; hasMore = false; failure = nil; preview = nil; pending = scope.flatMap { retained[$0] } != nil
        if repository != nil { refresh() }
    }
    func refresh() {
        run({ try await $0.page(cursor: nil, limit: 20) }) { page in self.files = page.files; self.hasMore = page.nextCursor != nil }
    }
    func upload(spec: UploadSpec, bytes: Data, ticket: Int) {
        guard canUpload, ticket == generation, let scope else { return }
        retained[scope] = .upload(key: UUID().uuidString.lowercased(), spec: spec, bytes: bytes); pending = true; retry()
    }
    func delete(_ file: RemoteFile) {
        guard canUpload, let scope else { return }
        retained[scope] = .delete(key: UUID().uuidString.lowercased(), file: file); pending = true; retry()
    }
    func retry() {
        guard let scope, let command = retained[scope] else { return }
        run({ repo in
            switch command {
            case .upload(let key, let spec, let bytes):
                let created = try await repo.requestUpload(spec, key: key + ".request")
                guard case .success(let receipt) = created else { return created }
                let uploaded = try await repo.uploadContent(receipt.file, bytes: bytes)
                if case .failure(let error) = uploaded { return .failure(error) }
                return try await repo.complete(receipt.file, key: key + ".complete")
            case .delete(let key, let file): return try await repo.delete(file, key: key)
            }
        }) { _ in
            self.retained[scope] = nil; self.pending = false
            // Release this operation before starting the next independent read.
            self.busy = false; self.refresh()
        }
    }
    func open(_ file: RemoteFile) {
        run({ repo -> AppResult<RasterImage> in
            let answer = try await repo.download(file)
            switch answer {
            case .failure(let error): return .failure(error)
            case .success(let data): return await Task.detached(priority: .userInitiated) { PhotoDecoder.decode(data) }.value
            }
        }) { self.preview = $0 }
    }
    func importFailed(_ value: Failure, ticket: Int) { if ticket == generation { failure = value.publicInfo() } }
    private func run<T: Sendable>(_ operation: @escaping @MainActor (any RemoteFilesRepository) async throws -> AppResult<T>, receive: @escaping @MainActor (T) -> Void) {
        guard !busy, let repo = repository else { return }; let current = generation
        busy = true; failure = nil
        task = Task {
            do {
                let answer = try await operation(repo); try Task.checkCancellation()
                guard current == generation else { return }
                busy = false; task = nil
                switch answer { case .success(let value): receive(value); case .failure(let value): failure = value.publicInfo() }
            } catch is CancellationError {
            } catch {
                guard current == generation, !Task.isCancelled else { return }
                busy = false; task = nil; failure = .internalError(.init(message: "Files could not complete. Keep the pending operation for retry."))
            }
        }
    }
}
