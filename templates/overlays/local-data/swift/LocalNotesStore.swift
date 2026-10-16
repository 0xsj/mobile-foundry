import FoundryKernel
import FoundryQuery
import FoundryServices
import Observation

/// Main-actor feature owner. Commands commit through a repository, then reload its snapshot.
@MainActor @Observable
final class LocalNotesStore {
    private(set) var query: QueryState<[Note]> = .idle
    private(set) var mutation: MutationState<String> = .idle
    private(set) var title = ""
    private(set) var editingID: String?
    private(set) var touched = false
    private(set) var isBusy = false
    @ObservationIgnored private let repository: any NotesRepository
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var request: Task<Void, Never>?
    @ObservationIgnored private var closed = false
    init(repository: any NotesRepository, onUnexpectedError: @escaping (any Error) -> Void) {
        self.repository = repository; self.onUnexpectedError = onUnexpectedError
    }
    var canAct: Bool { !closed && !isBusy && query.value != nil }
    var titleError: String? {
        if case .invalid(_, let fields) = mutation.failure { return fields["title"] }
        if touched, case .failure(.invalid(_, let fields)) = CreateNote.make(title: title) { return fields["title"] }
        return nil
    }
    func editTitle(_ value: String) { guard canAct else { return }; title = value; mutation = .idle }
    func blurTitle() { if !closed { touched = true } }
    func edit(_ note: Note) {
        guard canAct && query.value?.contains(where: { $0.id == note.id }) == true else { return }
        editingID = note.id; title = note.title; touched = false; mutation = .idle
    }
    func newNote() { guard canAct else { return }; clearDraft(); mutation = .idle }
    private func clearDraft() { title = ""; editingID = nil; touched = false }

    @discardableResult func refresh() -> Task<Void, Never>? {
        guard !closed && !isBusy else { return nil }
        generation &+= 1; let current = generation; isBusy = true; query = query.starting()
        let task = Task { [self] in
            defer { if current == generation { isBusy = false; request = nil } }
            do {
                let answer = try await repository.list()
                guard current == generation else { return }; try Task.checkCancellation()
                query = query.settled(with: answer)
            } catch {
                guard current == generation else { return }
                if error is CancellationError || Task.isCancelled { query = query.restored(); return }
                onUnexpectedError(error)
                guard current == generation else { return }
                query = .failed(Self.unexpected, previous: query.value)
            }
        }
        request = task; return task
    }
    @discardableResult func save() -> Task<Void, Never>? {
        guard canAct else { return nil }; touched = true
        if let id = editingID {
            switch UpdateNote.make(id: id, title: title) {
            case .failure(let failure): mutation = .failed(failure); return nil
            case .success(let command): return perform(.update(command))
            }
        }
        switch CreateNote.make(title: title) {
        case .failure(let failure): mutation = .failed(failure); return nil
        case .success(let command): return perform(.create(command))
        }
    }
    @discardableResult func delete(_ note: Note) -> Task<Void, Never>? {
        guard canAct && query.value?.contains(where: { $0.id == note.id }) == true,
              case .success(let command) = DeleteNote.make(id: note.id) else { return nil }
        return perform(.delete(command))
    }
    private enum Action { case create(CreateNote), update(UpdateNote), delete(DeleteNote) }
    private func perform(_ action: Action) -> Task<Void, Never> {
        generation &+= 1; let current = generation; isBusy = true; mutation = .submitting
        let task = Task { [self] in
            defer { if current == generation { isBusy = false; request = nil } }
            do {
                let result: AppResult<String>
                switch action {
                case .create(let command): result = try await repository.create(command).map { _ in "Note created." }
                case .update(let command): result = try await repository.update(command).map { _ in "Note updated." }
                case .delete(let command): result = try await repository.delete(command).map { "Note deleted." }
                }
                guard current == generation else { return }; try Task.checkCancellation()
                mutation = mutation.settled(with: result)
                guard case .success = result else { return }
                switch action {
                case .create, .update: clearDraft()
                case .delete(let command): if editingID == command.id { clearDraft() }
                }
                query = query.starting()
                let snapshot = try await repository.list()
                guard current == generation else { return }; try Task.checkCancellation()
                query = query.settled(with: snapshot)
            } catch {
                guard current == generation else { return }
                if error is CancellationError || Task.isCancelled {
                    if mutation.isSubmitting { mutation = .failed(Self.stopped) }; query = query.restored(); return
                }
                onUnexpectedError(error)
                guard current == generation else { return }
                if mutation.value != nil { query = .failed(Self.unexpected, previous: query.value) }
                else { mutation = .failed(Self.unexpected) }
            }
        }
        request = task; return task
    }
    func stop() {
        generation &+= 1; request?.cancel(); request = nil; isBusy = false; query = query.restored()
        if mutation.isSubmitting { mutation = .failed(Self.stopped) }
    }
    /// Account-scoped owners are terminal: discard drafts and visible data with their lease.
    func shutdown() {
        closed = true; stop(); query = .idle; mutation = .idle; clearDraft()
    }
    private static let stopped = Failure.canceled(.init(message: "Action stopped before confirmation. Refresh notes before trying again.", code: "notes.local_canceled"))
    private static let unexpected = Failure.internalError(.init(message: "An unexpected error occurred."))
}
