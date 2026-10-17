import Foundation
import FoundryHTTP
import FoundryKernel

/// Transient admitted input. Never encode into saved state, a journal or a vault.
public struct RegisterIdentity: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let login: String
    public let displayName: String
    public let password: String
    private init(login: String, displayName: String, password: String) {
        self.login = login; self.displayName = displayName; self.password = password
    }
    public static func make(login: String, displayName: String, password: String) -> AppResult<Self> {
        let login = RegistrationAdmission.trim(login), name = RegistrationAdmission.trim(displayName)
        var fields: [String: String] = [:]
        if !login.utf8.allSatisfy({ $0 < 128 }) || !RegistrationAdmission.login(login.lowercased()) {
            fields["login"] = "Use 3–128 characters: a–z, 0–9, . _ @ + -. Start with a letter or number."
        }
        if !RegistrationAdmission.name(name) { fields["display_name"] = "Enter a display name of 1–80 characters without control characters." }
        if password.unicodeScalars.count < 12 || password.utf8.count > 256 { fields["password"] = "Use at least 12 characters and at most 256 UTF-8 bytes." }
        guard fields.isEmpty else { return .failure(.invalid(.init(message: "Check your account details.", code: "identity.invalid_registration"), fields: fields)) }
        return .success(Self(login: login.lowercased(), displayName: name, password: password))
    }
    public var description: String { "RegisterIdentity(<redacted>)" }
    public var debugDescription: String { description }
}

public struct RegisteredIdentity: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let id: String
    public let login: String
    public let displayName: String
    public init?(id: String, login: String, displayName: String) {
        guard Principal(id: id, displayName: displayName) != nil, RegistrationAdmission.login(login), RegistrationAdmission.name(displayName) else { return nil }
        self.id = id; self.login = login; self.displayName = displayName
    }
    public func confirms(_ command: RegisterIdentity) -> Bool {
        login == command.login && displayName.utf8.elementsEqual(command.displayName.utf8)
    }
    public var description: String { "RegisteredIdentity(<redacted>)" }
    public var debugDescription: String { description }
}

/// One unauthenticated attempt. A profile is not a session or account grant.
@MainActor public protocol IdentityRegistration {
    func register(_ command: RegisterIdentity) async throws -> AppResult<RegisteredIdentity>
}

@MainActor public struct HTTPIdentityRegistration: IdentityRegistration {
    private let client: any HTTPClient
    public init(client: any HTTPClient) { self.client = client }
    public static func create(baseURL: String, transport: any HTTPTransport, allowLocalHTTP: Bool = false) -> AppResult<Self> {
        guard let base = GoSessionAuthority.canonicalBase(baseURL, allowLocalHTTP: allowLocalHTTP) else {
            return .failure(.internalError(.init(message: "Use an admitted API base for registration.", code: "identity.registration_configuration")))
        }
        return DefaultHTTPClient.create(baseURL: base, transport: transport).map { Self(client: $0) }
    }
    public func register(_ command: RegisterIdentity) async throws -> AppResult<RegisteredIdentity> {
        try Task.checkCancellation()
        let answer = try await client.requestEntity(.POST, path: "identity/register", options: .init(body: .object([
            "login": .string(command.login), "display_name": .string(command.displayName), "password": .string(command.password)
        ])))
        try Task.checkCancellation()
        return answer.flatMap { entity in
            guard entity.metadata.status == 201, let body = entity.body.object, Set(body.keys) == ["id", "login", "display_name"],
                  let id = body["id"]?.string, GoSessionWire.uuid(id), let login = body["login"]?.string, let name = body["display_name"]?.string,
                  let value = RegisteredIdentity(id: id, login: login, displayName: name), value.confirms(command) else {
                return .failure(.internalError(.init(message: "Registration returned an unexpected response.", code: "identity.registration_response",
                    requestID: entity.metadata.requestID, correlationID: entity.metadata.correlationID)))
            }
            return .success(value)
        }
    }
}

private enum RegistrationAdmission {
    // Go unicode.IsSpace, deliberately explicit rather than platform trim defaults.
    static let whitespace = CharacterSet(charactersIn: "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D} \u{0085}\u{00A0}\u{1680}\u{2000}\u{2001}\u{2002}\u{2003}\u{2004}\u{2005}\u{2006}\u{2007}\u{2008}\u{2009}\u{200A}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}")
    static func trim(_ value: String) -> String { value.trimmingCharacters(in: whitespace) }
    static func login(_ value: String) -> Bool { value.range(of: #"\A[a-z0-9][a-z0-9._@+-]{2,127}\z"#, options: .regularExpression) != nil }
    static func name(_ value: String) -> Bool {
        value == trim(value) && (1...80).contains(value.unicodeScalars.count) && value.unicodeScalars.allSatisfy { $0.properties.generalCategory != .control }
    }
}
