import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Testing

@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] != nil))
@MainActor func nativeGoRegistrationExplicitSignInAndDuplicateNeverChangesCredentials() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let service = try HTTPIdentityRegistration.create(baseURL: base, transport: transport, allowLocalHTTP: true).get()
    let login = "swift-registration-" + UUID().uuidString.lowercased(), password = "native registration private password"
    let command = try RegisterIdentity.make(login: " \(login.uppercased()) ", displayName: " Cafe\u{301} 🧱 ", password: password).get()
    let registered = try await service.register(command).get()
    #expect(registered.confirms(command))
    let vault = MemoryCredentialVault()
    let owner = SessionController(authority: try GoSessionAuthority.create(baseURL: base, transport: transport, allowLocalHTTP: true).get(),
        vault: vault, now: { Int64(Date().timeIntervalSince1970 * 1000) }, onUnexpectedError: { _ in Issue.record("Unexpected registration flow failure") })
    defer { owner.shutdown() }
    #expect(owner.lease == nil && owner.snapshot.phase == .signedOut)
    #expect(try await vault.load().get() == nil)
    await owner.signIn(login: command.login, password: password)?.value
    #expect(owner.lease?.scope.principalID == registered.id && owner.snapshot.account?.name == "Personal")
    let original = try #require(owner.lease)
    let duplicate = try RegisterIdentity.make(login: login, displayName: "Replacement", password: "a different duplicate password").get()
    if case .failure(let failure) = try await service.register(duplicate) { #expect(failure.kind == .conflict && failure.meta.code == "identity.login_exists") }
    else { Issue.record("Duplicate login was registered") }
    #expect(owner.isCurrent(original))
    await owner.signOut()?.value; await owner.signIn(login: login, password: password)?.value
    #expect(owner.lease?.scope.principalID == registered.id && owner.snapshot.principal?.displayName.utf8.elementsEqual(command.displayName.utf8) == true)
    await owner.signOut()?.value
}
