package dev.mobilefoundry.kernel

import java.util.Collections

/** Stable categories shared by native foundations. Specific reasons belong in code. */
enum class FailureKind(val value: String) {
    UNAUTHENTICATED("unauthenticated"),
    FORBIDDEN("forbidden"),
    RATE_LIMITED("rate_limited"),
    UNAVAILABLE("unavailable"),
    TIMEOUT("timeout"),
    CANCELED("canceled"),
    INTERNAL("internal"),
    NOT_FOUND("not_found"),
    INVALID("invalid"),
    CONFLICT("conflict"),
}

/** Caller-selected application metadata. Original Throwables and stacks stay separate. */
data class FailureMeta(
    val message: String,
    val code: String? = null,
    val requestId: String? = null,
    val correlationId: String? = null,
)

/** Timing information, not permission to retry. Negative values cannot be constructed. */
@JvmInline
value class RetryAfter private constructor(val milliseconds: Long) {
    companion object {
        fun fromMilliseconds(milliseconds: Long): RetryAfter? =
            if (milliseconds >= 0) RetryAfter(milliseconds) else null
    }
}

/**
 * Expected failures as immutable values. Unexpected defects remain exceptions.
 * Canceled is reserved for boundaries explicitly returning cancellation as a value.
 */
sealed interface Failure {
    val meta: FailureMeta
    val kind: FailureKind

    data class Unauthenticated(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.UNAUTHENTICATED
    }

    data class Forbidden(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.FORBIDDEN
    }

    data class RateLimited(
        override val meta: FailureMeta,
        val retryAfter: RetryAfter? = null,
    ) : Failure {
        override val kind: FailureKind get() = FailureKind.RATE_LIMITED
    }

    data class Unavailable(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.UNAVAILABLE
    }

    data class Timeout(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.TIMEOUT
    }

    data class Canceled(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.CANCELED
    }

    data class Internal(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.INTERNAL
    }

    data class NotFound(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.NOT_FOUND
    }

    /** Snapshot plus runtime read-only wrapper; a val Map alone would still alias callers. */
    class Invalid(override val meta: FailureMeta, fields: Map<String, String>) : Failure {
        val fields: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(fields))
        override val kind: FailureKind get() = FailureKind.INVALID

        override fun equals(other: Any?): Boolean =
            other is Invalid && meta == other.meta && fields == other.fields

        override fun hashCode(): Int = 31 * meta.hashCode() + fields.hashCode()

        override fun toString(): String = "Invalid(meta=$meta, fields=$fields)"
    }

    data class Conflict(override val meta: FailureMeta) : Failure {
        override val kind: FailureKind get() = FailureKind.CONFLICT
    }
}

/** Redact internal message/code while preserving IDs. Other values already select public fields. */
fun Failure.publicInfo(): Failure = when (this) {
    is Failure.Internal -> Failure.Internal(
        FailureMeta(
            message = "An unexpected error occurred.",
            requestId = meta.requestId,
            correlationId = meta.correlationId,
        ),
    )
    else -> this
}
