import Foundation
import FoundryKernel

public enum ProblemDecoder {
    public static func kind(status: Int) -> FailureKind {
        switch status {
        case 400, 413, 415, 422, 428: .invalid
        case 401: .unauthenticated
        case 403: .forbidden
        case 404: .notFound
        case 408, 504: .timeout
        case 409, 412: .conflict
        case 429: .rateLimited
        case 502, 503: .unavailable
        default: .internalError
        }
    }

    public static func metadata(_ response: WireResponse, correlationID: String?) -> ResponseMetadata {
        .init(status: response.status, etag: header(response.headers, "etag"),
              requestID: nonblank(header(response.headers, "x-request-id")),
              correlationID: correlationID ?? nonblank(header(response.headers, "x-correlation-id")))
    }

    public static func decode(_ response: WireResponse, body: JSONValue?, correlationID: String?, now: Date) -> Failure {
        let problem = body?.object ?? [:]
        let kind = problem["kind"]?.string.flatMap(FailureKind.init(rawValue:)) ?? kind(status: response.status)
        let responseMeta = metadata(response, correlationID: correlationID)
        let meta = FailureMeta(message: nonblank(problem["detail"]?.string) ?? nonblank(problem["title"]?.string) ?? "The request failed.",
                               code: nonblank(problem["code"]?.string),
                               requestID: responseMeta.requestID ?? nonblank(problem["request_id"]?.string),
                               correlationID: responseMeta.correlationID)
        switch kind {
        case .unauthenticated: return .unauthenticated(meta)
        case .forbidden: return .forbidden(meta)
        case .rateLimited: return .rateLimited(meta, retryAfter: retryAfter(header(response.headers, "retry-after"), now: now))
        case .unavailable: return .unavailable(meta)
        case .timeout: return .timeout(meta)
        case .canceled: return .canceled(meta)
        case .internalError: return .internalError(meta)
        case .notFound: return .notFound(meta)
        case .invalid:
            return .invalid(meta, fields: (problem["fields"]?.object ?? [:]).compactMapValues(\.string))
        case .conflict: return .conflict(meta)
        }
    }

    public static func retryAfter(_ input: String?, now: Date) -> RetryAfter? {
        guard let input else { return nil }
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let maxDelay: Int64 = 9_007_199_254_740_991
        if text.range(of: "^[0-9]+$", options: .regularExpression) != nil {
            guard let seconds = Int64(text), seconds <= maxDelay / 1000 else { return nil }
            return RetryAfter(milliseconds: seconds * 1000)
        }
        let forms = [
            ("^[A-Za-z]{3}, [0-9]{2} [A-Za-z]{3} [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$", "EEE, dd MMM yyyy HH:mm:ss 'GMT'"),
            ("^[A-Za-z]+, [0-9]{2}-[A-Za-z]{3}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$", "EEEE, dd-MMM-yy HH:mm:ss 'GMT'"),
            ("^[A-Za-z]{3} [A-Za-z]{3} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} [0-9]{4}$", "EEE MMM d HH:mm:ss yyyy"),
        ]
        for (pattern, format) in forms where text.range(of: pattern, options: .regularExpression) != nil {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
            formatter.dateFormat = format
            formatter.isLenient = false
            formatter.twoDigitStartDate = Date(timeIntervalSince1970: -631_152_000) // 1950
            guard let date = formatter.date(from: text) else { return nil }
            let milliseconds = max(0, (date.timeIntervalSince(now) * 1000).rounded(.towardZero))
            guard milliseconds.isFinite, milliseconds <= Double(maxDelay) else { return nil }
            return RetryAfter(milliseconds: Int64(milliseconds))
        }
        return nil
    }
}
