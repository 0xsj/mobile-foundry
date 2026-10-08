import Foundation
import Testing
import FoundryKernel
import FoundryQuery

private struct StateFixture: Decodable {
    let id: String
    let phase: String
    let snapshot: String?
    let expectedValue: String?
    let loading: Bool
    let failed: Bool
    let restoredPhase: String
}

@Test func sharedStateMatrixPreservesAbsenceAndEmptySnapshots() throws {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let fixtures = try JSONDecoder().decode([StateFixture].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/query/states.json")))
    let failure = Failure.unavailable(.init(message: "Unavailable"))
    for fixture in fixtures {
        let state: QueryState<String>
        switch fixture.phase {
        case "idle": state = .idle
        case "loading": state = .loading(previous: fixture.snapshot)
        case "loaded": state = .loaded(try #require(fixture.snapshot))
        case "failed": state = .failed(failure, previous: fixture.snapshot)
        default: Issue.record("Unknown phase"); continue
        }
        #expect(state.value == fixture.expectedValue, "\(fixture.id)")
        #expect(state.isLoading == fixture.loading)
        #expect(state.failure == (fixture.failed ? failure : nil))
        #expect(state.starting() == .loading(previous: fixture.expectedValue))
        #expect(state.settled(with: .failure(failure)) == .failed(failure, previous: fixture.expectedValue))
        #expect(state.settled(with: .success("New")) == .loaded("New"))
        if fixture.restoredPhase == "idle" { #expect(state.restored() == .idle) }
        else { #expect(state.restored() == .loaded(try #require(fixture.expectedValue))) }
    }
}

@Test func repeatedRefreshFailureAndCancellationRetainEmptySuccess() {
    let failure = Failure.timeout(.init(message: "Timed out"))
    var state: QueryState<[Int]> = .idle
    state = state.starting().settled(with: .success([1]))
    #expect(state == .loaded([1]))
    state = state.starting().settled(with: .success([]))
    state = state.starting().settled(with: .failure(failure))
    state = state.starting().starting()
    #expect(state == .loading(previous: []))
    #expect(state.restored() == .loaded([]))
}
