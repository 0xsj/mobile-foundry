import FoundryKernel
import FoundryHTTP

public enum HealthStatus: String, Sendable { case ok }

public struct Health: Equatable, Sendable {
    public let status: HealthStatus = .ok
    public let requestID: String?
    public let correlationID: String?
    public let version: String?
}

public struct HealthService: Sendable {
    private let http: any HTTPClient
    public init(http: any HTTPClient) { self.http = http }

    public func live(options: RequestOptions = .init()) async throws -> AppResult<Health> {
        try await check(path: "health/live", options: options)
    }

    public func ready(options: RequestOptions = .init()) async throws -> AppResult<Health> {
        try await check(path: "health/ready", options: options)
    }

    private func check(path: String, options: RequestOptions) async throws -> AppResult<Health> {
        let response = try await http.requestEntity(.GET, path: path, options: options)
        switch response {
        case .failure(let failure): return .failure(failure)
        case .success(let entity):
            guard entity.body.object?["status"] == .string("ok") else {
                return .failure(.internalError(.init(message: "The API returned an unexpected response.", code: "http.invalid_response",
                                                     requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID)))
            }
            return .success(Health(requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID, version: entity.metadata.etag))
        }
    }
}
