import FoundryKernel
import FoundryServices
import FoundrySession

/// The repository target is fixed at construction. Cancellation cannot roll back an accepted write.
@MainActor public final class SessionNotesRepository: NotesRepository {
    private let repository: any NotesRepository
    private let session: SessionController
    private let lease: SessionLease
    public init(repository: any NotesRepository, session: SessionController, lease: SessionLease) {
        self.repository = repository; self.session = session; self.lease = lease
    }
    public func list() async throws -> AppResult<[Note]> { try await access { try await self.repository.list() } }
    public func create(_ command: CreateNote) async throws -> AppResult<Note> { try await access { try await self.repository.create(command) } }
    public func update(_ command: UpdateNote) async throws -> AppResult<Note> { try await access { try await self.repository.update(command) } }
    public func delete(_ command: DeleteNote) async throws -> AppResult<Void> { try await access { try await self.repository.delete(command) } }
    private func access<T: Sendable>(_ operation: () async throws -> AppResult<T>) async throws -> AppResult<T> {
        try Task.checkCancellation()
        guard session.isCurrent(lease) else { return .failure(SessionFailures.scopeChanged) }
        do {
            let answer = try await operation(); try Task.checkCancellation()
            return session.isCurrent(lease) ? answer : .failure(SessionFailures.scopeChanged)
        } catch {
            try Task.checkCancellation()
            if error is CancellationError { throw error }
            guard session.isCurrent(lease) else { return .failure(SessionFailures.scopeChanged) }; throw error
        }
    }
}
