@testable import ProductApp
import FoundryKernel
import FoundrySession
import Testing

@MainActor private final class WorkspaceService: AccountManagement, AccountDirectory {
    var value = managedWorkspace()
    var writes = 0
    var writeFailure: Failure?
    var mismatch = false
    var waits = false
    var continuation: CheckedContinuation<AppResult<ManagedAccount>, Never>?
    func read(_ accountID: String) async throws -> AppResult<ManagedAccount> { .success(value) }
    func list(cursor: String?, limit: Int) async throws -> AppResult<AccountPage> { .success(AccountPage(accounts: [value.account], nextCursor: nil)) }
    func create(_ name: AccountName) async throws -> AppResult<ManagedAccount> {
        writes += 1
        if waits { return await withCheckedContinuation { continuation = $0 } }
        if let writeFailure { return .failure(writeFailure) }
        return .success(managedWorkspace(id: "project", owner: mismatch ? "foreign" : "principal", name: name.value))
    }
    func rename(_ account: ManagedAccount, to name: AccountName) async throws -> AppResult<ManagedAccount> {
        writes += 1
        if let writeFailure { return .failure(writeFailure) }
        value = managedWorkspace(name: name.value, version: account.version + 1); return .success(value)
    }
    func finish() { continuation?.resume(returning: .success(managedWorkspace(id: "project"))); continuation = nil }
}
private func managedWorkspace(id: String = "personal", owner: String = "principal", name: String = "Personal", version: UInt64 = 1) -> ManagedAccount {
    ManagedAccount(id: id, ownerID: owner, name: name, version: version, createdAtMicros: 1, updatedAtMicros: 1)!
}
private struct WorkspaceAuthority: SessionAuthority {
    let id = "workspace.test"
    func signIn(_ command: SignIn) async throws -> AppResult<SessionGrant> { .success(SessionGrant(authorityID: id, principal: Principal(id: "principal", displayName: "Member")!, account: Account(id: "personal", name: "Personal")!, accessToken: "fixture-access", refreshToken: "fixture-refresh", expiresAtMillis: 1_800_000_000_000)!) }
    func renew(_ credential: StoredCredential) async throws -> AppResult<SessionGrant> { try await signIn(SignIn.make(login: "member", password: "private password").get()) }
    func revoke(_ credential: StoredCredential) async throws -> AppResult<Void> { .success(()) }
}
@MainActor private func workspaceOwner() async -> SessionController {
    let owner = SessionController(authority: WorkspaceAuthority(), vault: MemoryCredentialVault(), now: { 1 }, onUnexpectedError: { _ in Issue.record("Unexpected workspace test failure") })
    await owner.signIn(login: "member", password: "private password")?.value; return owner
}
@MainActor private func workspaceIdle(_ model: WorkspaceModel) async throws {
    for _ in 0..<100 { if !model.isBusy { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Workspace did not settle")
}
@MainActor private func workspaceModel(_ owner: SessionController, _ service: WorkspaceService) async throws -> WorkspaceModel {
    let model = WorkspaceModel(controller: owner); model.bind(service, directory: service, lease: owner.lease); model.refresh(); try await workspaceIdle(model); return model
}
@MainActor @Test func workspaceCreateAdmitsInputBlocksDoubleSubmitAndKeepsSelection() async throws {
    let owner = await workspaceOwner(); defer { owner.shutdown() }; let lease = try #require(owner.lease), service = WorkspaceService()
    let model = try await workspaceModel(owner, service)
    model.create(" \n "); #expect(service.writes == 0 && model.failure?.kind == .invalid)
    model.create(" Project "); model.create("Queued duplicate"); try await workspaceIdle(model)
    #expect(service.writes == 1 && model.created?.account.name == "Project" && !model.canCreate)
    #expect(owner.isCurrent(lease) && owner.snapshot.account?.id == "personal")
    model.create("Another queued duplicate"); #expect(service.writes == 1)
}
@MainActor @Test func workspaceRenameConflictRequiresFreshReadAndPreservesSessionLease() async throws {
    let owner = await workspaceOwner(); defer { owner.shutdown() }; let lease = try #require(owner.lease), service = WorkspaceService()
    let model = try await workspaceModel(owner, service)
    service.writeFailure = .conflict(.init(message: "Workspace changed")); model.rename("Mine"); try await workspaceIdle(model)
    #expect(model.current == nil && model.displayAccount == nil && model.failure?.kind == .conflict && !model.canRename)
    model.rename("Blind retry"); #expect(service.writes == 1)
    service.writeFailure = nil; service.value = managedWorkspace(name: "Someone else's rename", version: 2)
    model.refresh(); try await workspaceIdle(model); #expect(model.current?.version == 2)
    model.rename("Reviewed rename"); try await workspaceIdle(model)
    #expect(model.current?.version == 3 && model.displayAccount?.name == "Reviewed rename" && owner.isCurrent(lease))
    model.cancel(); #expect(model.displayAccount?.name == "Reviewed rename" && !model.canRename)
}
@MainActor @Test func workspaceLostCreateRequiresReadReviewAndExplicitDuplicateRiskChoice() async throws {
    let owner = await workspaceOwner(); defer { owner.shutdown() }; let service = WorkspaceService(), model = try await workspaceModel(owner, service)
    service.writeFailure = .timeout(.init(message: "Lost reply")); model.create("Project"); try await workspaceIdle(model)
    #expect(model.createUncertain && !model.canCreate && service.writes == 1)
    model.refresh(); try await workspaceIdle(model); model.allowNewCreate(); #expect(model.createUncertain && !model.canCreate)
    model.review(); try await workspaceIdle(model); #expect(model.reviewed && model.reviewAccounts.count == 1 && service.writes == 1)
    model.allowNewCreate(); #expect(!model.createUncertain && model.canCreate && service.writes == 1)
    model.create("Second uncertain create"); try await workspaceIdle(model)
    model.allowNewCreate(); #expect(model.createUncertain && !model.reviewed && service.writes == 2)
    model.refresh(); try await workspaceIdle(model); model.review(); try await workspaceIdle(model); model.allowNewCreate()
    service.writeFailure = nil; model.create("Deliberately new"); try await workspaceIdle(model); #expect(service.writes == 3)
}
@MainActor @Test func workspaceCanceledCreateRetainsWarningAndIgnoresLateCompletionAfterRebind() async throws {
    let owner = await workspaceOwner(); defer { owner.shutdown() }; let service = WorkspaceService(), model = try await workspaceModel(owner, service)
    service.waits = true; model.create("Project")
    for _ in 0..<100 { if service.continuation != nil { break }; try await Task.sleep(for: .milliseconds(5)) }
    #expect(service.continuation != nil)
    model.bind(nil, directory: nil, lease: nil); model.bind(service, directory: service, lease: owner.lease)
    #expect(model.createUncertain && !model.canCreate)
    service.finish(); try await Task.sleep(for: .milliseconds(10))
    #expect(model.created == nil && model.createUncertain && service.writes == 1)
}
@MainActor @Test func workspaceMismatchedProviderReplyCannotBecomeConfirmedCreation() async throws {
    let owner = await workspaceOwner(); defer { owner.shutdown() }; let service = WorkspaceService(), model = try await workspaceModel(owner, service)
    service.mismatch = true; model.create("Project"); try await workspaceIdle(model)
    #expect(model.created == nil && model.current == nil && model.createUncertain && model.failure?.kind == .internalError)
}
@MainActor @Test func workspaceOldLeaseCannotPublishEvenWithoutCompositionRebind() async throws {
    let owner = await workspaceOwner(); defer { owner.shutdown() }; let service = WorkspaceService(), model = try await workspaceModel(owner, service)
    service.waits = true; model.create("Project")
    for _ in 0..<100 { if service.continuation != nil { break }; try await Task.sleep(for: .milliseconds(5)) }
    await owner.renew()?.value; let replacement = try #require(owner.lease)
    service.finish(); try await Task.sleep(for: .milliseconds(10))
    #expect(owner.isCurrent(replacement) && model.created == nil && !model.canCreate && service.writes == 1)
    model.bind(service, directory: service, lease: replacement); #expect(model.createUncertain)
}
