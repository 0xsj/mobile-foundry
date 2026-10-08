import FoundryKernel

/// An admitted create command. Titles preserve input; the limit counts Unicode scalars.
public struct CreateNote: Equatable, Sendable {
    public let title: String
    private init(title: String) { self.title = title }

    public static func make(title: String) -> AppResult<Self> {
        let error: String?
        if !title.unicodeScalars.contains(where: { ![9, 10, 13, 32].contains($0.value) }) {
            error = "Enter a title."
        } else if title.unicodeScalars.count > 200 {
            error = "Use 200 characters or fewer."
        } else { error = nil }
        if let error {
            return .failure(.invalid(.init(message: "Check the highlighted field.", code: "notes.invalid_title"), fields: ["title": error]))
        }
        return .success(Self(title: title))
    }
}

/// Narrow write port. A missing response/cancellation does not establish rollback.
public protocol NoteCreator: Sendable {
    func create(_ command: CreateNote) async throws -> AppResult<Note>
}
