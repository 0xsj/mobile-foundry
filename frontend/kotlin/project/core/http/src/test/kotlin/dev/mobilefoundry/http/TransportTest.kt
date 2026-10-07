package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class TransportTest {
    @Test fun nativeSuccessAndRedirectIsNotFollowed() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("{\"status\":\"ok\"}").addHeader("x-request-id", "native-id").build())
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/elsewhere")).build())
            val http = DefaultHTTPClient.create(server.url("/v1/").toString(), OkHttpTransport()).value()
            val entity = http.requestEntity(HTTPMethod.GET, "health/live").value()
            assertEquals("native-id", entity.metadata.requestId)
            assertEquals("/v1/health/live", server.takeRequest(1, TimeUnit.SECONDS)!!.url.encodedPath)
            val redirected = http.request(HTTPMethod.GET, "health/ready")
            assertEquals(FailureKind.INTERNAL, (redirected as Outcome.Err).error.kind)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun deadlineDuringBodyReadCancelsNativeCall() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("{\"status\":\"ok\"}").bodyDelay(1, TimeUnit.SECONDS).build())
            val canceled = CountDownLatch(1)
            val native = OkHttpClient.Builder().eventListener(object : EventListener() { override fun canceled(call: Call) { canceled.countDown() } }).build()
            val http = DefaultHTTPClient.create(server.url("/v1/").toString(), OkHttpTransport(native)).value()
            val result = http.request(HTTPMethod.GET, "health/live", RequestOptions(timeoutMilliseconds = 150))
            assertEquals(FailureKind.TIMEOUT, (result as Outcome.Err).error.kind)
            assertTrue(canceled.await(1, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun callerCancellationCancelsNativeCall() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("{\"status\":\"ok\"}").bodyDelay(1, TimeUnit.SECONDS).build())
            val canceled = CountDownLatch(1)
            val native = OkHttpClient.Builder().eventListener(object : EventListener() { override fun canceled(call: Call) { canceled.countDown() } }).build()
            val http = DefaultHTTPClient.create(server.url("/v1/").toString(), OkHttpTransport(native)).value()
            val request = async(Dispatchers.Default) { http.request(HTTPMethod.GET, "health/live") }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(canceled.await(1, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun brokenErrorBodyPreservesStatus() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(503).body("x").setHeader("Content-Length", "10").addHeader("x-request-id", "req").build())
            val native = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build()
            val http = DefaultHTTPClient.create(server.url("/v1/").toString(), OkHttpTransport(native)).value()
            val failure = (http.request(HTTPMethod.GET, "health/live") as Outcome.Err).error
            assertEquals(FailureKind.UNAVAILABLE, failure.kind)
            assertEquals("req", failure.meta.requestId)
            assertEquals(1, server.requestCount)
        }
    }
}
