import FoundryKernel
import FoundryQuery
import Observation

// Verification-only feature: copied into a temporary generated app, not the profile.
@MainActor protocol CounterService {
    func read() -> AppResult<Int>
    func increment() -> AppResult<Int>
}
@MainActor final class MemoryCounterService: CounterService {
    private var count = 0
    func read() -> AppResult<Int> { .success(count) }
    func increment() -> AppResult<Int> { count += 1; return .success(count) }
}
@Observable @MainActor final class CounterModel {
    private let service: any CounterService
    var query: QueryState<Int> = .idle
    var mutation: MutationState<Int> = .idle
    init(service: any CounterService) { self.service = service; refresh() }
    func refresh() { query = query.starting().settled(with: service.read()) }
    func increment() {
        mutation = mutation.starting().settled(with: service.increment())
        if mutation.value != nil { refresh() }
    }
}
