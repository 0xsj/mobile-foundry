import Foundation
import FoundryHTTP
import FoundrySession
import Testing

private final class RequestRecord: @unchecked Sendable {
    private let lock = NSLock()
    private var requests: [URLRequest] = []
    func append(_ request: URLRequest) { lock.lock(); requests.append(request); lock.unlock() }
    func clear() { lock.lock(); requests = []; lock.unlock() }
    var snapshot: [URLRequest] { lock.lock(); defer { lock.unlock() }; return requests }
}
private final class BearerProtocol: URLProtocol, @unchecked Sendable {
    static let record = RequestRecord()
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.record.append(request)
        let response = HTTPURLResponse(url: request.url!, statusCode: 401, httpVersion: "HTTP/1.1", headerFields: ["content-type": "application/problem+json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"type":"about:blank","status":401,"title":"Unauthorized"}"#.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
@Suite(.serialized) struct AuthenticatedNativeTransportTests {
    @Test @MainActor func urlSessionReceivesOneOwnedBearerRequestAnd401KeepsSession() async throws {
        BearerProtocol.record.clear()
        let clock = ManualSessionClock(nowMillis: 1_700_000_000_000)
        let controller = SessionController(authority: DemoSessionAuthority(now: { clock.nowMillis }), vault: MemoryCredentialVault(), now: { clock.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected session error") })
        await controller.signIn(login: "alex@example.test", password: "catalog-demo")?.value
        let lease = try #require(controller.lease), config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [BearerProtocol.self]
        let transport = URLSessionHTTPTransport(configuration: config); defer { transport.invalidate() }
        let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test/api", transport: transport, session: controller, lease: lease).get()
        let answer = try await client.requestEntity(.GET, path: "notes")
        if case .failure(let failure) = answer { #expect(failure.kind == .unauthenticated) } else { Issue.record("Expected rejection") }
        #expect(controller.lease == lease)
        let requests = BearerProtocol.record.snapshot; #expect(requests.count == 1)
        #expect(requests.first?.value(forHTTPHeaderField: "authorization") == "Bearer \(controller.accessToken(for: lease)!)")
        #expect(requests.first?.url?.path == "/api/notes")
    }
}
