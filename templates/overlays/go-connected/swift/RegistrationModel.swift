import FoundryKernel
import FoundrySession
import Observation

/// Presentation only. The command/password lives only in the submitted task.
@MainActor @Observable final class RegistrationModel {
    private(set) var isBusy = false
    private(set) var registered: RegisteredIdentity?
    private(set) var loginHint = ""
    private(set) var message: String?
    private(set) var failure: Failure?
    private(set) var uncertain = false
    @ObservationIgnored private let service: any IdentityRegistration
    @ObservationIgnored private let controller: SessionController
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    static let interrupted = "Account creation was not confirmed. The account may exist. Try signing in before submitting again."
    init(service: any IdentityRegistration, controller: SessionController) { self.service = service; self.controller = controller }
    var canSubmit: Bool { !isBusy && registered == nil && controller.snapshot.phase == .signedOut }
    func open() {
        cancel()
        registered = nil; failure = nil
        if !uncertain { message = nil; loginHint = "" }
    }
    func reset() {
        cancel(); registered = nil; loginHint = ""; message = nil; failure = nil; uncertain = false
    }
    func cancel() {
        generation += 1; task?.cancel(); task = nil
        if isBusy { uncertain = true; registered = nil; failure = nil; message = Self.interrupted }
        isBusy = false
    }
    func register(login: String, displayName: String, password: String) {
        guard canSubmit else { return }
        let command: RegisterIdentity
        switch RegisterIdentity.make(login: login, displayName: displayName, password: password) {
        case .failure(let value): failure = value; return
        case .success(let value): command = value
        }
        isBusy = true; registered = nil; message = nil; failure = nil; uncertain = false; loginHint = command.login
        let current = generation, epoch = controller.snapshot.epoch
        task = Task {
            do {
                try Task.checkCancellation()
                let answer = try await service.register(command); try Task.checkCancellation()
                guard current == generation else { return }
                guard controller.snapshot.epoch == epoch, controller.snapshot.phase == .signedOut else { cancel(); return }
                switch answer {
                case .success(let value):
                    if value.confirms(command) { registered = value; message = "Account created. Sign in to open your personal workspace." }
                    else { uncertain = true; message = Self.interrupted }
                case .failure(let value):
                    failure = value.publicInfo()
                    uncertain = [.unavailable, .timeout, .canceled, .internalError].contains(value.kind)
                    if uncertain { message = Self.interrupted }
                }
            } catch {
                guard current == generation else { return }
                uncertain = true; message = Self.interrupted
            }
            guard current == generation else { return }
            isBusy = false; task = nil
        }
    }
}
