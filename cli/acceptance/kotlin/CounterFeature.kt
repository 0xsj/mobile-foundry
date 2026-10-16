package dev.mobilefoundry.product

import androidx.compose.runtime.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*

// Verification-only feature: copied into a temporary generated app, not the profile.
interface CounterService {
    fun read(): AppResult<Int>
    fun increment(): AppResult<Int>
}
class MemoryCounterService : CounterService {
    private var count = 0
    override fun read(): AppResult<Int> = Outcome.Ok(count)
    override fun increment(): AppResult<Int> = Outcome.Ok(++count)
}
class CounterModel(private val service: CounterService) {
    var query: QueryState<Int> by mutableStateOf(QueryState.Idle)
        private set
    var mutation: MutationState<Int> by mutableStateOf(MutationState.Idle)
        private set
    init { refresh() }
    fun refresh() { query = query.starting().settled(service.read()) }
    fun increment() {
        mutation = mutation.starting().settled(service.increment())
        if (mutation.value != null) refresh()
    }
}
