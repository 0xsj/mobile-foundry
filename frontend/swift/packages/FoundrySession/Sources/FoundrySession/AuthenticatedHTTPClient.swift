import Foundation
import FoundryHTTP
import FoundryKernel

/// One lease, one attempt. This adapter never renews, retries or retargets a request.
@MainActor public final class AuthenticatedHTTPClient: HTTPClient {
    private let client: any HTTPClient
    private let session: SessionController
    private let lease: SessionLease
    private init(client: any HTTPClient, session: SessionController, lease: SessionLease) {
        self.client = client; self.session = session; self.lease = lease
    }
    public static func create(baseURL: String, transport: any HTTPTransport, session: SessionController,
                              lease: SessionLease, timeoutMilliseconds: Int64 = 15_000, allowLocalHTTP: Bool = false) -> AppResult<AuthenticatedHTTPClient> {
        let parts = URLComponents(string: baseURL), scheme = parts?.scheme?.lowercased()
        let local = ["localhost", "127.0.0.1", "::1", "[::1]"].contains(parts?.host?.lowercased() ?? "")
        guard scheme == "https" || (allowLocalHTTP && scheme == "http" && local) else {
            return .failure(.internalError(.init(message: "Session requests require HTTPS or explicitly enabled local HTTP.", code: "session.invalid_configuration")))
        }
        return DefaultHTTPClient.create(baseURL: baseURL, transport: transport, timeoutMilliseconds: timeoutMilliseconds)
            .map { Self(client: $0, session: session, lease: lease) }
    }
    public func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions = .init()) async throws -> AppResult<Entity> {
        try Task.checkCancellation()
        guard let token = session.accessToken(for: lease) else { return .failure(SessionFailures.scopeChanged) }
        guard !options.headers.keys.contains(where: { $0.lowercased() == "authorization" }) else {
            return .failure(.invalid(.init(message: "Authorization is owned by the session.", code: "session.authorization_override"), fields: [:]))
        }
        guard token.range(of: "^[A-Za-z0-9._~+/-]+=*$", options: .regularExpression) != nil else {
            return .failure(.internalError(.init(message: "The access credential is not a Bearer token.", code: "session.invalid_access_token")))
        }
        var headers = options.headers; headers["authorization"] = "Bearer \(token)"
        let forwarded = RequestOptions(timeoutMilliseconds: options.timeoutMilliseconds, correlationID: options.correlationID,
            query: options.query, headers: headers, body: options.body, ifMatch: options.ifMatch, idempotencyKey: options.idempotencyKey)
        do {
            let answer = try await client.requestEntity(method, path: path, options: forwarded)
            try Task.checkCancellation()
            return session.isCurrent(lease) ? answer : .failure(SessionFailures.scopeChanged)
        } catch {
            try Task.checkCancellation()
            if error is CancellationError { throw error }
            guard session.isCurrent(lease) else { return .failure(SessionFailures.scopeChanged) }
            throw error
        }
    }
}
