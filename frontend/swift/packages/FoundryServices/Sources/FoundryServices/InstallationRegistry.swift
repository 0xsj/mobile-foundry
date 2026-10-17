import Foundation
import FoundryKernel

/// Installation ownership survives login/account changes. Never put this key in UI or logs.
public struct InstallationIdentity: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let id: String
    public let key: String
    public init?(id: String, key: String) {
        guard RemoteServiceAdmission.uuid(id), key.hasPrefix("i1."), key.utf8.count == 46 else { return nil }
        let encoded = String(key.dropFirst(3))
        guard encoded.range(of: "^[A-Za-z0-9_-]{43}\\z", options: .regularExpression) != nil,
              let bytes = Data(base64Encoded: encoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/") + "="), bytes.count == 32,
              bytes.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") == encoded else { return nil }
        self.id = id; self.key = key
    }
    public var description: String { "InstallationIdentity(<redacted>)" }
    public var debugDescription: String { description }
    private struct Record: Codable { let version: Int; let id: String; let key: String }
    public func encoded() throws -> Data { try JSONEncoder().encode(Record(version: 1, id: id, key: key)) }
    public static func decode(_ bytes: Data) -> AppResult<Self> {
        guard bytes.count <= 1024, let r = try? JSONDecoder().decode(Record.self, from: bytes), r.version == 1,
              let value = Self(id: r.id, key: r.key) else { return .failure(InstallationFailures.invalidStorage) }
        return .success(value)
    }
}
/// Must serialize load/create and acknowledge durable storage before returning a new identity.
/// Corrupt records and lost encryption keys are failures, never implicit resets.
public protocol InstallationVault: Sendable { func loadOrCreate() async throws -> AppResult<InstallationIdentity> }

public enum PushProvider: String, Sendable { case apns, fcm }
public enum PushEnvironment: String, Sendable { case sandbox, production }
public struct PushRoute: Equatable, Sendable {
    public let provider: PushProvider; public let application: String; public let environment: PushEnvironment
    public init?(provider: PushProvider, application: String, environment: PushEnvironment) {
        guard application.range(of: "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\z", options: .regularExpression) != nil,
              provider != .fcm || environment == .production else { return nil }
        self.provider = provider; self.application = application; self.environment = environment
    }
}
/// Opaque address; admission here does not establish a real APNs/FCM token.
public struct DeliveryTarget: Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let route: PushRoute; public let token: String
    public init?(route: PushRoute, token: String) {
        guard (1...4096).contains(token.utf8.count), token.utf8.allSatisfy({ (33...126).contains($0) }) else { return nil }
        self.route = route; self.token = token
    }
    public var description: String { "DeliveryTarget(<redacted>)" }; public var debugDescription: String { description }
}
public struct InstallationRecord: Equatable, Sendable {
    public let id: String; public let registrationID: String; public let version: Int64
    public let route: PushRoute; public let registered: Bool; public let currentBinding: Bool
    public let createdAt: Date; public let updatedAt: Date; public let expiresAt: Date
    public init?(id: String, registrationID: String, version: Int64, route: PushRoute, registered: Bool, currentBinding: Bool, createdAt: Date, updatedAt: Date, expiresAt: Date) {
        guard RemoteServiceAdmission.uuid(id), RemoteServiceAdmission.uuid(registrationID), (1...9_007_199_254_740_991).contains(version),
              [createdAt, updatedAt, expiresAt].allSatisfy(RemoteServiceAdmission.date), createdAt <= updatedAt, createdAt < expiresAt else { return nil }
        self.id = id; self.registrationID = registrationID; self.version = version; self.route = route; self.registered = registered; self.currentBinding = currentBinding
        self.createdAt = createdAt; self.updatedAt = updatedAt; self.expiresAt = expiresAt
    }
}
/// Implementations are bound to one exact session lease. No account target or automatic retry.
public protocol InstallationRegistry: Sendable {
    func read(_ identity: InstallationIdentity) async throws -> AppResult<InstallationRecord?>
    func register(_ identity: InstallationIdentity, registrationID: String, target: DeliveryTarget, replacing: InstallationRecord?) async throws -> AppResult<InstallationRecord>
    func unregister(_ identity: InstallationIdentity, current: InstallationRecord) async throws -> AppResult<InstallationRecord>
}
public enum InstallationFailures {
    public static let invalidInput = Failure.invalid(.init(message: "Check the installation target and current revision.", code: "installations.invalid_input"), fields: [:])
    public static let invalidStorage = Failure.unavailable(.init(message: "The saved installation key could not be read. It has been kept.", code: "installations.storage_invalid"))
    public static let storage = Failure.unavailable(.init(message: "Secure installation storage is unavailable. Unlock the device and try again.", code: "installations.storage_unavailable"))
}
