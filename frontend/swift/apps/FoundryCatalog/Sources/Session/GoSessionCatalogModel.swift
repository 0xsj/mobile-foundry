import Foundation
import FoundryHTTP
import FoundryKernel
import FoundrySession
import FoundryServices
import Observation
import OSLog

@MainActor @Observable final class GoSessionCatalogModel {
    let controller: SessionController
    let baseURL: String
    let provider: SessionProvider
    let notes = RemoteNotesCatalogModel()
    private(set) var accounts: [Account] = []
    private(set) var nextCursor: String?
    private(set) var directoryFailure: Failure?
    private(set) var isLoading = false
    @ObservationIgnored private let transport: URLSessionHTTPTransport
    @ObservationIgnored private let allowLocalHTTP: Bool
    @ObservationIgnored private var directoryTask: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    init(authority: GoSessionAuthority, transport: URLSessionHTTPTransport, vault: any CredentialVault,
         provider: SessionProvider, allowLocalHTTP: Bool) {
        self.transport = transport; self.baseURL = authority.baseURL; self.provider = provider; self.allowLocalHTTP = allowLocalHTTP
        controller = SessionController(authority: authority, vault: vault, now: { Int64(Date().timeIntervalSince1970 * 1000) }, onUnexpectedError: { _ in
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "go-session").error("Unexpected Go session failure.")
        })
    }
    func clearDirectory() {
        generation += 1; directoryTask?.cancel(); directoryTask = nil
        accounts = []; nextCursor = nil; directoryFailure = nil; isLoading = false
    }
    func load(cursor: String? = nil) {
        guard !isLoading, let lease = controller.lease else { return }
        generation += 1; let current = generation
        isLoading = true; directoryFailure = nil
        directoryTask = Task {
            defer { if current == generation { isLoading = false; directoryTask = nil } }
            do {
                let client = try AuthenticatedHTTPClient.create(baseURL: baseURL, transport: transport, session: controller, lease: lease, allowLocalHTTP: allowLocalHTTP).get()
                let answer = try await HTTPAccountDirectory(client: client, principalID: lease.scope.principalID).list(cursor: cursor, limit: 20)
                try Task.checkCancellation()
                guard current == generation, controller.isCurrent(lease) else { return }
                switch answer {
                case .success(let page): accounts = page.accounts; nextCursor = page.nextCursor
                case .failure(let failure): directoryFailure = failure.publicInfo()
                }
            } catch is CancellationError {} catch {
                guard current == generation, controller.isCurrent(lease) else { return }
                directoryFailure = .internalError(.init(message: "Account loading could not complete."))
            }
        }
    }
    func refreshScope() {
        clearDirectory(); load()
        guard let lease = controller.lease,
              case .success(let client) = AuthenticatedHTTPClient.create(baseURL:baseURL,transport:transport,session:controller,lease:lease,allowLocalHTTP:allowLocalHTTP) else { notes.bind(nil,scope:nil); return }
        notes.bind(HTTPRemoteNotesRepository(client:client,accountID:lease.scope.accountID,principalID:lease.scope.principalID),scope:lease.scope)
    }
    func close() { notes.bind(nil,scope:nil); clearDirectory(); controller.shutdown(); transport.invalidate() }
}
