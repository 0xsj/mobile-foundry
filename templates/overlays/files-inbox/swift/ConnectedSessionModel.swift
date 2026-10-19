import Foundation
import FoundryData
import FoundryHTTP
import FoundryKernel
import FoundryServices
import FoundrySession
import Observation
import OSLog

/// Product composition owns navigation/data lifetime; adapters stay behind ports.
@MainActor @Observable final class ConnectedSessionModel {
    let controller: SessionController
    let files: FilesStore
    let inbox = InboxStore()
    let fileDestination = FileDestinationStore()
    let notes: RemoteNotesStore
    let identity: IdentitySettingsModel?
    let registration: RegistrationModel?
    let workspaces: WorkspaceModel
    private(set) var liveStatus = "Live updates paused."
    private(set) var accounts: [Account] = []
    private(set) var nextCursor: String?
    private(set) var directoryFailure: Failure?
    private(set) var isLoadingAccounts = false
    @ObservationIgnored private let baseURL: String
    @ObservationIgnored private let transport: any HTTPTransport
    @ObservationIgnored private let allowLocalHTTP: Bool
    @ObservationIgnored private var directoryTask: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var boundLease: SessionLease?
    @ObservationIgnored private var liveTask: Task<Void, Never>?
    @ObservationIgnored private let socketTransport: any LiveSocketTransport

    init(authority: any SessionAuthority, vault: any CredentialVault, baseURL: String,
         transport: any HTTPTransport, allowLocalHTTP: Bool, journal: any PendingNotesStore = MemoryPendingNotesStore(),
         socketTransport: any LiveSocketTransport = URLSessionLiveSocketTransport(), cache: any NotesReadCache = MemoryNotesReadCache(), filesJournal: any PendingFilesStore = MemoryPendingFilesStore()) {
        notes = RemoteNotesStore(journal: journal, cache: cache)
        files = FilesStore(journal: filesJournal)
        self.baseURL = baseURL; self.transport = transport; self.allowLocalHTTP = allowLocalHTTP
        self.socketTransport = socketTransport
        let owner = SessionController(authority: authority, vault: vault,
            now: { Int64(Date().timeIntervalSince1970 * 1000) }, onUnexpectedError: { _ in
                Logger(subsystem: "ProductSession", category: "session").error("Unexpected session failure.")
            })
        controller = owner
        workspaces = WorkspaceModel(controller: owner)
        if case .success(let service) = HTTPIdentityRegistration.create(baseURL: baseURL, transport: transport, allowLocalHTTP: allowLocalHTTP) {
            registration = RegistrationModel(service: service, controller: owner)
        } else { registration = nil }
        if case .success(let service) = HTTPIdentityRecovery.create(baseURL: baseURL, transport: transport, allowLocalHTTP: allowLocalHTTP) {
            identity = IdentitySettingsModel(controller: owner, publicService: service)
        } else { identity = nil }
    }
    func bindCurrentScope(force: Bool = false) {
        let lease = controller.lease
        guard force || lease != boundLease else { return }
        detach(); boundLease = lease
        guard let lease, controller.isCurrent(lease), case .success(let client) = client(lease) else { return }
        registration?.reset()
        workspaces.bind(HTTPAccountManagement(client: client, principalID: lease.scope.principalID),
            directory: HTTPAccountDirectory(client: client, principalID: lease.scope.principalID), lease: lease)
        inbox.bind(HTTPNotificationInboxRepository(client: client, accountID: lease.scope.accountID, principalID: lease.scope.principalID), scope: lease.scope)
        files.bind(HTTPRemoteFilesRepository(client: client, accountID: lease.scope.accountID, principalID: lease.scope.principalID), scope: lease.scope)
        fileDestination.bind(HTTPRemoteFilesRepository(client: client, accountID: lease.scope.accountID, principalID: lease.scope.principalID), scope: lease.scope)
        notes.bind(HTTPRemoteNotesRepository(client: client, accountID: lease.scope.accountID,
            principalID: lease.scope.principalID), scope: lease.scope)
        if case .success(let service) = HTTPIdentityRecovery.create(baseURL: baseURL, transport: transport, authenticatedClient: client, allowLocalHTTP: allowLocalHTTP) {
            identity?.bind(service, lease: lease)
        }
        reconnectLive()
        loadAccounts()
    }
    func detach() {
        generation += 1; directoryTask?.cancel(); directoryTask = nil; boundLease = nil
        liveTask?.cancel(); liveTask = nil; liveStatus = "Live updates paused."
        registration?.cancel()
        workspaces.bind(nil, directory: nil, lease: nil)
        identity?.bind(nil, lease: nil)
        accounts = []; nextCursor = nil; directoryFailure = nil; isLoadingAccounts = false
        inbox.bind(nil, scope: nil)
        files.bind(nil, scope: nil)
        fileDestination.bind(nil, scope: nil)
        notes.bind(nil, scope: nil)
    }
    func openNotice(_ item: InboxNotification) {
        guard let lease = boundLease, controller.isCurrent(lease), inbox.items.contains(item) else { return }
        fileDestination.open(item)
    }
    func signIn(login: String, password: String) { detach(); controller.signIn(login: login, password: password) }
    func renew() { detach(); controller.renew() }
    func restore() { detach(); controller.restore() }
    func select(_ accountID: String) { guard accountID != controller.snapshot.account?.id else { return }; detach(); controller.selectAccount(accountID) }
    func signOut() { detach(); controller.signOut() }
    func reconnectLive() {
        liveTask?.cancel(); liveTask = nil
        guard let lease = controller.lease,
              case .success(let feed) = GoAccountLiveHints.create(baseURL: baseURL, session: controller, lease: lease,
                  transport: socketTransport, allowLocalHTTP: allowLocalHTTP) else { return }
        let current = generation
        liveTask = Task {
            for attempt in 0..<6 {
                guard !Task.isCancelled, current == generation, controller.isCurrent(lease) else { return }
                liveStatus = attempt == 0 ? "Connecting live updates…" : "Reconnecting live updates…"
                do {
                    _ = try await feed.watch {
                        guard !Task.isCancelled, current == self.generation, self.controller.isCurrent(lease) else { return }
                        self.liveStatus = "Live updates connected."; self.notes.invalidate()
                    }
                    try Task.checkCancellation()
                    controller.expireIfNeeded()
                    guard current == generation, controller.isCurrent(lease) else { return }
                    if attempt < 5 { try await Task.sleep(for: .milliseconds(Int64((1 << attempt) * 1000) + Int64.random(in: 0...500))) }
                } catch {
                    if !Task.isCancelled, current == generation { liveStatus = "Live updates paused. Refresh or reconnect." }
                    return
                }
            }
            if current == generation { liveStatus = "Live updates paused. Refresh or reconnect." }
        }
    }
    func loadAccounts(cursor: String? = nil) {
        guard !isLoadingAccounts, let lease = controller.lease, case .success(let client) = client(lease) else { return }
        let current = generation; isLoadingAccounts = true; directoryFailure = nil
        directoryTask = Task {
            defer { if current == generation { isLoadingAccounts = false; directoryTask = nil } }
            do {
                let answer = try await HTTPAccountDirectory(client: client, principalID: lease.scope.principalID).list(cursor: cursor, limit: 20)
                try Task.checkCancellation()
                guard current == generation, controller.isCurrent(lease) else { return }
                switch answer {
                case .success(let page): accounts = page.accounts; nextCursor = page.nextCursor
                case .failure(let failure): directoryFailure = failure.publicInfo()
                }
            } catch is CancellationError {} catch {
                guard current == generation, controller.isCurrent(lease) else { return }
                directoryFailure = .internalError(.init(message: "Accounts could not be loaded. Try refreshing."))
            }
        }
    }
    private func client(_ lease: SessionLease) -> AppResult<AuthenticatedHTTPClient> {
        AuthenticatedHTTPClient.create(baseURL: baseURL, transport: transport, session: controller,
            lease: lease, allowLocalHTTP: allowLocalHTTP)
    }
}

@MainActor enum ConnectionComposition {
    private static var vaults: [String: KeychainCredentialVault] = [:]
    static func make() -> AppResult<ConnectedSessionModel> {
        let transport = URLSessionHTTPTransport()
        return GoSessionAuthority.create(baseURL: ConnectionConfiguration.baseURL, transport: transport,
            allowLocalHTTP: ConnectionConfiguration.allowLocalHTTP).map { authority in
            let namespace = "\(Bundle.main.bundleIdentifier ?? "product").session.\(authority.id)"
            let vault = vaults[namespace] ?? KeychainCredentialVault(service: namespace)
            vaults[namespace] = vault
            return ConnectedSessionModel(authority: authority, vault: vault, baseURL: authority.baseURL,
                transport: transport, allowLocalHTTP: ConnectionConfiguration.allowLocalHTTP,
                journal: SQLitePendingNotesStore(url: URL.applicationSupportDirectory
                    .appending(path: "PendingNotes", directoryHint: .isDirectory).appending(path: "pending-notes.sqlite")),
                cache: SQLiteNotesReadCache(url: URL.applicationSupportDirectory
                    .appending(path: "NotesReadCache", directoryHint: .isDirectory).appending(path: "notes-read-cache.sqlite")),
                filesJournal: SQLitePendingFilesStore(url: URL.applicationSupportDirectory
                    .appending(path: "PendingFiles", directoryHint: .isDirectory).appending(path: "pending-files.sqlite")))
        }
    }
}
