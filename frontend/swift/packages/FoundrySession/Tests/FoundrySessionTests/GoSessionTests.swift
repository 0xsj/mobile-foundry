import Foundation
import FoundryHTTP
import FoundryKernel
@testable import FoundrySession
import Testing

private struct GoFixtures: Decodable {
    struct Row: Decodable { let name: String; let kind: String; let status: Int; let body: JSONValue; let valid: Bool }
    let nowMillis: Int64; let ownerID: String; let grant: JSONValue; let account: JSONValue; let cases: [Row]
}
private func fixtures() throws -> GoFixtures {
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    return try JSONDecoder().decode(GoFixtures.self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/go-session/responses.json")))
}
private func response(_ body: JSONValue = .null, status: Int = 200, etag: String? = nil) throws -> WireResponse {
    WireResponse(status: status, headers: etag.map { ["etag": $0] } ?? [:], body: try JSONEncoder().encode(body))
}
private func changed(_ value: JSONValue, _ key: String, _ replacement: JSONValue) -> JSONValue {
    var body = value.object!; body[key] = replacement; return .object(body)
}
private func rotation(_ fixture: GoFixtures, _ letter: String, generation: Double) -> JSONValue {
    var body = fixture.grant.object!
    body["access_token"] = .string("a1." + String(repeating: letter, count: 42) + "A")
    body["refresh_token"] = .string("r1." + String(repeating: letter, count: 42) + "A")
    body["session_version"] = .number(generation)
    return .object(body)
}
private actor GoTransport: HTTPTransport {
    var requests: [PreparedRequest] = []
    private var replies: [WireResponse]
    let beforeReply: @Sendable (PreparedRequest) async throws -> Void
    init(_ replies: [WireResponse] = [], beforeReply: @escaping @Sendable (PreparedRequest) async throws -> Void = { _ in }) {
        self.replies = replies; self.beforeReply = beforeReply
    }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        requests.append(request); try await beforeReply(request); try Task.checkCancellation()
        guard !replies.isEmpty else { Issue.record("Unexpected network attempt"); return .failure(.unavailable(.init(message: "No reply"))) }
        return .success(replies.removeFirst())
    }
}
private actor GoVault: CredentialVault {
    var credential: StoredCredential?
    var saves = 0
    var failSave = false
    init(_ initial: StoredCredential? = nil) { credential = initial }
    func setFail(_ value: Bool) { failSave = value }
    func load() async throws -> AppResult<StoredCredential?> { .success(credential) }
    func save(_ value: StoredCredential) async throws -> AppResult<Void> {
        saves += 1
        if failSave { return .failure(SessionFailures.storage) }
        credential = value; return .success(())
    }
    func clear() async throws -> AppResult<Void> { credential = nil; return .success(()) }
}
@Test func sharedGoConfigurationAndResponseAdmission() async throws {
    let f = try fixtures()
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    struct Configuration: Decodable { let input: String; let local: Bool; let valid: Bool; let canonical: String?; let authority: String? }
    let configurations = try JSONDecoder().decode([Configuration].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/go-session/configuration.json")))
    let transport = GoTransport()
    for row in configurations {
        switch GoSessionAuthority.create(baseURL: row.input, transport: transport, allowLocalHTTP: row.local) {
        case .success(let value): #expect(row.valid); #expect(value.baseURL == row.canonical); #expect(value.id == row.authority)
        case .failure: #expect(!row.valid)
        }
    }
    #expect(await transport.requests.isEmpty)
    for row in f.cases {
        let entity = Entity(body: row.body, metadata: .init(status: row.status))
        let valid = row.kind == "grant" ? GoSessionWire.tokens(entity, now: f.nowMillis) != nil : GoSessionWire.account(row.body, ownerID: f.ownerID) != nil
        #expect(valid == row.valid, Comment(rawValue: row.name))
    }
}
@Test @MainActor func rotatedCheckpointSurvivesAccountFailureAndSelectionRestoresSameAccount() async throws {
    let f = try fixtures(), vault = GoVault()
    let second = changed(f.account, "id", .string("00000000-0000-4000-8000-000000000102"))
    let r2 = rotation(f, "C", generation: 2), r3 = rotation(f, "D", generation: 3), r4 = rotation(f, "E", generation: 4)
    let transport = GoTransport(try [response(f.grant), response(f.account, status: 201, etag: "\"1\""),
        response(r2), response(.object(["kind": .string("unavailable"), "detail": .string("Account temporarily unavailable")]), status: 503),
        response(r3), response(f.account, etag: "\"1\""), response(second, etag: "\"1\""),
        response(r4), response(second, etag: "\"1\""), response(status: 204)]) { request in
        if request.method == .GET {
            let saved = try await vault.load().get()
            #expect(saved != nil)
            // Rotation must be durable before the next authenticated read.
            #expect(request.headers["authorization"] == "Bearer a1." + String(saved!.refreshToken.dropFirst(3)))
        }
    }
    let authority = try GoSessionAuthority.create(baseURL: "https://api.example.test/v1", transport: transport, now: { f.nowMillis }).get()
    let controller = SessionController(authority: authority, vault: vault, now: { f.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected error") })
    await controller.signIn(login: "alex", password: "a test passphrase")?.value
    let prior = try #require(controller.lease)
    await controller.renew()?.value
    #expect(controller.lease == nil); #expect(controller.snapshot.failure?.kind == .unavailable)
    #expect(try await vault.load().get()?.refreshToken == r2.object?["refresh_token"]?.string)
    await controller.renew()?.value
    #expect(controller.snapshot.phase == .signedIn); #expect(!controller.isCurrent(prior))
    let requests = await transport.requests
    #expect(try JSONDecoder().decode(JSONValue.self, from: requests[4].body!).object?["refresh_token"] == r2.object?["refresh_token"])
    let personalLease = try #require(controller.lease)
    await controller.selectAccount("00000000-0000-4000-8000-000000000102")?.value
    #expect(!controller.isCurrent(personalLease)); #expect(controller.lease?.scope.accountID.hasSuffix("102") == true)
    controller.shutdown()
    let restored = SessionController(authority: authority, vault: vault, now: { f.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected error") })
    await restored.restore()?.value
    #expect(restored.snapshot.phase == .signedIn); #expect(restored.lease?.scope.accountID.hasSuffix("102") == true)
    #expect(await vault.saves == 5)
    await restored.signOut()?.value
    #expect(try await vault.load().get() == nil); #expect(restored.snapshot.phase == .signedOut)
}
@Test @MainActor func failedCheckpointStopsIOAndRecoveryUsesKnownCurrentCredential() async throws {
    let f = try fixtures()
    let transport = GoTransport(try [response(rotation(f, "C", generation: 2)), response(rotation(f, "D", generation: 3)), response(f.account, etag: "\"1\"")])
    let authority = try GoSessionAuthority.create(baseURL: "https://api.example.test/v1", transport: transport, now: { f.nowMillis }).get()
    let scope = SessionScope(authorityID: authority.id, principalID: f.ownerID, accountID: f.account.object!["id"]!.string!)!
    let saved = StoredCredential(scope: scope, refreshToken: f.grant.object!["refresh_token"]!.string!)!
    let vault = GoVault(saved); await vault.setFail(true)
    let controller = SessionController(authority: authority, vault: vault, now: { f.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected error") })
    await controller.restore()?.value
    #expect(controller.lease == nil); #expect(controller.snapshot.failure == SessionFailures.storage)
    #expect(await transport.requests.count == 1)
    await controller.restore()?.value
    #expect(await transport.requests.count == 1) // No rotation while pending persistence still fails.
    await vault.setFail(false)
    await controller.restore()?.value
    #expect(controller.snapshot.phase == .signedIn)
    let requests = await transport.requests
    #expect(try JSONDecoder().decode(JSONValue.self, from: requests[1].body!).object?["refresh_token"] == rotation(f, "C", generation: 2).object?["refresh_token"])
}
@Test @MainActor func issuerMismatchAndRenewedPrincipalMismatchCannotRetargetSavedScope() async throws {
    let f = try fixtures()
    let badPrincipal = changed(f.grant, "principal", .object(["id": .string("00000000-0000-4000-8000-000000000002"), "login": .string("sam"), "display_name": .string("Sam")]))
    let transport = GoTransport(try [response(badPrincipal)])
    let authority = try GoSessionAuthority.create(baseURL: "https://api.example.test/v1", transport: transport, now: { f.nowMillis }).get()
    let other = try GoSessionAuthority.create(baseURL: "https://other.example.test/v1", transport: transport, now: { f.nowMillis }).get()
    let scope = SessionScope(authorityID: other.id, principalID: f.ownerID, accountID: f.account.object!["id"]!.string!)!
    let vault = GoVault(StoredCredential(scope: scope, refreshToken: f.grant.object!["refresh_token"]!.string!)!)
    let controller = SessionController(authority: authority, vault: vault, now: { f.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected error") })
    await controller.restore()?.value
    #expect(await transport.requests.isEmpty); #expect(try await vault.load().get() == nil)
    let correct = SessionScope(authorityID: authority.id, principalID: scope.principalID, accountID: scope.accountID)!
    _ = try await vault.save(StoredCredential(scope: correct, refreshToken: f.grant.object!["refresh_token"]!.string!)!)
    await controller.restore()?.value
    #expect(controller.lease == nil); #expect(try await vault.load().get() == nil); #expect(await transport.requests.count == 1)
}
@Test func directoryRefusesForeignOrDuplicateRowsAndForwardsCursor() async throws {
    let f = try fixtures()
    let pages: [JSONValue] = [
        .object(["accounts": .array([f.account]), "next_cursor": .string("opaque")]),
        .object(["accounts": .array([changed(f.account, "owner_id", .string("00000000-0000-4000-8000-000000000002"))]), "next_cursor": .null]),
        .object(["accounts": .array([f.account, f.account]), "next_cursor": .null])]
    let transport = GoTransport(try pages.map { try response($0) })
    let client = try DefaultHTTPClient.create(baseURL: "https://api.example.test/v1", transport: transport).get()
    let directory = HTTPAccountDirectory(client: client, principalID: f.ownerID)
    #expect(try await directory.list(limit: 1).get().nextCursor == "opaque")
    for cursor in ["opaque", "opaque"] { if case .success = try await directory.list(cursor: cursor, limit: 2) { Issue.record("Invalid account page admitted") } }
    #expect(await transport.requests[1].url.query?.contains("cursor=opaque") == true)
}
