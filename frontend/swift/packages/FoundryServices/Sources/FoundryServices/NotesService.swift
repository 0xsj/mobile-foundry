import Foundation
import FoundryHTTP
import FoundryKernel

public struct Note: Equatable, Identifiable, Sendable {
    public let id: String
    public let title: String

    public init?(id: String, title: String) {
        guard (1...64).contains(id.utf8.count), id.utf8.allSatisfy({
            (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95
        }), title.unicodeScalars.contains(where: { ![9, 10, 13, 32].contains($0.value) }) else { return nil }
        self.id = id
        self.title = title
    }
}

/// Provider-independent read port. Cancellation and unexpected defects throw.
public protocol NotesService: Sendable {
    func list() async throws -> AppResult<[Note]>
}

public struct InMemoryNotesService: NotesService {
    private let notes: [Note]

    /// Seeds are owned domain values; duplicate IDs are a programmer error.
    public init(notes: [Note]) {
        precondition(Set(notes.map(\.id)).count == notes.count, "Note IDs must be unique")
        self.notes = notes
    }

    public func list() async throws -> AppResult<[Note]> {
        try Task.checkCancellation()
        return .success(notes)
    }
}

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
