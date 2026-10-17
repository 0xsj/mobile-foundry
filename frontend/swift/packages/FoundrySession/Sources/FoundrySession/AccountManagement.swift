import Foundation
import FoundryHTTP
import FoundryKernel

/// Account names count Unicode scalars, preserve normalization and trim Go's space set.
public struct AccountName: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let value: String
    private init(_ value: String) { self.value = value }
    private static let spaces = CharacterSet(charactersIn: "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D} \u{0085}\u{00A0}\u{1680}\u{2000}\u{2001}\u{2002}\u{2003}\u{2004}\u{2005}\u{2006}\u{2007}\u{2008}\u{2009}\u{200A}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}")
    public static func make(_ input: String) -> AppResult<Self> {
        let value = input.trimmingCharacters(in: spaces)
        guard (1...80).contains(value.unicodeScalars.count), value.unicodeScalars.allSatisfy({ $0.properties.generalCategory != .control }) else {
            return .failure(.invalid(.init(message: "Enter a workspace name of 1–80 characters without control characters.", code: "account.invalid_name"), fields: ["name": "Use 1–80 characters without control characters."]))
        }
        return .success(Self(value))
    }
    public var description: String { "AccountName(<redacted>)" }
    public var debugDescription: String { description }
}

/// A versioned observation, separate from the session's identity/account selection.
public struct ManagedAccount: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let account: Account
    public let ownerID: String
    public let version: UInt64
    public let createdAtMicros: Int64
    public let updatedAtMicros: Int64
    public init?(id: String, ownerID: String, name: String, version: UInt64, createdAtMicros: Int64, updatedAtMicros: Int64) {
        guard let account = Account(id: id, name: name), Principal(id: ownerID, displayName: "Owner") != nil,
              case .success(let admitted) = AccountName.make(name), admitted.value.utf8.elementsEqual(name.utf8),
              (1...9_007_199_254_740_991).contains(version), createdAtMicros >= 0, updatedAtMicros >= createdAtMicros else { return nil }
        self.account = account; self.ownerID = ownerID; self.version = version
        self.createdAtMicros = createdAtMicros; self.updatedAtMicros = updatedAtMicros
    }
    public var description: String { "ManagedAccount(<redacted>)" }
    public var debugDescription: String { description }
}

/// One attempt per call. Implementations supply ownership/CAS; no receipt or replay contract.
@MainActor public protocol AccountManagement {
    func read(_ accountID: String) async throws -> AppResult<ManagedAccount>
    func create(_ name: AccountName) async throws -> AppResult<ManagedAccount>
    func rename(_ account: ManagedAccount, to name: AccountName) async throws -> AppResult<ManagedAccount>
}

/// Inject an exact-lease authenticated client. This adapter never renews or selects accounts.
@MainActor public struct HTTPAccountManagement: AccountManagement {
    private let client: any HTTPClient
    private let principalID: String
    public init(client: any HTTPClient, principalID: String) { self.client = client; self.principalID = principalID }
    public func read(_ accountID: String) async throws -> AppResult<ManagedAccount> {
        guard GoSessionWire.uuid(accountID), GoSessionWire.uuid(principalID) else { return .failure(SessionFailures.scopeMismatch) }
        return try await request(.GET, path: "accounts/\(accountID)", status: 200).flatMap {
            $0.account.id == accountID ? .success($0) : .failure(Self.unexpected)
        }
    }
    public func create(_ name: AccountName) async throws -> AppResult<ManagedAccount> {
        guard GoSessionWire.uuid(principalID) else { return .failure(SessionFailures.scopeMismatch) }
        return try await request(.POST, path: "accounts", status: 201, options: .init(body: .object(["name": .string(name.value)]))).flatMap {
            $0.version == 1 && $0.createdAtMicros == $0.updatedAtMicros && $0.account.name.utf8.elementsEqual(name.value.utf8) ? .success($0) : .failure(Self.unexpected)
        }
    }
    public func rename(_ account: ManagedAccount, to name: AccountName) async throws -> AppResult<ManagedAccount> {
        guard GoSessionWire.uuid(account.account.id), account.ownerID == principalID, GoSessionWire.uuid(principalID), account.version < 9_007_199_254_740_991 else {
            return .failure(.invalid(.init(message: "Load a current owned workspace before renaming.", code: "account.rename_input"), fields: [:]))
        }
        return try await request(.PATCH, path: "accounts/\(account.account.id)", status: 200,
            options: .init(headers: ["If-Match": "\"\(account.version)\""], body: .object(["name": .string(name.value)]))).flatMap {
            $0.account.id == account.account.id && $0.version == account.version + 1 && $0.createdAtMicros == account.createdAtMicros &&
            $0.updatedAtMicros >= account.updatedAtMicros && $0.account.name.utf8.elementsEqual(name.value.utf8) ? .success($0) : .failure(Self.unexpected)
        }
    }
    private static var unexpected: Failure { .internalError(.init(message: "The account operation returned an unexpected response. Refresh before another write.", code: "account.invalid_response")) }
    private func request(_ method: HTTPMethod, path: String, status: Int, options: RequestOptions = .init()) async throws -> AppResult<ManagedAccount> {
        try Task.checkCancellation()
        let answer = try await client.requestEntity(method, path: path, options: options)
        try Task.checkCancellation()
        return answer.flatMap { entity in
            guard entity.metadata.status == status, let b = entity.body.object, Set(b.keys) == ["id", "owner_id", "name", "version", "created_at", "updated_at"],
                  let id = b["id"]?.string, GoSessionWire.uuid(id), b["owner_id"]?.string == principalID,
                  let name = b["name"]?.string, let version = GoSessionWire.version(b["version"]), entity.metadata.etag == "\"\(version)\"",
                  let created = b["created_at"]?.string.flatMap(GoSessionWire.micros), let updated = b["updated_at"]?.string.flatMap(GoSessionWire.micros),
                  let value = ManagedAccount(id: id, ownerID: principalID, name: name, version: version, createdAtMicros: created, updatedAtMicros: updated) else {
                return .failure(GoSessionWire.failure(entity))
            }
            return .success(value)
        }
    }
}
