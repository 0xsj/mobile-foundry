import Foundation
import Testing
import FoundryKernel
import FoundryHTTP

private final class StubState: @unchecked Sendable {
    enum Mode { case success, offline, wait }
    private let lock = NSLock()
    private var mode: Mode = .success
    private var begins = 0
    private var stops = 0
    func reset(_ mode: Mode) { lock.lock(); self.mode = mode; begins = 0; stops = 0; lock.unlock() }
    func start() -> Mode { lock.lock(); defer { lock.unlock() }; begins += 1; return mode }
    func stop() { lock.lock(); stops += 1; lock.unlock() }
    var counts: (Int, Int) { lock.lock(); defer { lock.unlock() }; return (begins, stops) }
}
private final class StubProtocol: URLProtocol, @unchecked Sendable {
    static let state = StubState()
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let mode = Self.state.start()
        if case .wait = mode { return }
        if case .offline = mode { client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet)); return }
        let status = 200
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: ["x-request-id": "native-id"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{\"status\":\"ok\"}".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() { Self.state.stop() }
}

@Suite(.serialized) struct TransportTests {
    private func transport() -> URLSessionHTTPTransport {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        return URLSessionHTTPTransport(configuration: config)
    }
    @Test func nativeSuccessPreservesHeaders() async throws {
        StubProtocol.state.reset(.success)
        let transport = transport(); defer { transport.invalidate() }
        let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: transport).get()
        let entity = try await http.requestEntity(.GET, path: "health/live").get()
        #expect(entity.metadata.requestID == "native-id")
        #expect(entity.body.object?["status"] == .string("ok"))
        #expect(StubProtocol.state.counts.0 == 1)
    }
    @Test func nativeOfflineIsUnavailable() async throws {
        StubProtocol.state.reset(.offline)
        let transport = transport(); defer { transport.invalidate() }
        let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: transport).get()
        guard case .failure(let failure) = try await http.request(.GET, path: "health/live") else { Issue.record("Expected unavailable"); return }
        #expect(failure.kind == .unavailable); #expect(StubProtocol.state.counts.0 == 1)
    }
    @Test func deadlineAndCallerCancellationStopNativeTask() async throws {
        StubProtocol.state.reset(.wait)
        let transport = transport(); defer { transport.invalidate() }
        let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid", transport: transport).get()
        guard case .failure(let failure) = try await http.request(.GET, path: "health/live", options: .init(timeoutMilliseconds: 100)) else { Issue.record("Expected timeout"); return }
        #expect(failure.kind == .timeout)
        for _ in 0..<200 where StubProtocol.state.counts.1 == 0 { try await Task.sleep(for: .milliseconds(5)) }
        #expect(StubProtocol.state.counts.1 == 1)
        StubProtocol.state.reset(.wait)
        let request = Task { try await http.request(.GET, path: "health/live") }
        for _ in 0..<200 where StubProtocol.state.counts.0 == 0 { try await Task.sleep(for: .milliseconds(5)) }
        #expect(StubProtocol.state.counts.0 == 1)
        request.cancel()
        do { _ = try await request.value; Issue.record("Cancellation became value") } catch is CancellationError {}
        for _ in 0..<200 where StubProtocol.state.counts.1 == 0 { try await Task.sleep(for: .milliseconds(5)) }
        #expect(StubProtocol.state.counts.1 == 1)
    }
}
