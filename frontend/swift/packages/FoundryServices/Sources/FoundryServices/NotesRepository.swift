import Foundation
import FoundryKernel

/// A local-data capability composed from narrow read/create ports. No storage or SDK types escape.
public protocol NotesRepository: NotesService, NoteCreator {
    func update(_ command: UpdateNote) async throws -> AppResult<Note>
    func delete(_ command: DeleteNote) async throws -> AppResult<Void>
}

public struct UpdateNote: Equatable, Sendable {
    public let id: String
    public let title: String
    private init(id: String, title: String) { self.id = id; self.title = title }
    public static func make(id: String, title: String) -> AppResult<Self> {
        guard Note(id: id, title: "ID admission") != nil else { return .failure(invalidNoteID) }
        return CreateNote.make(title: title).map { Self(id: id, title: $0.title) }
    }
}

public struct DeleteNote: Equatable, Sendable {
    public let id: String
    private init(id: String) { self.id = id }
    public static func make(id: String) -> AppResult<Self> {
        guard Note(id: id, title: "ID admission") != nil else { return .failure(invalidNoteID) }
        return .success(Self(id: id))
    }
}

private let invalidNoteID = Failure.invalid(.init(message: "The note identifier is invalid.", code: "notes.invalid_id"),
                                            fields: ["id": "Use a valid note identifier."])
public enum NotesRepositoryFailure {
    public static var missing: Failure { .notFound(.init(message: "This note no longer exists. Refresh your notes.", code: "notes.not_found")) }
}

/// Mutable memory adapter with the same order and missing-record behavior as local storage.
public actor InMemoryNotesRepository: ObservableNotesRepository {
    private var notes: [Note]
    private var observers: [UUID: NotesStream.Continuation] = [:]
    public init(notes: [Note] = []) {
        precondition(Set(notes.map(\.id)).count == notes.count, "Note IDs must be unique")
        self.notes = notes
    }
    public func list() async throws -> AppResult<[Note]> { try Task.checkCancellation(); return .success(notes) }
    public func observe() async throws -> NotesSubscription {
        try Task.checkCancellation()
        let id = UUID()
        let (stream, continuation) = NotesStream.makeStream(bufferingPolicy: .bufferingNewest(1))
        observers[id] = continuation
        continuation.onTermination = { [weak self] _ in Task { await self?.removeObserver(id) } }
        continuation.yield(.success(notes))
        return NotesSubscription(values: stream, onCancel: { continuation.finish() })
    }
    private func removeObserver(_ id: UUID) { observers[id] = nil }
    private func publish() { for continuation in observers.values { continuation.yield(.success(notes)) } }
    public func create(_ command: CreateNote) async throws -> AppResult<Note> {
        try Task.checkCancellation()
        var id = UUID().uuidString
        while notes.contains(where: { $0.id == id }) { id = UUID().uuidString }
        let note = Note(id: id, title: command.title)!; notes.append(note); publish(); return .success(note)
    }
    public func update(_ command: UpdateNote) async throws -> AppResult<Note> {
        try Task.checkCancellation()
        guard let index = notes.firstIndex(where: { $0.id == command.id }) else { return .failure(NotesRepositoryFailure.missing) }
        let note = Note(id: command.id, title: command.title)!
        if notes[index] != note { notes[index] = note; publish() }
        return .success(note)
    }
    public func delete(_ command: DeleteNote) async throws -> AppResult<Void> {
        try Task.checkCancellation()
        guard let index = notes.firstIndex(where: { $0.id == command.id }) else { return .failure(NotesRepositoryFailure.missing) }
        notes.remove(at: index); publish(); return .success(())
    }
}
