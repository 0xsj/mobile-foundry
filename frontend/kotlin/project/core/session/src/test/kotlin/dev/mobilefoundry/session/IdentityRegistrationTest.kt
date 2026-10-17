package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IdentityRegistrationTest {
    @Test fun sharedAdmissionPasswordPreservationAndRedaction() {
        val rows = Json.parseToJsonElement(File(System.getProperty("native.registration.fixtures"), "inputs.json").readText()).jsonArray
        for (row in rows) {
            val r = row.jsonObject; val input = r.getValue("input").jsonObject
            val answer = RegisterIdentity.make(input.getValue("login").jsonPrimitive.content, input.getValue("display_name").jsonPrimitive.content, input.getValue("password").jsonPrimitive.content)
            if (r.getValue("valid").jsonPrimitive.boolean) {
                val command = (answer as Outcome.Ok).value; val expected = r.getValue("expected").jsonObject
                assertEquals(expected.getValue("login").jsonPrimitive.content, command.login)
                assertEquals(expected.getValue("display_name").jsonPrimitive.content, command.displayName)
                assertEquals(input.getValue("password").jsonPrimitive.content, command.password)
                assertEquals("RegisterIdentity(<redacted>)", command.toString())
            } else assertTrue(r.getValue("name").toString(), answer is Outcome.Err)
        }
        for (bad in listOf("\uD800private secret", "\uDC00private secret")) assertTrue(RegisterIdentity.make("alex", "Alex", bad) is Outcome.Err)
        assertNotNull(RegisteredIdentity.make("memory.member", "alex", "Alex"))
        assertEquals("RegisteredIdentity(<redacted>)", RegisteredIdentity.make(ID, "alex", "Alex").toString())
        assertNull(RegisteredIdentity.make(ID, "alex\r", "Alex"))
    }
    @Test fun onePublicAttemptWithoutIdempotencyOrSignIn() = runBlocking {
        val command = (RegisterIdentity.make(" ALEX ", " Alex ", "private passphrase") as Outcome.Ok).value
        val client = RegistrationClient(body())
        assertTrue((HTTPIdentityRegistration(client).register(command) as Outcome.Ok).value.confirms(command))
        assertEquals(1, client.calls); assertEquals(HTTPMethod.POST, client.method); assertEquals("identity/register", client.path)
        assertTrue(client.options!!.headers.isEmpty()); assertNull(client.options!!.idempotencyKey)
        assertEquals(buildJsonObject { put("login", "alex"); put("display_name", "Alex"); put("password", "private passphrase") }, client.options!!.body)
        val failed = RegistrationClient(failure = Failure.Timeout(FailureMeta("Lost reply")))
        assertEquals(FailureKind.TIMEOUT, (HTTPIdentityRegistration(failed).register(command) as Outcome.Err).error.kind); assertEquals(1, failed.calls)
    }
    @Test fun refuseUnexpectedStatusFieldsAndDifferentInput() = runBlocking {
        val command = (RegisterIdentity.make("alex", "Cafe\u0301", "private passphrase") as Outcome.Ok).value
        val good = body(name = command.displayName)
        for ((status, b) in listOf(200 to good, 201 to body(name = "Café"), 201 to body(login = "sam", name = command.displayName),
            201 to JsonObject(good + ("id" to JsonPrimitive("bad"))), 201 to JsonObject(good + ("access_token" to JsonPrimitive("not-a-grant"))))) {
            val client = RegistrationClient(b, status)
            val failure = (HTTPIdentityRegistration(client).register(command) as Outcome.Err).error
            assertEquals(FailureKind.INTERNAL, failure.kind); assertEquals("owned-request", failure.meta.requestId); assertEquals(1, client.calls)
        }
    }
    @Test fun canceledWorkNeverDispatchesAndRemoteHTTPRefuses() = runBlocking {
        val command = (RegisterIdentity.make("alex", "Alex", "private passphrase") as Outcome.Ok).value
        val client = RegistrationClient(body())
        val task = launch(start = CoroutineStart.LAZY) { HTTPIdentityRegistration(client).register(command) }; task.cancelAndJoin()
        assertEquals(0, client.calls)
        assertTrue(HTTPIdentityRegistration.create("http://example.com/v1", OkHttpTransport(), true) is Outcome.Err)
    }
    companion object {
        private const val ID = "00000000-0000-4000-8000-000000000001"
        private fun body(login: String = "alex", name: String = "Alex") = buildJsonObject { put("id", ID); put("login", login); put("display_name", name) }
    }
}
private class RegistrationClient(private val body: JsonElement = JsonNull, private val status: Int = 201, private val failure: Failure? = null) : HTTPClient {
    var calls = 0; var method: HTTPMethod? = null; var path: String? = null; var options: RequestOptions? = null
    override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
        calls++; this.method = method; this.path = path; this.options = options
        return failure?.let { Outcome.Err(it) } ?: Outcome.Ok(Entity(body, ResponseMetadata(status, null, "owned-request", null)))
    }
}
