import Foundation
import Testing
import FoundryKernel

private struct Fixture {
    let caseID: String
    let kind: FailureKind
    let meta: FailureMeta
    let fields: [String: String]
    let retryAfter: RetryAfter?
    let publicMessage: String
    let publicCode: String?

    var failure: Failure {
        switch kind {
        case .unauthenticated: .unauthenticated(meta)
        case .forbidden: .forbidden(meta)
        case .rateLimited: .rateLimited(meta, retryAfter: retryAfter)
        case .unavailable: .unavailable(meta)
        case .timeout: .timeout(meta)
        case .canceled: .canceled(meta)
        case .internalError: .internalError(meta)
        case .notFound: .notFound(meta)
        case .invalid: .invalid(meta, fields: fields)
        case .conflict: .conflict(meta)
        }
    }
}

private func fixtures() throws -> [Fixture] {
    // Canonical fixture stays in the repository; it is not a library/app resource.
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let url = root.appendingPathComponent("contracts/fixtures/kernel/failures.tsv")
    let text = try String(contentsOf: url, encoding: .utf8)
    let lines = text.split(separator: "\n")
    #expect(lines.first?.hasPrefix("kind\tmessage\tcode\t") == true)
    return try lines.dropFirst().map { line in
        let cells = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
        try #require(cells.count == 11)
        let kind = try #require(FailureKind(rawValue: cells[0]))
        func optional(_ index: Int) -> String? { cells[index].isEmpty ? nil : cells[index] }
        let delay: RetryAfter?
        if let raw = optional(7) {
            let milliseconds = try #require(Int64(raw))
            let candidate: RetryAfter? = RetryAfter(milliseconds: milliseconds)
            let admitted: RetryAfter = try #require(candidate)
            delay = admitted
        } else {
            delay = nil
        }
        return Fixture(
            caseID: cells[10],
            kind: kind,
            meta: FailureMeta(message: cells[1], code: optional(2), requestID: optional(3), correlationID: optional(4)),
            fields: cells[5].isEmpty ? [:] : [cells[5]: cells[6]],
            retryAfter: delay,
            publicMessage: cells[8],
            publicCode: optional(9)
        )
    }
}

@Test func sharedFixturesCoverEveryKindAndPublicProjection() throws {
    let cases = try fixtures()
    #expect(Set(cases.map { $0.kind }) == Set(FailureKind.allCases))
    #expect(cases.count == 13)
    #expect(Set(cases.map { $0.caseID }).count == cases.count)
    for fixture in cases {
        let failure = fixture.failure
        #expect(failure.kind == fixture.kind)
        #expect(failure.meta == fixture.meta)
        let publicValue = failure.publicInfo()
        #expect(publicValue.kind == fixture.kind)
        #expect(publicValue.meta.message == fixture.publicMessage)
        #expect(publicValue.meta.code == fixture.publicCode)
        #expect(publicValue.meta.requestID == fixture.meta.requestID)
        #expect(publicValue.meta.correlationID == fixture.meta.correlationID)
        #expect(publicValue.publicInfo() == publicValue)
        switch publicValue {
        case .invalid(_, let fields): #expect(fields == fixture.fields)
        case .rateLimited(_, let delay): #expect(delay == fixture.retryAfter)
        default: break
        }
        if fixture.kind != .internalError { #expect(publicValue == failure) }
    }
}

@Test func validationFieldsHaveValueSemantics() throws {
    var original = ["email": "Required."]
    let failure = Failure.invalid(FailureMeta(message: "Check input."), fields: original)
    original["email"] = "Changed by caller."
    original["password"] = "Extra."
    guard case .invalid(_, var extracted) = failure else {
        Issue.record("Expected invalid failure.")
        return
    }
    #expect(extracted == ["email": "Required."])
    extracted["email"] = "Changed after extraction."
    guard case .invalid(_, let stored) = failure else { return }
    #expect(stored == ["email": "Required."])
}

@Test func retryTimingRejectsNegativeAndPreservesBounds() {
    #expect(RetryAfter(milliseconds: -1) == nil)
    #expect(RetryAfter(milliseconds: .min) == nil)
    #expect(RetryAfter(milliseconds: 0)?.milliseconds == 0)
    #expect(RetryAfter(milliseconds: .max)?.milliseconds == Int64.max)
}

@Test func internalProjectionDoesNotChangeTheOriginalValue() {
    let failure = Failure.internalError(FailureMeta(
        message: "Private database detail.", code: "database.connection_failed"
    ))
    let publicValue = failure.publicInfo()
    #expect(publicValue.meta.message == "An unexpected error occurred.")
    #expect(publicValue.meta.code == nil)
    #expect(failure.meta.message == "Private database detail.")
    #expect(failure.meta.code == "database.connection_failed")
}
