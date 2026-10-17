import Foundation
import FoundryKernel
import FoundryServices
import Security

/// App/deployment-owned item, deliberately separate from the login credential vault.
actor KeychainInstallationVault: InstallationVault {
    private let service: String
    init(service: String) { precondition(!service.isEmpty); self.service = service }
    private var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
        kSecAttrAccount as String: "installation.v1", kSecAttrSynchronizable as String: false] }
    private func read() -> AppResult<InstallationIdentity?> {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?; let status = SecItemCopyMatching(q as CFDictionary, &value)
        if status == errSecItemNotFound { return .success(nil) }
        guard status == errSecSuccess else { return .failure(InstallationFailures.storage) }
        guard let bytes = value as? Data else { return .failure(InstallationFailures.invalidStorage) }
        return InstallationIdentity.decode(bytes).map { Optional($0) }
    }
    func loadOrCreate() async throws -> AppResult<InstallationIdentity> {
        try Task.checkCancellation()
        switch read() {
        case .failure(let failure): return .failure(failure)
        case .success(let existing?): return .success(existing)
        case .success(nil): break
        }
        guard let identity = newInstallationIdentity() else { return .failure(InstallationFailures.storage) }
        var q = query; q[kSecValueData as String] = try identity.encoded()
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(q as CFDictionary, nil)
        if status == errSecSuccess { return .success(identity) }
        if status == errSecDuplicateItem {
            switch read() { case .success(let saved?): return .success(saved); case .failure(let f): return .failure(f); default: break }
        }
        return .failure(InstallationFailures.storage)
    }
}
actor MemoryInstallationVault: InstallationVault {
    private var identity: InstallationIdentity?
    func loadOrCreate() async throws -> AppResult<InstallationIdentity> {
        try Task.checkCancellation()
        if let identity { return .success(identity) }
        guard let created = newInstallationIdentity() else { return .failure(InstallationFailures.storage) }
        identity = created; return .success(created)
    }
}
private func newInstallationIdentity() -> InstallationIdentity? {
    var bytes = [UInt8](repeating: 0, count: 32)
    guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { return nil }
    let key = "i1." + Data(bytes).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    return InstallationIdentity(id: UUID().uuidString.lowercased(), key: key)
}
