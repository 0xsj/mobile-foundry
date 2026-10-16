import Foundation
import FoundrySession

enum SessionProvider: String, CaseIterable, Identifiable { case device = "Device", memory = "Memory"; var id: Self { self } }
@MainActor enum SessionComposition {
    // One device vault instance serializes operations even when a gallery owner is replaced.
    private static let deviceVault = KeychainCredentialVault(service: "dev.mobilefoundry.catalog.demo-session.v1")
    private static var goVaults: [String: KeychainCredentialVault] = [:]
    static func vault(_ provider: SessionProvider) -> any CredentialVault {
        switch provider { case .device: deviceVault; case .memory: MemoryCredentialVault() }
    }
    static func goVault(_ provider: SessionProvider, authorityID: String) -> any CredentialVault {
        guard provider == .device else { return MemoryCredentialVault() }
        if let vault = goVaults[authorityID] { return vault }
        let vault = KeychainCredentialVault(service: "dev.mobilefoundry.catalog.go-session.\(authorityID)")
        goVaults[authorityID] = vault; return vault
    }
}
