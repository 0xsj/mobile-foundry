import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices

private actor Stub: HTTPTransport {
    let response: WireResponse
    var paths: [String] = []
    init(_ response: WireResponse) { self.response = response }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        paths.append(request.url.path); return .success(response)
    }
}
@Test func serviceValidatesSharedHealthFixtures() async throws {
    struct Fixture: Decodable { let id: String; let status: Int; let headers: [String: String]; let body: String?; let expectedHealthy: Bool? }
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let cases = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/http/responses.json")))
    for item in cases where item.expectedHealthy != nil {
        let stub = Stub(.init(status: item.status, headers: item.headers, body: item.body.map { Data($0.utf8) }))
        let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: stub).get()
        let result = try await HealthService(http: http).live()
        if item.expectedHealthy == true {
            let health = try result.get()
            #expect(health.status == .ok); #expect(health.version == "\"v1\"")
            #expect(health.requestID == "req-health")
        } else {
            guard case .failure(let failure) = result else { Issue.record("Shape admitted: \(item.id)"); continue }
            #expect(failure.meta.code == "http.invalid_response")
        }
        #expect(await stub.paths == ["/v1/health/live"])
    }
}
@Test func readyPreservesFailureMetadata() async throws {
    let stub = Stub(.init(status: 503, headers: ["x-request-id": "req"], body: Data("{\"detail\":\"Down\"}".utf8)))
    let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: stub).get()
    guard case .failure(let failure) = try await HealthService(http: http).ready(options: .init(correlationID: "corr")) else { Issue.record("Failure discarded"); return }
    #expect(failure.kind == .unavailable); #expect(failure.meta.requestID == "req"); #expect(failure.meta.correlationID == "corr")
    #expect(await stub.paths == ["/v1/health/ready"])
}
