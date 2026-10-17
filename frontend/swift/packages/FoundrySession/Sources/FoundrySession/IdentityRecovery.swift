import Foundation
import FoundryHTTP
import FoundryKernel

public struct RecoveryContact: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let email: String?
    public let verifiedAt: String?
    public init(email: String?, verifiedAt: String?) { self.email = email; self.verifiedAt = verifiedAt }
    public var description: String { "RecoveryContact(<redacted>)" }
    public var debugDescription: String { description }
}

/// One attempt per explicit action. Authenticated clients must bind an exact lease.
@MainActor public protocol IdentityRecovery {
    func contact() async throws -> AppResult<RecoveryContact>
    func requestVerification(email: String, password: String) async throws -> AppResult<Void>
    func verify(token: String) async throws -> AppResult<Void>
    func requestReset(email: String) async throws -> AppResult<Void>
    func reset(token: String, password: String) async throws -> AppResult<Void>
    func change(currentPassword: String, newPassword: String) async throws -> AppResult<Void>
}

@MainActor public struct HTTPIdentityRecovery: IdentityRecovery {
    private let publicClient: any HTTPClient
    private let authenticatedClient: (any HTTPClient)?
    public init(publicClient: any HTTPClient, authenticatedClient: (any HTTPClient)? = nil) {
        self.publicClient = publicClient; self.authenticatedClient = authenticatedClient
    }
    public static func create(baseURL: String, transport: any HTTPTransport,
                              authenticatedClient: (any HTTPClient)? = nil, allowLocalHTTP: Bool = false) -> AppResult<Self> {
        guard let base = GoSessionAuthority.canonicalBase(baseURL, allowLocalHTTP: allowLocalHTTP) else { return .failure(SessionFailures.scopeMismatch) }
        return DefaultHTTPClient.create(baseURL: base, transport: transport).map { Self(publicClient: $0, authenticatedClient: authenticatedClient) }
    }
    public func contact() async throws -> AppResult<RecoveryContact> {
        guard let authenticatedClient else { return .failure(SessionFailures.scopeChanged) }
        return try await authenticatedClient.requestEntity(.GET, path: "identity/email", options: .init()).flatMap { entity in
            guard entity.metadata.status == 200, let b = entity.body.object else { return .failure(Self.response) }
            if b == ["verified": .bool(false)] { return .success(.init(email: nil, verifiedAt: nil)) }
            guard Set(b.keys) == ["verified", "email", "verified_at"], b["verified"] == .bool(true),
                  let email = b["email"]?.string, Self.mail(email),
                  let at = b["verified_at"]?.string, GoSessionWire.micros(at) != nil else { return .failure(Self.response) }
            return .success(.init(email: email, verifiedAt: at))
        }
    }
    public func requestVerification(email: String, password: String) async throws -> AppResult<Void> {
        guard Self.mail(email), Self.password(password) else { return .failure(Self.input) }
        guard let authenticatedClient else { return .failure(SessionFailures.scopeChanged) }
        return try await post(authenticatedClient, "email/request", ["email": email, "password": password])
    }
    public func verify(token: String) async throws -> AppResult<Void> {
        guard GoSessionWire.token(token, prefix: "e1") else { return .failure(Self.input) }
        return try await post(publicClient, "email/verify", ["token": token])
    }
    public func requestReset(email: String) async throws -> AppResult<Void> {
        guard Self.mail(email) else { return .failure(Self.input) }
        return try await post(publicClient, "password/request-reset", ["email": email], status: 202)
    }
    public func reset(token: String, password: String) async throws -> AppResult<Void> {
        guard GoSessionWire.token(token, prefix: "p1"), Self.password(password) else { return .failure(Self.input) }
        return try await post(publicClient, "password/reset", ["token": token, "password": password])
    }
    public func change(currentPassword: String, newPassword: String) async throws -> AppResult<Void> {
        guard Self.password(currentPassword), Self.password(newPassword) else { return .failure(Self.input) }
        guard let authenticatedClient else { return .failure(SessionFailures.scopeChanged) }
        return try await post(authenticatedClient, "password/change", ["current_password": currentPassword, "new_password": newPassword])
    }
    private func post(_ client: any HTTPClient, _ path: String, _ body: [String: String], status: Int = 204) async throws -> AppResult<Void> {
        try Task.checkCancellation()
        return try await client.requestEntity(.POST, path: "identity/\(path)", options: .init(body: .object(body.mapValues(JSONValue.string)))).flatMap {
            $0.metadata.status == status ? .success(()) : .failure(Self.response)
        }
    }
    private static func mail(_ s: String) -> Bool { (3...254).contains(s.utf8.count) && s.utf8.allSatisfy { $0 > 32 && $0 < 127 } && s.contains("@") }
    private static func password(_ s: String) -> Bool { s.unicodeScalars.count >= 12 && s.utf8.count <= 256 }
    private static let input = Failure.invalid(.init(message: "Check the address, code and password (at least 12 characters).", code: "identity.invalid_input"), fields: [:])
    private static let response = Failure.internalError(.init(message: "The API returned an unexpected identity response.", code: "identity.invalid_response"))
}
