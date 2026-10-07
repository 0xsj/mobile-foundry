/// Stable categories shared by the native foundations. Specific reasons belong in code.
public enum FailureKind: String, CaseIterable, Sendable {
    case unauthenticated
    case forbidden
    case rateLimited = "rate_limited"
    case unavailable
    case timeout
    case canceled
    case internalError = "internal"
    case notFound = "not_found"
    case invalid
    case conflict
}

/// Deliberately selected application metadata, never an original exception or stack.
/// Non-internal messages, codes, and identifiers must already suit their public boundary.
public struct FailureMeta: Equatable, Sendable {
    public let message: String
    public let code: String?
    public let requestID: String?
    public let correlationID: String?

    public init(
        message: String,
        code: String? = nil,
        requestID: String? = nil,
        correlationID: String? = nil
    ) {
        self.message = message
        self.code = code
        self.requestID = requestID
        self.correlationID = correlationID
    }
}

/// Nonnegative retry timing information. It does not determine whether retrying is safe.
public struct RetryAfter: Equatable, Sendable {
    public let milliseconds: Int64

    /// Rejects negative timing; zero is valid.
    public init?(milliseconds: Int64) {
        guard milliseconds >= 0 else { return nil }
        self.milliseconds = milliseconds
    }
}

/// Expected failures as immutable values. Unexpected defects remain exceptions.
/// The canceled variant is reserved for boundaries explicitly returning cancellation.
public enum Failure: Error, Equatable, Sendable {
    case unauthenticated(FailureMeta)
    case forbidden(FailureMeta)
    case rateLimited(FailureMeta, retryAfter: RetryAfter? = nil)
    case unavailable(FailureMeta)
    case timeout(FailureMeta)
    case canceled(FailureMeta)
    case internalError(FailureMeta)
    case notFound(FailureMeta)
    case invalid(FailureMeta, fields: [String: String])
    case conflict(FailureMeta)

    public var kind: FailureKind {
        switch self {
        case .unauthenticated: .unauthenticated
        case .forbidden: .forbidden
        case .rateLimited: .rateLimited
        case .unavailable: .unavailable
        case .timeout: .timeout
        case .canceled: .canceled
        case .internalError: .internalError
        case .notFound: .notFound
        case .invalid: .invalid
        case .conflict: .conflict
        }
    }

    public var meta: FailureMeta {
        switch self {
        case .unauthenticated(let meta), .forbidden(let meta),
             .unavailable(let meta), .timeout(let meta), .canceled(let meta),
             .internalError(let meta), .notFound(let meta), .conflict(let meta):
            meta
        case .rateLimited(let meta, _), .invalid(let meta, _):
            meta
        }
    }

    /// Explicit public projection. Internal message/code are redacted; IDs survive.
    /// Other variants already contain caller-selected public fields.
    public func publicInfo() -> Failure {
        guard case .internalError(let meta) = self else { return self }
        return .internalError(FailureMeta(
            message: "An unexpected error occurred.",
            requestID: meta.requestID,
            correlationID: meta.correlationID
        ))
    }
}
