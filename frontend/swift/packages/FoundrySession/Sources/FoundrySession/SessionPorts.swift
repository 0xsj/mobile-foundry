import FoundryKernel

public typealias CredentialCheckpoint = @Sendable (StoredCredential) async throws -> AppResult<Void>

public protocol SessionAuthority: Sendable {
    var id: String { get }
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant>
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant>
    /// Multi-step authorities must checkpoint rotated credentials before subsequent IO.
    func renew(_ credential: StoredCredential, checkpoint: @escaping CredentialCheckpoint) async throws -> AppResult<SessionGrant>
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void>
}
extension SessionAuthority {
    public func renew(_ credential: StoredCredential, checkpoint: @escaping CredentialCheckpoint) async throws -> AppResult<SessionGrant> {
        let answer = try await renew(credential)
        if case .success(let grant) = answer, case .failure(let failure) = try await checkpoint(StoredCredential(grant)) { return .failure(failure) }
        return answer
    }
}

public protocol AccountSelectingAuthority: SessionAuthority {
    func selectAccount(_ accountID: String, grant: SessionGrant) async throws -> AppResult<SessionGrant>
}

public struct AccountPage: Sendable {
    public let accounts: [Account]
    public let nextCursor: String?
    public init(accounts: [Account], nextCursor: String?) { self.accounts = accounts; self.nextCursor = nextCursor }
}
public protocol AccountDirectory: Sendable {
    func list(cursor: String?, limit: Int) async throws -> AppResult<AccountPage>
}
/// The controller is the sole writer; each vault instance serializes native/memory access.
public protocol CredentialVault: Sendable {
    func load() async throws -> AppResult<StoredCredential?>
    func save(_ credential: StoredCredential) async throws -> AppResult<Void>
    func clear() async throws -> AppResult<Void>
}
public actor MemoryCredentialVault: CredentialVault {
    private var credential: StoredCredential?
    public init(credential: StoredCredential? = nil) { self.credential = credential }
    public func load() async throws -> AppResult<StoredCredential?> { try Task.checkCancellation(); return .success(credential) }
    public func save(_ credential: StoredCredential) async throws -> AppResult<Void> { try Task.checkCancellation(); self.credential = credential; return .success(()) }
    public func clear() async throws -> AppResult<Void> { try Task.checkCancellation(); credential = nil; return .success(()) }
}
