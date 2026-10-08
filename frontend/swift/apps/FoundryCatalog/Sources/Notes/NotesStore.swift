import FoundryKernel
import FoundryQuery
import FoundryServices
import Observation

@MainActor @Observable
final class NotesStore {
    private(set) var state: QueryState<[Note]> = .idle
    @ObservationIgnored private let service: any NotesService
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var request: Task<AppResult<[Note]>, any Error>?

    init(service: any NotesService, onUnexpectedError: @escaping (any Error) -> Void) {
        self.service = service
        self.onUnexpectedError = onUnexpectedError
    }

    /// The caller's task owns this load. A replacement also cancels its predecessor.
    func load() async {
        generation &+= 1
        let current = generation
        request?.cancel()
        state = state.starting()
        let operation = Task { try await service.list() }
        request = operation
        defer { if current == generation { request = nil } }
        do {
            let answer = try await withTaskCancellationHandler {
                try await operation.value
            } onCancel: {
                operation.cancel()
            }
            guard current == generation else { return }
            try Task.checkCancellation()
            state = state.settled(with: answer)
        } catch is CancellationError {
            if current == generation { restore() }
        } catch {
            guard current == generation else { return }
            guard !Task.isCancelled else { restore(); return }
            onUnexpectedError(error)
            guard current == generation else { return }
            state = .failed(.internalError(.init(message: "An unexpected error occurred.")), previous: state.value)
        }
    }

    func cancel() {
        generation &+= 1
        request?.cancel()
        request = nil
        restore()
    }

    private func restore() { state = state.restored() }
}
