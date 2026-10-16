@testable import FoundryCatalog
import Foundation
import FoundryKernel
import FoundryQuery
import FoundryServices
import Testing

@MainActor private func waitUntil(_ predicate: () -> Bool) async throws {
    for _ in 0..<400 { if predicate() { return }; try await Task.sleep(for: .milliseconds(5)) }
    Issue.record("Live reader did not reach expected state")
}
private struct ObservationStep: Decodable {
    let action: String; let reference: String?; let title: String?; let first: [String]; let second: [String]
}
@MainActor @Test func sharedLiveReaderScenarioCoversCRUDPauseAndFreshResume() async throws {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<6 { root.deleteLastPathComponent() }
    let steps = try JSONDecoder().decode([ObservationStep].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/notes-observation/scenario.json")))
    let repo = InMemoryNotesRepository()
    let first = LiveNotesStore(source: repo) { _ in Issue.record("Unexpected defect") }
    let second = LiveNotesStore(source: repo) { _ in Issue.record("Unexpected defect") }
    defer { first.shutdown(); second.shutdown() }
    first.start(); second.start(); var ids: [String: String] = [:]
    for step in steps {
        switch step.action {
        case "create": ids[step.reference!] = try await repo.create(CreateNote.make(title: step.title!).get()).get().id
        case "update": _ = try await repo.update(UpdateNote.make(id: ids[step.reference!]!, title: step.title!).get()).get()
        case "delete": _ = try await repo.delete(DeleteNote.make(id: ids[step.reference!]!).get()).get()
        case "missing":
            let result = try await repo.delete(DeleteNote.make(id: "missing").get())
            if case .failure(let failure) = result { #expect(failure.kind == .notFound) } else { Issue.record("Missing ID was deleted") }
        case "pause": second.pause()
        case "resume": second.start()
        default: break
        }
        try await waitUntil { first.query.value?.map(\.title) == step.first && second.query.value?.map(\.title) == step.second }
    }
}
private enum ObservationDefect: Error { case unexpected }
private actor ScriptedObserver: NotesObserver {
    private var streams: [NotesStream.Continuation] = []
    var calls: Int { streams.count }
    func observe() async throws -> NotesSubscription {
        let (values, continuation) = NotesStream.makeStream(bufferingPolicy: .bufferingNewest(1))
        streams.append(continuation); continuation.yield(.success([]))
        return NotesSubscription(values: values, onCancel: { continuation.finish() })
    }
    func send(_ answer: AppResult<[Note]>) { streams.last?.yield(answer) }
    func fail() { streams.last?.finish(throwing: ObservationDefect.unexpected) }
}
@MainActor @Test func expectedReadFailureRetainsContentAndRecoversOnNextSnapshot() async throws {
    let source = ScriptedObserver(); var defects = 0
    let reader = LiveNotesStore(source: source) { _ in defects += 1 }; reader.start()
    defer { reader.shutdown() }
    try await waitUntil { reader.query.value == [] }
    let note = Note(id: "one", title: "Owned content")!
    await source.send(.success([note])); try await waitUntil { reader.query.value == [note] }
    await source.send(.failure(.unavailable(.init(message: "Read unavailable", code: "notes.storage_unavailable"))))
    try await waitUntil { reader.query.failure != nil }
    #expect(reader.isWatching && reader.query.value == [note] && defects == 0)
    await source.send(.success([])); try await waitUntil { reader.query == .loaded([]) }
    #expect(reader.isWatching && defects == 0)
}
private actor DelayedObservation: NotesObserver {
    private var gate: CheckedContinuation<NotesSubscription, any Error>?
    var pending: Bool { gate != nil }
    func observe() async throws -> NotesSubscription { try await withCheckedThrowingContinuation { gate = $0 } }
    func fail() { gate?.resume(throwing: ObservationDefect.unexpected); gate = nil }
}
@MainActor @Test func supersededAndTerminalReadersIgnoreLateDefectsAndCannotRestart() async throws {
    let source = DelayedObservation(); var defects = 0
    let reader = LiveNotesStore(source: source) { _ in defects += 1 }
    let old = try #require(reader.start())
    for _ in 0..<400 { if await source.pending { break }; try await Task.sleep(for: .milliseconds(5)) }
    #expect(await source.pending)
    reader.shutdown(); await source.fail(); await old.value
    #expect(defects == 0 && reader.query == .idle && !reader.isWatching && reader.start() == nil)
}
@MainActor @Test func admissionClosureClearsPrivateContentBeforePublishingAndAfterDiagnostics() async throws {
    for closeInDiagnostic in [false, true] {
        let source = ScriptedObserver(); var active = true; var defects = 0
        let reader = LiveNotesStore(source: source, admitted: { active }) { _ in defects += 1; active = false }
        reader.start(); try await waitUntil { reader.query.value == [] }
        let note = Note(id: "private", title: "Private content")!
        await source.send(.success([note])); try await waitUntil { reader.query.value == [note] }
        if closeInDiagnostic { await source.fail() } else { active = false; await source.send(.success([])) }
        try await waitUntil { reader.query == .idle && !reader.isWatching }
        #expect(reader.start() == nil && defects == (closeInDiagnostic ? 1 : 0))
    }
    let source = ScriptedObserver()
    let rejected = LiveNotesStore(source: source, admitted: { false }) { _ in Issue.record("Unexpected defect") }
    #expect(rejected.start() == nil && rejected.query == .idle)
    #expect(await source.calls == 0)
}
@MainActor @Test func routeBundlesRetainMemoryAcrossBranchesAndReopenDeviceData() async throws {
    let directory = URL.temporaryDirectory.appendingPathComponent("live-gallery-\(UUID().uuidString)")
    defer { try? FileManager.default.removeItem(at: directory) }
    let url = directory.appendingPathComponent("notes.sqlite")
    let model = LiveNotesCatalogModel(url: url)
    for provider in LocalNotesProvider.allCases {
        let source = model.source(provider); source.resume(); await source.writer.refresh()?.value
        // resume already owns the initial writer read; wait for admission instead of duplicating it.
        try await waitUntil { source.writer.canAct }
        source.writer.editTitle(provider.rawValue); await source.writer.save()?.value
        try await waitUntil { source.first.query.value?.map(\.title) == [provider.rawValue] && source.second.query.value?.map(\.title) == [provider.rawValue] }
        source.pause(); #expect(model.source(provider) === source)
        source.resume(); try await waitUntil { source.first.query.value?.map(\.title) == [provider.rawValue] }
        source.pause()
    }
    model.shutdown()
    let reopened = LiveNotesCatalogModel(url: url); defer { reopened.shutdown() }
    let device = reopened.source(.device); device.resume()
    try await waitUntil { device.first.query.value?.map(\.title) == [LocalNotesProvider.device.rawValue] }
    let memory = reopened.source(.memory); memory.resume(); try await waitUntil { memory.first.query.value == [] }
}
