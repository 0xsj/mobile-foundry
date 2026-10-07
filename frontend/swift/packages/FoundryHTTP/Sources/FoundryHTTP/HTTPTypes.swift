import Foundation
import FoundryKernel

public enum HTTPMethod: String, Sendable { case GET, HEAD, POST, PUT, PATCH, DELETE }

public struct QueryItem: Equatable, Sendable {
    public let name: String
    public let value: String
    public init(_ name: String, _ value: String) { self.name = name; self.value = value }
}

public struct RequestOptions: Sendable {
    public let timeoutMilliseconds: Int64?
    public let correlationID: String?
    public let query: [QueryItem]
    public let headers: [String: String]
    public let body: JSONValue?
    public let ifMatch: String?
    public let idempotencyKey: String?

    public init(timeoutMilliseconds: Int64? = nil, correlationID: String? = nil,
                query: [QueryItem] = [], headers: [String: String] = [:], body: JSONValue? = nil,
                ifMatch: String? = nil, idempotencyKey: String? = nil) {
        self.timeoutMilliseconds = timeoutMilliseconds; self.correlationID = correlationID
        self.query = query; self.headers = headers; self.body = body
        self.ifMatch = ifMatch; self.idempotencyKey = idempotencyKey
    }
}

public struct PreparedRequest: Sendable {
    public let url: URL
    public let method: HTTPMethod
    public let headers: [String: String]
    public let body: Data?
    public init(url: URL, method: HTTPMethod, headers: [String: String], body: Data? = nil) {
        self.url = url; self.method = method; self.headers = headers; self.body = body
    }
}

/// nil body means reading failed; empty Data means an empty response.
public struct WireResponse: Sendable {
    public let status: Int
    public let headers: [String: String]
    public let body: Data?
    public init(status: Int, headers: [String: String] = [:], body: Data?) {
        self.status = status; self.headers = headers; self.body = body
    }
}

public struct ResponseMetadata: Equatable, Sendable {
    public let status: Int
    public let etag: String?
    public let requestID: String?
    public let correlationID: String?
    public init(status: Int, etag: String? = nil, requestID: String? = nil, correlationID: String? = nil) {
        self.status = status; self.etag = etag; self.requestID = requestID; self.correlationID = correlationID
    }
}

public struct Entity: Equatable, Sendable {
    public let body: JSONValue
    public let metadata: ResponseMetadata
    public init(body: JSONValue, metadata: ResponseMetadata) { self.body = body; self.metadata = metadata }
}

/// Implementations must cooperate with task cancellation and release native body resources.
public protocol HTTPTransport: Sendable {
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse>
}

public protocol HTTPClient: Sendable {
    func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity>
}

extension HTTPClient {
    public func request(_ method: HTTPMethod, path: String, options: RequestOptions = .init()) async throws -> AppResult<JSONValue> {
        try await requestEntity(method, path: path, options: options).map(\.body)
    }
}

func header(_ headers: [String: String], _ name: String) -> String? {
    headers.first { $0.key.lowercased() == name.lowercased() }?.value
}

func nonblank(_ text: String?) -> String? {
    guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
    return text
}

func internalFailure(_ code: String, _ message: String, correlationID: String? = nil) -> Failure {
    .internalError(.init(message: message, code: code, correlationID: correlationID))
}
