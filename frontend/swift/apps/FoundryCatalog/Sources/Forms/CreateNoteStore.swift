import FoundryKernel
import FoundryQuery
import FoundryServices
import Observation

@MainActor @Observable
final class CreateNoteStore {
    private(set) var title = ""
    private(set) var touched = false
    private(set) var mutation: MutationState<Note> = .idle
    @ObservationIgnored private var creator: any NoteCreator
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var request: Task<Void, Never>?

    init(creator: any NoteCreator, onUnexpectedError: @escaping (any Error) -> Void) {
        self.creator = creator; self.onUnexpectedError = onUnexpectedError
    }

    var canEdit: Bool { !mutation.isSubmitting && mutation.value == nil }
    var titleError: String? {
        if case .invalid(_, let fields) = mutation.failure { return fields["title"] ?? localError }
        return localError
    }
    private var localError: String? {
        guard touched, case .failure(.invalid(_, let fields)) = CreateNote.make(title: title) else { return nil }
        return fields["title"]
    }

    func editTitle(_ value: String) {
        guard canEdit else { return }
        title = value
        mutation = .idle
    }

    func blurTitle() { touched = true }

    /// Admission is synchronous: a second tap/Return cannot dispatch another write.
    @discardableResult func submit() -> Task<Void, Never>? {
        guard canEdit else { return nil }
        touched = true
        let command: CreateNote
        switch CreateNote.make(title: title) {
        case .failure: return nil
        case .success(let value): command = value
        }
        generation &+= 1
        let current = generation
        let service = creator
        mutation = mutation.starting()
        let operation = Task { [self] in
            defer { if current == generation { request = nil } }
            do {
                let result = try await service.create(command)
                guard current == generation else { return }
                try Task.checkCancellation()
                mutation = mutation.settled(with: result)
            } catch is CancellationError {
                if current == generation { mutation = .failed(Self.canceled) }
            } catch {
                guard current == generation else { return }
                guard !Task.isCancelled else { mutation = .failed(Self.canceled); return }
                onUnexpectedError(error)
                guard current == generation else { return }
                mutation = .failed(.internalError(.init(message: "An unexpected error occurred.")))
            }
        }
        request = operation
        return operation
    }

    /// Replace an idle dependency without resetting the user's draft.
    @discardableResult func use(_ creator: any NoteCreator) -> Bool {
        guard !mutation.isSubmitting else { return false }
        self.creator = creator
        mutation = .idle
        return true
    }

    func reset() {
        guard !mutation.isSubmitting else { return }
        title = ""; touched = false; mutation = mutation.reset()
    }

    /// Abandon local observation; this makes no statement about remote commit status.
    func stop() {
        generation &+= 1
        request?.cancel(); request = nil
        if mutation.isSubmitting { mutation = .failed(Self.canceled) }
    }

    private static let canceled = Failure.canceled(.init(message: "Submission stopped before confirmation.", code: "notes.create_canceled"))
}
