import Foundation
import FoundryHTTP
import FoundryKernel

public struct HTTPInstallationRegistry: InstallationRegistry {
    private let client: any HTTPClient
    public init(client: any HTTPClient) { self.client = client }
    public func read(_ identity: InstallationIdentity) async throws -> AppResult<InstallationRecord?> {
        let result = try await request(.GET, path: "installations/\(identity.id)", identity: identity)
        if case .failure(let failure) = result, failure.kind == .notFound { return .success(nil) }
        return result.flatMap { e in
            guard e.metadata.status == 200, let value = decode(e), value.id == identity.id else { return .failure(invalid(e)) }
            return .success(value)
        }
    }
    public func register(_ identity: InstallationIdentity, registrationID: String, target: DeliveryTarget, replacing: InstallationRecord?) async throws -> AppResult<InstallationRecord> {
        guard RemoteServiceAdmission.uuid(registrationID), replacing == nil || (replacing!.id == identity.id && replacing!.registrationID != registrationID && replacing!.version < 9_007_199_254_740_991) else { return .failure(InstallationFailures.invalidInput) }
        var body: [String: JSONValue] = ["registration_id": .string(registrationID), "provider": .string(target.route.provider.rawValue), "application": .string(target.route.application), "environment": .string(target.route.environment.rawValue), "token": .string(target.token)]
        if replacing == nil { body["installation_id"] = .string(identity.id) }
        return try await request(replacing == nil ? .POST : .PUT, path: replacing == nil ? "installations" : "installations/\(identity.id)", identity: identity, body: .object(body), version: replacing?.version).flatMap { e in
            guard e.metadata.status == (replacing == nil ? 201 : 200), let value = decode(e), value.id == identity.id,
                  value.registrationID == registrationID, value.version == (replacing?.version ?? 0) + 1, value.route == target.route, value.registered, value.currentBinding,
                  replacing == nil || replacing!.createdAt == value.createdAt else { return .failure(invalid(e)) }
            return .success(value)
        }
    }
    public func unregister(_ identity: InstallationIdentity, current: InstallationRecord) async throws -> AppResult<InstallationRecord> {
        guard current.id == identity.id, current.currentBinding, !current.registered || current.version < 9_007_199_254_740_991 else { return .failure(InstallationFailures.invalidInput) }
        return try await request(.DELETE, path: "installations/\(identity.id)", identity: identity, version: current.version).flatMap { e in
            guard e.metadata.status == 200, let value = decode(e), value.id == identity.id, value.registrationID == current.registrationID,
                  value.version == current.version + (current.registered ? 1 : 0), value.route == current.route, !value.registered, value.currentBinding,
                  value.createdAt == current.createdAt, value.expiresAt == current.expiresAt else { return .failure(invalid(e)) }
            return .success(value)
        }
    }
    private func request(_ method: HTTPMethod, path: String, identity: InstallationIdentity, body: JSONValue? = nil, version: Int64? = nil) async throws -> AppResult<Entity> {
        try Task.checkCancellation()
        let result = try await client.requestEntity(method, path: path, options: .init(headers: ["X-Installation-Key": identity.key], body: body, ifMatch: version.map { "\"\($0)\"" }))
        try Task.checkCancellation(); return result
    }
    private func decode(_ e: Entity) -> InstallationRecord? {
        guard let b = e.body.object, Set(b.keys) == Set(["id","registration_id","version","provider","application","environment","registered","current_binding","created_at","updated_at","expires_at"]),
              let id = b["id"]?.string, let registration = b["registration_id"]?.string,
              case .number(let n) = b["version"], n.isFinite, n >= 1, n <= 9_007_199_254_740_991, n.rounded(.towardZero) == n,
              e.metadata.etag == "\"\(Int64(n))\"",
              let provider = b["provider"]?.string.flatMap(PushProvider.init(rawValue:)), let app = b["application"]?.string,
              let env = b["environment"]?.string.flatMap(PushEnvironment.init(rawValue:)), let route = PushRoute(provider: provider, application: app, environment: env),
              case .bool(let registered) = b["registered"], case .bool(let binding) = b["current_binding"],
              let created = RemoteServiceAdmission.timestamp(b["created_at"]?.string), let updated = RemoteServiceAdmission.timestamp(b["updated_at"]?.string), let expires = RemoteServiceAdmission.timestamp(b["expires_at"]?.string) else { return nil }
        return .init(id: id, registrationID: registration, version: Int64(n), route: route, registered: registered, currentBinding: binding, createdAt: created, updatedAt: updated, expiresAt: expires)
    }
    private func invalid(_ e: Entity) -> Failure { .internalError(.init(message: "The installation response was invalid.", code: "installations.invalid_remote_response", requestID: e.metadata.requestID, correlationID: e.metadata.correlationID)) }
}
