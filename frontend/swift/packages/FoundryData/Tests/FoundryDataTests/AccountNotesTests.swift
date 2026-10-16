import Foundation
import FoundryData
import FoundryKernel
import FoundryServices
import FoundrySession
import Testing

private struct PartitionRow: Decodable { let authority: String; let principal: String; let account: String; let identifier: String }
@Test func sharedPartitionsKeepAuthorityAccountAndCaseDistinct() throws {
    var root = URL(fileURLWithPath: #filePath); for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode([PartitionRow].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/account-access/partitions.json")))
    for row in rows {
        let scope = try #require(SessionScope(authorityID: row.authority, principalID: row.principal, accountID: row.account))
        #expect(AccountNotesPartition.identifier(for: scope) == row.identifier)
    }
}
private func scope(_ person: String = "alex", account: String = "alex-personal", authority: String = "catalog-demo.v1") -> SessionScope {
    SessionScope(authorityID: authority, principalID: person, accountID: account)!
}
@Test @MainActor func nativeSQLiteAccountIsolationReopenAndGuestPreservation() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent("account-notes-test-\(UUID())")
    defer { try? FileManager.default.removeItem(at: root) }
    let guest = SQLiteNotesRepository(url: root.appendingPathComponent("guest/notes.sqlite"))
    let guestNote = try await guest.create(CreateNote.make(title: "Guest").get()).get()
    let accountsRoot = root.appendingPathComponent("accounts")
    let first = AccountNotesRepositories(storage: .device, root: accountsRoot)
    let alex = first.repository(for: scope()), sam = first.repository(for: scope("sam", account: "sam-personal"))
    let note = try await alex.create(CreateNote.make(title: "Alex only").get()).get()
    #expect(try await sam.list().get().isEmpty)
    if case .failure(let failure) = try await sam.update(UpdateNote.make(id: note.id, title: "Wrong account").get()) { #expect(failure.kind == .notFound) } else { Issue.record("Wrong-account update admitted") }
    if case .failure(let failure) = try await sam.delete(DeleteNote.make(id: note.id).get()) { #expect(failure.kind == .notFound) } else { Issue.record("Wrong-account delete admitted") }
    for other in [scope(account: "Alex-personal"), scope(authority: "other")] { #expect(try await first.repository(for: other).list().get().isEmpty) }
    #expect(try await first.repository(for: scope("other-member")).list().get() == [note])
    let reopened = AccountNotesRepositories(storage: .device, root: accountsRoot)
    #expect(try await reopened.repository(for: scope()).list().get() == [note])
    _ = try await reopened.repository(for: scope()).update(UpdateNote.make(id: note.id, title: "Edited").get()).get()
    #expect(try await alex.list().get().first?.title == "Edited")
    _ = try await alex.delete(DeleteNote.make(id: note.id).get()).get()
    #expect(try await reopened.repository(for: scope()).list().get().isEmpty)
    #expect(try await guest.list().get() == [guestNote])
}
@Test @MainActor func memoryAccountFactoryRetainsPerAccountAndDiscardsOnReplacement() async throws {
    let root = URL(fileURLWithPath: "/unused-memory-notes")
    let factory = AccountNotesRepositories(storage: .memory, root: root)
    let note = try await factory.repository(for: scope()).create(CreateNote.make(title: "Memory Alex").get()).get()
    #expect(try await factory.repository(for: scope("sam", account: "sam-personal")).list().get().isEmpty)
    #expect(try await factory.repository(for: scope()).list().get() == [note])
    #expect(try await AccountNotesRepositories(storage: .memory, root: root).repository(for: scope()).list().get().isEmpty)
}
@MainActor private func activeSession() async -> SessionController {
    let clock = ManualSessionClock(nowMillis: 1_700_000_000_000)
    let controller = SessionController(authority: DemoSessionAuthority(now: { clock.nowMillis }), vault: MemoryCredentialVault(), now: { clock.nowMillis }, onUnexpectedError: { _ in Issue.record("Unexpected session error") })
    await controller.signIn(login: "alex@example.test", password: "catalog-demo")?.value; return controller
}
@Test @MainActor func closingLeaseGatesAllCRUDAndNewLeaseReloadsItsOriginalAccount() async throws {
    let session = await activeSession(), factory = AccountNotesRepositories(storage: .memory, root: URL(fileURLWithPath: "/unused-memory-notes"))
    let lease = try #require(session.lease), old = factory.bind(session: session, lease: lease)
    let note = try await old.create(CreateNote.make(title: "Retained").get()).get()
    await session.signIn(login: "sam@example.test", password: "catalog-demo")?.value
    #expect(try await old.list() == .failure(SessionFailures.scopeChanged))
    #expect(try await old.create(CreateNote.make(title: "Blocked").get()) == .failure(SessionFailures.scopeChanged))
    #expect(try await old.update(UpdateNote.make(id: note.id, title: "Blocked").get()) == .failure(SessionFailures.scopeChanged))
    if case .failure(let failure) = try await old.delete(DeleteNote.make(id: note.id).get()) { #expect(failure == SessionFailures.scopeChanged) } else { Issue.record("Expected closure") }
    #expect(try await factory.bind(session: session, lease: #require(session.lease)).list().get().isEmpty)
    await session.signIn(login: "alex@example.test", password: "catalog-demo")?.value
    #expect(try await factory.bind(session: session, lease: #require(session.lease)).list().get() == [note])
    #expect(try await old.list() == .failure(SessionFailures.scopeChanged))
}
private actor GateNotes: NotesRepository {
    enum Mode: CaseIterable { case commit, failure, defect }
    let mode: Mode
    let target = InMemoryNotesRepository()
    var entered = false
    private var gate: CheckedContinuation<Void, Never>?
    init(_ mode: Mode) { self.mode = mode }
    func release() { gate?.resume(); gate = nil }
    func list() async throws -> AppResult<[Note]> { try await target.list() }
    func create(_ command: CreateNote) async throws -> AppResult<Note> {
        await withCheckedContinuation { gate = $0; entered = true }
        switch mode {
        case .commit: return try await target.create(command)
        case .failure: return .failure(.unavailable(.init(message: "Old data failure")))
        case .defect: throw URLError(.unknown)
        }
    }
    func update(_ command: UpdateNote) async throws -> AppResult<Note> { try await target.update(command) }
    func delete(_ command: DeleteNote) async throws -> AppResult<Void> { try await target.delete(command) }
}
@Test @MainActor func acceptedOldWriteKeepsItsTargetAndLateErrorsStayOutOfNewAccount() async throws {
    for mode in GateNotes.Mode.allCases {
        let session = await activeSession(), original = GateNotes(mode)
        let old = SessionNotesRepository(repository: original, session: session, lease: try #require(session.lease))
        let command = try CreateNote.make(title: "Accepted before switch").get()
        let task = Task { try await old.create(command) }
        while !(await original.entered) { await Task.yield() }
        await session.signIn(login: "sam@example.test", password: "catalog-demo")?.value
        await original.release()
        #expect(try await task.value == .failure(SessionFailures.scopeChanged))
        #expect(try await original.list().get().count == (mode == .commit ? 1 : 0))
    }
}
