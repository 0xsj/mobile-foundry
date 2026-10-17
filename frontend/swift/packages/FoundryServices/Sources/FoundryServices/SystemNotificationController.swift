import Foundation
import FoundryKernel
import Observation

@MainActor @Observable public final class SystemNotificationController {
    public private(set) var access: NotificationAccess?
    public private(set) var busy = false
    public private(set) var lastAcceptedID: String?
    public private(set) var failure: Failure?
    @ObservationIgnored private let port: any SystemNotificationPort
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var closed = false
    public init(port: any SystemNotificationPort, onUnexpectedError: @escaping (any Error) -> Void) { self.port = port; self.onUnexpectedError = onUnexpectedError }
    public var canPost: Bool { !closed && !busy && access?.canPost == true }
    public func refresh() {
        guard !busy, !closed else { return }; access = nil
        run { try await self.port.access() } receive: { self.access = $0 }
    }
    public func requestPermission() {
        guard !busy, !closed else { return }; access = nil
        run { try await self.port.requestPermission() } receive: { self.access = $0 }
    }
    public func postPreview() {
        guard canPost, let preview = LocalNotificationPreview(id: UUID().uuidString.lowercased()) else { return }
        run { try await self.port.postPreview(preview) } receive: { self.lastAcceptedID = preview.id }
    }
    public func clearPreview() { run { try await self.port.clearPreview() } receive: { _ in self.lastAcceptedID = nil } }
    public func openSettings() { run { try await self.port.openSettings() } receive: { _ in } }
    public func close() { closed = true; generation += 1; task?.cancel(); task = nil; busy = false; access = nil }
    private func run<T: Sendable>(_ operation: @escaping @MainActor () async throws -> AppResult<T>, receive: @escaping @MainActor (T) -> Void) {
        guard !busy, !closed else { return }; let current = generation
        busy = true; failure = nil
        task = Task {
            defer { if current == generation { busy = false; task = nil } }
            do {
                let answer = try await operation(); try Task.checkCancellation()
                guard current == generation else { return }
                switch answer {
                case .success(let value): receive(value)
                case .failure(let error): failure = error.publicInfo(); access = nil
                }
            } catch is CancellationError {} catch {
                guard current == generation, !Task.isCancelled else { return }
                onUnexpectedError(error)
                if current == generation { access = nil; failure = .internalError(.init(message: "An unexpected error occurred.")) }
            }
        }
    }
}
