import FoundryHTTP
import FoundryKernel

public struct HTTPNoteCreator: NoteCreator {
    private let http: any HTTPClient
    public init(http: any HTTPClient) { self.http = http }

    public func create(_ command: CreateNote) async throws -> AppResult<Note> {
        try Task.checkCancellation()
        let answer = try await http.requestEntity(.POST, path: "notes", options: .init(body: .object(["title": .string(command.title)])))
        switch answer {
        case .failure(let failure): return .failure(failure)
        case .success(let entity):
            guard let body = entity.body.object?["note"]?.object,
                  case .string(let id) = body["id"], case .string(let title) = body["title"],
                  let note = Note(id: id, title: title) else {
                return .failure(.internalError(.init(message: "The created note response was invalid.", code: "notes.invalid_create_response",
                                                     requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID)))
            }
            try Task.checkCancellation()
            return .success(note)
        }
    }
}
