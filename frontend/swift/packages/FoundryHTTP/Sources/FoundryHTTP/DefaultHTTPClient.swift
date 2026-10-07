import Foundation
import FoundryKernel

public struct DefaultHTTPClient: HTTPClient {
    private let base: URL
    private let transport: any HTTPTransport
    private let timeoutMilliseconds: Int64
    private let headers: [String: String]
    private let now: @Sendable () -> Date

    public static func create(baseURL: String, transport: any HTTPTransport,
                              timeoutMilliseconds: Int64 = 15_000, headers: [String: String] = [:],
                              now: @escaping @Sendable () -> Date = { Date() }) -> AppResult<DefaultHTTPClient> {
        if RequestPreparation.timeout(timeoutMilliseconds) != nil {
            return .failure(internalFailure("http.invalid_configuration", "The default request budget is invalid."))
        }
        return RequestPreparation.base(baseURL).map {
            DefaultHTTPClient(base: $0, transport: transport, timeoutMilliseconds: timeoutMilliseconds, headers: headers, now: now)
        }
    }

    public func requestEntity(_ method: HTTPMethod, path: String, options: RequestOptions = .init()) async throws -> AppResult<Entity> {
        try Task.checkCancellation()
        let budget = options.timeoutMilliseconds ?? timeoutMilliseconds
        if let failure = RequestPreparation.timeout(budget) { return .failure(failure) }
        let timeout = Failure.timeout(.init(message: "The request exceeded its time budget.", correlationID: options.correlationID))
        if budget == 0 { return .failure(timeout) }
        let result = try await withThrowingTaskGroup(of: AppResult<Entity>.self) { group in
            group.addTask { try await exchange(method, path: path, options: options) }
            group.addTask {
                try await Task.sleep(for: .milliseconds(budget))
                return .failure(timeout)
            }
            defer { group.cancelAll() }
            // Exactly two children have been added; an empty next is impossible.
            return try await group.next()!
        }
        try Task.checkCancellation()
        return result
    }

    private func exchange(_ method: HTTPMethod, path: String, options: RequestOptions) async throws -> AppResult<Entity> {
        let prepared = try RequestPreparation.prepare(base: base, method: method, path: path, options: options, commonHeaders: headers)
        let request: PreparedRequest
        switch prepared {
        case .success(let value): request = value
        case .failure(let failure): return .failure(failure)
        }
        try Task.checkCancellation()
        let answer = try await transport.send(request)
        try Task.checkCancellation()
        switch answer {
        case .failure(let failure): return .failure(failure)
        case .success(let response):
            let metadata = ProblemDecoder.metadata(response, correlationID: options.correlationID)
            guard (100...599).contains(response.status) else {
                return .failure(internalFailure("http.unreadable_response", "The response is not readable.", correlationID: metadata.correlationID))
            }
            let successful = (200...299).contains(response.status)
            if successful && (method == .HEAD || response.status == 204 || response.status == 205) {
                return .success(.init(body: .null, metadata: metadata))
            }
            guard let bytes = response.body else {
                if !successful { return .failure(ProblemDecoder.decode(response, body: nil, correlationID: options.correlationID, now: now())) }
                return .failure(.unavailable(.init(message: "The response body could not be read.", requestID: metadata.requestID, correlationID: metadata.correlationID)))
            }
            if successful, let raw = String(data: bytes, encoding: .utf8), raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return .success(.init(body: .null, metadata: metadata))
            }
            let body: JSONValue
            do { body = try JSONDecoder().decode(JSONValue.self, from: bytes) }
            catch is DecodingError {
                if !successful { return .failure(ProblemDecoder.decode(response, body: nil, correlationID: options.correlationID, now: now())) }
                return .failure(.internalError(.init(message: "The successful response was not valid JSON.", code: "http.invalid_response", requestID: metadata.requestID, correlationID: metadata.correlationID)))
            }
            try Task.checkCancellation()
            return successful ? .success(.init(body: body, metadata: metadata)) :
                .failure(ProblemDecoder.decode(response, body: body, correlationID: options.correlationID, now: now()))
        }
    }
}
