import Foundation
import FoundryHTTP
import FoundryKernel
import FoundryServices

enum CreateNoteScenario: String, CaseIterable, Identifiable, Sendable {
    case success = "Success", fieldError = "Field error", unavailable = "Unavailable"
    case slow = "Slow", timeout = "Timeout", malformed = "Malformed"
    var id: Self { self }

    var failure: Failure? {
        switch self {
        case .success, .slow: nil
        case .fieldError: .invalid(.init(message: "Check the highlighted field.", code: "notes.title_reserved"), fields: ["title": "This title is reserved."])
        case .unavailable: .unavailable(.init(message: "Notes are temporarily unavailable.", code: "notes.unavailable"))
        case .timeout: .timeout(.init(message: "No confirmation was received.", code: "notes.create_timeout"))
        case .malformed: .internalError(.init(message: "Private malformed create detail.", code: "notes.invalid_create_response"))
        }
    }
}

enum CreateNoteComposition {
    static func creator(provider: NotesProvider, scenario: CreateNoteScenario) -> any NoteCreator {
        let creator: any NoteCreator
        switch provider {
        case .memory: creator = MemoryCreateScenario(base: InMemoryNoteCreator(), scenario: scenario)
        case .http:
            switch DefaultHTTPClient.create(baseURL: "https://catalog.invalid/v1", transport: CreateNoteTransport(scenario: scenario)) {
            case .success(let http): creator = HTTPNoteCreator(http: http)
            case .failure(let failure): creator = RefusedNoteCreator(failure: failure)
            }
        }
        return DelayedNoteCreator(base: creator, milliseconds: scenario == .slow ? 1_500 : 250)
    }
}

private struct RefusedNoteCreator: NoteCreator {
    let failure: Failure
    func create(_ command: CreateNote) async throws -> AppResult<Note> { .failure(failure) }
}

private struct MemoryCreateScenario: NoteCreator {
    let base: any NoteCreator
    let scenario: CreateNoteScenario
    func create(_ command: CreateNote) async throws -> AppResult<Note> {
        try Task.checkCancellation()
        if let failure = scenario.failure { return .failure(failure) }
        return try await base.create(command)
    }
}

private struct DelayedNoteCreator: NoteCreator {
    let base: any NoteCreator
    let milliseconds: Int
    func create(_ command: CreateNote) async throws -> AppResult<Note> {
        try await Task.sleep(for: .milliseconds(milliseconds))
        return try await base.create(command)
    }
}

private struct CreateNoteTransport: HTTPTransport {
    let scenario: CreateNoteScenario
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        try Task.checkCancellation()
        let value = try request.body.map { try JSONDecoder().decode(JSONValue.self, from: $0) }
        let title = value?.object?["title"]?.string ?? ""
        let body: JSONValue
        let status: Int
        switch scenario {
        case .fieldError:
            status = 422
            body = .object(["detail": .string("Check the highlighted field."), "code": .string("notes.title_reserved"),
                            "fields": .object(["title": .string("This title is reserved.")])])
        case .unavailable:
            status = 503
            body = .object(["detail": .string("Notes are temporarily unavailable."), "code": .string("notes.unavailable")])
        case .timeout:
            status = 504
            body = .object(["detail": .string("No confirmation was received."), "code": .string("notes.create_timeout")])
        case .malformed:
            status = 201; body = .object(["note": .object(["id": .string(""), "title": .string(title)])])
        case .success, .slow:
            status = 201; body = .object(["note": .object(["id": .string(UUID().uuidString), "title": .string(title)])])
        }
        return .success(.init(status: status, headers: ["x-request-id": "catalog-create"], body: try JSONEncoder().encode(body)))
    }
}
