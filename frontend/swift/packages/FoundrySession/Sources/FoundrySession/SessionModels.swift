import Foundation
import FoundryKernel

private func validID(_ value: String) -> Bool {
    !value.isEmpty && value.utf8.count <= 200 && value.utf8.allSatisfy {
        (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || [45, 46, 95].contains($0)
    }
}
private func validName(_ value: String) -> Bool { value.unicodeScalars.count <= 200 && !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
private func validSecret(_ value: String) -> Bool { !value.isEmpty && value.utf8.count <= 8192 && value.utf8.allSatisfy { (33...126).contains($0) } }

public struct Principal: Equatable, Sendable {
    public let id: String; public let displayName: String
    public init?(id: String, displayName: String) { guard validID(id), validName(displayName) else { return nil }; self.id = id; self.displayName = displayName }
}
public struct Account: Equatable, Sendable {
    public let id: String; public let name: String
    public init?(id: String, name: String) { guard validID(id), validName(name) else { return nil }; self.id = id; self.name = name }
}
public struct SessionScope: Hashable, Sendable {
    public let authorityID: String; public let principalID: String; public let accountID: String
    public init?(authorityID: String, principalID: String, accountID: String) {
        guard validID(authorityID), validID(principalID), validID(accountID) else { return nil }
        self.authorityID = authorityID; self.principalID = principalID; self.accountID = accountID
    }
}
public struct SignIn: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let login: String; public let password: String
    private init(login: String, password: String) { self.login = login; self.password = password }
    public static func make(login: String, password: String) -> AppResult<Self> {
        let normalized = login.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        var fields: [String: String] = [:]
        if normalized.isEmpty || normalized.unicodeScalars.count > 320 { fields["login"] = "Enter a login of up to 320 characters." }
        if password.isEmpty || password.unicodeScalars.count > 1024 { fields["password"] = "Enter a password of up to 1024 characters." }
        guard fields.isEmpty else { return .failure(.invalid(.init(message: "Check your sign-in details.", code: "session.invalid_sign_in"), fields: fields)) }
        return .success(Self(login: normalized, password: password))
    }
    public var description: String { "SignIn(<redacted>)" }; public var debugDescription: String { description }
}
public struct SessionGrant: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let scope: SessionScope; public let principal: Principal; public let account: Account
    public let accessToken: String; public let refreshToken: String; public let expiresAtMillis: Int64
    public init?(authorityID: String, principal: Principal, account: Account, accessToken: String, refreshToken: String, expiresAtMillis: Int64) {
        guard let scope = SessionScope(authorityID: authorityID, principalID: principal.id, accountID: account.id),
              validSecret(accessToken), validSecret(refreshToken), expiresAtMillis > 0 else { return nil }
        self.scope = scope; self.principal = principal; self.account = account; self.accessToken = accessToken
        self.refreshToken = refreshToken; self.expiresAtMillis = expiresAtMillis
    }
    public var description: String { "SessionGrant(<redacted>)" }; public var debugDescription: String { description }
}
public struct StoredCredential: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let scope: SessionScope; public let refreshToken: String
    public init?(scope: SessionScope, refreshToken: String) { guard validSecret(refreshToken) else { return nil }; self.scope = scope; self.refreshToken = refreshToken }
    public init(_ grant: SessionGrant) { scope = grant.scope; refreshToken = grant.refreshToken }
    private struct Record: Codable { let version: Int; let authority: String; let principal: String; let account: String; let refreshToken: String }
    public func encoded() throws -> Data {
        try JSONEncoder().encode(Record(version: 1, authority: scope.authorityID, principal: scope.principalID, account: scope.accountID, refreshToken: refreshToken))
    }
    public static func decode(_ data: Data) -> AppResult<Self> {
        guard data.count <= 65_536,
              let record = try? JSONDecoder().decode(Record.self, from: data), record.version == 1,
              let scope = SessionScope(authorityID: record.authority, principalID: record.principal, accountID: record.account),
              let value = Self(scope: scope, refreshToken: record.refreshToken) else { return .failure(SessionFailures.invalidCredential) }
        return .success(value)
    }
    public var description: String { "StoredCredential(<redacted>)" }; public var debugDescription: String { description }
}
public enum SessionPhase: String, Sendable { case signedOut, restoring, signingIn, signedIn, renewing, selectingAccount, signingOut, blocked
    public var isBusy: Bool { [.restoring, .signingIn, .renewing, .selectingAccount, .signingOut].contains(self) }
}
public struct SessionSnapshot: Equatable, Sendable {
    public let phase: SessionPhase; public let principal: Principal?; public let account: Account?
    public let failure: Failure?; public let epoch: UInt64
    public init(phase: SessionPhase = .signedOut, principal: Principal? = nil, account: Account? = nil, failure: Failure? = nil, epoch: UInt64 = 0) {
        self.phase = phase; self.principal = principal; self.account = account; self.failure = failure; self.epoch = epoch
    }
}
public struct SessionLease: Hashable, Sendable { public let scope: SessionScope; public let epoch: UInt64; let ownerID: UUID }
public enum SessionFailures {
    public static let scopeChanged = Failure.canceled(.init(message: "Your session changed. Reopen or sign in before trying again.", code: "session.scope_changed"))
    public static let invalidCredential = Failure.unavailable(.init(message: "Saved session data could not be read. It has been kept.", code: "session.credential_invalid"))
    public static let storage = Failure.unavailable(.init(message: "Session storage is unavailable. Try again.", code: "session.storage_unavailable"))
    public static let expired = Failure.unauthenticated(.init(message: "Your access has expired. Renew or sign in again.", code: "session.expired"))
    public static let rejected = Failure.unauthenticated(.init(message: "Sign in again to continue.", code: "session.rejected"))
    public static let scopeMismatch = Failure.unauthenticated(.init(message: "The saved session no longer matches this account. Sign in again.", code: "session.scope_mismatch"))
}
