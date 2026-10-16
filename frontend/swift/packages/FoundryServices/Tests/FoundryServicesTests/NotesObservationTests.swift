import FoundryServices
import Testing

@Test func memoryObserversStartCurrentAndReceiveOwnedCRUDSnapshots() async throws {
    let repository = InMemoryNotesRepository()
    let first = try await repository.observe(), second = try await repository.observe()
    defer { first.cancel(); second.cancel() }
    var a = first.values.makeAsyncIterator(), b = second.values.makeAsyncIterator()
    let initial = try await a.next()?.get(); #expect(initial == []); #expect(try await b.next()?.get() == [])
    let note = try await repository.create(CreateNote.make(title: "First").get()).get()
    #expect(try await a.next()?.get() == [note]); #expect(try await b.next()?.get() == [note]); #expect(initial == [])
    let updated = try await repository.update(UpdateNote.make(id: note.id, title: "Updated").get()).get()
    #expect(try await a.next()?.get() == [updated]); #expect(try await b.next()?.get() == [updated])
    _ = try await repository.delete(DeleteNote.make(id: note.id).get()).get()
    #expect(try await a.next()?.get() == []); #expect(try await b.next()?.get() == [])
    first.cancel(); first.cancel(); #expect(try await a.next() == nil)
    _ = try await repository.create(CreateNote.make(title: "Second reader remains").get()).get()
    #expect(try await b.next()?.get().map(\.title) == ["Second reader remains"])
}
@Test func slowMemoryReaderCoalescesToLatestAndResumeStartsCurrent() async throws {
    let repository = InMemoryNotesRepository(), subscription = try await repository.observe()
    defer { subscription.cancel() }
    var iterator = subscription.values.makeAsyncIterator(); #expect(try await iterator.next()?.get() == [])
    for index in 0..<80 { _ = try await repository.create(CreateNote.make(title: "Note \(index)").get()).get() }
    #expect(try await iterator.next()?.get().count == 80)
    subscription.cancel()
    _ = try await repository.create(CreateNote.make(title: "While paused").get()).get()
    let fresh = try await repository.observe(); defer { fresh.cancel() }
    var resumed = fresh.values.makeAsyncIterator(); #expect(try await resumed.next()?.get().count == 81)
}
