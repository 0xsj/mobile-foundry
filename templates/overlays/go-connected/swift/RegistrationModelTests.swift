@testable import ProductApp
import FoundryKernel
import FoundrySession
import Testing

@MainActor private final class RegistrationService: IdentityRegistration {
    var calls = 0
    var failure: Failure?
    var mismatch = false
    var waits = false
    var continuation: CheckedContinuation<AppResult<RegisteredIdentity>, Never>?
    func register(_ command: RegisterIdentity) async throws -> AppResult<RegisteredIdentity> {
        calls += 1
        if waits { return await withCheckedContinuation { continuation = $0 } }
        if let failure { return .failure(failure) }
        return .success(registrationResult(login: mismatch ? "someone.else" : command.login, name: command.displayName))
    }
    func finish() { continuation?.resume(returning: .success(registrationResult())); continuation = nil }
}
private func registrationResult(login: String = "alex", name: String = "Alex") -> RegisteredIdentity {
    RegisteredIdentity(id: "00000000-0000-4000-8000-000000000001", login: login, displayName: name)!
}
private struct RegistrationAuthority: SessionAuthority {
    let id = "registration.test"
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> {
        .success(SessionGrant(authorityID: id, principal: Principal(id: "principal", displayName: "Member")!, account: Account(id: "personal", name: "Personal")!,
            accessToken: "fixture-access", refreshToken: "fixture-refresh", expiresAtMillis: 1_800_000_000_000)!)
    }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { .failure(SessionFailures.rejected) }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { .success(()) }
}
@MainActor private func registrationOwner(_ vault: MemoryCredentialVault = MemoryCredentialVault()) -> SessionController {
    SessionController(authority: RegistrationAuthority(), vault: vault, now: { 1 }, onUnexpectedError: { _ in Issue.record("Unexpected registration test failure") })
}
@MainActor private func registrationIdle(_ model: RegistrationModel) async throws {
    for _ in 0..<100 { if !model.isBusy { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Registration did not settle")
}
@MainActor private func registrationWaiting(_ service: RegistrationService) async throws {
    for _ in 0..<100 { if service.continuation != nil { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Registration did not dispatch")
}
@MainActor @Test func registrationOwnerRejectsLocalInputAndSignedInSubmission() async throws {
    let owner = registrationOwner(); defer { owner.shutdown() }; let service = RegistrationService(), model = RegistrationModel(service: service, controller: owner)
    model.register(login: "alex", displayName: "Alex", password: "short")
    #expect(service.calls == 0 && model.failure?.kind == .invalid && !model.isBusy)
    await owner.signIn(login: "existing", password: "private passphrase")?.value
    model.register(login: "alex", displayName: "Alex", password: "private passphrase")
    #expect(service.calls == 0 && !model.canSubmit && owner.lease != nil)
}
@MainActor @Test func registrationSuccessNeverSignsInOrWritesCredentials() async throws {
    let vault = MemoryCredentialVault(), owner = registrationOwner(vault); defer { owner.shutdown() }
    let service = RegistrationService(), model = RegistrationModel(service: service, controller: owner)
    model.register(login: " ALEX ", displayName: " Alex ", password: "private passphrase"); model.register(login: "alex", displayName: "Alex", password: "private passphrase")
    try await registrationIdle(model)
    #expect(service.calls == 1 && model.registered?.login == "alex" && model.loginHint == "alex" && !model.uncertain)
    #expect(owner.snapshot.phase == .signedOut && owner.lease == nil)
    #expect(try await vault.load().get() == nil)
    model.register(login: "alex", displayName: "Alex", password: "private passphrase"); #expect(service.calls == 1)
}
@MainActor @Test func registrationCancellationPreservesUncertaintyAndLateReplyCannotAffectReplacement() async throws {
    let owner = registrationOwner(); defer { owner.shutdown() }; let service = RegistrationService(); service.waits = true
    let model = RegistrationModel(service: service, controller: owner)
    model.register(login: "alex", displayName: "Alex", password: "private passphrase"); try await registrationWaiting(service)
    model.cancel(); #expect(model.uncertain && model.message == RegistrationModel.interrupted && !model.isBusy)
    await owner.signIn(login: "existing", password: "private passphrase")?.value
    let replacement = try #require(owner.lease)
    service.finish(); try await Task.sleep(for: .milliseconds(10))
    #expect(owner.isCurrent(replacement) && model.registered == nil && model.uncertain && service.calls == 1)
}
@MainActor @Test func registrationTransportOrMismatchedReplyRequiresExplicitRecoveryWithoutReplay() async throws {
    for mismatch in [false, true] {
        let owner = registrationOwner(); defer { owner.shutdown() }; let service = RegistrationService()
        if mismatch { service.mismatch = true } else { service.failure = .timeout(.init(message: "Lost reply")) }
        let model = RegistrationModel(service: service, controller: owner)
        model.register(login: "alex", displayName: "Alex", password: "private passphrase"); try await registrationIdle(model)
        #expect(model.uncertain && model.registered == nil && model.message == RegistrationModel.interrupted && model.loginHint == "alex")
        model.open(); #expect(service.calls == 1 && model.uncertain && owner.lease == nil)
    }
}
@MainActor @Test func registrationDuplicateIsRefusedWithoutInventingSessionSuccess() async throws {
    let owner = registrationOwner(); defer { owner.shutdown() }; let service = RegistrationService()
    service.failure = .conflict(.init(message: "That login is already registered.", code: "identity.login_exists"))
    let model = RegistrationModel(service: service, controller: owner)
    model.register(login: "alex", displayName: "Alex", password: "private passphrase"); try await registrationIdle(model)
    #expect(model.failure?.kind == .conflict && !model.uncertain && model.registered == nil && owner.lease == nil && service.calls == 1)
}
@MainActor @Test func registrationOwnerAlsoChecksSessionEpochBeforePublishing() async throws {
    let owner = registrationOwner(); defer { owner.shutdown() }; let service = RegistrationService(); service.waits = true
    let model = RegistrationModel(service: service, controller: owner)
    model.register(login: "alex", displayName: "Alex", password: "private passphrase"); try await registrationWaiting(service)
    await owner.signIn(login: "existing", password: "private passphrase")?.value
    let replacement = try #require(owner.lease); service.finish(); try await registrationIdle(model)
    #expect(owner.isCurrent(replacement) && model.registered == nil && service.calls == 1)
}
