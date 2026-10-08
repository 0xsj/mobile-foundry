import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private struct NotesFixture: Decodable {
    struct Value: Decodable { let id: String; let title: String }
    let id: String
    let status: Int
    let body: String
    let expected: [Value]?
    let kind: String?
}

private actor NotesWireStub: HTTPTransport {
    let response: WireResponse
    var paths: [String] = []
    init(_ response: WireResponse) { self.response = response }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        paths.append(request.url.path)
        return .success(response)
    }
}

@Test func notesAdmissionAndMemoryParityUseSharedFixtures() async throws {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let fixtures = try JSONDecoder().decode([NotesFixture].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/notes/responses.json")))
    for fixture in fixtures {
        let wire = NotesWireStub(.init(status: fixture.status, headers: ["x-request-id": "notes-req", "x-correlation-id": "notes-corr"], body: Data(fixture.body.utf8)))
        let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
        let result = try await HTTPNotesService(http: http).list()
        if let expected = fixture.expected {
            let notes = try expected.map { try #require(Note(id: $0.id, title: $0.title)) }
            #expect(try result.get() == notes)
            let memory = InMemoryNotesService(notes: notes)
            #expect(try await memory.list().get() == notes)
        } else {
            guard case .failure(let failure) = result else { Issue.record("Admitted \(fixture.id)"); continue }
            #expect(failure.kind.rawValue == fixture.kind)
            #expect(failure.meta.code == (fixture.status == 503 ? "notes.unavailable" : "notes.invalid_response"))
            #expect(failure.meta.requestID == "notes-req")
            #expect(failure.meta.correlationID == "notes-corr")
        }
        #expect(await wire.paths == ["/v1/notes"])
    }
}

@Test func noteIdentifiersAndMemorySnapshotAreOwned() async throws {
    #expect(Note(id: String(repeating: "a", count: 64), title: "Idea") != nil)
    #expect(Note(id: String(repeating: "a", count: 65), title: "Idea") == nil)
    #expect(Note(id: "", title: "Idea") == nil)
    #expect(Note(id: "é", title: "Idea") == nil)
    #expect(Note(id: "n1", title: " \t\r\n") == nil)
    let note = try #require(Note(id: "n1", title: "Idea"))
    var seeds = [note]
    let memory = InMemoryNotesService(notes: seeds)
    seeds.removeAll()
    var returned = try await memory.list().get()
    returned.removeAll()
    #expect(try await memory.list().get() == [note])
}

private final class NotesDefect: Error, @unchecked Sendable {}
private struct ThrowingNotesHTTP: HTTPClient {
    let error: any Error
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> { throw error }
}

@Test func notesAdaptersPreserveDefectsAndCancellation() async throws {
    let defect = NotesDefect()
    do {
        _ = try await HTTPNotesService(http: ThrowingNotesHTTP(error: defect)).list()
        Issue.record("Defect swallowed")
    } catch { #expect(error as? NotesDefect === defect) }
    let services: [any NotesService] = [InMemoryNotesService(notes: []), HTTPNotesService(http: ThrowingNotesHTTP(error: CancellationError()))]
    for service in services {
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await service.list()
        }
        do { _ = try await task.value; Issue.record("Cancellation admitted") }
        catch { #expect(error is CancellationError) }
    }
}
