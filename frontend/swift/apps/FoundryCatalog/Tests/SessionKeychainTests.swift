import Foundation
import FoundryKernel
import FoundrySession
import Security
import Testing
@testable import FoundryCatalog

private func keychainQuery(_ service: String) -> [String: Any] {
    [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "refresh", kSecAttrSynchronizable as String: false]
}
private func savedCredential(_ person: String) -> StoredCredential {
    StoredCredential(scope: SessionScope(authorityID: "catalog-demo.v1", principalID: person, accountID: "\(person)-personal")!, refreshToken: "refresh.\(person).opaque")!
}
@Test func keychainCredentialSurvivesNewAdapterUpdatesAndIdempotentClear() async throws {
    let service = "dev.mobilefoundry.session.tests.\(UUID().uuidString)"
    defer { SecItemDelete(keychainQuery(service) as CFDictionary) }
    let first = KeychainCredentialVault(service: service)
    #expect(try await first.load().get() == nil)
    _ = try await first.save(savedCredential("alex")).get()
    #expect(try await KeychainCredentialVault(service: service).load().get() == savedCredential("alex"))
    _ = try await first.save(savedCredential("sam")).get()
    #expect(try await first.load().get() == savedCredential("sam"))
    var query = keychainQuery(service); query[kSecReturnAttributes as String] = true
    var attributes: CFTypeRef?; #expect(SecItemCopyMatching(query as CFDictionary, &attributes) == errSecSuccess)
    let item = try #require(attributes as? [String: Any])
    #expect(item[kSecAttrAccessible as String] as? String == kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String)
    #expect((item[kSecAttrSynchronizable as String] as? Bool) != true)
    _ = try await first.clear().get(); _ = try await first.clear().get()
    #expect(try await KeychainCredentialVault(service: service).load().get() == nil)
}
@Test func malformedAndFutureKeychainRecordsAreKeptUntilExplicitClear() async throws {
    let service = "dev.mobilefoundry.session.tests.\(UUID().uuidString)"
    defer { SecItemDelete(keychainQuery(service) as CFDictionary) }
    let vault = KeychainCredentialVault(service: service)
    _ = try await vault.save(savedCredential("alex")).get()
    for bytes in [Data("invalid JSON".utf8), Data("{\"version\":2,\"authority\":\"catalog-demo.v1\",\"principal\":\"alex\",\"account\":\"alex-personal\",\"refreshToken\":\"token\"}".utf8)] {
        #expect(SecItemUpdate(keychainQuery(service) as CFDictionary, [kSecValueData as String: bytes] as CFDictionary) == errSecSuccess)
        #expect(try await vault.load() == .failure(SessionFailures.invalidCredential))
        var query = keychainQuery(service); query[kSecReturnData as String] = true
        var read: CFTypeRef?; #expect(SecItemCopyMatching(query as CFDictionary, &read) == errSecSuccess); #expect(read as? Data == bytes)
    }
    _ = try await vault.clear().get(); #expect(try await vault.load().get() == nil)
}
@Test @MainActor func sessionGalleryLifecycleUsesRealKeychainWithRestoreExpiryAndSignOut() async throws {
    let service = "dev.mobilefoundry.session.tests.\(UUID().uuidString)"
    defer { SecItemDelete(keychainQuery(service) as CFDictionary) }
    let vault = KeychainCredentialVault(service: service), clock = ManualSessionClock(nowMillis: 1_700_000_000_000)
    func owner() -> SessionCatalogModel { SessionCatalogModel(vault: vault, clock: clock) { _ in Issue.record("Unexpected defect") } }
    let first = owner(); await first.controller.signIn(login: "alex@example.test", password: "catalog-demo")?.value
    #expect(first.controller.snapshot.phase == .signedIn); #expect(first.controller.lease != nil)
    first.controller.shutdown(); let reopened = owner(); await reopened.controller.restore()?.value
    #expect(reopened.controller.snapshot.principal?.id == "alex")
    reopened.advanceTime(); #expect(reopened.controller.lease == nil)
    await reopened.controller.renew()?.value; #expect(reopened.controller.lease != nil)
    await reopened.rejectRenewal().value; #expect(reopened.controller.snapshot.failure == SessionFailures.rejected)
    await reopened.controller.signOut()?.value; #expect(reopened.controller.snapshot.phase == .signedOut); #expect(try await vault.load().get() == nil)
}
