package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.AppResult
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.kernel.Outcome
import java.net.URI
import java.net.URISyntaxException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object RequestPreparation {
    fun base(text: String): AppResult<HttpUrl> {
        fun refused() = Outcome.Err(internalFailure("http.invalid_configuration", "The API base URL is invalid."))
        val uri = try { URI(text) } catch (_: URISyntaxException) { return refused() }
        if (text != text.trim() || uri.scheme?.lowercase() !in listOf("http", "https") || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.host.isNullOrBlank() ||
            (uri.port != -1 && uri.port !in 1..65535) || !validSegments(uri.rawPath.orEmpty())) return refused()
        val base = text.toHttpUrlOrNull() ?: return refused()
        return Outcome.Ok(base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/").build())
    }

    fun timeout(value: Long): Failure? = if (value in 0..2_147_483_647) null else
        internalFailure("http.invalid_timeout", "The request budget is invalid.")

    fun prepare(base: HttpUrl, method: HTTPMethod, path: String, options: RequestOptions,
                commonHeaders: Map<String, String>): AppResult<PreparedRequest> {
        fun refused(message: String) = Outcome.Err(internalFailure("http.invalid_request", message, options.correlationId))
        if (path != path.trim() || path.startsWith("//") || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(path) ||
            unsafe(path, false) || !validSegments(path)) return refused("Supply an API-relative path without traversal or invalid encoding.")
        val url = base.newBuilder().addEncodedPathSegments(path.removePrefix("/"))
        options.query.forEach { url.addQueryParameter(it.name, it.value) }
        val body = options.body?.let {
            if (method == HTTPMethod.GET || method == HTTPMethod.HEAD || it !is JsonObject || !supportedJSON(it)) {
                return refused("Request bodies must be JSON objects on body-capable methods.")
            }
            try { Json.encodeToString(JsonElement.serializer(), it).toByteArray(Charsets.UTF_8) }
            catch (_: SerializationException) { return refused("The request body is not valid JSON.") }
        }
        val headers = linkedMapOf<String, String>()
        for (layer in listOf(commonHeaders, options.headers)) {
            for ((name, value) in layer) {
                if (!validHeader(name, value)) return refused("Invalid request header.")
                headers[name.lowercase()] = value
            }
        }
        for ((name, value) in listOf("x-correlation-id" to options.correlationId, "if-match" to options.ifMatch, "idempotency-key" to options.idempotencyKey)) {
            if (value != null) {
                if (!validHeader(name, value)) return refused("Invalid request header.")
                headers[name] = value
            }
        }
        headers["accept"] = "application/json, application/problem+json"
        headers.remove("content-type")
        if (body != null) headers["content-type"] = "application/json"
        return Outcome.Ok(PreparedRequest(url.build(), method, headers, body))
    }

    private fun validSegments(path: String): Boolean = path.split('/').all {
        val decoded = decodeSegment(it) ?: return@all false
        decoded != "." && decoded != ".." && !unsafe(decoded, true)
    }
    private fun decodeSegment(segment: String): String? {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < segment.length) {
            if (segment[index] == '%') {
                if (index + 2 >= segment.length) return null
                val high = segment[index + 1].digitToIntOrNull(16) ?: return null
                val low = segment[index + 2].digitToIntOrNull(16) ?: return null
                bytes.write(high * 16 + low); index += 3
            } else {
                val end = segment.indexOf('%', index).let { if (it == -1) segment.length else it }
                bytes.write(segment.substring(index, end).toByteArray(Charsets.UTF_8)); index = end
            }
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: CharacterCodingException) { null }
    }
    private fun unsafe(value: String, segment: Boolean): Boolean = value.any {
        it.code <= 31 || it.code == 127 || it == '\\' || (if (segment) it == '/' else it == '?' || it == '#')
    }
    private fun validHeader(name: String, value: String): Boolean = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$").matches(name) &&
        value.none { it.code <= 8 || it.code in 10..31 || it.code == 127 || it.code >= 256 }
}

internal fun supportedJSON(value: JsonElement): Boolean = when (value) {
    is JsonObject -> value.values.all(::supportedJSON)
    is JsonArray -> value.all(::supportedJSON)
    JsonNull -> true
    is JsonPrimitive -> value.isString || value.booleanOrNull != null || value.doubleOrNull?.isFinite() == true
}
