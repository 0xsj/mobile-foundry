package dev.mobilefoundry.query

import dev.mobilefoundry.kernel.AppResult
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.kernel.Outcome

/** Value state only. Supply immutable, non-null snapshots; payloads are not copied. */
sealed interface QueryState<out Value : Any> {
    data object Idle : QueryState<Nothing>
    data class Loading<out Value : Any>(val previous: Value?) : QueryState<Value>
    data class Loaded<out Value : Any>(val value: Value) : QueryState<Value>
    data class Failed<out Value : Any>(val failure: Failure, val previous: Value?) : QueryState<Value>
}

/** The current or last successful snapshot, including an empty value. */
val <Value : Any> QueryState<Value>.value: Value?
    get() = when (this) {
        QueryState.Idle -> null
        is QueryState.Loading -> previous
        is QueryState.Loaded -> value
        is QueryState.Failed -> previous
    }

val QueryState<*>.failure: Failure?
    get() = (this as? QueryState.Failed)?.failure

val QueryState<*>.isLoading: Boolean
    get() = this is QueryState.Loading

/** Returns loading state; does not start or admit asynchronous work. */
fun <Value : Any> QueryState<Value>.starting(): QueryState<Value> = QueryState.Loading(value)

/** Apply only an admitted result. No stale-request protection is performed here. */
fun <Value : Any> QueryState<Value>.settled(result: AppResult<Value>): QueryState<Value> = when (result) {
    is Outcome.Ok -> QueryState.Loaded(result.value)
    is Outcome.Err -> QueryState.Failed(result.error, value)
}

/** Drop progress/failure while retaining the last successful snapshot. */
fun <Value : Any> QueryState<Value>.restored(): QueryState<Value> = value?.let { QueryState.Loaded(it) } ?: QueryState.Idle
