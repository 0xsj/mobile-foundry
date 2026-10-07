package dev.mobilefoundry.kernel

/** A typed success or expected failure. Neither branch implies exception conversion. */
sealed interface Outcome<out Value, out Problem> {
    data class Ok<out Value>(val value: Value) : Outcome<Value, Nothing>
    data class Err<out Problem>(val error: Problem) : Outcome<Nothing, Problem>
}

/** The foundation failure vocabulary, with nullable success available for absence. */
typealias AppResult<Value> = Outcome<Value, Failure>

/** Transform success only. Callback exceptions propagate unchanged. */
inline fun <Value, Problem, Next> Outcome<Value, Problem>.map(
    transform: (Value) -> Next,
): Outcome<Next, Problem> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

/** Transform failure only. A successful payload is preserved unchanged. */
inline fun <Value, Problem, Next> Outcome<Value, Problem>.mapError(
    transform: (Problem) -> Next,
): Outcome<Value, Next> = when (this) {
    is Outcome.Ok -> this
    is Outcome.Err -> Outcome.Err(transform(error))
}

/** Chain success only; retain the same error type and propagate callback exceptions. */
inline fun <Value, Problem, Next> Outcome<Value, Problem>.flatMap(
    transform: (Value) -> Outcome<Next, Problem>,
): Outcome<Next, Problem> = when (this) {
    is Outcome.Ok -> transform(value)
    is Outcome.Err -> this
}
