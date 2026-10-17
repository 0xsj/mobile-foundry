import FoundryKernel
import FoundrySession
import Observation

/// Holds presentation only; passwords and codes live in the submitted task, never saved state.
@MainActor @Observable final class IdentitySettingsModel {
    private(set) var isBusy = false
    private(set) var message: String?
    private(set) var failure: Failure?
    private(set) var contact: RecoveryContact?
    @ObservationIgnored private let controller: SessionController
    @ObservationIgnored private let publicService: any IdentityRecovery
    @ObservationIgnored private var service: (any IdentityRecovery)?
    @ObservationIgnored private var lease: SessionLease?
    @ObservationIgnored private var passwordLease: SessionLease?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    init(controller: SessionController, publicService: any IdentityRecovery) {
        self.controller = controller; self.publicService = publicService
    }
    func bind(_ service: (any IdentityRecovery)?, lease: SessionLease?) {
        cancel(); self.service = service; self.lease = lease; contact = nil
    }
    func cancel() {
        generation += 1; task?.cancel(); task = nil; isBusy = false
        let abandoned = passwordLease; passwordLease = nil
        if let abandoned, controller.isCurrent(abandoned) {
            message = "Password change was interrupted. Sign in again; use recovery if needed."
            controller.signOut()
        }
    }
    func requestReset(email: String) {
        guard controller.lease == nil, !controller.isBusy else { return }
        perform(message: "If an eligible address exists, recovery instructions may arrive.") { try await self.publicService.requestReset(email: email) }
    }
    func reset(token: String, password: String) {
        guard controller.lease == nil, !controller.isBusy else { return }
        perform(message: "Password reset. Sign in with your new password.") { try await self.publicService.reset(token: token, password: password) }
    }
    func requestVerification(email: String, password: String) {
        guard let service, let lease, controller.isCurrent(lease) else { return }
        perform(message: "Verification requested. Enter the code from your email.") { try await service.requestVerification(email: email, password: password) }
    }
    func verify(token: String) {
        guard let lease, controller.isCurrent(lease) else { return }
        perform(message: "Email verified. Refresh the verified address to confirm.") { try await self.publicService.verify(token: token) }
    }
    func change(currentPassword: String, newPassword: String) {
        guard !isBusy, let service, let lease, controller.isCurrent(lease) else { return }
        passwordLease = lease
        perform(message: "Password changed. All sessions were revoked; sign in again.") { try await service.change(currentPassword: currentPassword, newPassword: newPassword) }
    }
    func loadContact() {
        guard let service, let lease, controller.isCurrent(lease) else { return }
        perform(message: "Verified address refreshed.") {
            let answer = try await service.contact(); try Task.checkCancellation()
            guard self.controller.isCurrent(lease) else { return .failure(SessionFailures.scopeChanged) }
            return answer.map { self.contact = $0 }
        }
    }
    private func perform(message success: String, action: @escaping @MainActor () async throws -> AppResult<Void>) {
        guard !isBusy else { return }
        isBusy = true; message = nil; failure = nil; let current = generation
        task = Task {
            do {
                let answer = try await action(); try Task.checkCancellation()
                guard current == generation else { return }
                switch answer {
                case .success: message = success
                case .failure(let value): failure = value.publicInfo(); if value.kind == .invalid { passwordLease = nil }
                }
            } catch {
                guard current == generation, !Task.isCancelled else { return }
                failure = .unavailable(.init(message: "The action could not be confirmed. Refresh or request a new code; it was not retried."))
            }
            guard current == generation else { return }
            isBusy = false; task = nil
            let changed = passwordLease; passwordLease = nil
            if let changed, controller.isCurrent(changed) {
                if failure != nil { message = "Password change could not be confirmed. Sign in again; use recovery if needed." }
                controller.signOut()
            }
        }
    }
}
