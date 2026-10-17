import Foundation
import FoundryKernel
import Observation

/// A process-local provider address, separate from permission, auth and registry state.
/// Provider callbacks update this store on the main actor; no callback sends HTTP.
@MainActor @Observable public final class PushTokenStore {
    public private(set) var target: DeliveryTarget?
    public private(set) var revision = 0

    public init() {}

    public func receive(_ target: DeliveryTarget?) {
        guard self.target?.route != target?.route || self.target?.token != target?.token else { return }
        self.target = target
        revision += 1
    }

    /// An explicit provider invalidation also orders events while already empty.
    public func invalidate() { target = nil; revision += 1 }
}

/// Explicit collection only. Publishes admitted results to its process-local store
/// before returning success. Does not prompt, register with the backend or retry.
@MainActor public protocol PushTokenPort {
    func collect() async throws -> AppResult<Void>
}

public enum PushTokenFailures {
    public static let unconfigured = Failure.unavailable(.init(message: "Push provider setup is missing. Follow the push token setup guide.", code: "push.unconfigured"))
    public static let unavailable = Failure.unavailable(.init(message: "The push provider is unavailable. Collect again when the device and provider are ready.", code: "push.unavailable"))
}

/// Screen-owned request state; provider/store ownership outlives the screen.
@MainActor @Observable public final class PushTokenController {
    public let tokens: PushTokenStore
    public private(set) var busy = false
    public private(set) var failure: Failure?
    @ObservationIgnored private let port: any PushTokenPort
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var closed = false

    public init(tokens: PushTokenStore, port: any PushTokenPort, onUnexpectedError: @escaping (any Error) -> Void) {
        self.tokens = tokens
        self.port = port
        self.onUnexpectedError = onUnexpectedError
    }

    public var target: DeliveryTarget? { closed || busy ? nil : tokens.target }

    public func collect() {
        guard !closed, !busy else { return }
        busy = true
        failure = nil
        tokens.receive(nil)
        task = Task {
            defer { if !closed { busy = false; task = nil } }
            do {
                let answer = try await port.collect()
                try Task.checkCancellation()
                guard !closed else { return }
                if case .failure(let error) = answer { failure = error.publicInfo() }
            } catch is CancellationError {} catch {
                guard !closed, !Task.isCancelled else { return }
                onUnexpectedError(error)
                if !closed { failure = .internalError(.init(message: "An unexpected error occurred.")) }
            }
        }
    }

    public func close() {
        closed = true
        task?.cancel()
        task = nil
        busy = false
    }
}
