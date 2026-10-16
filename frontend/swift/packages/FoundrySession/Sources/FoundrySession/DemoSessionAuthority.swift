import Foundation
import FoundryKernel

/// Local fixture authority. It proves no identity and must not authorize real data.
public actor DemoSessionAuthority: SessionAuthority {
    public nonisolated let id = "catalog-demo.v1"
    private let now: @Sendable () -> Int64
    private var revoked: Set<String> = []
    private var rejectsRenewals = false
    public init(now: @escaping @Sendable () -> Int64) { self.now = now }
    public func rejectRenewals() { rejectsRenewals = true }
    public func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> {
        try Task.checkCancellation()
        guard command.password == "catalog-demo", let person = ["alex@example.test": "alex", "sam@example.test": "sam"][command.login] else {
            return .failure(.unauthenticated(.init(message: "Use a listed demo account and catalog-demo password.", code: "session.demo_credentials")))
        }
        return grant(person)
    }
    public func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> {
        try Task.checkCancellation()
        let parts = credential.refreshToken.split(separator: ".", omittingEmptySubsequences: false)
        guard !rejectsRenewals, credential.scope.authorityID == id, parts.count == 4, parts[0] == "demo", parts[1] == "refresh",
              ["alex", "sam"].contains(String(parts[2])), UUID(uuidString: String(parts[3])) != nil,
              !revoked.contains(credential.refreshToken) else { return .failure(SessionFailures.rejected) }
        revoked.insert(credential.refreshToken)
        return grant(String(parts[2]))
    }
    public func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> {
        try Task.checkCancellation(); revoked.insert(credential.refreshToken); return .success(())
    }
    private func grant(_ person: String) -> AppResult<SessionGrant> {
        let name = person == "alex" ? "Alex" : "Sam"
        let time = now()
        guard time > 0, time <= Int64.max - 60_000 else { return .failure(SessionFailures.expired) }
        return .success(SessionGrant(authorityID: id, principal: Principal(id: person, displayName: name)!,
            account: Account(id: "\(person)-personal", name: "\(name)'s account")!,
            accessToken: "demo.access.\(UUID().uuidString)", refreshToken: "demo.refresh.\(person).\(UUID().uuidString)",
            expiresAtMillis: time + 60_000)!)
    }
}
/// Test/catalog manual time only; no dependency on a UI executor.
public final class ManualSessionClock: @unchecked Sendable {
    private let lock = NSLock(); private var value: Int64
    public init(nowMillis: Int64) { value = nowMillis }
    public var nowMillis: Int64 { lock.withLock { value } }
    public func advance(milliseconds: Int64) { precondition(milliseconds >= 0 && milliseconds <= 86_400_000); lock.withLock { value += milliseconds } }
}
