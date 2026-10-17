package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test

private class TokenAuthority(private val token: String) : SessionAuthority {
    override val id = "catalog-demo.v1"
    private fun grant() = SessionGrant.make(id, Principal.make("alex", "Alex")!!, Account.make("alex-personal", "Alex")!!, token, "refresh.secret", 1_700_000_060_000)!!
    override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = Outcome.Ok(grant())
    override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = Outcome.Ok(grant())
    override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = Outcome.Ok(Unit)
}
private class RecordingTransport(private val status: Int = 200) : HTTPTransport {
    val requests = mutableListOf<PreparedRequest>()
    override suspend fun send(request: PreparedRequest): AppResult<WireResponse> {
        requests.add(request)
        return Outcome.Ok(WireResponse(status, mapOf("content-type" to if (status == 401) "application/problem+json" else "application/json", "x-request-id" to "one"),
            (if (status == 401) """{"type":"about:blank","status":401,"title":"Unauthorized"}""" else """{"ok":true}""").toByteArray()))
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedHTTPTest {
    private suspend fun TestScope.signedIn(token: String = "access.secret"): Triple<SessionController, MemoryCredentialVault, ManualSessionClock> {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = MemoryCredentialVault()
        val session = SessionController(this, TokenAuthority(token), vault, clock::now) { throw AssertionError(it) }
        session.signIn("alex", "ok")!!.join(); return Triple(session, vault, clock)
    }
    private fun client(session: SessionController, transport: HTTPTransport, lease: SessionLease = session.lease!!) =
        (AuthenticatedHTTPClient.create("https://example.test/api", transport, session, lease) as Outcome.Ok).value
    @Test fun sharedBearerFixturesAndCallerHeaderAdmissionAvoidIO() = runTest {
        val rows = Json.parseToJsonElement(File(System.getProperty("account.fixtures"), "bearer.json").readText()).jsonArray
        rows.forEach { element ->
            val row = element.jsonObject; val valid = row.getValue("valid").jsonPrimitive.boolean
            val (session, _, _) = signedIn(row.getValue("token").jsonPrimitive.content); val transport = RecordingTransport(); val client = client(session, transport)
            assertEquals(valid, client.requestEntity(HTTPMethod.GET, "notes") is Outcome.Ok)
            assertEquals(if (valid) 1 else 0, transport.requests.size)
            assertTrue(client.requestEntity(HTTPMethod.GET, "notes", RequestOptions(headers = mapOf("AuThOrIzAtIoN" to "other"))) is Outcome.Err)
            assertEquals(if (valid) 1 else 0, transport.requests.size)
        }
    }
    @Test fun ownsAuthorizationPreservesOptionsAnd401DoesNotRenewOrClear() = runTest {
        val (session, vault, _) = signedIn(); val transport = RecordingTransport(401); val lease = session.lease!!; val saved = vault.load()
        val answer = client(session, transport).requestEntity(HTTPMethod.PATCH, "notes/one", RequestOptions(500, "trace", listOf(QueryItem("tag", "one"), QueryItem("tag", "two")),
            mapOf("x-custom" to "yes"), JsonObject(mapOf("title" to JsonPrimitive("new"))), "version-1", "command-1"))
        assertTrue((answer as Outcome.Err).error is Failure.Unauthenticated); assertEquals(lease, session.lease); assertEquals(saved, vault.load())
        assertEquals(1, transport.requests.size); val request = transport.requests.single()
        assertEquals(HTTPMethod.PATCH, request.method); assertEquals("/api/notes/one", request.url.encodedPath); assertEquals("tag=one&tag=two", request.url.encodedQuery)
        assertEquals("Bearer access.secret", request.headers["authorization"]); assertEquals("version-1", request.headers["if-match"])
        assertEquals("command-1", request.headers["idempotency-key"]); assertEquals("trace", request.headers["x-correlation-id"]); assertEquals("yes", request.headers["x-custom"])
        assertEquals("new", Json.parseToJsonElement(String(request.body!!)).jsonObject.getValue("title").jsonPrimitive.content)
    }
    @Test fun originAndRelativePathAdmission() = runTest {
        val (session, _, _) = signedIn(); val transport = RecordingTransport(); val lease = session.lease!!
        listOf("http://example.test", "http://localhost", "ftp://localhost").forEach { assertTrue(AuthenticatedHTTPClient.create(it, transport, session, lease) is Outcome.Err) }
        assertTrue(AuthenticatedHTTPClient.create("http://example.test", transport, session, lease, allowLocalHTTP = true) is Outcome.Err)
        listOf("http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080").forEach { assertTrue(AuthenticatedHTTPClient.create(it, transport, session, lease, allowLocalHTTP = true) is Outcome.Ok) }
        val client = client(session, transport)
        listOf("https://other.test", "//other.test", "../other").forEach { assertTrue(client.requestEntity(HTTPMethod.GET, it) is Outcome.Err) }
        assertTrue(transport.requests.isEmpty())
    }
    @Test fun expiryRenewalAndSignOutRejectOldClientsWithoutIO() = runTest {
        val (session, _, clock) = signedIn(); val transport = RecordingTransport(); val old = client(session, transport)
        session.renew()!!.join()
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), old.requestEntity(HTTPMethod.GET, "notes"))
        val fresh = client(session, transport); clock.advance(60_000)
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), fresh.requestEntity(HTTPMethod.GET, "notes")); session.signOut()!!.join()
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), fresh.requestEntity(HTTPMethod.GET, "notes")); assertTrue(transport.requests.isEmpty())
    }
    @Test fun closedAccountSuppressesLateSuccessFailureAndDefect() = runTest {
        for (result in 0..2) {
            val (session, _, _) = signedIn(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val transport = HTTPTransport { withContext(NonCancellable) {
                entered.complete(Unit); release.await()
                when (result) { 0 -> Outcome.Ok(WireResponse(200, body = "{}".toByteArray())); 1 -> Outcome.Err(Failure.Unavailable(FailureMeta("Old account error"))); else -> error("Old account defect") }
            } }
            val client = client(session, transport); val task = async { client.requestEntity(HTTPMethod.GET, "notes") }; entered.await()
            session.signOut()!!.join(); release.complete(Unit)
            assertEquals(Outcome.Err(SessionFailures.scopeChanged), task.await())
        }
    }
    @Test fun cancellationRemainsNativeAfterIgnoringTransport() = runTest {
        val (session, _, _) = signedIn(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val transport = HTTPTransport { withContext(NonCancellable) { entered.complete(Unit); release.await(); Outcome.Ok(WireResponse(200, body = "{}".toByteArray())) } }
        val client = client(session, transport); val task = async { client.requestEntity(HTTPMethod.GET, "notes") }; entered.await()
        task.cancel(); release.complete(Unit); task.join(); assertTrue(task.isCancelled)
    }
    @Test fun realOkHttpAttemptCarriesBearerAndDoesNotFollowRedirect() = runBlocking {
        val clock = ManualSessionClock(1_700_000_000_000)
        val session = SessionController(this, TokenAuthority("access.secret"), MemoryCredentialVault(), clock::now) { throw AssertionError(it) }
        session.signIn("alex", "ok")!!.join()
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://other.test/notes").build())
            server.start()
            val transport = OkHttpTransport()
            val client = (AuthenticatedHTTPClient.create(server.url("/api").toString(), transport, session, session.lease!!, allowLocalHTTP = true) as Outcome.Ok).value
            assertTrue(client.requestEntity(HTTPMethod.GET, "notes") is Outcome.Err)
            val request = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)!!; assertEquals("Bearer access.secret", request.headers["authorization"]); assertEquals("/api/notes", request.url.encodedPath); assertEquals(1, server.requestCount)
        }
    }
    @Test fun binaryRequestsKeepLeaseAndAuthorizationOwnership() = runTest {
        val (session, _, _) = signedIn(); val transport = RecordingTransport(); val http = client(session, transport)
        val bytes = byteArrayOf(0, -1, -128)
        http.requestEntity(HTTPMethod.PUT, "files/content", RequestOptions(binaryBody = BinaryBody(bytes, "image/png")))
        assertArrayEquals(bytes, transport.requests.single().body)
        assertEquals("Bearer access.secret", transport.requests.single().headers["authorization"])
        session.renew()!!.join()
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), http.requestBytes(HTTPMethod.GET, "files/content"))
        assertEquals(1, transport.requests.size); session.shutdown()
    }

}
