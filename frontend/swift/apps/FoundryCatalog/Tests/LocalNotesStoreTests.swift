@testable import FoundryCatalog
import Foundation
import FoundryData
import FoundryKernel
import FoundryQuery
import FoundryServices
import Testing

@MainActor @Test func localNotesFeatureUsesMemoryAndSQLiteThroughTheSameCRUDStateOwner() async throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("local-notes-ui-\(UUID().uuidString)")
    defer { try? FileManager.default.removeItem(at: directory) }
    let disk = SQLiteNotesRepository(url: directory.appendingPathComponent("notes.sqlite"))
    for repo in [InMemoryNotesRepository(), disk] as [any NotesRepository] {
        let store = LocalNotesStore(repository: repo) { _ in Issue.record("Unexpected defect") }
        #expect(!store.canAct); await store.refresh()?.value; #expect(store.canAct && store.query.value == [])
        #expect(store.save() == nil && store.titleError == "Enter a title.")
        store.editTitle("  Idea 💡  "); let write = try #require(store.save())
        #expect(store.isBusy && store.save() == nil && store.refresh() == nil)
        store.editTitle("Rejected duplicate edit"); store.newNote(); #expect(store.title == "  Idea 💡  ")
        await write.value
        let first = try #require(store.query.value?.first); #expect(first.title == "  Idea 💡  " && store.title.isEmpty)
        store.edit(first); store.editTitle("Renamed"); await store.save()?.value
        #expect(store.query.value == [Note(id: first.id, title: "Renamed")!] && store.mutation.value == "Note updated.")
        let reopened = LocalNotesStore(repository: repo) { _ in Issue.record("Unexpected defect") }; await reopened.refresh()?.value
        #expect(reopened.query.value == store.query.value)
        await store.delete(try #require(store.query.value?.first))?.value
        #expect(store.query.value == [] && store.mutation.value == "Note deleted.")
        await reopened.refresh()?.value; #expect(reopened.query.value == [])
    }
}
private actor LateLocalRepository: NotesRepository {
    var notes: [Note] = []
    var continuation: CheckedContinuation<AppResult<Note>, Never>?
    var hasPending: Bool { continuation != nil }
    func list() async throws -> AppResult<[Note]> { .success(notes) }
    func create(_ command: CreateNote) async throws -> AppResult<Note> {
        await withCheckedContinuation { continuation = $0 } // Deliberately ignores cancellation.
    }
    func update(_ command: UpdateNote) async throws -> AppResult<Note> { .failure(NotesRepositoryFailure.missing) }
    func delete(_ command: DeleteNote) async throws -> AppResult<Void> { .failure(NotesRepositoryFailure.missing) }
    func commit() {
        let note = Note(id: "late-commit", title: "Committed after observation stopped")!; notes.append(note)
        continuation?.resume(returning: .success(note)); continuation = nil
    }
}
@MainActor @Test func stoppedLocalWriteCannotPublishButFreshReadCanObserveItsCommit() async throws {
    let repo = LateLocalRepository()
    let owner = LocalNotesStore(repository: repo) { _ in Issue.record("Late defect reported") }; await owner.refresh()?.value
    owner.editTitle("Draft"); let write = try #require(owner.save())
    for _ in 0..<100 { if await repo.hasPending { break }; try await Task.sleep(for: .milliseconds(10)) }
    #expect(await repo.hasPending)
    owner.stop(); #expect(!owner.isBusy && owner.query.value == [] && owner.mutation.failure?.kind == .canceled)
    await repo.commit(); await write.value
    #expect(owner.query.value == [] && owner.mutation.value == nil && owner.title == "Draft")
    let fresh = LocalNotesStore(repository: repo) { _ in Issue.record("Unexpected defect") }; await fresh.refresh()?.value
    #expect(fresh.query.value?.first?.id == "late-commit")
}
private actor FailedPostCommitRead: NotesRepository {
    var fail = false
    var notes: [Note] = []
    var creates = 0
    func list() async throws -> AppResult<[Note]> {
        fail ? .failure(.unavailable(.init(message: "Read unavailable", code: "notes.storage_unavailable"))) : .success(notes)
    }
    func create(_ command: CreateNote) async throws -> AppResult<Note> {
        creates += 1; let note = Note(id: "saved", title: command.title)!; notes.append(note); fail = true; return .success(note)
    }
    func update(_ command: UpdateNote) async throws -> AppResult<Note> { .failure(NotesRepositoryFailure.missing) }
    func delete(_ command: DeleteNote) async throws -> AppResult<Void> { .failure(NotesRepositoryFailure.missing) }
    func recover() { fail = false }
}
@MainActor @Test func committedReceiptSurvivesFailedSnapshotReloadWithoutReplayingWrite() async throws {
    let repo = FailedPostCommitRead()
    let store = LocalNotesStore(repository: repo) { _ in Issue.record("Expected failure reported as defect") }
    await store.refresh()?.value; store.editTitle("Saved"); await store.save()?.value
    #expect(store.mutation.value == "Note created." && store.query.failure?.meta.code == "notes.storage_unavailable")
    #expect(store.query.value == [] && store.title.isEmpty)
    #expect(await repo.creates == 1)
    await repo.recover(); await store.refresh()?.value
    #expect(store.query.value == [Note(id: "saved", title: "Saved")!])
    #expect(await repo.creates == 1)
}
@MainActor @Test func accountEditorShutdownDropsDraftDataAndCannotBeRevivedByDeferredEvents() async throws {
    let repo = InMemoryNotesRepository()
    _ = try await repo.create(CreateNote.make(title: "Account record").get()).get()
    let store = LocalNotesStore(repository: repo) { _ in Issue.record("Unexpected defect") }
    await store.refresh()?.value; store.edit(try #require(store.query.value?.first)); store.editTitle("Private unsaved draft")
    store.shutdown(); store.editTitle("Deferred edit"); store.blurTitle()
    #expect(store.title.isEmpty && store.editingID == nil && !store.touched && store.query.value == nil && store.mutation.value == nil)
    #expect(store.refresh() == nil && store.save() == nil && !store.canAct)
    #expect(try await repo.list().get().first?.title == "Account record")
}
@MainActor @Test func terminalAccountEditorCannotPublishAnAcceptedLateCommit() async throws {
    let repo = LateLocalRepository()
    let owner = LocalNotesStore(repository: repo) { _ in Issue.record("Late defect") }; await owner.refresh()?.value
    owner.editTitle("Draft"); let task = try #require(owner.save())
    while !(await repo.hasPending) { await Task.yield() }
    owner.shutdown(); await repo.commit(); await task.value
    #expect(owner.query.value == nil && owner.title.isEmpty && owner.mutation.value == nil && !owner.isBusy)
    #expect(try await repo.list().get().count == 1)
}
