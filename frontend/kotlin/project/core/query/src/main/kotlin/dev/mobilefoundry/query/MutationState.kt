package dev.mobilefoundry.query

import dev.mobilefoundry.kernel.*

/** Pure write presentation state. Failure/cancellation does not imply remote rollback. */
sealed interface MutationState<out Value : Any> {
    data object Idle : MutationState<Nothing>
    data object Submitting : MutationState<Nothing>
    data class Succeeded<out Value : Any>(val value: Value) : MutationState<Value>
    data class Failed(val failure: Failure) : MutationState<Nothing>
}

val MutationState<*>.isSubmitting: Boolean get() = this is MutationState.Submitting
val <Value : Any> MutationState<Value>.value: Value? get() = (this as? MutationState.Succeeded)?.value
val MutationState<*>.failure: Failure? get() = (this as? MutationState.Failed)?.failure
fun <Value : Any> MutationState<Value>.starting(): MutationState<Value> = MutationState.Submitting
fun <Value : Any> MutationState<Value>.settled(result: AppResult<Value>): MutationState<Value> = when (result) {
    is Outcome.Ok -> MutationState.Succeeded(result.value)
    is Outcome.Err -> MutationState.Failed(result.error)
}
fun <Value : Any> MutationState<Value>.reset(): MutationState<Value> = MutationState.Idle
