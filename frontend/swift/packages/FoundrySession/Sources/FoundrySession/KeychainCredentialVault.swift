import Foundation
import FoundryKernel
import Security

/// Exact app-owned generic-password item. No access group, synchronization or plaintext fallback.
public actor KeychainCredentialVault: CredentialVault {
    private let service: String
    public init(service: String) { precondition(!service.isEmpty); self.service = service }
    private var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
        kSecAttrAccount as String: "refresh", kSecAttrSynchronizable as String: false] }
    public func load() async throws -> AppResult<StoredCredential?> {
        try Task.checkCancellation()
        var request = query; request[kSecReturnData as String] = true; request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return .success(nil) }
        guard status == errSecSuccess else { return .failure(failure(status)) }
        guard let data = result as? Data else { return .failure(SessionFailures.invalidCredential) }
        return StoredCredential.decode(data).map { Optional($0) }
    }
    public func save(_ credential: StoredCredential) async throws -> AppResult<Void> {
        try Task.checkCancellation()
        let attributes: [String: Any] = [kSecValueData as String: try credential.encoded(),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound { status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) }
        return status == errSecSuccess ? .success(()) : .failure(failure(status))
    }
    public func clear() async throws -> AppResult<Void> {
        try Task.checkCancellation()
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound ? .success(()) : .failure(failure(status))
    }
    private func failure(_ status: OSStatus) -> Failure {
        if status == errSecInteractionNotAllowed || status == errSecAuthFailed {
            return .unavailable(.init(message: "Unlock this device to access the saved session.", code: "session.storage_locked"))
        }
        return SessionFailures.storage
    }
}
