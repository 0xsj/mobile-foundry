import XCTest
import Security
import FoundryKernel
import FoundryServices
import FoundrySession
@testable import FoundryCatalog

final class InstallationVaultTests: XCTestCase {
    private func query(_ service: String) -> [String: Any] { [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"installation.v1",kSecAttrSynchronizable as String:false] }
    func testKeychainReopensSameIdentityAndSeparatesDeployments() async throws {
        let service = "dev.mobilefoundry.test.installation.\(UUID())", other = service + ".other"
        defer { SecItemDelete(query(service) as CFDictionary); SecItemDelete(query(other) as CFDictionary) }
        let vault = KeychainInstallationVault(service: service)
        let first = try await vault.loadOrCreate().get()
        // Clearing auth storage is not clearing physical installation ownership.
        _ = try await KeychainCredentialVault(service: service).clear().get()
        let again = try await KeychainInstallationVault(service: service).loadOrCreate().get()
        XCTAssertEqual(first, again)
        let different = try await KeychainInstallationVault(service: other).loadOrCreate().get()
        XCTAssertNotEqual(first, different)
        var q = query(service); q[kSecReturnAttributes as String] = true
        var result: CFTypeRef?
        XCTAssertEqual(SecItemCopyMatching(q as CFDictionary, &result), errSecSuccess)
        let attrs = try XCTUnwrap(result as? [String: Any])
        XCTAssertEqual(attrs[kSecAttrAccessible as String] as? String, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String)
    }
    func testCorruptionIsKeptAndConcurrentCreationChoosesOneIdentity() async throws {
        let service = "dev.mobilefoundry.test.installation.\(UUID())"
        defer { SecItemDelete(query(service) as CFDictionary) }
        async let left = KeychainInstallationVault(service: service).loadOrCreate()
        async let right = KeychainInstallationVault(service: service).loadOrCreate()
        let (a,b) = try await (left.get(),right.get()); XCTAssertEqual(a,b)
        let bad = Data("{\"version\":99}".utf8)
        XCTAssertEqual(SecItemUpdate(query(service) as CFDictionary,[kSecValueData as String:bad] as CFDictionary),errSecSuccess)
        let result = try await KeychainInstallationVault(service: service).loadOrCreate()
        if case .failure(let error) = result { XCTAssertEqual(error,InstallationFailures.invalidStorage) } else { XCTFail("Replaced corrupt proof") }
        var q = query(service); q[kSecReturnData as String] = true
        var saved: CFTypeRef?; XCTAssertEqual(SecItemCopyMatching(q as CFDictionary,&saved),errSecSuccess)
        XCTAssertEqual(saved as? Data,bad)
    }
}
