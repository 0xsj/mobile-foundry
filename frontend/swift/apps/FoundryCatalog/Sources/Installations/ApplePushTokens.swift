import Foundation
import FoundryKernel
import FoundryServices
import UIKit

/// One app/process route. APNs callbacks carry no user, account or request identity.
@MainActor final class ApplePushTokens: PushTokenPort {
    private let tokens: PushTokenStore
    private let route: () -> PushRoute?
    private let register: () -> Void
    private let timeout: Duration
    private var pending: (UUID, CheckedContinuation<AppResult<Void>, any Error>)?
    private var timer: Task<Void, Never>?
    private var requested = false

    init(tokens: PushTokenStore, route: @escaping () -> PushRoute? = {
        ApplePushTokens.route(application: Bundle.main.bundleIdentifier, environment: Bundle.main.object(forInfoDictionaryKey: "CatalogAPNsEnvironment") as? String)
    }, register: @escaping () -> Void = { UIApplication.shared.registerForRemoteNotifications() }, timeout: Duration = .seconds(30)) {
        self.tokens = tokens
        self.route = route
        self.register = register
        self.timeout = timeout
    }

    static func route(application: String?, environment: String?) -> PushRoute? {
        guard let application, let environment, let admitted = PushEnvironment(rawValue: environment) else { return nil }
        return PushRoute(provider: .apns, application: application, environment: admitted)
    }

    func collect() async throws -> AppResult<Void> {
        try Task.checkCancellation()
        guard route() != nil else { return .failure(PushTokenFailures.unconfigured) }
        guard pending == nil else { return .failure(PushTokenFailures.unavailable) }
        let id = UUID()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                guard !Task.isCancelled else { continuation.resume(throwing: CancellationError()); return }
                pending = (id, continuation)
                requested = true
                timer = Task { [weak self, timeout] in
                    do { try await Task.sleep(for: timeout) } catch { return }
                    self?.finish(id: id, result: .failure(PushTokenFailures.unavailable))
                }
                register()
            }
        } onCancel: {
            Task { @MainActor [weak self] in self?.cancel(id: id) }
        }
    }

    func didRegister(_ bytes: Data) {
        guard requested, let route = route() else { return }
        // Do not assume APNs tokens always have a fixed byte count.
        guard !bytes.isEmpty, bytes.count <= 2048,
              let target = DeliveryTarget(route: route, token: bytes.map { String(format: "%02x", $0) }.joined()) else {
            didFail(); return
        }
        tokens.receive(target)
        if let id = pending?.0 { finish(id: id, result: .success(())) }
    }

    func didFail() {
        guard requested else { return }
        tokens.invalidate()
        if let id = pending?.0 { finish(id: id, result: .failure(PushTokenFailures.unavailable)) }
    }

    private func finish(id: UUID, result: AppResult<Void>) {
        guard let current = pending, current.0 == id else { return }
        pending = nil
        timer?.cancel(); timer = nil
        current.1.resume(returning: result)
    }

    private func cancel(id: UUID) {
        guard let current = pending, current.0 == id else { return }
        pending = nil
        timer?.cancel(); timer = nil
        current.1.resume(throwing: CancellationError())
    }
}

@MainActor enum PushTokenComposition {
    static let tokens = PushTokenStore()
    static let provider = ApplePushTokens(tokens: tokens)
}
