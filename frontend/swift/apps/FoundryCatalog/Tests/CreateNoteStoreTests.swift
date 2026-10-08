import Testing
import FoundryKernel
import FoundryQuery
import FoundryServices
@testable import FoundryCatalog

private actor ControlledCreator: NoteCreator {
    var requests: [CheckedContinuation<AppResult<Note>, any Error>?] = []
    var arrivals: [(Int, CheckedContinuation<Void, Never>)] = []
    var titles: [String] = []
    func create(_ command: CreateNote) async throws -> AppResult<Note> {
        titles.append(command.title)
        return try await withCheckedThrowingContinuation { continuation in
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
    func finish(_ index: Int, _ result: Result<AppResult<Note>, any Error>) {
        requests[index]!.resume(with: result); requests[index] = nil
    }
}

@MainActor @Test func invalidCreateNeverCallsServiceAndTouchedValidationUpdates() async {
    let creator = ControlledCreator()
    let store = CreateNoteStore(creator: creator) { _ in Issue.record("Unexpected defect") }
    #expect(store.titleError == nil)
    store.blurTitle()
    #expect(store.titleError == "Enter a title.")
    store.editTitle(" \t\r\n")
    #expect(store.submit() == nil)
    #expect(store.mutation == .idle && store.touched)
    #expect(await creator.titles.isEmpty)
    store.editTitle("Idea")
    #expect(store.titleError == nil)
    store.editTitle(String(repeating: "a", count: 201))
    #expect(store.titleError == "Use 200 characters or fewer.")
    store.reset()
    #expect(store.title.isEmpty && !store.touched && store.titleError == nil)
}

@MainActor @Test func bothCreateProvidersRetainDraftMapFieldErrorsAndResetAfterSuccess() async throws {
    for provider in NotesProvider.allCases {
        let store = CreateNoteStore(creator: CreateNoteComposition.creator(provider: provider, scenario: .fieldError)) { _ in Issue.record("Unexpected defect") }
        store.editTitle("  Idea  ")
        await store.submit()?.value
        #expect(store.title == "  Idea  " && store.titleError == "This title is reserved.")
        #expect(store.mutation.failure?.kind == .invalid)
        store.editTitle("  Revised idea  ")
        #expect(store.titleError == nil && store.mutation == .idle)
        #expect(store.use(CreateNoteComposition.creator(provider: provider, scenario: .success)))
        #expect(store.title == "  Revised idea  ")
        await store.submit()?.value
        #expect(store.mutation.value?.title == "  Revised idea  ")
        #expect(!store.canEdit && store.submit() == nil)
        store.reset()
        #expect(store.title.isEmpty && !store.touched && store.mutation == .idle)
    }
}

@MainActor @Test func createAdmissionBlocksDuplicateAndBusyMutationsBeforeDispatch() async throws {
    let creator = ControlledCreator()
    let store = CreateNoteStore(creator: creator) { _ in Issue.record("Unexpected defect") }
    store.editTitle("Original")
    let task = try #require(store.submit())
    #expect(store.mutation == .submitting && store.submit() == nil)
    store.editTitle("Ignored"); store.reset()
    #expect(!store.use(InMemoryNoteCreator()))
    await creator.waitForCalls(1)
    let submittedTitles = await creator.titles
    #expect(store.title == "Original" && submittedTitles == ["Original"])
    let unknown = Failure.invalid(.init(message: "This note cannot be created."), fields: ["other": "Not accepted."])
    await creator.finish(0, .success(.failure(unknown)))
    await task.value
    #expect(store.title == "Original" && store.titleError == nil && store.mutation.failure == unknown)
}

private final class CreateStoreDefect: Error, @unchecked Sendable {}

@MainActor @Test func stoppedCreateCannotPublishLateSuccessFailureOrDefectIntoNewAttempt() async throws {
    let oldNote = try #require(Note(id: "old", title: "Old"))
    let nextNote = try #require(Note(id: "next", title: "Next"))
    let outcomes: [Result<AppResult<Note>, any Error>] = [
        .success(.success(oldNote)), .success(.failure(.unavailable(.init(message: "Old refusal")))), .failure(CreateStoreDefect()),
    ]
    for oldResult in outcomes {
        let creator = ControlledCreator()
        let store = CreateNoteStore(creator: creator) { _ in Issue.record("Obsolete defect reported") }
        store.editTitle("Old")
        let old = try #require(store.submit())
        await creator.waitForCalls(1)
        store.stop()
        #expect(store.mutation.failure?.kind == .canceled && store.title == "Old")
        store.editTitle("Next")
        let next = try #require(store.submit())
        await creator.waitForCalls(2)
        await creator.finish(0, oldResult); await old.value
        #expect(store.mutation == .submitting)
        await creator.finish(1, .success(.success(nextNote))); await next.value
        #expect(store.mutation == .succeeded(nextNote))
    }
}

@MainActor @Test func currentCreateDefectReportsIdentityAndUsesGenericPublicFailure() async throws {
    let creator = ControlledCreator()
    let defect = CreateStoreDefect()
    var observed: (any Error)?
    let store = CreateNoteStore(creator: creator) { observed = $0 }
    store.editTitle("Keep me")
    let task = try #require(store.submit())
    await creator.waitForCalls(1)
    await creator.finish(0, .failure(defect)); await task.value
    #expect(observed as? CreateStoreDefect === defect)
    #expect(store.title == "Keep me")
    #expect(store.mutation.failure?.publicInfo().meta.message == "An unexpected error occurred.")
}

@MainActor @Test func directCreateTaskCancellationSettlesWithoutReportingLateDefect() async throws {
    let creator = ControlledCreator()
    let store = CreateNoteStore(creator: creator) { _ in Issue.record("Canceled defect reported") }
    store.editTitle("Keep me")
    let task = try #require(store.submit())
    await creator.waitForCalls(1)
    task.cancel()
    await creator.finish(0, .failure(CreateStoreDefect())); await task.value
    #expect(store.mutation.failure?.kind == .canceled && store.canEdit && store.title == "Keep me")
}
