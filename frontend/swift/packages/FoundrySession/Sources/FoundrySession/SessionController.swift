import FoundryKernel
import Foundation
import Observation

/// Reusable main-executor lifecycle owner. UI snapshots and leases contain no credentials.
@MainActor @Observable
public final class SessionController {
    public private(set) var snapshot = SessionSnapshot()
    @ObservationIgnored private let authority: any SessionAuthority
    @ObservationIgnored private let vault: any CredentialVault
    @ObservationIgnored private let now: @Sendable () -> Int64
    @ObservationIgnored private let onUnexpectedError: (any Error) -> Void
    @ObservationIgnored private var grant: SessionGrant?
    @ObservationIgnored private var credential: StoredCredential?
    @ObservationIgnored private var persistedCredential: StoredCredential?
    @ObservationIgnored private var generation: UInt64 = 0
    @ObservationIgnored private var epoch: UInt64 = 0
    @ObservationIgnored private var request: Task<Void, Never>?
    @ObservationIgnored private var closed = false
    @ObservationIgnored private let ownerID = UUID()
    public init(authority: any SessionAuthority, vault: any CredentialVault, now: @escaping @Sendable () -> Int64,
                onUnexpectedError: @escaping (any Error) -> Void) {
        self.authority = authority; self.vault = vault; self.now = now; self.onUnexpectedError = onUnexpectedError
    }
    public var isBusy: Bool { snapshot.phase.isBusy }
    public var canRenew: Bool { !closed && !isBusy && credential != nil }
    public var canSelectAccount: Bool { !closed && !isBusy && lease != nil && authority is any AccountSelectingAuthority }
    public var lease: SessionLease? { guard let grant, grant.expiresAtMillis > now() else { return nil }; return SessionLease(scope: grant.scope, epoch: epoch, ownerID: ownerID) }
    public func isCurrent(_ lease: SessionLease) -> Bool { self.lease == lease }
    public func accessToken(for lease: SessionLease) -> String? { isCurrent(lease) ? grant?.accessToken : nil }
    public func expireIfNeeded() { if grant != nil && lease == nil { invalidate(); snapshot = SessionSnapshot(phase: .blocked, failure: SessionFailures.expired, epoch: epoch) } }

    @discardableResult public func restore() -> Task<Void, Never>? {
        guard !closed, !isBusy else { return nil }
        return start(.restoring) { current in
            if let pending = self.credential, pending != self.persistedCredential {
                try await self.renew(pending, current: current); return
            }
            let loaded = try await self.vault.load(); guard self.admit(current) else { return }
            switch loaded {
            case .failure(let failure): self.block(failure)
            case .success(nil): self.credential = nil; self.persistedCredential = nil; self.snapshot = SessionSnapshot(epoch: self.epoch)
            case .success(let saved?): self.credential = saved; self.persistedCredential = saved; try await self.renew(saved, current: current)
            }
        }
    }
    @discardableResult public func signIn(login: String, password: String) -> Task<Void, Never>? {
        guard !closed, !isBusy else { return nil }
        guard case .success(let command) = SignIn.make(login: login, password: password) else {
            if case .failure(let failure) = SignIn.make(login: login, password: password) {
                snapshot = SessionSnapshot(phase: snapshot.phase, principal: snapshot.principal, account: snapshot.account, failure: failure, epoch: epoch)
            }; return nil
        }
        return start(.signingIn) { current in
            let cleared = try await self.vault.clear(); guard self.admit(current) else { return }
            guard case .success = cleared else { if case .failure(let failure) = cleared { self.block(failure) }; return }
            self.credential = nil
            self.persistedCredential = nil
            let answer = try await self.authority.signIn(command); guard self.admit(current) else { return }
            switch answer { case .failure(let failure): self.block(failure); case .success(let value): try await self.activate(value, expected: nil, current: current) }
        }
    }
    @discardableResult public func renew() -> Task<Void, Never>? {
        guard !closed, !isBusy, let saved = credential else { return nil }
        return start(.renewing) { current in try await self.renew(saved, current: current) }
    }
    private func renew(_ saved: StoredCredential, current: UInt64) async throws {
        guard saved.scope.authorityID == authority.id else { try await refuseAndClear(SessionFailures.scopeMismatch, current: current); return }
        if persistedCredential != saved {
            let written = try await vault.save(saved); guard admit(current) else { return }
            guard case .success = written else { if case .failure(let failure) = written { block(failure) }; return }
            persistedCredential = saved
        }
        let answer = try await authority.renew(saved) { [self] rotated in
            try await self.checkpoint(rotated, expected: saved.scope, current: current)
        }; guard admit(current) else { return }
        switch answer {
        case .success(let value): try await activate(value, expected: saved.scope, current: current)
        case .failure(let failure):
            if failure.kind == .unauthenticated || failure.kind == .forbidden { try await refuseAndClear(failure, current: current) }
            else { block(failure) }
        }
    }
    private func checkpoint(_ rotated: StoredCredential, expected: SessionScope, current: UInt64) async throws -> AppResult<Void> {
        guard admit(current) else { return .failure(SessionFailures.scopeChanged) }
        guard rotated.scope == expected, rotated.scope.authorityID == authority.id else { return .failure(SessionFailures.scopeMismatch) }
        // Keep the known current family in this owner even if durable saving fails.
        credential = rotated
        let answer = try await vault.save(rotated)
        guard admit(current) else { return .failure(SessionFailures.scopeChanged) }
        if case .success = answer { persistedCredential = rotated }
        return answer
    }
    @discardableResult public func selectAccount(_ accountID: String) -> Task<Void, Never>? {
        guard canSelectAccount, let prior = grant, accountID != prior.account.id,
              let selecting = authority as? any AccountSelectingAuthority,
              let expected = SessionScope(authorityID: authority.id, principalID: prior.principal.id, accountID: accountID) else { return nil }
        return start(.selectingAccount) { current in
            let answer = try await selecting.selectAccount(accountID, grant: prior); guard self.admit(current) else { return }
            switch answer {
            case .success(let value): try await self.activate(value, expected: expected, current: current)
            case .failure(let failure): self.block(failure)
            }
        }
    }
    private func activate(_ value: SessionGrant, expected: SessionScope?, current: UInt64) async throws {
        guard value.scope.authorityID == authority.id, expected == nil || expected == value.scope else {
            try await refuseAndClear(SessionFailures.scopeMismatch, current: current); return
        }
        guard value.expiresAtMillis > now() else { block(SessionFailures.expired); return }
        let saved = StoredCredential(value)
        let written: AppResult<Void> = persistedCredential == saved ? .success(()) : try await vault.save(saved); guard admit(current) else { return }
        guard case .success = written else { if case .failure(let failure) = written { block(failure) }; return }
        persistedCredential = saved
        guard value.expiresAtMillis > now() else { credential = saved; block(SessionFailures.expired); return }
        credential = saved; grant = value
        snapshot = SessionSnapshot(phase: .signedIn, principal: value.principal, account: value.account, epoch: epoch)
    }
    private func refuseAndClear(_ failure: Failure, current: UInt64) async throws {
        let cleared = try await vault.clear(); guard admit(current) else { return }
        if case .failure(let storageFailure) = cleared { block(storageFailure) }
        else { credential = nil; persistedCredential = nil; block(failure) }
    }
    @discardableResult public func signOut() -> Task<Void, Never>? {
        guard !closed, snapshot.phase != .signingOut else { return nil }
        let prior = request; let previous = credential
        prior?.cancel()
        return start(.signingOut) { current in
            // Await an abandoned save before clearing; cancellation does not undo native work.
            await prior?.value; guard self.admit(current) else { return }
            let cleared = try await self.vault.clear(); guard self.admit(current) else { return }
            guard case .success = cleared else { if case .failure(let failure) = cleared { self.block(failure) }; return }
            self.credential = nil
            self.persistedCredential = nil
            var failure: Failure?
            do {
                if let previous, case .failure(let refused) = try await self.authority.revoke(previous) { failure = refused.publicInfo() }
            } catch {
                guard self.admit(current) else { return }
                self.onUnexpectedError(error)
                failure = .internalError(.init(message: "An unexpected error occurred."))
            }
            guard self.admit(current) else { return }
            self.snapshot = SessionSnapshot(failure: failure, epoch: self.epoch)
        }
    }
    public func shutdown() { closed = true; generation &+= 1; request?.cancel(); request = nil; invalidate(); snapshot = SessionSnapshot(epoch: epoch) }
    private func invalidate() { epoch &+= 1; grant = nil }
    private func block(_ failure: Failure) { grant = nil; snapshot = SessionSnapshot(phase: .blocked, failure: failure.publicInfo(), epoch: epoch) }
    private func admit(_ current: UInt64) -> Bool { current == generation && !Task.isCancelled }
    private func start(_ phase: SessionPhase, operation: @escaping @MainActor (UInt64) async throws -> Void) -> Task<Void, Never> {
        generation &+= 1; let current = generation; invalidate(); snapshot = SessionSnapshot(phase: phase, epoch: epoch)
        let task = Task { [self] in
            defer {
                if current == generation {
                    if Task.isCancelled && snapshot.phase.isBusy {
                        block(.canceled(.init(message: "Session action stopped. Restore or sign in again.", code: "session.canceled")))
                    }
                    request = nil
                }
            }
            do { try await operation(current) }
            catch {
                guard current == generation else { return }
                if error is CancellationError || Task.isCancelled {
                    block(.canceled(.init(message: "Session action stopped. Restore or sign in again.", code: "session.canceled")))
                } else {
                    onUnexpectedError(error)
                    if current == generation { block(.internalError(.init(message: "An unexpected error occurred."))) }
                }
            }
        }; request = task; return task
    }
}
