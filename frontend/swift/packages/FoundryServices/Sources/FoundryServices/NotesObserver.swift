import FoundryKernel

public typealias NotesStream = AsyncThrowingStream<AppResult<[Note]>, any Error>
/// Explicit ownership also handles early loop exit and subscriptions that were never iterated.
public struct NotesSubscription: Sendable {
    public let values: NotesStream
    private let onCancel: @Sendable () -> Void
    public init(values: NotesStream, onCancel: @escaping @Sendable () -> Void) { self.values = values; self.onCancel = onCancel }
    public func cancel() { onCancel() } // Implementations make cancellation idempotent.
}
/// Current snapshots, not an event log. Cancel the task and subscription on disposal.
public protocol NotesObserver: Sendable {
    func observe() async throws -> NotesSubscription
}
public protocol ObservableNotesRepository: NotesRepository, NotesObserver {}
