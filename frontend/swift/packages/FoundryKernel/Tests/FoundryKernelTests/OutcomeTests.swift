import Testing
import FoundryKernel

private enum MappingDefect: Error, Equatable {
    case unexpected
}

@Test func successMappingAndChaining() {
    let original: AppResult<Int> = .success(2)
    var mapCalls = 0
    let mapped = original.map { value in
        mapCalls += 1
        return value * 3
    }
    #expect(mapped == .success(6))
    #expect(mapCalls == 1)

    var chainCalls = 0
    let refusal = Failure.conflict(FailureMeta(message: "Changed."))
    let chained = mapped.flatMap { _ -> AppResult<String> in
        chainCalls += 1
        return .failure(refusal)
    }
    #expect(chained == .failure(refusal))
    #expect(chainCalls == 1)

    var errorCalls = 0
    let unchanged = original.mapError { _ in
        errorCalls += 1
        return MappingDefect.unexpected
    }
    #expect(unchanged == .success(2))
    #expect(errorCalls == 0)
}

@Test func failureSkipsSuccessCallbacksAndMapsOnlyError() {
    let failure = Failure.unavailable(FailureMeta(message: "Unavailable."))
    let original: AppResult<Int> = .failure(failure)
    var successCalls = 0
    let mapped = original.map { value in
        successCalls += 1
        return String(value)
    }
    let chained = original.flatMap { value -> AppResult<String> in
        successCalls += 1
        return .success(String(value))
    }
    #expect(mapped == .failure(failure))
    #expect(chained == .failure(failure))
    #expect(successCalls == 0)

    var errorCalls = 0
    let mappedError = original.mapError { error in
        errorCalls += 1
        #expect(error == failure)
        return MappingDefect.unexpected
    }
    #expect(mappedError == .failure(.unexpected))
    #expect(errorCalls == 1)
}

@Test func absenceAndEmptyCollectionsAreSuccessfulValues() {
    let missing: AppResult<String?> = .success(nil)
    let empty: AppResult<[String]> = .success([])
    #expect(missing == .success(nil))
    #expect(empty == .success([]))
    #expect(missing.map { $0?.count } == .success(nil))
}
