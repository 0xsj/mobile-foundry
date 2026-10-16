import FoundryKernel
import FoundryQuery
import FoundryServices
import Observation

/// One reader owns one subscription. Persistent source identity is independent of this lifetime.
@MainActor @Observable final class LiveNotesStore {
    private(set) var query: QueryState<[Note]> = .idle
    private(set) var isWatching = false
    @ObservationIgnored private let source: any NotesObserver
    @ObservationIgnored private let admitted: () -> Bool
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var closed = false
    init(source: any NotesObserver, admitted: @escaping () -> Bool = { true }, onUnexpectedError: @escaping (any Error) -> Void) {
        self.source = source; self.admitted = admitted; self.onUnexpectedError = onUnexpectedError
    }
    @discardableResult func start() -> Task<Void, Never>? {
        guard !closed && !isWatching else { return nil }
        guard admitted() else { shutdown(); return nil }
        generation += 1; let current = generation; isWatching = true; query = query.starting()
        let task = Task {
            defer { if current == generation { isWatching = false; self.task = nil; query = query.restored() } }
            do {
                let subscription = try await source.observe()
                defer { subscription.cancel() }
                try Task.checkCancellation()
                for try await answer in subscription.values {
                    try Task.checkCancellation(); guard current == generation else { return }
                    guard admitted() else { shutdown(); return }
                    query = query.settled(with: answer)
                }
            } catch {
                guard current == generation && !Task.isCancelled else { return }
                guard admitted() else { shutdown(); return }
                if error is CancellationError { return }
                onUnexpectedError(error)
                guard current == generation else { return }
                guard admitted() else { shutdown(); return }
                query = .failed(.internalError(.init(message: "Live notes could not be observed.", code: "notes.observation_failed")), previous: query.value)
            }
        }
        self.task = task; return task
    }
    @discardableResult func restart() -> Task<Void, Never>? { pause(); return start() }
    func pause() { generation += 1; task?.cancel(); task = nil; isWatching = false; query = query.restored() }
    func shutdown() { closed = true; pause(); query = .idle }
}
