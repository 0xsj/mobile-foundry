import Foundation
import FoundryHTTP
import FoundryKernel

/// Recoverable invalidations only. The consumer owns HTTP reads and reconciliation.
@MainActor public protocol AccountLiveHints {
    func watch(onHint: @escaping @MainActor @Sendable () -> Void) async throws -> AppResult<Void>
}

/// Transport seam keeps native sockets out of feature owners. Never log bearer/request.
public protocol LiveSocketTransport: Sendable {
    func watch(url: URL, bearer: String, onText: @escaping @Sendable (String) async throws -> Void) async throws
}

@MainActor public struct GoAccountLiveHints: AccountLiveHints {
    private let url: URL
    private let session: SessionController
    private let lease: SessionLease
    private let transport: any LiveSocketTransport
    public static func create(baseURL: String, session: SessionController, lease: SessionLease,
                              transport: any LiveSocketTransport = URLSessionLiveSocketTransport(), allowLocalHTTP: Bool = false) -> AppResult<Self> {
        guard let base = GoSessionAuthority.canonicalBase(baseURL, allowLocalHTTP: allowLocalHTTP),
              lease.scope.authorityID == GoSessionAuthority.deploymentID(base), GoSessionWire.uuid(lease.scope.accountID), let url = URL(string: base + "/accounts/\(lease.scope.accountID)/realtime"),
              var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return .failure(SessionFailures.scopeMismatch) }
        parts.scheme = parts.scheme == "https" ? "wss" : "ws"
        guard let socketURL = parts.url else { return .failure(SessionFailures.scopeMismatch) }
        return .success(Self(url: socketURL, session: session, lease: lease, transport: transport))
    }
    public func watch(onHint: @escaping @MainActor @Sendable () -> Void) async throws -> AppResult<Void> {
        guard let bearer = session.accessToken(for: lease), GoSessionWire.token(bearer, prefix: "a1") else { return .failure(SessionFailures.scopeChanged) }
        let admission = HintAdmission(account: lease.scope.accountID)
        do {
            try await transport.watch(url: url, bearer: bearer) { text in
                try Task.checkCancellation()
                try await admission.accept(text)
                try await MainActor.run {
                    guard session.isCurrent(lease) else { throw SessionFailures.scopeChanged }
                    onHint()
                }
            }
            try Task.checkCancellation()
            return .failure(.unavailable(.init(message: "Live updates disconnected. Refresh or reconnect.", code: "realtime.disconnected")))
        } catch {
            try Task.checkCancellation()
            guard session.isCurrent(lease) else { return .failure(SessionFailures.scopeChanged) }
            if let failure = error as? Failure { return .failure(failure.publicInfo()) }
            if error is URLError { return .failure(.unavailable(.init(message: "Live updates unavailable. You can still refresh.", code: "realtime.unavailable"))) }
            throw error
        }
    }
}

private actor HintAdmission {
    let account: String
    var ready = false
    init(account: String) { self.account = account }
    func accept(_ text: String) throws {
        guard text.utf8.count <= 1024, let b = try? JSONDecoder().decode(JSONValue.self, from: Data(text.utf8)).object,
              Set(b.keys) == ["type", "resource", "account_id", "refetch"], b["resource"] == .string("notes"),
              b["account_id"] == .string(account), b["refetch"] == .bool(true),
              b["type"] == .string(ready ? "changed" : "ready") else {
            throw Failure.internalError(.init(message: "Live updates returned an unexpected message.", code: "realtime.invalid_message"))
        }
        ready = true
    }
}

public struct URLSessionLiveSocketTransport: LiveSocketTransport {
    public init() {}
    public func watch(url: URL, bearer: String, onText: @escaping @Sendable (String) async throws -> Void) async throws {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil; config.urlCache = nil
        config.timeoutIntervalForRequest = 15; config.timeoutIntervalForResource = 600
        let session = URLSession(configuration: config, delegate: SocketRedirectPolicy(), delegateQueue: nil)
        var request = URLRequest(url: url); request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
        let socket = session.webSocketTask(with: request)
        socket.maximumMessageSize = 1024
        defer { socket.cancel(with: .goingAway, reason: nil); session.invalidateAndCancel() }
        try await withTaskCancellationHandler {
            try Task.checkCancellation(); socket.resume()
            while true {
                let message = try await socket.receive()
                try Task.checkCancellation()
                guard case .string(let text) = message else {
                    throw Failure.internalError(.init(message: "Live updates require text messages.", code: "realtime.invalid_message"))
                }
                try await onText(text)
            }
        } onCancel: { socket.cancel(with: .goingAway, reason: nil) }
    }
}

private final class SocketRedirectPolicy: NSObject, URLSessionTaskDelegate, Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) { completionHandler(nil) }
}
