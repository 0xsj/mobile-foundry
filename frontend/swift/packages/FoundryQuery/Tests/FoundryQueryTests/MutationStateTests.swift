import Foundation
import Testing
import FoundryKernel
import FoundryQuery

@Test func mutationMatrixDoesNotRetainReceiptsAcrossAttempts() throws {
    struct Case: Decodable { let phase: String; let value: String?; let busy: Bool; let failed: Bool }
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let cases = try JSONDecoder().decode([Case].self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/query/mutations.json")))
    let failure = Failure.timeout(.init(message: "No confirmation"))
    for row in cases {
        let state: MutationState<String>
        switch row.phase {
        case "idle": state = .idle
        case "submitting": state = .submitting
        case "succeeded": state = .succeeded(try #require(row.value))
        case "failed": state = .failed(failure)
        default: Issue.record("Unknown mutation phase"); continue
        }
        #expect(state.value == row.value)
        #expect(state.isSubmitting == row.busy)
        #expect(state.failure == (row.failed ? failure : nil))
        #expect(state.starting() == .submitting && state.starting().value == nil)
        #expect(state.settled(with: .success("Next")) == .succeeded("Next"))
        #expect(state.settled(with: .failure(failure)) == .failed(failure))
        #expect(state.reset() == .idle)
    }
}
