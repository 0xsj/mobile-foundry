package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AccountManagementTest {
    @Test fun sharedNameAdmissionAndScalarLimits() {
        for (row in Json.parseToJsonElement(File(System.getProperty("account.names.fixtures"), "names.json").readText()).jsonArray) {
            val r = row.jsonObject; val answer = AccountName.make(r.getValue("input").jsonPrimitive.content)
            if (r.getValue("valid").jsonPrimitive.boolean) assertEquals(r.getValue("canonical").jsonPrimitive.content, (answer as Outcome.Ok).value.value)
            else assertTrue(answer is Outcome.Err)
        }
        assertTrue(GoSessionWire.name("\u200BProject\u200B")); assertFalse(GoSessionWire.name(" Project "))
        assertEquals("🧱", (AccountName.make("\u0085 🧱 \u0085") as Outcome.Ok).value.value)
        assertTrue(AccountName.make("🧱".repeat(80)) is Outcome.Ok); assertTrue(AccountName.make("🧱".repeat(81)) is Outcome.Err)
        assertTrue(AccountName.make("bad\uD800name") is Outcome.Err)
        assertEquals("AccountName(<redacted>)", (AccountName.make("Private project") as Outcome.Ok).value.toString())
    }
    @Test fun oneAttemptStrongVersionAndNoReplayKey() = runBlocking {
        val name = (AccountName.make(" Cafe\u0301 ") as Outcome.Ok).value; val client = WorkspaceClient(); val service = HTTPAccountManagement(client, OWNER)
        val before = (service.read(ID) as Outcome.Ok).value
        client.body = body(name.value, 2); client.etag = "\"2\""
        assertEquals(2L, (service.rename(before, name) as Outcome.Ok).value.version)
        assertEquals(mapOf("If-Match" to "\"1\""), client.options!!.headers); assertNull(client.options!!.idempotencyKey)
        assertEquals(HTTPMethod.PATCH, client.method); assertEquals("accounts/$ID", client.path); assertEquals(2, client.calls)
        client.body = body(name.value); client.status = 201; client.etag = "\"1\""
        assertTrue(service.create(name) is Outcome.Ok); assertTrue(client.options!!.headers.isEmpty()); assertEquals("accounts", client.path)
        client.failure = Failure.Timeout(FailureMeta("Lost reply"))
        assertEquals(FailureKind.TIMEOUT, (service.create(name) as Outcome.Err).error.kind); assertEquals(4, client.calls)
    }
    @Test fun refuseForeignOwnerETagVersionTargetAndNormalizationChanges() = runBlocking {
        val name = (AccountName.make("Cafe\u0301") as Outcome.Ok).value
        val before = ManagedAccount.make(ID, OWNER, "Personal", 1, 1_767_225_600_000_000, 1_767_225_600_000_000)!!
        for ((b, tag) in listOf(body(name.value, 2) to "W/\"2\"", body(name.value, 3) to "\"3\"", body("Café", 2) to "\"2\"", body(name.value, 2, id = OWNER) to "\"2\"", body(name.value, 2, owner = ID) to "\"2\"")) {
            val client = WorkspaceClient().apply { body = b; etag = tag }
            assertTrue(HTTPAccountManagement(client, OWNER).rename(before, name) is Outcome.Err); assertEquals(1, client.calls)
        }
        val client = WorkspaceClient(); var entered = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel(); entered = true
            HTTPAccountManagement(client, OWNER).create(name)
        }
        job.join(); assertTrue(entered); assertEquals(0, client.calls)
    }
    companion object {
        private const val ID = "00000000-0000-4000-8000-000000000001"
        private const val OWNER = "00000000-0000-4000-8000-000000000002"
        private fun body(name: String = "Personal", version: Long = 1, id: String = ID, owner: String = OWNER) = buildJsonObject {
            put("id", id); put("owner_id", owner); put("name", name); put("version", version); put("created_at", "2026-01-01T00:00:00Z"); put("updated_at", "2026-01-01T00:00:00Z")
        }
    }
    private class WorkspaceClient : HTTPClient {
        var body: JsonElement = body(); var status = 200; var etag = "\"1\""; var failure: Failure? = null
        var calls = 0; var options: RequestOptions? = null; var method: HTTPMethod? = null; var path: String? = null
        override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
            calls++; this.options = options; this.method = method; this.path = path
            return failure?.let { Outcome.Err(it) } ?: Outcome.Ok(Entity(body, ResponseMetadata(status, etag, null, null)))
        }
    }
}
