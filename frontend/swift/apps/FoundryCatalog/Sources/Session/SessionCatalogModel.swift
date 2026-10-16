import Foundation
import FoundrySession
import FoundryData
import OSLog

/// Retain fixture dependencies with the controller across SwiftUI value reconstruction.
@MainActor final class SessionCatalogModel {
    let controller: SessionController
    let clock: ManualSessionClock
    let authority: DemoSessionAuthority
    let notes: AccountNotesRepositories
    init(vault: any CredentialVault, clock: ManualSessionClock = ManualSessionClock(nowMillis: Int64(Date().timeIntervalSince1970 * 1000)),
         notes: AccountNotesRepositories = AccountNotesRepositories(storage: .device, root: URL.applicationSupportDirectory.appendingPathComponent("AccountNotes/v1")),
         onUnexpectedError: @escaping (any Error) -> Void = { error in
             Logger(subsystem: "dev.mobilefoundry.catalog", category: "session")
                 .error("Unexpected session error: \(String(reflecting: error), privacy: .private)")
         }) {
        self.clock = clock
        self.notes = notes
        let authority = DemoSessionAuthority(now: { clock.nowMillis }); self.authority = authority
        controller = SessionController(authority: authority, vault: vault, now: { clock.nowMillis }, onUnexpectedError: onUnexpectedError)
    }
    func advanceTime() { clock.advance(milliseconds: 61_000); controller.expireIfNeeded() }
    @discardableResult func rejectRenewal() -> Task<Void, Never> {
        Task { await authority.rejectRenewals(); await controller.renew()?.value }
    }
}
