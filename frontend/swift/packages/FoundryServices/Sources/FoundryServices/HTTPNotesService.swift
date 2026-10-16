import FoundryHTTP
import FoundryKernel

public struct HTTPNotesService: NotesService {
    private let http: any HTTPClient
    public init(http: any HTTPClient) { self.http = http }

    public func list() async throws -> AppResult<[Note]> {
        try Task.checkCancellation()
        let answer = try await http.requestEntity(.GET, path: "notes", options: .init())
        switch answer {
        case .failure(let failure): return .failure(failure)
        case .success(let entity):
            var notes: [Note] = []
            var ids = Set<String>()
            guard case .array(let items) = entity.body.object?["notes"] else { return invalid(entity) }
            for item in items {
                guard let object = item.object,
                      case .string(let id) = object["id"],
                      case .string(let title) = object["title"],
                      let note = Note(id: id, title: title), ids.insert(id).inserted else { return invalid(entity) }
                notes.append(note)
            }
            try Task.checkCancellation()
            return .success(notes)
        }
    }

    private func invalid(_ entity: Entity) -> AppResult<[Note]> {
        .failure(.internalError(.init(message: "The notes response was invalid.", code: "notes.invalid_response",
                                     requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID)))
    }
}
