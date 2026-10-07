import Foundation
import Testing
import FoundryKernel
@testable import FoundryHTTP

func fixtureURL(_ name: String) -> URL {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    return root.appendingPathComponent("contracts/fixtures/http/" + name)
}
struct ResponseFixture: Decodable {
    let id: String; let status: Int; let headers: [String: String]; let body: String?; let method: String
    let expectedKind: String?; let expectedCode: String?; let expectedMessage: String?
    let expectedFields: [String: String]?; let expectedRequestID: String?; let expectedCorrelationID: String?
    let expectedRetryMs: Int64?; let correlationID: String?
}
actor StubTransport: HTTPTransport {
    let response: WireResponse
    var requests: [PreparedRequest] = []
    init(_ response: WireResponse) { self.response = response }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        requests.append(request); return .success(response)
    }
}
actor WaitingTransport: HTTPTransport {
    var started = false
    var cleaned = false
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        started = true
        defer { cleaned = true }
        try await Task.sleep(for: .seconds(60))
        return .success(.init(status: 200, body: Data("{}".utf8)))
    }
}
private final class Defect: Error, @unchecked Sendable {}
private struct BrokenTransport: HTTPTransport {
    let error: Defect
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> { throw error }
}

@Test func sharedResponseFixtures() async throws {
    let cases = try JSONDecoder().decode([ResponseFixture].self, from: Data(contentsOf: fixtureURL("responses.json")))
    #expect(cases.count == 38)
    for item in cases {
        let stub = StubTransport(.init(status: item.status, headers: item.headers, body: item.body.map { Data($0.utf8) }))
        let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1/", transport: stub).get()
        let result = try await client.requestEntity(try #require(HTTPMethod(rawValue: item.method)), path: "health/live", options: .init(correlationID: item.correlationID))
        if let kind = item.expectedKind {
            guard case .failure(let failure) = result else { Issue.record("Expected failure: \(item.id)"); continue }
            #expect(failure.kind.rawValue == kind, "\(item.id)")
            #expect(failure.meta.code == item.expectedCode, "\(item.id)")
            if let message = item.expectedMessage { #expect(failure.meta.message == message) }
            if let id = item.expectedRequestID { #expect(failure.meta.requestID == id) }
            if let id = item.expectedCorrelationID { #expect(failure.meta.correlationID == id) }
            if case .invalid(_, let fields) = failure, let expected = item.expectedFields { #expect(fields == expected) }
            if case .rateLimited(_, let delay) = failure { #expect(delay?.milliseconds == item.expectedRetryMs) }
        } else {
            let entity = try result.get()
            #expect(entity.metadata.status == item.status)
            if item.id == "healthy" {
                #expect(entity.metadata.etag == "\"v1\"")
                #expect(entity.metadata.requestID == "req-health")
                #expect(entity.metadata.correlationID == "remote")
            }
            if ["no-content", "reset-content", "head", "empty", "literal-null"].contains(item.id) { #expect(entity.body == .null) }
        }
    }
}

@Test func sharedPathsAndRequestPreparation() async throws {
    struct PathCase: Decodable { let path: String; let allowed: Bool }
    let paths = try JSONDecoder().decode([PathCase].self, from: Data(contentsOf: fixtureURL("paths.json")))
    for item in paths {
        let stub = StubTransport(.init(status: 204, body: Data()))
        let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: stub).get()
        let answer = try await client.request(.GET, path: item.path)
        let requests = await stub.requests
        #expect(requests.count == (item.allowed ? 1 : 0), "\(item.path)")
        if !item.allowed { guard case .failure(let failure) = answer else { Issue.record("Path admitted"); continue }; #expect(failure.meta.code == "http.invalid_request") }
        else { #expect(requests.first.map { URLComponents(url: $0.url, resolvingAgainstBaseURL: false)?.percentEncodedPath.hasPrefix("/v1/") == true } == true) }
    }
    let stub = StubTransport(.init(status: 204, body: Data()))
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: stub, headers: ["X-Mode": "common"]).get()
    _ = try await client.request(.POST, path: "/records", options: .init(correlationID: "corr", query: [.init("tag", "a b"), .init("tag", "c+")], headers: ["x-mode": "call"], body: .object(["name": .string("item")]), ifMatch: "v1", idempotencyKey: "once"))
    let request = try #require(await stub.requests.first)
    #expect(request.url.path == "/v1/records")
    #expect(URLComponents(url: request.url, resolvingAgainstBaseURL: false)?.queryItems?.map(\.value) == ["a b", "c+"])
    #expect(request.headers["x-mode"] == "call")
    #expect(request.headers["accept"] == "application/json, application/problem+json")
    #expect(request.headers["content-type"] == "application/json")
    #expect(request.headers["x-correlation-id"] == "corr")
    #expect(request.headers["if-match"] == "v1")
    #expect(request.headers["idempotency-key"] == "once")
    #expect(try JSONDecoder().decode(JSONValue.self, from: #require(request.body)) == .object(["name": .string("item")]))
}

@Test func refusedConfigurationAndBodiesNeverSend() async throws {
    let stub = StubTransport(.init(status: 200, body: Data("{}".utf8)))
    for base in ["ftp://example.invalid", "https://user:pass@example.invalid", "https://example.invalid?v=1", "https://example.invalid/#x", "https://example.invalid/a/../b", " https://example.invalid", "https://example.invalid:0"] {
        guard case .failure(let failure) = DefaultHTTPClient.create(baseURL: base, transport: stub) else { Issue.record("Base admitted: \(base)"); continue }
        #expect(failure.meta.code == "http.invalid_configuration")
    }
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: stub).get()
    for options in [RequestOptions(body: .object([:])), RequestOptions(headers: ["bad name": "x"]), RequestOptions(headers: ["x-test": "line\nnew"]), RequestOptions(timeoutMilliseconds: -1)] {
        guard case .failure = try await client.request(.GET, path: "health/live", options: options) else { Issue.record("Request admitted"); continue }
    }
    guard case .failure = try await client.request(.POST, path: "records", options: .init(body: .array([]))) else { Issue.record("Array body admitted"); return }
    #expect(await stub.requests.isEmpty)
}

@Test func sharedRetryAfterFixtures() throws {
    struct Entry: Decodable { let value: String; let expectedMs: Int64? }
    struct Fixture: Decodable { let nowMilliseconds: Int64; let cases: [Entry] }
    let fixture = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: fixtureURL("retry-after.json")))
    for entry in fixture.cases {
        #expect(ProblemDecoder.retryAfter(entry.value, now: Date(timeIntervalSince1970: Double(fixture.nowMilliseconds) / 1000))?.milliseconds == entry.expectedMs, "\(entry.value)")
    }
}

@Test func deadlineCancelsAndJoinsTransport() async throws {
    let stub = WaitingTransport()
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: stub, timeoutMilliseconds: 20).get()
    guard case .failure(let failure) = try await client.request(.GET, path: "health/live") else { Issue.record("Expected timeout"); return }
    #expect(failure.kind == .timeout)
    #expect(await stub.started)
    #expect(await stub.cleaned)
}

@Test func callerCancellationThrowsAndCleansUp() async throws {
    let stub = WaitingTransport()
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: stub).get()
    let task = Task { try await client.request(.GET, path: "health/live") }
    while !(await stub.started) { await Task.yield() }
    task.cancel()
    do { _ = try await task.value; Issue.record("Cancellation became a result") } catch is CancellationError {}
    #expect(await stub.cleaned)
}

@Test func zeroBudgetAndPreCanceledTaskNeverSend() async throws {
    let stub = StubTransport(.init(status: 204, body: Data()))
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: stub).get()
    guard case .failure(let failure) = try await client.request(.GET, path: "health/live", options: .init(timeoutMilliseconds: 0)) else { Issue.record("Expected timeout"); return }
    #expect(failure.kind == .timeout)
    let task = Task {
        withUnsafeCurrentTask { $0?.cancel() }
        return try await client.request(.GET, path: "health/live")
    }
    do { _ = try await task.value; Issue.record("Expected cancellation") } catch is CancellationError {}
    #expect(await stub.requests.isEmpty)
}

@Test func unexpectedErrorKeepsIdentity() async throws {
    let defect = Defect()
    let client = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: BrokenTransport(error: defect)).get()
    do { _ = try await client.request(.GET, path: "health/live"); Issue.record("Defect classified") }
    catch { #expect((error as? Defect) === defect) }
}
