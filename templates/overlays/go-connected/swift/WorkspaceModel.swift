import FoundryKernel
import FoundrySession
import Observation

/// Product owner for non-replayable Account commands; Notes intent stays independent.
@MainActor @Observable final class WorkspaceModel {
    private(set) var displayAccount: Account?
    private(set) var current: ManagedAccount?
    private(set) var created: ManagedAccount?
    private(set) var isBusy = false
    private(set) var failure: Failure?
    private(set) var message: String?
    private(set) var createUncertain = false
    private(set) var reviewed = false
    private(set) var reviewAccounts: [Account] = []
    private(set) var nextCursor: String?
    @ObservationIgnored private let controller: SessionController
    @ObservationIgnored private var service: (any AccountManagement)?
    @ObservationIgnored private var directory: (any AccountDirectory)?
    @ObservationIgnored private var lease: SessionLease?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var creating = false
    @ObservationIgnored private var uncertainOwner: String?
    static let interruptedCreate = "Creation was not confirmed. The workspace may already exist. Review workspaces before choosing to create again."
    init(controller: SessionController) { self.controller = controller }
    private func owner(_ lease: SessionLease) -> String { "\(lease.scope.authorityID)/\(lease.scope.principalID)" }
    var canRename: Bool { !isBusy && current != nil && lease.map(controller.isCurrent) == true }
    var canCreate: Bool { canRename && !createUncertain && created == nil }
    func bind(_ service: (any AccountManagement)?, directory: (any AccountDirectory)?, lease: SessionLease?) {
        cancel()
        self.service = service; self.directory = directory; self.lease = lease
        current = nil; displayAccount = nil; created = nil; failure = nil; reviewed = false; reviewAccounts = []; nextCursor = nil
        createUncertain = lease.map { owner($0) == uncertainOwner } ?? false
        message = createUncertain ? Self.interruptedCreate : nil
    }
    func cancel() {
        if creating, let lease { uncertainOwner = owner(lease); createUncertain = true; message = Self.interruptedCreate }
        generation += 1; task?.cancel(); task = nil; isBusy = false; creating = false; current = nil
        reviewed = false
    }
    func refresh() {
        guard !isBusy, let lease, controller.isCurrent(lease), let service else { return }
        current = nil
        perform { try await service.read(lease.scope.accountID) } receive: { value in
            guard value.account.id == lease.scope.accountID, value.ownerID == lease.scope.principalID else { self.rejectWrite(); return }
            self.current = value; self.displayAccount = value.account
            self.message = self.createUncertain ? Self.interruptedCreate : "Current workspace loaded. Review its name before renaming."
        }
    }
    func create(_ rawName: String) {
        guard canCreate, let service, let lease else { return }
        guard case .success(let name) = admit(rawName) else { return }
        reviewed = false; reviewAccounts = []; nextCursor = nil
        creating = true
        perform(write: true, operation: { try await service.create(name) }) { value in
            guard value.ownerID == lease.scope.principalID, value.account.id != lease.scope.accountID, value.version == 1, value.account.name.utf8.elementsEqual(name.value.utf8) else { self.rejectWrite(create: true); return }
            self.created = value; self.message = "Workspace created. Choose it explicitly from Workspaces."
        }
    }
    func rename(_ rawName: String) {
        guard canRename, let service, let before = current else { return }
        guard case .success(let name) = admit(rawName) else { return }
        perform(write: true, operation: { try await service.rename(before, to: name) }) { value in
            guard value.account.id == before.account.id, value.ownerID == before.ownerID, value.version == before.version + 1,
                  value.createdAtMicros == before.createdAtMicros, value.updatedAtMicros >= before.updatedAtMicros,
                  value.account.name.utf8.elementsEqual(name.value.utf8) else { self.rejectWrite(); return }
            self.current = value; self.displayAccount = value.account; self.message = "Workspace renamed."
        }
    }
    func review(cursor: String? = nil) {
        guard !isBusy, let lease, controller.isCurrent(lease), let directory else { return }
        let expected = generation
        isBusy = true; failure = nil; reviewed = false
        task = Task {
            defer { if expected == generation { isBusy = false; task = nil } }
            do {
                let answer = try await directory.list(cursor: cursor, limit: 20); try Task.checkCancellation()
                guard expected == generation, controller.isCurrent(lease) else { return }
                switch answer {
                case .success(let page): reviewAccounts = page.accounts; nextCursor = page.nextCursor; reviewed = true
                case .failure(let value): failure = value.publicInfo()
                }
            } catch {
                guard expected == generation, controller.isCurrent(lease) else { return }
                failure = .unavailable(.init(message: "Workspaces could not be reviewed. Refresh again."))
            }
        }
    }
    func allowNewCreate() {
        guard reviewed, createUncertain, !isBusy, let lease, controller.isCurrent(lease) else { return }
        uncertainOwner = nil; createUncertain = false; created = nil
        message = "A new create may duplicate an earlier workspace. Enter a name deliberately."
    }
    func anotherCreate() { guard !isBusy, !createUncertain else { return }; created = nil }
    private func admit(_ rawName: String) -> AppResult<AccountName> {
        let result = AccountName.make(rawName); if case .failure(let value) = result { failure = value }; return result
    }
    private func rejectWrite(create: Bool = false) {
        current = nil; if !create { displayAccount = nil }; failure = .internalError(.init(message: "The result was not confirmed. Refresh before another write."))
        if create, let lease { uncertainOwner = owner(lease); createUncertain = true; message = Self.interruptedCreate }
    }
    private func perform(write: Bool = false, operation: @escaping @MainActor () async throws -> AppResult<ManagedAccount>, receive: @escaping @MainActor (ManagedAccount) -> Void) {
        guard let lease, controller.isCurrent(lease) else { return }
        let expected = generation
        isBusy = true; failure = nil; message = nil
        task = Task {
            defer {
                if expected == generation {
                    if creating && !controller.isCurrent(lease) { uncertainOwner = owner(lease) }
                    isBusy = false; creating = false; task = nil
                }
            }
            do {
                let answer = try await operation(); try Task.checkCancellation()
                guard expected == generation, controller.isCurrent(lease) else { return }
                switch answer {
                case .success(let value): receive(value)
                case .failure(let value):
                    failure = value.publicInfo()
                    if write { current = nil; if !creating { displayAccount = nil }; message = "Write not confirmed. Refresh the current workspace before another rename." }
                    if creating, [.unavailable, .timeout, .canceled, .internalError].contains(value.kind) {
                        uncertainOwner = owner(lease); createUncertain = true; message = Self.interruptedCreate
                    }
                }
            } catch {
                guard expected == generation, controller.isCurrent(lease) else { return }
                current = nil; if write && !creating { displayAccount = nil }; failure = .unavailable(.init(message: "The operation was interrupted. Refresh before another write."))
                if creating { uncertainOwner = owner(lease); createUncertain = true; message = Self.interruptedCreate }
            }
        }
    }
}
