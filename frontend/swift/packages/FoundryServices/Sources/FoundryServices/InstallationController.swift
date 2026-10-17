import Foundation
import FoundryKernel
import Observation

/// Native owner for current-state reconciliation, not a durable command queue.
@MainActor @Observable public final class InstallationController {
    public private(set) var record: InstallationRecord?
    public private(set) var installationID: String?
    public private(set) var busy = false
    public private(set) var needsRefresh = true
    public private(set) var failure: Failure?
    @ObservationIgnored private let vault: any InstallationVault
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var registry: (any InstallationRegistry)?
    @ObservationIgnored private var identity: InstallationIdentity?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var task: Task<Void, Never>?
    public init(vault: any InstallationVault, onUnexpectedError: @escaping (any Error) -> Void) { self.vault = vault; self.onUnexpectedError = onUnexpectedError }
    public var canRegister: Bool { registry != nil && identity != nil && !busy && !needsRefresh }
    public var canUnregister: Bool { canRegister && record?.registered == true && record?.currentBinding == true }
    public func bind(_ registry: (any InstallationRegistry)?) {
        generation += 1; task?.cancel(); task = nil; self.registry = registry
        record = nil; busy = false; needsRefresh = true; failure = nil
        if registry != nil { refresh() }
    }
    public func refresh() {
        guard !busy, registry != nil else { return }; needsRefresh = true
        run { repo in
            let loaded = try await self.vault.loadOrCreate()
            switch loaded {
            case .failure(let failure): return AppResult<(InstallationIdentity, InstallationRecord?)>.failure(failure)
            case .success(let identity): return try await repo.read(identity).map { (identity, $0) }
            }
        } receive: { pair in
            self.identity = pair.0; self.installationID = pair.0.id; self.record = pair.1; self.needsRefresh = false
        }
    }
    public func register(_ target: DeliveryTarget) {
        guard canRegister, let identity else { return }; let current = record
        let registration = UUID().uuidString.lowercased()
        needsRefresh = true
        run { try await $0.register(identity, registrationID: registration, target: target, replacing: current) } receive: { self.record = $0; self.needsRefresh = false }
    }
    public func unregister() {
        guard canUnregister, let identity, let current = record else { return }
        needsRefresh = true
        run { try await $0.unregister(identity, current: current) } receive: { self.record = $0; self.needsRefresh = false }
    }
    private func run<T: Sendable>(_ operation: @escaping @MainActor (any InstallationRegistry) async throws -> AppResult<T>, receive: @escaping @MainActor (T) -> Void) {
        guard !busy, let registry else { return }; let current = generation
        busy = true; failure = nil
        task = Task {
            defer { if current == generation { busy = false; task = nil } }
            do {
                let answer = try await operation(registry); try Task.checkCancellation()
                guard current == generation else { return }
                switch answer { case .success(let value): receive(value); case .failure(let error): failure = error.publicInfo() }
            } catch is CancellationError {} catch {
                if current == generation && !Task.isCancelled { onUnexpectedError(error); if current == generation { failure = .internalError(.init(message: "An unexpected error occurred.")) } }
            }
        }
    }
}
