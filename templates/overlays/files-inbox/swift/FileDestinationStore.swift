import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Observation

/// An inbox target is a hint. Only current, exact-lease reads populate this detail.
@MainActor @Observable final class FileDestinationStore {
    private(set) var presented = false
    private(set) var file: RemoteFile?
    private(set) var bytes: Data?
    private(set) var busy = false
    private(set) var failure: Failure?
    @ObservationIgnored private var reader: (any RemoteFileDetailReader)?
    @ObservationIgnored private var scope: SessionScope?
    @ObservationIgnored private var target: String?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var task: Task<Void, Never>?
    func bind(_ reader: (any RemoteFileDetailReader)?, scope: SessionScope?) {
        close(); self.reader = reader; self.scope = scope
    }
    func open(_ notice: InboxNotification) {
        guard reader != nil, let scope, notice.accountID == scope.accountID, notice.principalID == scope.principalID else { return }
        close(); target = notice.fileID; presented = true; refresh()
    }
    func close() {
        generation += 1; task?.cancel(); task = nil
        target = nil; presented = false; file = nil; bytes = nil; busy = false; failure = nil
    }
    func refresh() {
        guard !busy, presented, let target else { return }
        file = nil; bytes = nil
        run({ try await $0.get(id: target) }) { value in
            guard value.id == target, value.accountID == self.scope?.accountID, value.status != .deleted else {
                self.failure = self.invalidResponse; return
            }
            self.file = value
        }
    }
    func download() {
        guard !busy, let file, file.status == .available else { return }
        bytes = nil
        run({ try await $0.download(file) }) { value in
            guard file.spec.matches(value) else { self.failure = self.invalidResponse; return }
            self.bytes = value
        }
    }
    private var invalidResponse: Failure { .internalError(.init(message: "The file response could not be verified.", code: "files.invalid_remote_response")) }
    private func run<T: Sendable>(_ operation: @escaping @MainActor (any RemoteFileDetailReader) async throws -> AppResult<T>, receive: @escaping @MainActor (T) -> Void) {
        guard let reader, scope != nil else { return }; let current = generation
        busy = true; failure = nil
        task = Task {
            do {
                let result = try await operation(reader); try Task.checkCancellation()
                guard current == generation else { return }
                busy = false; task = nil
                switch result { case .success(let value): receive(value); case .failure(let error): failure = error.publicInfo() }
            } catch {
                guard current == generation else { return }
                busy = false; task = nil
                if !(error is CancellationError) { failure = .internalError(.init(message: "The file could not be opened. Try refreshing.")) }
            }
        }
    }
}
