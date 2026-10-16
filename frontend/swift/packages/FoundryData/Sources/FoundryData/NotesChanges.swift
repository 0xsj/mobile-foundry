import Foundation

/// Process-local invalidations for one admitted path. No callbacks execute under the registry lock.
final class NotesChanges: @unchecked Sendable {
    struct Subscription: Sendable {
        let values: AsyncStream<Void>
        let cancel: @Sendable () -> Void
    }
    static let shared = NotesChanges()
    private let lock = NSLock()
    private var listeners: [String: [UUID: AsyncStream<Void>.Continuation]] = [:]
    func observe(_ key: String) -> Subscription {
        let id = UUID()
        let (stream, continuation) = AsyncStream<Void>.makeStream(bufferingPolicy: .bufferingNewest(1))
        lock.lock(); listeners[key, default: [:]][id] = continuation; lock.unlock()
        continuation.onTermination = { [weak self] _ in self?.remove(key, id) }
        continuation.yield(()) // Register before the first read.
        return Subscription(values: stream, cancel: { continuation.finish() })
    }
    func committed(_ key: String) {
        lock.lock(); let callbacks = Array(listeners[key]?.values ?? Dictionary<UUID, AsyncStream<Void>.Continuation>().values); lock.unlock()
        for callback in callbacks { callback.yield(()) }
    }
    private func remove(_ key: String, _ id: UUID) {
        lock.lock(); defer { lock.unlock() }
        listeners[key]?[id] = nil
        if listeners[key]?.isEmpty == true { listeners[key] = nil }
    }
    // Internal inspection supports cancellation/registry-retention checks.
    func subscriberCount(_ key: String) -> Int { lock.lock(); defer { lock.unlock() }; return listeners[key]?.count ?? 0 }
}
