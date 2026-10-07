package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HealthServiceTest {
    @Test fun sharedHealthShapesAndLiveEndpoint() = runTest {
        val cases = Json.parseToJsonElement(File(System.getProperty("foundry.http.fixtures"), "responses.json").readText()).jsonArray
        for (element in cases) {
            val item = element.jsonObject
            val healthy = item["expectedHealthy"]?.jsonPrimitive?.boolean ?: continue
            val paths = mutableListOf<String>()
            val transport = HTTPTransport { request ->
                paths += request.url.encodedPath
                Outcome.Ok(WireResponse(item.getValue("status").jsonPrimitive.int, item.getValue("headers").jsonObject.mapValues { it.value.jsonPrimitive.content },
                    item["body"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toByteArray()))
            }
            val http = (DefaultHTTPClient.create("https://example.invalid/v1", transport) as Outcome.Ok).value
            val result = HealthService(http).live()
            if (healthy) {
                val health = (result as Outcome.Ok).value
                assertEquals(HealthStatus.OK, health.status); assertEquals("\"v1\"", health.version); assertEquals("req-health", health.requestId)
            } else assertEquals("http.invalid_response", (result as Outcome.Err).error.meta.code)
            assertEquals(listOf("/v1/health/live"), paths)
        }
    }
    @Test fun readyPreservesFailureMetadata() = runTest {
        var path: String? = null
        val http = (DefaultHTTPClient.create("https://example.invalid/v1", HTTPTransport {
            path = it.url.encodedPath
            Outcome.Ok(WireResponse(503, mapOf("x-request-id" to "req"), "{\"detail\":\"Down\"}".toByteArray()))
        }) as Outcome.Ok).value
        val failure = (HealthService(http).ready(RequestOptions(correlationId = "corr")) as Outcome.Err).error
        assertEquals(FailureKind.UNAVAILABLE, failure.kind)
        assertEquals("req", failure.meta.requestId); assertEquals("corr", failure.meta.correlationId)
        assertEquals("/v1/health/ready", path)
    }
}
