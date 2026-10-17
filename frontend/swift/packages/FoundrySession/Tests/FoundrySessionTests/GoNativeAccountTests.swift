import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import Testing

@Test(.enabled(if: ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] != nil))
@MainActor func nativeGoWorkspaceCreationCASOwnershipAndExplicitSelection() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
    let transport = URLSessionHTTPTransport(); defer { transport.invalidate() }
    let register = try HTTPIdentityRegistration.create(baseURL: base, transport: transport, allowLocalHTTP: true).get()
    let login = "swift-workspace-" + UUID().uuidString.lowercased(), password = "owned workspace passphrase"
    _ = try await register.register(RegisterIdentity.make(login: login, displayName: "Workspace owner", password: password).get()).get()
    let authority = try GoSessionAuthority.create(baseURL: base, transport: transport, allowLocalHTTP: true).get()
    let owner = SessionController(authority: authority, vault: MemoryCredentialVault(), now: { Int64(Date().timeIntervalSince1970 * 1000) }, onUnexpectedError: { _ in Issue.record("Unexpected workspace failure") })
    defer { owner.shutdown() }
    await owner.signIn(login: login, password: password)?.value
    let lease = try #require(owner.lease)
    let client = try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: owner, lease: lease, allowLocalHTTP: true).get()
    let service = HTTPAccountManagement(client: client, principalID: lease.scope.principalID)
    let personal = try await service.read(lease.scope.accountID).get()
    let name = try AccountName.make(" Native project 🧱 ").get()
    let project = try await service.create(name).get()
    #expect(owner.isCurrent(lease) && owner.snapshot.account?.id == personal.account.id && project.account.id != personal.account.id)
    let renamed = try await service.rename(project, to: AccountName.make("Renamed project").get()).get()
    #expect(renamed.version == 2)
    if case .failure(let failure) = try await service.rename(project, to: name) { #expect(failure.kind == .conflict) } else { Issue.record("Stale rename accepted") }
    #expect(try await service.read(project.account.id).get().version == 2)
    let otherLogin = "swift-other-" + UUID().uuidString.lowercased()
    _ = try await register.register(RegisterIdentity.make(login: otherLogin, displayName: "Other owner", password: password).get()).get()
    let other = SessionController(authority: authority, vault: MemoryCredentialVault(), now: { Int64(Date().timeIntervalSince1970 * 1000) }, onUnexpectedError: { _ in Issue.record("Unexpected foreign owner failure") })
    defer { other.shutdown() }
    await other.signIn(login: otherLogin, password: password)?.value
    let otherLease = try #require(other.lease)
    let foreign = HTTPAccountManagement(client: try AuthenticatedHTTPClient.create(baseURL: base, transport: transport, session: other, lease: otherLease, allowLocalHTTP: true).get(), principalID: otherLease.scope.principalID)
    if case .failure(let failure) = try await foreign.read(project.account.id) { #expect(failure.kind == .forbidden) } else { Issue.record("Foreign account read") }
    await owner.selectAccount(project.account.id)?.value
    #expect(owner.snapshot.account?.name == "Renamed project" && !owner.isCurrent(lease))
    if case .failure(let failure) = try await service.create(name) { #expect(failure.kind == .canceled) } else { Issue.record("Old lease wrote") }
    await other.signOut()?.value; await owner.signOut()?.value
}
