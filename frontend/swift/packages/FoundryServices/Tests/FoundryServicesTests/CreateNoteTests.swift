import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private func createFixture(_ name: String) throws -> Data {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    return try Data(contentsOf: root.appendingPathComponent("contracts/fixtures/notes/\(name).json"))
}

@Test func createTitlesUseSharedCodePointAdmissionAndPreserveInput() throws {
    struct Case: Decodable { let title: String; let error: String? }
    for row in try JSONDecoder().decode([Case].self, from: createFixture("create-titles")) {
        let result = CreateNote.make(title: row.title)
        if let error = row.error {
            guard case .failure(.invalid(let meta, let fields)) = result else { Issue.record("Invalid title admitted"); continue }
            #expect(meta.code == "notes.invalid_title")
            #expect(meta.message == "Check the highlighted field.")
            #expect(fields == ["title": error])
        } else { #expect(try result.get().title == row.title) }
    }
}

private actor CreateWire: HTTPTransport {
    let response: WireResponse
    var requests: [PreparedRequest] = []
    init(_ response: WireResponse) { self.response = response }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        requests.append(request); return .success(response)
    }
}

@Test func createHTTPUsesPostBodyAndSharedResponseAdmission() async throws {
    struct Case: Decodable {
        struct Value: Decodable { let id: String; let title: String }
        let id: String; let status: Int; let body: String
        let expected: Value?; let kind: String?; let code: String?; let fields: [String: String]?
    }
    let command = try CreateNote.make(title: "  Idea  ").get()
    for row in try JSONDecoder().decode([Case].self, from: createFixture("create-responses")) {
        let wire = CreateWire(.init(status: row.status, headers: ["x-request-id": "create-req", "x-correlation-id": "create-corr"], body: Data(row.body.utf8)))
        let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
        let result = try await HTTPNoteCreator(http: http).create(command)
        if let expected = row.expected {
            #expect(try result.get() == Note(id: expected.id, title: expected.title))
        } else {
            guard case .failure(let failure) = result else { Issue.record("Admitted \(row.id)"); continue }
            #expect(failure.kind.rawValue == row.kind)
            #expect(failure.meta.code == row.code)
            #expect(failure.meta.requestID == "create-req" && failure.meta.correlationID == "create-corr")
            if let fields = row.fields {
                guard case .invalid(_, let actual) = failure else { Issue.record("Expected invalid"); continue }
                #expect(actual == fields)
            }
        }
        let request = try #require(await wire.requests.first)
        #expect(await wire.requests.count == 1)
        #expect(request.method == .POST && request.url.path == "/v1/notes")
        #expect(request.headers["idempotency-key"] == nil)
        #expect(try JSONDecoder().decode(JSONValue.self, from: #require(request.body)) == .object(["title": .string(command.title)]))
    }
}

@Test func memoryCreatesUniqueNotesAtomicallyAndReturnsOwnedSnapshots() async throws {
    let memory = InMemoryNoteCreator()
    let command = try CreateNote.make(title: "Idea").get()
    let before = try await memory.list().get()
    async let first = memory.create(command)
    async let second = memory.create(command)
    let pair = try await [first.get(), second.get()]
    #expect(pair[0].id != pair[1].id)
    #expect(pair.allSatisfy { $0.title == "Idea" })
    var snapshot = try await memory.list().get()
    #expect(snapshot.count == 2 && before.isEmpty)
    snapshot.removeAll()
    #expect(try await memory.list().get().count == 2)
}

private final class CreateDefect: Error, @unchecked Sendable {}
private struct ThrowingCreateHTTP: HTTPClient {
    let error: any Error
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> { throw error }
}

@Test func createAdaptersPreserveDefectsAndRefuseAlreadyCanceledWork() async throws {
    let command = try CreateNote.make(title: "Idea").get()
    let defect = CreateDefect()
    do { _ = try await HTTPNoteCreator(http: ThrowingCreateHTTP(error: defect)).create(command); Issue.record("Defect swallowed") }
    catch { #expect(error as? CreateDefect === defect) }
    let memory = InMemoryNoteCreator()
    let providers: [any NoteCreator] = [memory, HTTPNoteCreator(http: ThrowingCreateHTTP(error: CancellationError()))]
    for provider in providers {
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await provider.create(command)
        }
        do { _ = try await task.value; Issue.record("Cancellation swallowed") }
        catch { #expect(error is CancellationError) }
    }
    #expect(try await memory.list().get().isEmpty)
}
