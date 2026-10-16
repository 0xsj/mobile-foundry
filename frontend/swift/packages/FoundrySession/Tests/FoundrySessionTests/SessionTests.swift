import Foundation
import FoundryKernel
@testable import FoundrySession
import Testing

private struct AdmissionFixture: Decodable {
    struct Command: Decodable { let login: String; let password: String; let valid: Bool; let normalizedLogin: String?; let field: String? }
    struct Scope: Decodable { let authority: String; let principal: String; let account: String; let valid: Bool }
    struct Expiry: Decodable { let value: Int64; let valid: Bool }
    let commands: [Command]; let scopes: [Scope]; let expiries: [Expiry]
}
@Test func sharedAdmissionAndVersionedCredentials() throws {
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let bytes = try Data(contentsOf: root.appendingPathComponent("contracts/fixtures/session/admission.json"))
    let fixture = try JSONDecoder().decode(AdmissionFixture.self, from: bytes)
    for row in fixture.commands {
        switch SignIn.make(login: row.login, password: row.password) {
        case .success(let value): #expect(row.valid); #expect(value.login == row.normalizedLogin); #expect(value.password == row.password); #expect(!String(reflecting: value).contains(row.password))
        case .failure(let failure): #expect(!row.valid); if case .invalid(_, let fields) = failure { #expect(fields[row.field!] != nil) } else { Issue.record("Wrong failure") }
        }
    }
    for row in fixture.scopes { #expect((SessionScope(authorityID: row.authority, principalID: row.principal, accountID: row.account) != nil) == row.valid) }
    let rows = (try JSONSerialization.jsonObject(with: bytes) as! [String: Any])["credentials"] as! [[String: Any]]
    for row in rows {
        switch StoredCredential.decode(try JSONSerialization.data(withJSONObject: row)) {
        case .success(let credential): #expect(row["valid"] as! Bool); #expect(try StoredCredential.decode(credential.encoded()).get() == credential); #expect(!String(reflecting: credential).contains(credential.refreshToken))
        case .failure: #expect(!(row["valid"] as! Bool))
        }
    }
    for row in fixture.expiries { #expect((makeGrant(expiry: row.value) != nil) == row.valid) }
    #expect(StoredCredential.decode(Data("not json".utf8)) == .failure(SessionFailures.invalidCredential))
}
private func makeGrant(person: String = "alex", expiry: Int64 = 1_700_000_060_000) -> SessionGrant? {
    SessionGrant(authorityID: "catalog-demo.v1", principal: Principal(id: person, displayName: person)!, account: Account(id: "\(person)-personal", name: person)!,
                 accessToken: "access.secret", refreshToken: "refresh.secret", expiresAtMillis: expiry)
}
private actor StubAuthority: SessionAuthority {
    nonisolated let id = "catalog-demo.v1"
    var answer: AppResult<SessionGrant>
    var revokeAnswer: AppResult<Void> = .success(())
    var calls = 0
    init(_ answer: AppResult<SessionGrant>) { self.answer = answer }
    func set(_ answer: AppResult<SessionGrant>) { self.answer = answer }
    func setRevoke(_ answer: AppResult<Void>) { revokeAnswer = answer }
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> { calls += 1; return answer }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { calls += 1; return answer }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { revokeAnswer }
}
private actor FailingVault: CredentialVault {
    let failure: Failure; let loadFails: Bool; let saveFails: Bool; let clearFails: Bool
    init(load: Bool = false, save: Bool = false, clear: Bool = false) { failure = SessionFailures.storage; loadFails = load; saveFails = save; clearFails = clear }
    func load() async throws -> AppResult<StoredCredential?> { loadFails ? .failure(failure) : .success(nil) }
    func save(_ credential: StoredCredential) async throws -> AppResult<Void> { saveFails ? .failure(failure) : .success(()) }
    func clear() async throws -> AppResult<Void> { clearFails ? .failure(failure) : .success(()) }
}
@MainActor private func owner(_ authority: any SessionAuthority, _ vault: any CredentialVault, _ clock: ManualSessionClock) -> SessionController {
    SessionController(authority: authority, vault: vault, now: { clock.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected exception") })
}
@Test @MainActor func restoreRotatesOnlyAfterPersistenceAndNewOwnersRejectOldLeases() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = MemoryCredentialVault()
    let first = owner(DemoSessionAuthority(now: { clock.nowMillis }), vault, clock)
    await first.restore()?.value; #expect(first.snapshot.phase == .signedOut)
    await first.signIn(login: "alex@example.test", password: "catalog-demo")?.value
    let lease = try #require(first.lease), before = try await vault.load().get()
    first.shutdown(); #expect(!first.isCurrent(lease)); #expect(first.renew() == nil && first.restore() == nil && first.signOut() == nil && first.signIn(login: "sam", password: "ok") == nil)
    let second = owner(DemoSessionAuthority(now: { clock.nowMillis }), vault, clock)
    await second.restore()?.value
    #expect(second.snapshot.principal?.id == "alex"); #expect(second.lease != nil); #expect(!second.isCurrent(lease))
    #expect(try await vault.load().get() != before)
    await second.signOut()?.value; #expect(second.snapshot.phase == .signedOut); #expect(try await vault.load().get() == nil)
}
@Test @MainActor func expiryAndSwitchingAccountsClosePreviousLeases() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), controller = owner(DemoSessionAuthority(now: { clock.nowMillis }), MemoryCredentialVault(), clock)
    await controller.signIn(login: "alex@example.test", password: "catalog-demo")?.value
    let old = try #require(controller.lease)
    clock.advance(milliseconds: 60_000); #expect(controller.lease == nil); #expect(controller.accessToken(for: old) == nil)
    controller.expireIfNeeded(); #expect(controller.snapshot.failure == SessionFailures.expired)
    await controller.renew()?.value; #expect(controller.lease != nil); #expect(!controller.isCurrent(old))
    await controller.signIn(login: "sam@example.test", password: "catalog-demo")?.value
    #expect(controller.snapshot.principal?.id == "sam"); #expect(!controller.isCurrent(old))
}
@Test @MainActor func invalidCommandsDoNotTouchAuthorityOrActiveIdentity() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), authority = StubAuthority(.success(makeGrant()!)), controller = owner(authority, MemoryCredentialVault(), clock)
    await controller.signIn(login: "alex", password: "ok")?.value; let before = controller.lease
    #expect(controller.signIn(login: " ", password: "") == nil)
    #expect(await authority.calls == 1); #expect(controller.lease == before); #expect(controller.snapshot.failure?.kind == .invalid)
}
@Test @MainActor func transientRenewalKeepsCredentialButRejectionClearsIt() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = MemoryCredentialVault(), authority = StubAuthority(.success(makeGrant()!)), controller = owner(authority, vault, clock)
    await controller.signIn(login: "alex", password: "ok")?.value
    let saved = try await vault.load().get()
    await authority.set(.failure(SessionFailures.storage)); await controller.renew()?.value
    #expect(controller.lease == nil); #expect(try await vault.load().get() == saved)
    await authority.set(.failure(SessionFailures.rejected)); await controller.renew()?.value
    #expect(try await vault.load().get() == nil); #expect(controller.snapshot.failure == SessionFailures.rejected)
}
@Test @MainActor func restoredScopeCannotBeReassignedByRenewal() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = MemoryCredentialVault(credential: StoredCredential(makeGrant()!)), authority = StubAuthority(.success(makeGrant(person: "sam")!)), controller = owner(authority, vault, clock)
    await controller.restore()?.value
    #expect(controller.lease == nil); #expect(controller.snapshot.failure == SessionFailures.scopeMismatch); #expect(try await vault.load().get() == nil)
    #expect(await authority.calls == 1)
    let wrong = SessionScope(authorityID: "other", principalID: "alex", accountID: "alex-personal")!
    _ = try await vault.save(StoredCredential(scope: wrong, refreshToken: "token")!)
    await controller.restore()?.value; #expect(await authority.calls == 1); #expect(try await vault.load().get() == nil)
}
@Test @MainActor func storageFailuresNeverPublishAnActiveScopeOrCompletedSignOut() async {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), authority = StubAuthority(.success(makeGrant()!))
    for vault in [FailingVault(load: true), FailingVault(save: true), FailingVault(clear: true)] {
        let controller = owner(authority, vault, clock)
        await controller.restore()?.value
        if !vault.loadFails { await controller.signIn(login: "alex", password: "ok")?.value }
        #expect(controller.lease == nil); #expect(controller.snapshot.phase == .blocked)
        await controller.signOut()?.value
        #expect(controller.snapshot.phase == (vault.clearFails ? .blocked : .signedOut))
    }
}
@Test @MainActor func expiredGrantsAndRevokeFailuresAreReportedWithClosedAccess() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = MemoryCredentialVault(), authority = StubAuthority(.success(makeGrant(expiry: clock.nowMillis)!)), controller = owner(authority, vault, clock)
    await controller.signIn(login: "alex", password: "ok")?.value
    #expect(controller.snapshot.failure == SessionFailures.expired); #expect(try await vault.load().get() == nil)
    await authority.set(.success(makeGrant()!)); await controller.signIn(login: "alex", password: "ok")?.value
    await authority.setRevoke(.failure(SessionFailures.storage)); await controller.signOut()?.value
    #expect(controller.snapshot.phase == .signedOut); #expect(controller.snapshot.failure == SessionFailures.storage); #expect(try await vault.load().get() == nil)
}
private actor SaveGateVault: CredentialVault {
    var credential: StoredCredential?
    var entered = false
    private var waiter: CheckedContinuation<Void, Never>?
    func load() async throws -> AppResult<StoredCredential?> { .success(credential) }
    func save(_ credential: StoredCredential) async throws -> AppResult<Void> {
        entered = true
        await withCheckedContinuation { waiter = $0 } // Deliberately ignores cancellation.
        self.credential = credential; return .success(())
    }
    func release() { waiter?.resume(); waiter = nil }
    func clear() async throws -> AppResult<Void> { credential = nil; return .success(()) }
}
@Test @MainActor func signOutWaitsForAnUncancelableSaveBeforeClearing() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = SaveGateVault(), controller = owner(StubAuthority(.success(makeGrant()!)), vault, clock)
    let signIn = try #require(controller.signIn(login: "alex", password: "ok"))
    while !(await vault.entered) { await Task.yield() }
    let logout = try #require(controller.signOut()); #expect(controller.lease == nil); #expect(controller.snapshot.phase == .signingOut)
    await vault.release(); await signIn.value; await logout.value
    #expect(controller.snapshot.phase == .signedOut); #expect(try await vault.load().get() == nil)
}
@Test @MainActor func canceledTaskThatIgnoresCancellationSettlesAndCannotAdmitIdentity() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), vault = SaveGateVault(), controller = owner(StubAuthority(.success(makeGrant()!)), vault, clock)
    let task = try #require(controller.signIn(login: "alex", password: "ok"))
    while !(await vault.entered) { await Task.yield() }
    task.cancel(); await vault.release(); await task.value
    #expect(!controller.isBusy); #expect(controller.lease == nil); #expect(controller.snapshot.failure?.kind == .canceled)
    // Cancellation alone is not logout: its native save may have committed.
    await controller.signOut()?.value; #expect(try await vault.load().get() == nil)
}
@Test func demoRotationRejectsThePreviousTokenOnlyInThatAuthorityInstance() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), authority = DemoSessionAuthority(now: { clock.nowMillis })
    let grant = try await authority.signIn(SignIn.make(login: "alex@example.test", password: "catalog-demo").get()).get()
    let saved = StoredCredential(grant), renewed = try await authority.renew(saved).get()
    #expect(renewed.refreshToken != grant.refreshToken)
    guard case .failure(let failure) = try await authority.renew(saved) else { Issue.record("Used token accepted"); return }
    #expect(failure == SessionFailures.rejected)
    #expect(try await DemoSessionAuthority(now: { clock.nowMillis }).renew(saved).get().scope == saved.scope)
}
private actor LateDefectAuthority: SessionAuthority {
    nonisolated let id = "catalog-demo.v1"
    var entered = false
    var gate: CheckedContinuation<Void, Never>?
    struct Defect: Error {}
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> { entered = true; await withCheckedContinuation { gate = $0 }; throw Defect() }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { .failure(SessionFailures.rejected) }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { .success(()) }
    func release() { gate?.resume(); gate = nil }
}
@Test @MainActor func lateDefectAfterShutdownCannotPublishOrReachDiagnostics() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), authority = LateDefectAuthority(), vault = MemoryCredentialVault()
    let controller = owner(authority, vault, clock), task = try #require(controller.signIn(login: "alex", password: "ok"))
    while !(await authority.entered) { await Task.yield() }
    controller.shutdown(); await authority.release(); await task.value
    #expect(controller.snapshot.phase == .signedOut); #expect(controller.snapshot.failure == nil); #expect(try await vault.load().get() == nil)
}
@Test @MainActor func cancellationSuppressesAnIgnoredLateDefectWithoutShutdown() async throws {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000), authority = LateDefectAuthority(), controller = owner(authority, MemoryCredentialVault(), clock)
    let task = try #require(controller.signIn(login: "alex", password: "ok"))
    while !(await authority.entered) { await Task.yield() }
    task.cancel(); await authority.release(); await task.value
    #expect(controller.lease == nil); #expect(!controller.isBusy); #expect(controller.snapshot.failure?.kind == .canceled)
}
