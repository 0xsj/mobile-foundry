import CryptoKit
import Foundation
import FoundryHTTP
import FoundryKernel

public enum GoAccountSelection: Sendable {
    case personal(name: String)
    case existing(accountID: String)
}

/// Composes owned Identity and Account HTTP contracts. No retry, redirect or 401 replay.
public struct GoSessionAuthority: AccountSelectingAuthority {
    public let id: String
    public let baseURL: String
    private let client: any HTTPClient
    private let selection: GoAccountSelection
    private let now: @Sendable () -> Int64

    public static func create(baseURL: String, transport: any HTTPTransport,
                              selection: GoAccountSelection = .personal(name: "Personal"),
                              timeoutMilliseconds: Int64 = 15_000, allowLocalHTTP: Bool = false,
                              now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) -> AppResult<Self> {
        guard let canonical = canonicalBase(baseURL, allowLocalHTTP: allowLocalHTTP) else { return .failure(configuration) }
        switch selection {
        case .personal(let name): guard GoSessionWire.name(name) else { return .failure(configuration) }
        case .existing(let identity): guard GoSessionWire.uuid(identity) else { return .failure(configuration) }
        }
        let authorityID = deploymentID(canonical)
        return DefaultHTTPClient.create(baseURL: canonical, transport: transport, timeoutMilliseconds: timeoutMilliseconds).map {
            Self(id: authorityID, baseURL: canonical, client: $0, selection: selection, now: now)
        }
    }
    private static let configuration = Failure.internalError(.init(message: "Use a valid HTTPS API base, or explicitly enabled loopback HTTP, and an admitted account selection.", code: "session.go_configuration"))
    static func deploymentID(_ canonical: String) -> String {
        let digest = SHA256.hash(data: Data("go.identity.v1\n\(canonical)".utf8)).map { String(format: "%02x", $0) }.joined()
        return "go.identity.v1.\(digest)"
    }
    static func canonicalBase(_ value: String, allowLocalHTTP: Bool) -> String? {
        guard value == value.trimmingCharacters(in: .whitespacesAndNewlines), let parts = URLComponents(string: value),
              let scheme = parts.scheme?.lowercased(), let rawHost = parts.host?.lowercased(), parts.user == nil, parts.password == nil,
              parts.query == nil, parts.fragment == nil, parts.port == nil || (1...65535).contains(parts.port!) else { return nil }
        let host = rawHost.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
        guard host == "::1" || host.range(of: "^[a-z0-9.-]+$", options: .regularExpression) != nil,
              scheme == "https" || (allowLocalHTTP && scheme == "http" && ["localhost", "127.0.0.1", "::1"].contains(host)) else { return nil }
        var path = parts.percentEncodedPath
        while path.hasSuffix("/") { path.removeLast() }
        for segment in path.split(separator: "/", omittingEmptySubsequences: false).dropFirst() {
            guard segment != ".", segment != "..", !segment.isEmpty,
                  segment.range(of: "^[A-Za-z0-9._~-]+$", options: .regularExpression) != nil else { return nil }
        }
        let port = parts.port.flatMap { (scheme == "https" && $0 == 443) || (scheme == "http" && $0 == 80) ? nil : $0 }
        return "\(scheme)://\(host == "::1" ? "[::1]" : host)\(port.map { ":\($0)" } ?? "")\(path)"
    }
    public func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> {
        try Task.checkCancellation()
        let answer = try await client.requestEntity(.POST, path: "identity/sign-in", options: .init(body: .object(["login": .string(command.login), "password": .string(command.password)])))
        switch answer {
        case .failure(let failure): return .failure(failure)
        case .success(let entity):
            guard let tokens = GoSessionWire.tokens(entity, now: now()), entity.body.object?["principal"]?.object?["login"]?.string == command.login else { return .failure(GoSessionWire.failure(entity)) }
            let account: AppResult<Account>
            switch selection {
            case .personal(let name): account = try await read(tokens, path: "accounts/bootstrap", name: name)
            case .existing(let identity): account = try await read(tokens, path: "accounts/\(identity)")
            }
            switch account {
            case .success(let value): return grant(tokens, account: value)
            case .failure(let failure):
                // Best-effort cleanup of the known new family. Preserve the primary refusal.
                _ = try await revokeToken(tokens.refresh, timeout: 2000)
                return .failure(failure)
            }
        }
    }
    /// Direct callers own credential persistence; controllers use the checkpoint overload.
    public func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> {
        try await renew(credential, checkpoint: { _ in .success(()) })
    }
    public func renew(_ credential: StoredCredential, checkpoint: @escaping CredentialCheckpoint) async throws -> AppResult<SessionGrant> {
        try Task.checkCancellation()
        guard credential.scope.authorityID == id, GoSessionWire.uuid(credential.scope.accountID), GoSessionWire.uuid(credential.scope.principalID) else { return .failure(SessionFailures.scopeMismatch) }
        guard GoSessionWire.token(credential.refreshToken, prefix: "r1") else { return .failure(SessionFailures.rejected) }
        let answer = try await client.requestEntity(.POST, path: "identity/renew", options: .init(body: .object(["refresh_token": .string(credential.refreshToken)])))
        switch answer {
        case .failure(let failure): return .failure(failure)
        case .success(let entity):
            guard let tokens = GoSessionWire.tokens(entity, now: now()) else { return .failure(GoSessionWire.failure(entity)) }
            guard tokens.principal.id == credential.scope.principalID,
                  let rotated = StoredCredential(scope: credential.scope, refreshToken: tokens.refresh) else { return .failure(SessionFailures.scopeMismatch) }
            if case .failure(let failure) = try await checkpoint(rotated) { return .failure(failure) }
            try Task.checkCancellation()
            return try await read(tokens, path: "accounts/\(credential.scope.accountID)").flatMap { grant(tokens, account: $0) }
        }
    }
    public func selectAccount(_ accountID: String, grant: SessionGrant) async throws -> AppResult<SessionGrant> {
        try Task.checkCancellation()
        guard grant.scope.authorityID == id, GoSessionWire.uuid(accountID), GoSessionWire.uuid(grant.principal.id), grant.expiresAtMillis > now() else { return .failure(SessionFailures.scopeMismatch) }
        let tokens = GoSessionWire.Tokens(principal: grant.principal, access: grant.accessToken, refresh: grant.refreshToken, expiry: grant.expiresAtMillis)
        return try await read(tokens, path: "accounts/\(accountID)").flatMap { self.grant(tokens, account: $0) }
    }
    public func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> {
        try Task.checkCancellation()
        guard credential.scope.authorityID == id else { return .failure(SessionFailures.scopeMismatch) }
        guard GoSessionWire.token(credential.refreshToken, prefix: "r1") else { return .failure(SessionFailures.rejected) }
        return try await revokeToken(credential.refreshToken)
    }
    private func revokeToken(_ token: String, timeout: Int64? = nil) async throws -> AppResult<Void> {
        let answer = try await client.requestEntity(.POST, path: "identity/sign-out", options: .init(timeoutMilliseconds: timeout, body: .object(["refresh_token": .string(token)])))
        return answer.flatMap { $0.metadata.status == 204 ? .success(()) : .failure(GoSessionWire.failure($0)) }
    }
    private func read(_ tokens: GoSessionWire.Tokens, path: String, name: String? = nil) async throws -> AppResult<Account> {
        let answer = try await client.requestEntity(name == nil ? .GET : .POST, path: path,
            options: .init(headers: ["authorization": "Bearer \(tokens.access)"], body: name.map { .object(["name": .string($0)]) }))
        return answer.flatMap { entity in
            guard (name == nil ? [200] : [200, 201]).contains(entity.metadata.status),
                  let account = GoSessionWire.account(entity.body, ownerID: tokens.principal.id),
                  let version = GoSessionWire.version(entity.body.object?["version"]), entity.metadata.etag == "\"\(version)\"",
                  name != nil || path == "accounts/\(account.id)" else { return .failure(GoSessionWire.failure(entity)) }
            return .success(account)
        }
    }
    private func grant(_ tokens: GoSessionWire.Tokens, account: Account) -> AppResult<SessionGrant> {
        guard tokens.expiry > now(), let value = SessionGrant(authorityID: id, principal: tokens.principal, account: account,
            accessToken: tokens.access, refreshToken: tokens.refresh, expiresAtMillis: tokens.expiry) else { return .failure(SessionFailures.expired) }
        return .success(value)
    }
}
