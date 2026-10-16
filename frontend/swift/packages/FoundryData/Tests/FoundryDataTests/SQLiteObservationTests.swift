import Foundation
@testable import FoundryData
import FoundryKernel
import FoundryServices
import Testing

private actor SnapshotProbe {
    var values: [AppResult<[Note]>] = []
    func append(_ answer: AppResult<[Note]>) { values.append(answer) }
    func wait(titles: [String]) async throws {
        for _ in 0..<400 {
            if (try? values.last?.get().map(\.title)) == titles { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Did not receive expected current snapshot: \(titles)")
    }
}
private func collect(_ subscription: NotesSubscription, probe: SnapshotProbe) -> Task<Void, Never> {
    Task {
        defer { subscription.cancel() }
        do { for try await answer in subscription.values { try Task.checkCancellation(); await probe.append(answer) } }
        catch is CancellationError {} catch { Issue.record("Unexpected stream defect") }
    }
}
@Test func sqliteReadersAcrossInstancesReceiveCRUDAndRemoveSubscriptionsOnCancellation() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent("observation-\(UUID())"), url = root.appendingPathComponent("notes.sqlite")
    defer { try? FileManager.default.removeItem(at: root) }
    let writer = SQLiteNotesRepository(url: url), reader = SQLiteNotesRepository(url: url), first = SnapshotProbe(), second = SnapshotProbe()
    let a = collect(try await reader.observe(), probe: first), b = collect(try await SQLiteNotesRepository(url: url).observe(), probe: second)
    defer { a.cancel(); b.cancel() }
    try await first.wait(titles: []); try await second.wait(titles: [])
    #expect(NotesChanges.shared.subscriberCount(url.path) == 2)
    let note = try await writer.create(CreateNote.make(title: "Created").get()).get()
    try await first.wait(titles: ["Created"]); try await second.wait(titles: ["Created"])
    _ = try await writer.update(UpdateNote.make(id: note.id, title: "Updated").get()).get()
    try await first.wait(titles: ["Updated"]); try await second.wait(titles: ["Updated"])
    a.cancel(); await a.value
    for _ in 0..<200 where NotesChanges.shared.subscriberCount(url.path) != 1 { await Task.yield() }
    #expect(NotesChanges.shared.subscriberCount(url.path) == 1)
    _ = try await writer.delete(DeleteNote.make(id: note.id).get()).get(); try await second.wait(titles: [])
    #expect(try await first.values.last?.get().map(\.title) == ["Updated"])
    b.cancel(); await b.value
    for _ in 0..<200 where NotesChanges.shared.subscriberCount(url.path) != 0 { await Task.yield() }
    #expect(NotesChanges.shared.subscriberCount(url.path) == 0)
}
@Test func sqliteRegistrationBeforeFirstReadAndBurstWritesConvergeWithoutSchemaChanges() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent("observation-burst-\(UUID())"), url = root.appendingPathComponent("notes.sqlite")
    defer { try? FileManager.default.removeItem(at: root) }
    let writer = SQLiteNotesRepository(url: url), probe = SnapshotProbe()
    let subscription = try await SQLiteNotesRepository(url: url).observe()
    _ = try await writer.create(CreateNote.make(title: "Committed before collection").get()).get()
    let task = collect(subscription, probe: probe); defer { task.cancel() }
    try await probe.wait(titles: ["Committed before collection"])
    for index in 0..<30 { _ = try await writer.create(CreateNote.make(title: "Burst \(index)").get()).get() }
    let expected = try await writer.list().get().map(\.title); try await probe.wait(titles: expected)
    #expect(expected.count == 31)
    task.cancel(); await task.value
}
@Test func sqliteObservationFailuresKeepDataAndUnusedSubscriptionsCanBeCanceled() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent("observation-corrupt-\(UUID())"), url = root.appendingPathComponent("notes.sqlite")
    try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: root) }
    let bytes = Data("Kept corrupt bytes".utf8); try bytes.write(to: url)
    let subscription = try await SQLiteNotesRepository(url: url).observe(); defer { subscription.cancel() }
    var iterator = subscription.values.makeAsyncIterator()
    let answer = try #require(await iterator.next())
    if case .failure(let failure) = answer { #expect(failure.meta.code == "notes.storage_corrupt") } else { Issue.record("Expected corrupt read value") }
    #expect(try Data(contentsOf: url) == bytes)
    subscription.cancel()
    let unused = try await SQLiteNotesRepository(url: url).observe(); unused.cancel(); unused.cancel()
    for _ in 0..<400 where NotesChanges.shared.subscriberCount(url.path) != 0 { try await Task.sleep(for: .milliseconds(5)) }
    #expect(NotesChanges.shared.subscriberCount(url.path) == 0)
}
