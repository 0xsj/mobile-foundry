package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

fun fixtures(name: String): JsonElement = Json.parseToJsonElement(File(System.getProperty("foundry.http.fixtures"), name).readText())
fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
fun JsonObject.headers(): Map<String, String> = getValue("headers").jsonObject.mapValues { it.value.jsonPrimitive.content }
fun JsonObject.response() = WireResponse(getValue("status").jsonPrimitive.int, headers(), text("body")?.toByteArray())
fun <T> AppResult<T>.value(): T = (this as Outcome.Ok).value
private class Stub(private val response: WireResponse) : HTTPTransport {
    val requests = mutableListOf<PreparedRequest>()
    override suspend fun send(request: PreparedRequest): AppResult<WireResponse> { requests += request; return Outcome.Ok(response) }
}

class HTTPTest {
    @Test fun sharedResponseFixtures() = runTest {
        for (element in fixtures("responses.json").jsonArray) {
            val item = element.jsonObject
            val stub = Stub(item.response())
            val http = DefaultHTTPClient.create("https://example.invalid/v1", stub).value()
            val result = http.requestEntity(HTTPMethod.valueOf(item.text("method")!!), "health/live", RequestOptions(correlationId = item.text("correlationID")))
            val kind = item.text("expectedKind")
            if (kind != null) {
                val failure = (result as Outcome.Err).error
                assertEquals(item.text("id"), kind, failure.kind.value)
                assertEquals(item.text("expectedCode"), failure.meta.code)
                item.text("expectedMessage")?.let { assertEquals(it, failure.meta.message) }
                item.text("expectedRequestID")?.let { assertEquals(it, failure.meta.requestId) }
                item.text("expectedCorrelationID")?.let { assertEquals(it, failure.meta.correlationId) }
                if (failure is Failure.Invalid && item["expectedFields"] != null) assertEquals(item.getValue("expectedFields").jsonObject.mapValues { it.value.jsonPrimitive.content }, failure.fields)
                if (failure is Failure.RateLimited) assertEquals(item["expectedRetryMs"]?.jsonPrimitive?.long, failure.retryAfter?.milliseconds)
            } else {
                val entity = result.value()
                assertEquals(item.getValue("status").jsonPrimitive.int, entity.metadata.status)
                if (item.text("id") == "healthy") {
                    assertEquals("\"v1\"", entity.metadata.etag)
                    assertEquals("req-health", entity.metadata.requestId)
                    assertEquals("remote", entity.metadata.correlationId)
                }
                if (item.text("id") in listOf("no-content", "reset-content", "head", "empty", "literal-null")) assertEquals(JsonNull, entity.body)
            }
        }
    }
    @Test fun sharedPathsAndRequestPreparation() = runTest {
        for (element in fixtures("paths.json").jsonArray) {
            val item = element.jsonObject
            val stub = Stub(WireResponse(204, body = byteArrayOf()))
            val http = DefaultHTTPClient.create("https://example.invalid/v1", stub).value()
            val result = http.request(HTTPMethod.GET, item.text("path")!!)
            val allowed = item.getValue("allowed").jsonPrimitive.boolean
            assertEquals(item.text("path"), if (allowed) 1 else 0, stub.requests.size)
            if (!allowed) assertEquals("http.invalid_request", (result as Outcome.Err).error.meta.code)
            else assertTrue(stub.requests.first().url.encodedPath.startsWith("/v1/"))
        }
        val stub = Stub(WireResponse(204, body = byteArrayOf()))
        val http = DefaultHTTPClient.create("https://example.invalid/v1", stub, headers = mapOf("X-Mode" to "common")).value()
        http.request(HTTPMethod.POST, "/records", RequestOptions(correlationId = "corr", query = listOf(QueryItem("tag", "a b"), QueryItem("tag", "c+")),
            headers = mapOf("x-mode" to "call"), body = buildJsonObject { put("name", "item") }, ifMatch = "v1", idempotencyKey = "once"))
        val request = stub.requests.single()
        assertEquals("/v1/records", request.url.encodedPath)
        assertEquals(listOf("a b", "c+"), request.url.queryParameterValues("tag"))
        assertEquals("call", request.headers["x-mode"])
        assertEquals("application/json, application/problem+json", request.headers["accept"])
        assertEquals("application/json", request.headers["content-type"])
        assertEquals("corr", request.headers["x-correlation-id"])
        assertEquals("v1", request.headers["if-match"])
        assertEquals("once", request.headers["idempotency-key"])
        assertEquals(buildJsonObject { put("name", "item") }, Json.parseToJsonElement(request.body!!.toString(Charsets.UTF_8)))
    }
    @Test fun rejectedConfigurationAndBodiesNeverSend() = runTest {
        val stub = Stub(WireResponse(204, body = byteArrayOf()))
        for (base in listOf("ftp://example.invalid", "https://user:pass@example.invalid", "https://example.invalid?v=1", "https://example.invalid/#x", "https://example.invalid/a/../b", " https://example.invalid", "https://example.invalid:0")) {
            assertEquals(base, "http.invalid_configuration", (DefaultHTTPClient.create(base, stub) as Outcome.Err).error.meta.code)
        }
        val http = DefaultHTTPClient.create("https://example.invalid", stub).value()
        for (options in listOf(RequestOptions(body = JsonObject(emptyMap())), RequestOptions(headers = mapOf("bad name" to "x")), RequestOptions(headers = mapOf("x-test" to "line\nnew")), RequestOptions(timeoutMilliseconds = -1))) assertTrue(http.request(HTTPMethod.GET, "health/live", options) is Outcome.Err)
        assertTrue(http.request(HTTPMethod.POST, "records", RequestOptions(body = JsonArray(emptyList()))) is Outcome.Err)
        assertEquals(0, stub.requests.size)
    }
    @Test fun sharedRetryAfterFixtures() {
        val fixture = fixtures("retry-after.json").jsonObject
        val now = fixture.getValue("nowMilliseconds").jsonPrimitive.long
        fixture.getValue("cases").jsonArray.forEach {
            val entry = it.jsonObject
            assertEquals(entry.text("value"), entry["expectedMs"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long, ProblemDecoder.retryAfter(entry.text("value"), now)?.milliseconds)
        }
    }
    @Test fun deadlineCancelsTransportAndCallerCancellationPropagates() = runTest {
        var cleaned = false
        val started = CompletableDeferred<Unit>()
        val stub = HTTPTransport {
            started.complete(Unit)
            try { delay(60_000); Outcome.Ok(WireResponse(204, body = byteArrayOf())) } finally { cleaned = true }
        }
        val http = DefaultHTTPClient.create("https://example.invalid", stub).value()
        val result = http.request(HTTPMethod.GET, "health/live", RequestOptions(timeoutMilliseconds = 20))
        assertEquals(FailureKind.TIMEOUT, (result as Outcome.Err).error.kind)
        assertTrue(cleaned)
        cleaned = false
        val request = async { http.request(HTTPMethod.GET, "health/live") }
        yield()
        request.cancelAndJoin()
        assertTrue(cleaned)
        assertTrue(request.isCancelled)
    }
    @Test fun zeroBudgetAndPreCanceledTaskNeverSend() = runTest {
        val stub = Stub(WireResponse(204, body = byteArrayOf()))
        val http = DefaultHTTPClient.create("https://example.invalid", stub).value()
        assertEquals(FailureKind.TIMEOUT, (http.request(HTTPMethod.GET, "health/live", RequestOptions(timeoutMilliseconds = 0)) as Outcome.Err).error.kind)
        val task = launch { currentCoroutineContext().cancel(); http.request(HTTPMethod.GET, "health/live"); fail("Cancellation became a value") }
        task.join()
        assertTrue(task.isCancelled)
        assertEquals(0, stub.requests.size)
    }
    @Test fun unexpectedExceptionKeepsIdentity() = runTest {
        class Defect(val marker: Any) : RuntimeException("programmer defect")
        val defect = Defect(Any())
        val http = DefaultHTTPClient.create("https://example.invalid", HTTPTransport { throw defect }).value()
        try { http.request(HTTPMethod.GET, "health/live"); fail("Defect classified") } catch (caught: Defect) { assertSame(defect, caught) }
    }
    @Test fun invalidUtf8IsNotEmptySuccess() = runTest {
        val http = DefaultHTTPClient.create("https://example.invalid", Stub(WireResponse(200, body = byteArrayOf(0xc3.toByte(), 0x28)))).value()
        assertEquals("http.invalid_response", (http.request(HTTPMethod.GET, "health/live") as Outcome.Err).error.meta.code)
    }
}
