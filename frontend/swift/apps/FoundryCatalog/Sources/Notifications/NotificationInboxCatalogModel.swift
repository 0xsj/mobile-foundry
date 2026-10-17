import FoundryKernel
import FoundryServices
import FoundrySession
import Observation

/// Ephemeral UI ownership; uncertain settings are reconciled by a fresh read,
/// never silently replayed. Every bind invalidates old completions, even renewal.
@MainActor @Observable final class NotificationInboxCatalogModel {
    private(set) var items: [InboxNotification] = []
    private(set) var unreadCount: Int64?
    private(set) var mediaEnabled: Bool?
    private(set) var unreadOnly = false
    private(set) var busy = false
    private(set) var needsRefresh = false
    private(set) var failure: Failure?
    private(set) var nextCursor: String?
    @ObservationIgnored private var repository: (any NotificationInboxRepository)?
    @ObservationIgnored private var scope: SessionScope?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var task: Task<Void, Never>?
    var canEdit: Bool { repository != nil && mediaEnabled != nil && !busy && !needsRefresh }
    var canLoadMore: Bool { canEdit && nextCursor != nil && items.count < 200 }
    func bind(_ repository: (any NotificationInboxRepository)?, scope: SessionScope?) {
        generation += 1; task?.cancel(); task = nil; self.repository = repository; self.scope = scope
        items = []; unreadCount = nil; mediaEnabled = nil; unreadOnly = false; busy = false; needsRefresh = false; failure = nil; nextCursor = nil
        if repository != nil && scope != nil { refresh() }
    }
    func chooseUnreadOnly(_ value: Bool) {
        guard !busy, value != unreadOnly else { return }
        unreadOnly = value; items = []; nextCursor = nil; needsRefresh = true; refresh()
    }
    func refresh() {
        let filter = unreadOnly
        run({ repo -> AppResult<(NotificationInboxPage, Bool)> in
            let page = try await repo.page(cursor: nil, limit: 20, unreadOnly: filter)
            switch page {
            case .failure(let error): return .failure(error)
            case .success(let value): return try await repo.mediaEnabled().map { (value, $0) }
            }
        }) { page, enabled in
            self.items = page.items; self.unreadCount = page.unreadCount; self.nextCursor = page.nextCursor
            self.mediaEnabled = enabled; self.needsRefresh = false
        }
    }
    func loadMore() {
        guard canLoadMore, let cursor = nextCursor else { return }; let filter = unreadOnly
        run({ try await $0.page(cursor: cursor, limit: 20, unreadOnly: filter) }) { page in
            guard Set(self.items.map(\.id)).isDisjoint(with: page.items.map(\.id)) else {
                self.needsRefresh = true; self.failure = .internalError(.init(message: "The inbox changed while paging. Refresh to continue.")); return
            }
            self.items += page.items; self.unreadCount = page.unreadCount; self.nextCursor = page.nextCursor
        }
    }
    func markRead(_ item: InboxNotification) {
        guard canEdit, item.isUnread, item.accountID == scope?.accountID, item.principalID == scope?.principalID,
              items.contains(where: { $0.id == item.id }) else { return }
        needsRefresh = true
        run({ try await $0.markRead(item) }) { _ in self.refresh() }
    }
    func setMediaEnabled(_ enabled: Bool) {
        guard canEdit, mediaEnabled != enabled else { return }
        needsRefresh = true
        run({ try await $0.setMediaEnabled(enabled) }) { _ in self.refresh() }
    }
    private func run<T: Sendable>(_ operation: @escaping @MainActor (any NotificationInboxRepository) async throws -> AppResult<T>, receive: @escaping @MainActor (T) -> Void) {
        guard !busy, let repo = repository, scope != nil else { return }; let current = generation
        busy = true; failure = nil
        task = Task {
            do {
                let result = try await operation(repo); try Task.checkCancellation()
                guard current == generation else { return }
                busy = false; task = nil
                switch result { case .success(let value): receive(value); case .failure(let error): failure = error.publicInfo() }
            } catch {
                guard current == generation else { return }
                busy = false; task = nil
                if !(error is CancellationError) { failure = .internalError(.init(message: "The inbox could not complete. Refresh to confirm server state.")) }
            }
        }
    }
}
