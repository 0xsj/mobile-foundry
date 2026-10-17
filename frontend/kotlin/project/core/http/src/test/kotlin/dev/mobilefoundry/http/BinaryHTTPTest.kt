package dev.mobilefoundry.http
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.JsonObject
class BinaryHTTPTest {
    @Test fun bytesAndAmbiguousBodies() = runTest {
        val bytes = byteArrayOf(0, -1, 1, -128); val requests = mutableListOf<PreparedRequest>()
        val client = DefaultHTTPClient.create("https://example.invalid", HTTPTransport { requests += it; Outcome.Ok(WireResponse(200, mapOf("content-type" to "image/png"), byteArrayOf(0, -1, 1, -128))) }).value()
        val body = BinaryBody(bytes, "image/png"); bytes[0] = 10
        val result = client.requestBytes(HTTPMethod.PUT, "files/content", RequestOptions(binaryBody = body)).value()
        assertArrayEquals(byteArrayOf(0, -1, 1, -128), result.bytes); assertEquals("image/png", requests.single().headers["content-type"])
        assertTrue(client.requestEntity(HTTPMethod.PUT, "files/content", RequestOptions(body = JsonObject(emptyMap()), binaryBody = body)) is Outcome.Err)
        assertTrue(client.requestEntity(HTTPMethod.GET, "files/content", RequestOptions(binaryBody = body)) is Outcome.Err)
        assertEquals(1, requests.size)
        val denied = DefaultHTTPClient.create("https://example.invalid", HTTPTransport { Outcome.Ok(WireResponse(403, body = """{"type":"urn:problem:forbidden","status":403,"title":"Forbidden","kind":"forbidden","code":"files.account_forbidden"}""".toByteArray())) }).value()
        assertEquals(FailureKind.FORBIDDEN, (denied.requestBytes(HTTPMethod.GET, "files/content") as Outcome.Err).error.kind)
    }
}
