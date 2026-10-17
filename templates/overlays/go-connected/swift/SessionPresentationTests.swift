@testable import ProductApp
import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import SwiftUI
import Testing

/// Observation tracks the public snapshot, not the deliberately private grant.
@MainActor @Test func librarySessionAdmissionTracksSignInAndSignOut() async throws {
    let model = ConnectedSessionModel(authority: PresentationAuthority(), vault: MemoryCredentialVault(),
        baseURL: "https://example.invalid/v1", transport: PresentationTransport(), allowLocalHTTP: false)
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow, window = UIWindow(windowScene: scene)
    let host = UIHostingController(rootView: LibraryScreen(model: model))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { model.controller.shutdown(); window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func fields(_ view: UIView) -> [UITextField] {
        ((view as? UITextField).map { [$0] } ?? []) + view.subviews.flatMap { fields($0) }
    }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Library did not render the current session phase")
    }
    #expect(fields(host.view).isEmpty)
    await model.controller.signIn(login: "alex", password: "fixture")?.value
    try await settle { !fields(host.view).isEmpty }
    await model.controller.signOut()?.value
    try await settle { fields(host.view).isEmpty }
}

private struct PresentationAuthority: SessionAuthority {
    let id = "presentation.test"
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> {
        .success(SessionGrant(authorityID: id,
            principal: Principal(id: "00000000-0000-4000-8000-000000000001", displayName: "Test")!,
            account: Account(id: "00000000-0000-4000-8000-000000000002", name: "Personal")!,
            accessToken: "fixture-access", refreshToken: "fixture-refresh", expiresAtMillis: Int64.max / 2)!)
    }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { .failure(SessionFailures.rejected) }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { .success(()) }
}

private struct PresentationTransport: HTTPTransport {
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> {
        .failure(.unavailable(.init(message: "No network selected in the presentation test.")))
    }
}

@Test func connectedProductSelectsOneCredentialWriterScene() throws {
    let manifest = try #require(Bundle.main.object(forInfoDictionaryKey: "UIApplicationSceneManifest") as? [String: Any])
    #expect(manifest["UIApplicationSupportsMultipleScenes"] as? Bool == false)
}


@MainActor @Test func interruptedPasswordChangeClosesAccessAndLateReplyCannotCloseReplacement() async throws {
    let vault = MemoryCredentialVault()
    let owner = SessionController(authority: PresentationAuthority(), vault: vault, now: { 1 }, onUnexpectedError: { _ in Issue.record("Unexpected failure") })
    await owner.signIn(login: "alex", password: "fixture")?.value
    let service = WaitingIdentityRecovery()
    let model = IdentitySettingsModel(controller: owner, publicService: service)
    model.bind(service, lease: owner.lease)
    model.change(currentPassword: "private old password", newPassword: "private new password")
    for _ in 0..<100 { if service.pending != nil { break }; try await Task.sleep(for: .milliseconds(5)) }
    #expect(service.pending != nil)
    model.cancel(); #expect(owner.lease == nil)
    for _ in 0..<100 { if !owner.isBusy { break }; try await Task.sleep(for: .milliseconds(5)) }
    #expect(try await vault.load().get() == nil)
    await owner.signIn(login: "alex", password: "fixture")?.value
    let replacement = try #require(owner.lease)
    service.pending?.resume(returning: .success(())); service.pending = nil
    try await Task.sleep(for: .milliseconds(10))
    #expect(owner.isCurrent(replacement)); #expect(!model.isBusy)
    owner.shutdown()
}

@MainActor private final class WaitingIdentityRecovery: IdentityRecovery {
    var pending: CheckedContinuation<AppResult<Void>, Never>?
    func contact() async throws -> AppResult<RecoveryContact> { .success(.init(email: nil, verifiedAt: nil)) }
    func requestVerification(email: String, password: String) async throws -> AppResult<Void> { .success(()) }
    func verify(token: String) async throws -> AppResult<Void> { .success(()) }
    func requestReset(email: String) async throws -> AppResult<Void> { .success(()) }
    func reset(token: String, password: String) async throws -> AppResult<Void> { .success(()) }
    func change(currentPassword: String, newPassword: String) async throws -> AppResult<Void> { await withCheckedContinuation { pending = $0 } }
}
