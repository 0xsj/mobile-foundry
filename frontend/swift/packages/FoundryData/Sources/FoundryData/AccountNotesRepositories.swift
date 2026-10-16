import CryptoKit
import Foundation
import FoundryServices
import FoundrySession

/// Naming only. Principal changes within an account do not change data ownership.
public enum AccountNotesPartition {
    public static func identifier(for scope: SessionScope) -> String {
        let canonical = "account-notes.v1\n\(scope.authorityID)\n\(scope.accountID)"
        return SHA256.hash(data: Data(canonical.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
public enum AccountNotesStorage: Sendable { case device, memory }
/// One retained factory per source. Guest Notes use a separate, unchanged namespace.
@MainActor public final class AccountNotesRepositories {
    private let storage: AccountNotesStorage
    private let root: URL
    private var repositories: [String: any ObservableNotesRepository] = [:]
    public init(storage: AccountNotesStorage, root: URL) { precondition(root.isFileURL); self.storage = storage; self.root = root }
    public func repository(for scope: SessionScope) -> any ObservableNotesRepository {
        let key = AccountNotesPartition.identifier(for: scope)
        if let repository = repositories[key] { return repository }
        let repository: any ObservableNotesRepository
        switch storage {
        case .device: repository = SQLiteNotesRepository(url: root.appendingPathComponent(key + ".sqlite"))
        case .memory: repository = InMemoryNotesRepository()
        }
        repositories[key] = repository; return repository
    }
    public func bind(session: SessionController, lease: SessionLease) -> SessionNotesRepository {
        SessionNotesRepository(repository: repository(for: lease.scope), session: session, lease: lease)
    }
}
