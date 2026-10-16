import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Testing

@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] != nil))
@MainActor func nativeGoSessionSignInScopeRotationReuseAndSignOut() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let http = try DefaultHTTPClient.create(baseURL: base, transport: transport).get()
    let login = "swift-" + UUID().uuidString.lowercased(), samLogin = "swift-" + UUID().uuidString.lowercased()
    let password = "native integration passphrase"
    for user in [login, samLogin] {
        let entity = try await http.requestEntity(.POST, path: "identity/register", options: .init(body: .object(["login": .string(user), "display_name": .string(user == login ? "Swift Alex" : "Swift Sam"), "password": .string(password)]))).get()
        #expect(entity.metadata.status == 201)
    }
    let authority = try GoSessionAuthority.create(baseURL: base, transport: transport, allowLocalHTTP: true).get()
    let vault = MemoryCredentialVault()
    let now: @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }
    let controller = SessionController(authority: authority, vault: vault, now: now, onUnexpectedError: { _ in Issue.record("Unexpected Go integration failure") })
    await controller.signIn(login: login, password: password)?.value
    #expect(controller.snapshot.phase == .signedIn)
    let firstLease = try #require(controller.lease), oldCredential = try #require(try await vault.load().get())
    let client = try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: controller, lease: firstLease, allowLocalHTTP: true).get()
    let workspace = try await client.requestEntity(.POST, path: "accounts", options: .init(body: .object(["name": .string("Swift workspace")]))).get()
    let workspaceID = try #require(workspace.body.object?["id"]?.string)
    let directory = HTTPAccountDirectory(client: client, principalID: firstLease.scope.principalID)
    let page = try await directory.list(limit: 1).get()
    let cursor: String = try #require(page.nextCursor)
    let continuation = try await directory.list(cursor: cursor, limit: 1).get()
    #expect(Set((page.accounts + continuation.accounts).map(\.id)) == Set([firstLease.scope.accountID, workspaceID]))
    await controller.selectAccount(workspaceID)?.value
    #expect(!controller.isCurrent(firstLease)); #expect(controller.lease?.scope.accountID == workspaceID)
    await controller.renew()?.value
    #expect(controller.snapshot.phase == .signedIn); #expect(try await vault.load().get()?.scope.accountID == workspaceID)
    controller.shutdown()
    let restored = SessionController(authority: authority, vault: vault, now: now, onUnexpectedError: { _ in Issue.record("Unexpected Go integration failure") })
    await restored.restore()?.value
    #expect(restored.snapshot.phase == .signedIn); #expect(restored.lease?.scope.accountID == workspaceID)
    let sam = SessionController(authority: authority, vault: MemoryCredentialVault(), now: now, onUnexpectedError: { _ in Issue.record("Unexpected Go integration failure") })
    await sam.signIn(login: samLogin, password: password)?.value
    let samClient = try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: sam, lease: try #require(sam.lease), allowLocalHTTP: true).get()
    if case .failure(let failure) = try await samClient.requestEntity(.GET, path: "accounts/\(workspaceID)") { #expect(failure.kind == .forbidden) } else { Issue.record("Foreign account admitted") }
    let replay = try await http.requestEntity(.POST, path: "identity/renew", options: .init(body: .object(["refresh_token": .string(oldCredential.refreshToken)])))
    if case .failure(let failure) = replay { #expect(failure.kind == .unauthenticated) } else { Issue.record("Consumed refresh replay accepted") }
    let currentClient = try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: restored, lease: try #require(restored.lease), allowLocalHTTP: true).get()
    if case .failure(let failure) = try await currentClient.requestEntity(.GET, path: "accounts/\(workspaceID)") { #expect(failure.kind == .unauthenticated) } else { Issue.record("Revoked session admitted") }
    #expect(restored.snapshot.phase == .signedIn) // HTTP is one attempt, not automatic session mutation.
    await restored.renew()?.value
    #expect(restored.lease == nil); #expect(try await vault.load().get() == nil)
    await restored.signIn(login: login, password: password)?.value
    let signedIn = try #require(restored.lease), access = try #require(restored.accessToken(for: signedIn))
    await restored.signOut()?.value
    #expect(try await vault.load().get() == nil)
    if case .failure(let failure) = try await http.requestEntity(.GET, path: "accounts/\(workspaceID)", options: .init(headers: ["authorization": "Bearer \(access)"])) { #expect(failure.kind == .unauthenticated) } else { Issue.record("Sign-out did not revoke access") }
    await sam.signOut()?.value
}
