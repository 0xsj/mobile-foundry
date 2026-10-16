@testable import FoundryCatalog
import Foundation
import FoundryData
import FoundrySession
import Testing

@MainActor @Test func catalogProbeReturns401WithoutRenewingAndDropsWorkOnClosure() async throws {
    let model = SessionCatalogModel(vault: MemoryCredentialVault(), clock: ManualSessionClock(nowMillis: 1_700_000_000_000),
        notes: AccountNotesRepositories(storage: .memory, root: URL(fileURLWithPath: "/unused-memory-notes")), onUnexpectedError: { _ in Issue.record("Unexpected defect") })
    await model.controller.signIn(login: "alex@example.test", password: "catalog-demo")?.value
    let lease = try #require(model.controller.lease), probe = AuthenticatedProbeStore(session: model.controller, lease: lease)
    await probe.run(.unauthorized)?.value
    #expect(probe.report != nil && model.controller.lease == lease)
    #expect(!(probe.report?.contains("access.") ?? true))
    let task = try #require(probe.run(.slow)); probe.shutdown(); await model.controller.signOut()?.value; await task.value
    #expect(probe.report == nil && !probe.isBusy && probe.run(.success) == nil)
}
