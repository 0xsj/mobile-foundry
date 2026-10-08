import Foundation
import FoundryHTTP
import FoundryKernel
import FoundryServices

enum NotesProvider: String, CaseIterable, Identifiable {
    case memory = "Memory", http = "HTTP"
    var id: Self { self }
}

enum NotesScenario: String, CaseIterable, Identifiable, Sendable {
    case content = "Content", empty = "Empty", unavailable = "Unavailable", slow = "Slow"
    var id: Self { self }
}

enum NotesComposition {
    static let seeds = [Note(id: "n1", title: "Sketch a native idea")!, Note(id: "n2", title: "Explore a GPU interaction")!]

    static func service(provider: NotesProvider, scenario: NotesScenario) -> any NotesService {
        let service: any NotesService
        switch provider {
        case .memory:
            service = scenario == .unavailable
                ? RefusedNotesService(failure: .unavailable(.init(message: "Notes are temporarily unavailable.", code: "notes.unavailable")))
                : InMemoryNotesService(notes: scenario == .empty ? [] : seeds)
        case .http:
            switch DefaultHTTPClient.create(baseURL: "https://catalog.invalid/v1", transport: NotesCatalogTransport(scenario: scenario)) {
            case .success(let http): service = HTTPNotesService(http: http)
            case .failure(let failure): service = RefusedNotesService(failure: failure)
            }
        }
        return DelayedNotesService(service: service, scenario: scenario)
    }
}

private struct RefusedNotesService: NotesService {
    let failure: Failure
    func list() async throws -> AppResult<[Note]> {
        try Task.checkCancellation()
        return .failure(failure)
    }
}

private struct DelayedNotesService: NotesService {
    let service: any NotesService
    let scenario: NotesScenario
    func list() async throws -> AppResult<[Note]> {
        try await Task.sleep(for: .milliseconds(scenario == .slow ? 1_500 : 250))
        return try await service.list()
    }
}

private struct NotesCatalogTransport: HTTPTransport {
    let scenario: NotesScenario
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        try Task.checkCancellation()
        let status = scenario == .unavailable ? 503 : 200
        let body: String
        if scenario == .unavailable {
            body = "{\"detail\":\"Notes are temporarily unavailable.\",\"code\":\"notes.unavailable\"}"
        } else if scenario == .empty {
            body = "{\"notes\":[]}"
        } else {
            body = "{\"notes\":[{\"id\":\"n1\",\"title\":\"Sketch a native idea\"},{\"id\":\"n2\",\"title\":\"Explore a GPU interaction\"}]}"
        }
        return .success(.init(status: status, headers: ["x-request-id": "catalog-notes"], body: Data(body.utf8)))
    }
}
