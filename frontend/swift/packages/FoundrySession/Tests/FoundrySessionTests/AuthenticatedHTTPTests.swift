import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Testing

private actor RecordingTransport: HTTPTransport {
    var requests: [PreparedRequest] = []
    let status: Int
    init(status: Int = 200) { self.status = status }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        requests.append(request)
        return .success(.init(status: status, headers: ["content-type": status == 401 ? "application/problem+json" : "application/json", "x-request-id": "one"],
            body: Data((status == 401 ? #"{"type":"about:blank","status":401,"title":"Unauthorized"}"# : #"{"ok":true}"#).utf8)))
    }
}
private actor TokenAuthority: SessionAuthority {
    nonisolated let id = "catalog-demo.v1"
    let token: String
    init(_ token: String) { self.token = token }
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> {
        .success(SessionGrant(authorityID: id, principal: Principal(id: "alex", displayName: "Alex")!, account: Account(id: "alex-personal", name: "Alex")!,
            accessToken: token, refreshToken: "refresh.secret", expiresAtMillis: 1_700_000_060_000)!)
    }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { try await signIn(SignIn.make(login: "alex", password: "ok").get()) }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { .success(()) }
}
@MainActor private func signedIn(_ token: String = "access.secret") async -> (SessionController, MemoryCredentialVault, ManualSessionClock) {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = MemoryCredentialVault()
    let controller = SessionController(authority: TokenAuthority(token), vault: vault, now: { clock.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected session error") })
    await controller.signIn(login: "alex", password: "ok")?.value
    return (controller, vault, clock)
}
@Test @MainActor func bearerFixturesAndCallerHeaderAdmissionAvoidIO() async throws {
    struct Row: Decodable { let token: String; let valid: Bool }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/account-access/bearer.json")))
    for row in rows {
        let (session, _, _) = await signedIn(row.token), transport = RecordingTransport()
        let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test", transport: transport, session: session, lease: #require(session.lease)).get()
        let answer = try await client.requestEntity(.GET, path: "notes")
        #expect(((try? answer.get()) != nil) == row.valid)
        #expect(await transport.requests.count == (row.valid ? 1 : 0))
        _ = try await client.requestEntity(.GET, path: "notes", options: .init(headers: ["AuThOrIzAtIoN": "other"]))
        #expect(await transport.requests.count == (row.valid ? 1 : 0))
    }
}
@Test @MainActor func ownsAuthorizationPreservesOptionsAnd401DoesNotRenewOrClear() async throws {
    let (session, vault, _) = await signedIn(), transport = RecordingTransport(status: 401), lease = try #require(session.lease)
    let before = try await vault.load().get()
    let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test/api", transport: transport, session: session, lease: lease).get()
    let answer = try await client.requestEntity(.PATCH, path: "notes/one", options: .init(timeoutMilliseconds: 500, correlationID: "trace", query: [.init("tag", "one"), .init("tag", "two")], headers: ["x-custom": "yes"], body: .object(["title": .string("new")]), ifMatch: "version-1", idempotencyKey: "command-1"))
    guard case .failure(let failure) = answer else { Issue.record("Expected failure"); return }
    #expect(failure.kind == .unauthenticated); #expect(session.lease == lease); #expect(try await vault.load().get() == before)
    let requests = await transport.requests; #expect(requests.count == 1); let request = try #require(requests.first)
    #expect(request.method == .PATCH); #expect(request.url.path == "/api/notes/one"); #expect(request.url.query == "tag=one&tag=two")
    #expect(request.headers["authorization"] == "Bearer access.secret"); #expect(request.headers["if-match"] == "version-1")
    #expect(request.headers["idempotency-key"] == "command-1"); #expect(request.headers["x-correlation-id"] == "trace"); #expect(request.headers["x-custom"] == "yes")
    #expect(try JSONSerialization.jsonObject(with: #require(request.body)) as? [String: String] == ["title": "new"])
}
@Test @MainActor func secureOriginAndRelativePathAdmission() async throws {
    let (session, _, _) = await signedIn(), transport = RecordingTransport(), lease = try #require(session.lease)
    for url in ["http://example.test", "http://localhost", "ftp://localhost"] {
        #expect((try? AuthenticatedHTTPClient.create(baseURL: url, transport: transport, session: session, lease: lease).get()) == nil)
    }
    #expect((try? AuthenticatedHTTPClient.create(baseURL: "http://example.test", transport: transport, session: session, lease: lease, allowLocalHTTP: true).get()) == nil)
    for url in ["http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080"] {
        #expect((try? AuthenticatedHTTPClient.create(baseURL: url, transport: transport, session: session, lease: lease, allowLocalHTTP: true).get()) != nil)
    }
    let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test/api", transport: transport, session: session, lease: lease).get()
    for path in ["https://other.test", "//other.test", "../other"] { #expect((try? await client.requestEntity(.GET, path: path).get()) == nil) }
    #expect(await transport.requests.isEmpty)
}
@Test @MainActor func oldOwnerRenewalExpiryAndSignOutRejectWithoutIO() async throws {
    let (session, vault, clock) = await signedIn(), transport = RecordingTransport(), lease = try #require(session.lease)
    let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test", transport: transport, session: session, lease: lease).get()
    await session.renew()?.value
    #expect(try await client.requestEntity(.POST, path: "notes", options: .init(body: .object([:]))) == .failure(SessionFailures.scopeChanged))
    let renewed = try #require(session.lease), next = try AuthenticatedHTTPClient.create(baseURL: "https://example.test", transport: transport, session: session, lease: renewed).get()
    clock.advance(milliseconds: 60_000)
    #expect(try await next.requestEntity(.GET, path: "notes") == .failure(SessionFailures.scopeChanged))
    await session.signOut()?.value
    #expect(try await next.requestEntity(.GET, path: "notes") == .failure(SessionFailures.scopeChanged))
    let other = SessionController(authority: TokenAuthority("access.secret"), vault: vault, now: { clock.nowMillis }, onUnexpectedError: { _ in })
    #expect(other.accessToken(for: lease) == nil); #expect(await transport.requests.isEmpty)
}
private actor GateTransport: HTTPTransport {
    enum Result: CaseIterable { case success, failure, defect }
    let result: Result
    private var entered = false
    private var gate: CheckedContinuation<Void, Never>?
    init(_ result: Result) { self.result = result }
    func didEnter() -> Bool { entered }
    func release() { gate?.resume(); gate = nil }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        await withCheckedContinuation { gate = $0; entered = true }
        switch result {
        case .success: return .success(.init(status: 200, body: Data("{}".utf8)))
        case .failure: return .failure(.unavailable(.init(message: "Old account error")))
        case .defect: throw URLError(.badServerResponse)
        }
    }
}
@Test @MainActor func accountClosureSuppressesLateSuccessFailureAndDefect() async throws {
    for result in GateTransport.Result.allCases {
        let (session, _, _) = await signedIn(), transport = GateTransport(result), lease = try #require(session.lease)
        let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test", transport: transport, session: session, lease: lease).get()
        let task = Task { try await client.requestEntity(.GET, path: "notes") }
        while !(await transport.didEnter()) { await Task.yield() }
        await session.signOut()?.value; await transport.release()
        #expect(try await task.value == .failure(SessionFailures.scopeChanged))
    }
}
@Test @MainActor func nativeCancellationRemainsCancellationAfterIgnoringTransport() async throws {
    let (session, _, _) = await signedIn(), transport = GateTransport(.success), lease = try #require(session.lease)
    let client = try AuthenticatedHTTPClient.create(baseURL: "https://example.test", transport: transport, session: session, lease: lease).get()
    let task = Task { try await client.requestEntity(.GET, path: "notes") }
    while !(await transport.didEnter()) { await Task.yield() }
    task.cancel(); await transport.release()
    do { _ = try await task.value; Issue.record("Expected cancellation") } catch is CancellationError {}
}
