package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.AppResult
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.kernel.FailureMeta
import dev.mobilefoundry.kernel.map
import java.util.Collections
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl

enum class HTTPMethod { GET, HEAD, POST, PUT, PATCH, DELETE }
data class QueryItem(val name: String, val value: String)
data class RequestOptions(
    val timeoutMilliseconds: Long? = null,
    val correlationId: String? = null,
    val query: List<QueryItem> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val ifMatch: String? = null,
    val idempotencyKey: String? = null,
)

class PreparedRequest(val url: HttpUrl, val method: HTTPMethod, headers: Map<String, String>, body: ByteArray? = null) {
    val headers: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(headers))
    private val bytes = body?.copyOf()
    val body: ByteArray? get() = bytes?.copyOf()
}

/** Null body represents a read failure; empty bytes represent no content. */
class WireResponse(val status: Int, headers: Map<String, String> = emptyMap(), body: ByteArray?) {
    val headers: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(headers))
    private val bytes = body?.copyOf()
    val body: ByteArray? get() = bytes?.copyOf()
}

data class ResponseMetadata(val status: Int, val etag: String?, val requestId: String?, val correlationId: String?)
data class Entity(val body: JsonElement, val metadata: ResponseMetadata)

/** Must cooperate with coroutine cancellation and close native response bodies. */
fun interface HTTPTransport { suspend fun send(request: PreparedRequest): AppResult<WireResponse> }

interface HTTPClient {
    suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions = RequestOptions()): AppResult<Entity>
    suspend fun request(method: HTTPMethod, path: String, options: RequestOptions = RequestOptions()): AppResult<JsonElement> =
        requestEntity(method, path, options).map { it.body }
}

internal fun Map<String, String>.header(name: String): String? = entries.firstOrNull { it.key.equals(name, true) }?.value
internal fun String?.nonblank(): String? = this?.takeIf { it.isNotBlank() }
internal fun internalFailure(code: String, message: String, correlationId: String? = null): Failure =
    Failure.Internal(FailureMeta(message, code, correlationId = correlationId))
