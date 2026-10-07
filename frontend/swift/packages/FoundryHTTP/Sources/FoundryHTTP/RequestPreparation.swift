import Foundation
import FoundryKernel

enum RequestPreparation {
    static func base(_ text: String) -> AppResult<URL> {
        guard text == text.trimmingCharacters(in: .whitespacesAndNewlines),
              var parts = URLComponents(string: text),
              ["http", "https"].contains(parts.scheme?.lowercased() ?? ""),
              nonblank(parts.host) != nil, parts.user == nil, parts.password == nil,
              parts.query == nil, parts.fragment == nil,
              parts.port == nil || (1...65535).contains(parts.port!),
              parts.url != nil else {
            return .failure(internalFailure("http.invalid_configuration", "The API base URL is invalid."))
        }
        // Reject ambiguous/traversing configured prefixes as well as request paths.
        for segment in parts.percentEncodedPath.split(separator: "/") {
            guard let decoded = String(segment).removingPercentEncoding,
                  decoded != ".", decoded != "..", !unsafe(decoded, segment: true) else {
                return .failure(internalFailure("http.invalid_configuration", "The API base path is invalid."))
            }
        }
        while parts.percentEncodedPath.hasSuffix("/") { parts.percentEncodedPath.removeLast() }
        parts.percentEncodedPath += "/"
        guard let url = parts.url else { return .failure(internalFailure("http.invalid_configuration", "The API base URL is invalid.")) }
        return .success(url)
    }

    static func timeout(_ value: Int64) -> Failure? {
        (0...2_147_483_647).contains(value) ? nil : internalFailure("http.invalid_timeout", "The request budget is invalid.")
    }

    static func prepare(base: URL, method: HTTPMethod, path: String, options: RequestOptions,
                        commonHeaders: [String: String]) throws -> AppResult<PreparedRequest> {
        func refused(_ message: String) -> AppResult<PreparedRequest> {
            .failure(internalFailure("http.invalid_request", message, correlationID: options.correlationID))
        }
        guard path == path.trimmingCharacters(in: .whitespacesAndNewlines),
              !path.hasPrefix("//"), path.range(of: "^[a-zA-Z][a-zA-Z0-9+.-]*:", options: .regularExpression) == nil,
              !unsafe(path, segment: false) else { return refused("Supply an API-relative path.") }
        for segment in path.split(separator: "/", omittingEmptySubsequences: false) {
            guard let decoded = String(segment).removingPercentEncoding,
                  decoded != ".", decoded != "..", !unsafe(decoded, segment: true) else {
                return refused("The API path contains traversal or invalid encoding.")
            }
        }
        let relative = path.hasPrefix("/") ? String(path.dropFirst()) : path
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789%-._~!$&'()*+,;=:@/")
        guard let encoded = relative.addingPercentEncoding(withAllowedCharacters: allowed),
              var parts = URLComponents(url: base, resolvingAgainstBaseURL: false) else { return refused("The request URL is invalid.") }
        parts.percentEncodedPath += encoded
        if !options.query.isEmpty { parts.queryItems = options.query.map { URLQueryItem(name: $0.name, value: $0.value) } }
        guard let url = parts.url else { return refused("The request URL is invalid.") }

        var body: Data?
        if let supplied = options.body {
            guard method != .GET, method != .HEAD, case .object = supplied else { return refused("Request bodies must be JSON objects on body-capable methods.") }
            do { body = try JSONEncoder().encode(supplied) }
            catch is EncodingError { return refused("The request body is not valid JSON.") }
        }
        var headers: [String: String] = [:]
        for layer in [commonHeaders, options.headers] {
            for (name, value) in layer {
                guard validHeader(name, value) else { return refused("Invalid request header.") }
                headers[name.lowercased()] = value
            }
        }
        for (name, value) in [("x-correlation-id", options.correlationID), ("if-match", options.ifMatch), ("idempotency-key", options.idempotencyKey)] {
            if let value {
                guard validHeader(name, value) else { return refused("Invalid request header.") }
                headers[name] = value
            }
        }
        headers["accept"] = "application/json, application/problem+json"
        headers["content-type"] = body == nil ? nil : "application/json"
        return .success(.init(url: url, method: method, headers: headers, body: body))
    }

    private static func unsafe(_ value: String, segment: Bool) -> Bool {
        value.unicodeScalars.contains { $0.value <= 31 || $0.value == 127 || $0 == "\\" || (segment ? $0 == "/" : $0 == "?" || $0 == "#") }
    }

    private static func validHeader(_ name: String, _ value: String) -> Bool {
        name.range(of: "^[!#$%&'*+.^_`|~0-9A-Za-z-]+$", options: .regularExpression) != nil &&
        !value.unicodeScalars.contains { $0.value <= 8 || (10...31).contains($0.value) || $0.value == 127 || $0.value >= 256 }
    }
}
