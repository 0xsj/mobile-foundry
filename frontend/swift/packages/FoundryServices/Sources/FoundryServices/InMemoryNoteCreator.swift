import Foundation
import FoundryKernel

/// Instance-local memory; actor isolation makes append/list snapshots atomic.
public actor InMemoryNoteCreator: NoteCreator, NotesService {
    private var notes: [Note]
    public init(notes: [Note] = []) {
        precondition(Set(notes.map(\.id)).count == notes.count, "Note IDs must be unique")
        self.notes = notes
    }

    public func create(_ command: CreateNote) async throws -> AppResult<Note> {
        try Task.checkCancellation()
        var id = UUID().uuidString
        while notes.contains(where: { $0.id == id }) { id = UUID().uuidString }
        let note = Note(id: id, title: command.title)!
        notes.append(note)
        return .success(note)
    }

    public func list() async throws -> AppResult<[Note]> {
        try Task.checkCancellation()
        return .success(notes)
    }
}
