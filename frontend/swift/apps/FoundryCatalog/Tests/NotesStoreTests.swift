import Foundation
import Testing
import FoundryHTTP
import FoundryKernel
import FoundryServices
@testable import FoundryCatalog

private actor SequenceMemoryNotes: NotesService {
    var answers: [AppResult<[Note]>]
    init(_ answers: [AppResult<[Note]>]) { self.answers = answers }
    func list() async throws -> AppResult<[Note]> {
        switch answers.removeFirst() {
        case .success(let notes): return try await InMemoryNotesService(notes: notes).list()
        case .failure(let failure): return .failure(failure)
        }
    }
}

private actor SequenceNotesWire: HTTPTransport {
    var responses: [WireResponse]
    init(_ responses: [WireResponse]) { self.responses = responses }
    func send(_ request: PreparedRequest) async throws -> AppResult<WireResponse> { .success(responses.removeFirst()) }
}

/// Intentionally ignores cancellation so generation admission is exercised.
private actor ControlledNotes: NotesService {
    var requests: [CheckedContinuation<AppResult<[Note]>, any Error>?] = []
    var arrivals: [(Int, CheckedContinuation<Void, Never>)] = []
    func list() async throws -> AppResult<[Note]> {
        try await withCheckedThrowingContinuation { continuation in
            requests.append(continuation)
            let ready = arrivals.filter { $0.0 <= requests.count }
            arrivals.removeAll { $0.0 <= requests.count }
            ready.forEach { $0.1.resume() }
        }
    }
    func waitForCalls(_ count: Int) async {
        if requests.count >= count { return }
        await withCheckedContinuation { arrivals.append((count, $0)) }
    }
    func finish(_ index: Int, with result: Result<AppResult<[Note]>, any Error>) {
        requests[index]!.resume(with: result)
        requests[index] = nil
    }
}

@MainActor @Test func bothProvidersShareRefreshFailureAndRecoveryBehavior() async throws {
    let notes = [try #require(Note(id: "n1", title: "Idea"))]
    let failure = Failure.unavailable(.init(message: "Unavailable", code: "notes.unavailable"))
    let wire = SequenceNotesWire([
        .init(status: 200, body: Data("{\"notes\":[{\"id\":\"n1\",\"title\":\"Idea\"}]}".utf8)),
        .init(status: 503, body: Data("{\"detail\":\"Unavailable\",\"code\":\"notes.unavailable\"}".utf8)),
        .init(status: 200, body: Data("{\"notes\":[]}".utf8)),
        .init(status: 503, body: Data("{\"detail\":\"Unavailable\",\"code\":\"notes.unavailable\"}".utf8)),
    ])
    let http = try DefaultHTTPClient.create(baseURL: "https://example.invalid/v1", transport: wire).get()
    let providers: [any NotesService] = [SequenceMemoryNotes([.success(notes), .failure(failure), .success([]), .failure(failure)]), HTTPNotesService(http: http)]
    for provider in providers {
        let store = NotesStore(service: provider) { _ in Issue.record("Unexpected defect") }
        #expect(store.state == .idle)
        await store.load()
        #expect(store.state == .loaded(notes))
        await store.load()
        #expect(store.state == .failed(failure, previous: notes))
        await store.load()
        #expect(store.state == .loaded([]))
        await store.load()
        #expect(store.state == .failed(failure, previous: []))
    }
}

@MainActor @Test func refreshRetainsDataAndCancelRestoresIt() async throws {
    let service = ControlledNotes()
    let store = NotesStore(service: service) { _ in Issue.record("Unexpected defect") }
    let notes = [try #require(Note(id: "n1", title: "Saved"))]
    let first = Task { await store.load() }
    await service.waitForCalls(1)
    #expect(store.state == .loading(previous: nil))
    await service.finish(0, with: .success(.success(notes)))
    await first.value
    let refresh = Task { await store.load() }
    await service.waitForCalls(2)
    #expect(store.state == .loading(previous: notes))
    store.cancel()
    #expect(store.state == .loaded(notes))
    await service.finish(1, with: .success(.success([])))
    await refresh.value
    #expect(store.state == .loaded(notes))
}

@MainActor @Test func obsoleteFailureCannotReplaceNewerSuccess() async throws {
    let service = ControlledNotes()
    let store = NotesStore(service: service) { _ in Issue.record("Obsolete defect reported") }
    let old = Task { await store.load() }
    await service.waitForCalls(1)
    let latest = Task { await store.load() }
    await service.waitForCalls(2)
    let notes = [try #require(Note(id: "new", title: "Newest"))]
    await service.finish(1, with: .success(.success(notes)))
    await latest.value
    await service.finish(0, with: .success(.failure(.unavailable(.init(message: "Old failure")))))
    await old.value
    #expect(store.state == .loaded(notes))
}

@MainActor @Test func parentCancellationRestoresIdleEvenWithLateResult() async {
    let service = ControlledNotes()
    let store = NotesStore(service: service) { _ in Issue.record("Cancellation reported") }
    let task = Task { await store.load() }
    await service.waitForCalls(1)
    task.cancel()
    await service.finish(0, with: .success(.success([])))
    await task.value
    #expect(store.state == .idle)
}

@MainActor @Test func obsoleteSuccessCannotReplaceNewerFailure() async throws {
    let service = ControlledNotes()
    let store = NotesStore(service: service) { _ in Issue.record("Unexpected defect") }
    let old = Task { await store.load() }
    await service.waitForCalls(1)
    let latest = Task { await store.load() }
    await service.waitForCalls(2)
    let failure = Failure.unavailable(.init(message: "Current failure"))
    await service.finish(1, with: .success(.failure(failure)))
    await latest.value
    await service.finish(0, with: .success(.success([try #require(Note(id: "old", title: "Obsolete"))])))
    await old.value
    #expect(store.state == .failed(failure, previous: nil))
}

private final class StoreDefect: Error, @unchecked Sendable {}

@MainActor @Test func defectIdentityReachesDiagnosticsAndPublicStateIsGeneric() async {
    let service = ControlledNotes()
    let defect = StoreDefect()
    var observed: (any Error)?
    let store = NotesStore(service: service) { observed = $0 }
    let task = Task { await store.load() }
    await service.waitForCalls(1)
    await service.finish(0, with: .failure(defect))
    await task.value
    #expect(observed as? StoreDefect === defect)
    #expect(store.state == .failed(.internalError(.init(message: "An unexpected error occurred.")), previous: nil))
}

@MainActor @Test func obsoleteDefectCannotReportOrReplaceCurrentState() async {
    let service = ControlledNotes()
    let store = NotesStore(service: service) { _ in Issue.record("Obsolete defect reported") }
    let old = Task { await store.load() }
    await service.waitForCalls(1)
    let latest = Task { await store.load() }
    await service.waitForCalls(2)
    await service.finish(1, with: .success(.success([])))
    await latest.value
    await service.finish(0, with: .failure(StoreDefect()))
    await old.value
    #expect(store.state == .loaded([]))
}
