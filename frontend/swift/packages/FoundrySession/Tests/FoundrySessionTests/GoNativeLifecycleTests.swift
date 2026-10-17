import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Testing

@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_LIFECYCLE_MAIL_BASE"] != nil))
@MainActor func nativeGoLifecycleMailPasswordRevocationAndRealSocket() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let mailbox = try #require(ProcessInfo.processInfo.environment["GO_LIFECYCLE_MAIL_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let http = try DefaultHTTPClient.create(baseURL: base, transport: transport).get()
    let login = "swift-life-" + UUID().uuidString.lowercased(), email = login + "@example.com"
    let password = "native lifecycle first password", next = "native lifecycle second password", final = "native lifecycle third password"
    _ = try await http.requestEntity(.POST, path: "identity/register", options: .init(body: .object(["login": .string(login), "display_name": .string("Swift lifecycle"), "password": .string(password)]))).get()
    let authority = try GoSessionAuthority.create(baseURL: base, transport: transport, allowLocalHTTP: true).get()
    let owner = SessionController(authority: authority, vault: MemoryCredentialVault(), now: { Int64(Date().timeIntervalSince1970 * 1000) }, onUnexpectedError: { _ in Issue.record("Unexpected lifecycle error") })
    defer { owner.shutdown() }
    await owner.signIn(login: login, password: password)?.value
    let lease = try #require(owner.lease)
    let auth = try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: owner, lease: lease, allowLocalHTTP: true).get()
    let recovery = try HTTPIdentityRecovery.create(baseURL: base, transport: transport, authenticatedClient: auth, allowLocalHTTP: true).get()
    let feed = try GoAccountLiveHints.create(baseURL: base, session: owner, lease: lease, allowLocalHTTP: true).get()
    var hints = 0
    let watch = Task { try await feed.watch { hints += 1 } }
    defer { watch.cancel() }
    func waitForHints(_ count: Int) async throws {
        for _ in 0..<250 { if hints >= count { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Native socket did not receive the expected frame")
    }
    try await waitForHints(1)
    _ = try await auth.requestEntity(.POST, path: "accounts/\(lease.scope.accountID)/notes", options: .init(body: .object(["title": .string("Native socket change")]), idempotencyKey: UUID().uuidString.lowercased())).get()
    try await waitForHints(2); watch.cancel(); _ = try? await watch.value
    try await recovery.requestVerification(email: email, password: password).get()
    let verify = try await capturedCode(mailbox, email: email, purpose: "verify_email")
    try await recovery.verify(token: verify).get()
    #expect(try await recovery.contact().get().email == email)
    try await recovery.requestReset(email: email).get()
    let reset = try await capturedCode(mailbox, email: email, purpose: "reset_password")
    try await recovery.reset(token: reset, password: next).get()
    if case .failure(let f) = try await auth.requestEntity(.GET, path: "identity/me") { #expect(f.kind == .unauthenticated) }
    else { Issue.record("Reset did not revoke old access") }
    await owner.signOut()?.value; await owner.signIn(login: login, password: next)?.value
    let changedClient = try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: owner, lease: try #require(owner.lease), allowLocalHTTP: true).get()
    let changedRecovery = try HTTPIdentityRecovery.create(baseURL: base, transport: transport, authenticatedClient: changedClient, allowLocalHTTP: true).get()
    try await changedRecovery.change(currentPassword: next, newPassword: final).get()
    await owner.signOut()?.value; await owner.signIn(login: login, password: final)?.value
    #expect(owner.lease != nil)
    if case .success = try await changedRecovery.reset(token: reset, password: password) { Issue.record("Consumed reset code replayed") }
    await owner.signOut()?.value
}

private func capturedCode(_ mailbox: String, email: String, purpose: String) async throws -> String {
    var url = URLComponents(string: mailbox + "/code")!
    url.queryItems = [.init(name: "email", value: email), .init(name: "purpose", value: purpose)]
    for _ in 0..<100 {
        let (data, response) = try await URLSession.shared.data(from: url.url!)
        if (response as? HTTPURLResponse)?.statusCode == 200, let token = try JSONDecoder().decode(JSONValue.self, from: data).object?["token"]?.string { return token }
        try await Task.sleep(for: .milliseconds(50))
    }
    throw Failure.unavailable(.init(message: "Owned SMTP fixture did not capture the code."))
}
