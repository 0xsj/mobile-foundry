import Foundation
import FoundryHTTP
import FoundryKernel
@testable import FoundrySession
import Testing

private let liveBase = "https://example.invalid/v1"
private let liveAccount = "00000000-0000-4000-8000-000000000002"
private func hint(_ kind: String = "ready", account: String = liveAccount, extra: String = "") -> String {
    "{\"type\":\"\(kind)\",\"resource\":\"notes\",\"account_id\":\"\(account)\",\"refetch\":true\(extra)}"
}

@MainActor @Test func liveHintsAdmitOnlyScopedReadyThenChangedAndNeverRetarget() async throws {
    let owner = SessionController(authority: LiveAuthority(), vault: MemoryCredentialVault(), now: { 1 }, onUnexpectedError: { _ in Issue.record("Unexpected failure") })
    await owner.signIn(login: "alex", password: "fixture")?.value
    let lease = try #require(owner.lease)
    struct Vector: Decodable { let frames: [JSONValue]; let hints: Int }
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let vectors = try JSONDecoder().decode([Vector].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/native-lifecycle/hints.json")))
    for vector in vectors {
        let frames = try vector.frames.map { String(decoding: try JSONEncoder().encode($0), as: UTF8.self) }
        let expected = vector.hints
        let feed = try GoAccountLiveHints.create(baseURL: liveBase, session: owner, lease: lease, transport: ScriptedLiveSocket(frames: frames)).get()
        var count = 0
        _ = try await feed.watch { count += 1 }
        #expect(count == expected)
    }
    if case .success = GoAccountLiveHints.create(baseURL: "https://another.invalid/v1", session: owner, lease: lease) { Issue.record("Cross-deployment bearer target admitted") }
    let feed = try GoAccountLiveHints.create(baseURL: liveBase, session: owner, lease: lease, transport: ScriptedLiveSocket(frames: [hint(), hint("changed")])).get()
    var count = 0
    _ = try await feed.watch { count += 1; owner.signOut() }
    #expect(count == 1)
    owner.shutdown()
}

private struct ScriptedLiveSocket: LiveSocketTransport {
    let frames: [String]
    func watch(url: URL, bearer: String, onText: @escaping @Sendable (String) async throws -> Void) async throws {
        #expect(url.absoluteString == "wss://example.invalid/v1/accounts/\(liveAccount)/realtime")
        #expect(bearer == "a1." + String(repeating: "A", count: 43))
        for frame in frames { try await onText(frame) }
    }
}
private struct LiveAuthority: SessionAuthority {
    let id = GoSessionAuthority.deploymentID(liveBase)
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> {
        .success(SessionGrant(authorityID: id, principal: Principal(id: "00000000-0000-4000-8000-000000000001", displayName: "Test")!,
            account: Account(id: liveAccount, name: "Personal")!, accessToken: "a1." + String(repeating: "A", count: 43),
            refreshToken: "r1." + String(repeating: "A", count: 43), expiresAtMillis: 10_000)!)
    }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { .failure(SessionFailures.rejected) }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { .success(()) }
}
