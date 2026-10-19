@testable import ProductApp
import Foundation
import FoundryKernel
import FoundryServices
import FoundrySession
import Testing

private actor InboxRepositoryStub: NotificationInboxRepository {
    let scope: SessionScope
    var enabled = true
    var settingsWrites = 0
    var loseSetting = true
    var blockPage = false
    var read = false
    var readWrites = 0
    let duplicatePage: Bool
    var held: CheckedContinuation<AppResult<NotificationInboxPage>, Never>?
    init(_ scope: SessionScope, blockPage: Bool = false, duplicatePage: Bool = false) { self.scope = scope; self.blockPage = blockPage; self.duplicatePage = duplicatePage }
    private var item: InboxNotification {
        InboxNotification(id: "00000000-0000-4000-8000-000000000001", accountID: scope.accountID, principalID: scope.principalID,
            fileID: "00000000-0000-4000-8000-000000000004", occurredAt: Date(timeIntervalSince1970: 1_000), createdAt: Date(timeIntervalSince1970: 1_001), readAt: read ? Date(timeIntervalSince1970: 1_002) : nil)!
    }
    func page(cursor: String?, limit: Int, unreadOnly: Bool) async throws -> AppResult<NotificationInboxPage> {
        if blockPage { return await withCheckedContinuation { held = $0 } }
        return .success(.init(items: unreadOnly && read ? [] : [item], unreadCount: read ? 0 : 1, nextCursor: duplicatePage && cursor == nil ? "next" : nil))
    }
    func release() { held?.resume(returning: .success(.init(items: [item], unreadCount: 999, nextCursor: nil))); held = nil }
    func isHeld() -> Bool { held != nil }
    func mediaEnabled() async throws -> AppResult<Bool> { .success(enabled) }
    func setMediaEnabled(_ value: Bool) async throws -> AppResult<Bool> {
        settingsWrites += 1; enabled = value
        if loseSetting { loseSetting = false; return .failure(.unavailable(.init(message: "Acknowledgement lost"))) }; return .success(value)
    }
    func markRead(_ item: InboxNotification) async throws -> AppResult<InboxNotification> {
        readWrites += 1; read = true
        return .failure(.unavailable(.init(message: "Acknowledgement lost")))
    }
}
@Suite @MainActor struct InboxStoreTests {
    private var account: SessionScope { SessionScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")! }
    private func idle(_ model: InboxStore) async throws {
        for _ in 0..<200 { if !model.busy { return }; try await Task.sleep(for: .milliseconds(5)) }; Issue.record("Inbox owner did not settle")
    }
    @Test func uncertainPreferenceRequiresReadbackAndNeverAutoReplays() async throws {
        let repo = InboxRepositoryStub(account); let model = InboxStore()
        model.bind(repo, scope: account); try await idle(model); #expect(model.canEdit && model.mediaEnabled == true)
        model.setMediaEnabled(false); try await idle(model)
        #expect(model.needsRefresh && !model.canEdit && model.mediaEnabled == true)
        model.setMediaEnabled(false); #expect(await repo.settingsWrites == 1)
        model.refresh(); try await idle(model); #expect(model.mediaEnabled == false && model.canEdit)
        model.bind(nil, scope: nil); #expect(model.items.isEmpty && model.unreadCount == nil)
        model.bind(repo, scope: account); try await idle(model)
        #expect(model.mediaEnabled == false && !model.needsRefresh); #expect(await repo.settingsWrites == 1)
    }
    @Test func lateCompletionCannotPublishAfterAccountReplacement() async throws {
        let old = InboxRepositoryStub(account, blockPage: true); let model = InboxStore(); model.bind(old, scope: account)
        for _ in 0..<200 { if await old.isHeld() { break }; try await Task.sleep(for: .milliseconds(5)) }
        #expect(await old.isHeld())
        let other = SessionScope(authorityID: account.authorityID, principalID: account.principalID, accountID: "00000000-0000-4000-8000-000000000099")!
        let replacement = InboxRepositoryStub(other); model.bind(replacement, scope: other); try await idle(model)
        await old.release(); try await Task.sleep(for: .milliseconds(20))
        #expect(model.unreadCount == 1 && model.items.allSatisfy { $0.accountID == other.accountID })
        model.bind(nil, scope: nil)
    }
    @Test func unconfirmedReadRequiresServerCountAndDoesNotReplay() async throws {
        let repo = InboxRepositoryStub(account); let model = InboxStore()
        model.bind(repo, scope: account); try await idle(model)
        let item = try #require(model.items.first)
        model.markRead(item); try await idle(model)
        #expect(model.needsRefresh && !model.canEdit && model.unreadCount == 1)
        model.markRead(item); #expect(await repo.readWrites == 1)
        model.refresh(); try await idle(model)
        #expect(model.unreadCount == 0 && model.items.first?.isUnread == false && model.canEdit)
        model.chooseUnreadOnly(true); try await idle(model)
        #expect(model.items.isEmpty && model.unreadCount == 0 && !model.needsRefresh)
        #expect(await repo.readWrites == 1)
        model.bind(nil, scope: nil)
    }
    @Test func duplicatePagingStopsUntilFullRefresh() async throws {
        let repo = InboxRepositoryStub(account, duplicatePage: true); let model = InboxStore()
        model.bind(repo, scope: account); try await idle(model); #expect(model.canLoadMore)
        model.loadMore(); try await idle(model)
        #expect(model.items.count == 1 && model.needsRefresh && !model.canLoadMore && model.failure != nil)
        model.refresh(); try await idle(model); #expect(model.items.count == 1 && model.canLoadMore && !model.needsRefresh)
        model.bind(nil, scope: nil)
    }
}
