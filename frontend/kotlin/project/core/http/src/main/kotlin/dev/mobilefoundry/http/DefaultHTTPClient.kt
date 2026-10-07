package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

class DefaultHTTPClient private constructor(
    private val base: HttpUrl, private val transport: HTTPTransport, private val timeoutMilliseconds: Long,
    private val headers: Map<String, String>, private val now: () -> Long,
) : HTTPClient {
    companion object {
        fun create(baseURL: String, transport: HTTPTransport, timeoutMilliseconds: Long = 15_000,
                   headers: Map<String, String> = emptyMap(), now: () -> Long = System::currentTimeMillis): AppResult<DefaultHTTPClient> {
            if (RequestPreparation.timeout(timeoutMilliseconds) != null) return Outcome.Err(internalFailure("http.invalid_configuration", "The default request budget is invalid."))
            return RequestPreparation.base(baseURL).map { DefaultHTTPClient(it, transport, timeoutMilliseconds, headers.toMap(), now) }
        }
    }
    override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
        currentCoroutineContext().ensureActive()
        val budget = options.timeoutMilliseconds ?: timeoutMilliseconds
        RequestPreparation.timeout(budget)?.let { return Outcome.Err(it) }
        val timeout = Outcome.Err(Failure.Timeout(FailureMeta("The request exceeded its time budget.", correlationId = options.correlationId)))
        if (budget == 0L) return timeout
        val result = withTimeoutOrNull(budget) { exchange(method, path, options) } ?: timeout
        currentCoroutineContext().ensureActive()
        return result
    }
    private suspend fun exchange(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
        val prepared = when (val preparation = RequestPreparation.prepare(base, method, path, options, headers)) {
            is Outcome.Ok -> preparation.value
            is Outcome.Err -> return preparation
        }
        currentCoroutineContext().ensureActive()
        val response = when (val answer = transport.send(prepared)) {
            is Outcome.Ok -> answer.value
            is Outcome.Err -> return answer
        }
        currentCoroutineContext().ensureActive()
        val meta = ProblemDecoder.metadata(response, options.correlationId)
        if (response.status !in 100..599) return Outcome.Err(internalFailure("http.unreadable_response", "The response is not readable.", meta.correlationId))
        val successful = response.status in 200..299
        if (successful && (method == HTTPMethod.HEAD || response.status in listOf(204, 205))) return Outcome.Ok(Entity(JsonNull, meta))
        val bytes = response.body ?: return if (!successful) Outcome.Err(ProblemDecoder.decode(response, null, options.correlationId, now())) else
            Outcome.Err(Failure.Unavailable(FailureMeta("The response body could not be read.", requestId = meta.requestId, correlationId = meta.correlationId)))
        val raw = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return if (!successful) Outcome.Err(ProblemDecoder.decode(response, null, options.correlationId, now())) else
                Outcome.Err(Failure.Internal(FailureMeta("The successful response was not valid JSON.", "http.invalid_response", meta.requestId, meta.correlationId)))
        }
        if (successful && raw.isBlank()) return Outcome.Ok(Entity(JsonNull, meta))
        val body = try {
            Json.parseToJsonElement(raw).also { if (!supportedJSON(it)) throw SerializationException("Unsupported JSON number") }
        } catch (_: SerializationException) {
            return if (!successful) Outcome.Err(ProblemDecoder.decode(response, null, options.correlationId, now())) else
                Outcome.Err(Failure.Internal(FailureMeta("The successful response was not valid JSON.", "http.invalid_response", meta.requestId, meta.correlationId)))
        }
        currentCoroutineContext().ensureActive()
        return if (successful) Outcome.Ok(Entity(body, meta)) else Outcome.Err(ProblemDecoder.decode(response, body, options.correlationId, now()))
    }
}
