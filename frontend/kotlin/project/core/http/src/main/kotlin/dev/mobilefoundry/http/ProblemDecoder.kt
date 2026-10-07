package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.*
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.*

object ProblemDecoder {
    fun kind(status: Int): FailureKind = when (status) {
        400, 413, 415, 422, 428 -> FailureKind.INVALID
        401 -> FailureKind.UNAUTHENTICATED
        403 -> FailureKind.FORBIDDEN
        404 -> FailureKind.NOT_FOUND
        408, 504 -> FailureKind.TIMEOUT
        409, 412 -> FailureKind.CONFLICT
        429 -> FailureKind.RATE_LIMITED
        502, 503 -> FailureKind.UNAVAILABLE
        else -> FailureKind.INTERNAL
    }
    fun metadata(response: WireResponse, correlationId: String?): ResponseMetadata = ResponseMetadata(
        response.status, response.headers.header("etag"), response.headers.header("x-request-id").nonblank(),
        correlationId ?: response.headers.header("x-correlation-id").nonblank(),
    )
    fun decode(response: WireResponse, body: JsonElement?, correlationId: String?, now: Long): Failure {
        val problem = body as? JsonObject
        fun text(key: String): String? = (problem?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content.nonblank()
        val kind = FailureKind.entries.firstOrNull { it.value == text("kind") } ?: kind(response.status)
        val responseMeta = metadata(response, correlationId)
        val meta = FailureMeta(text("detail") ?: text("title") ?: "The request failed.", text("code"),
            responseMeta.requestId ?: text("request_id"), responseMeta.correlationId)
        return when (kind) {
            FailureKind.UNAUTHENTICATED -> Failure.Unauthenticated(meta)
            FailureKind.FORBIDDEN -> Failure.Forbidden(meta)
            FailureKind.RATE_LIMITED -> Failure.RateLimited(meta, retryAfter(response.headers.header("retry-after"), now))
            FailureKind.UNAVAILABLE -> Failure.Unavailable(meta)
            FailureKind.TIMEOUT -> Failure.Timeout(meta)
            FailureKind.CANCELED -> Failure.Canceled(meta)
            FailureKind.INTERNAL -> Failure.Internal(meta)
            FailureKind.NOT_FOUND -> Failure.NotFound(meta)
            FailureKind.INVALID -> Failure.Invalid(meta, (problem?.get("fields") as? JsonObject).orEmpty()
                .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content } }.toMap())
            FailureKind.CONFLICT -> Failure.Conflict(meta)
        }
    }
    fun retryAfter(input: String?, now: Long): RetryAfter? {
        val text = input?.trim() ?: return null
        val maxDelay = 9_007_199_254_740_991L
        if (Regex("^[0-9]+$").matches(text)) {
            val seconds = text.toLongOrNull() ?: return null
            return if (seconds <= maxDelay / 1000) RetryAfter.fromMilliseconds(seconds * 1000) else null
        }
        val forms = listOf(
            "^[A-Za-z]{3}, [0-9]{2} [A-Za-z]{3} [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$" to "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "^[A-Za-z]+, [0-9]{2}-[A-Za-z]{3}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$" to "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",
            "^[A-Za-z]{3} [A-Za-z]{3} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} [0-9]{4}$" to "EEE MMM d HH:mm:ss yyyy",
        )
        for ((pattern, format) in forms) if (Regex(pattern).matches(text)) {
            val parser = SimpleDateFormat(format, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT"); isLenient = false
                set2DigitYearStart(Date(-631_152_000_000L))
            }
            val position = ParsePosition(0)
            val date = parser.parse(text, position) ?: return null
            if (position.index != text.length) return null
            val delay = if (date.time <= now) 0L else try { Math.subtractExact(date.time, now) } catch (_: ArithmeticException) { return null }
            return if (delay <= maxDelay) RetryAfter.fromMilliseconds(delay) else null
        }
        return null
    }
}
