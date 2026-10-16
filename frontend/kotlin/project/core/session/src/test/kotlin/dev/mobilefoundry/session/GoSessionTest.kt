package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.GoSessionWire.string
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private fun goFixtures() = Json.parseToJsonElement(File(System.getProperty("go.session.fixtures"), "responses.json").readText()) as JsonObject
private fun wire(body: JsonElement = JsonNull, status: Int = 200, etag: String? = null) = WireResponse(status, etag?.let { mapOf("etag" to it) } ?: emptyMap(), body.toString().toByteArray())
private fun changed(body: JsonElement, key: String, value: JsonElement) = JsonObject((body as JsonObject).toMutableMap().apply { put(key, value) })
private fun rotation(f: JsonObject, letter: String, version: Int) = JsonObject((f["grant"] as JsonObject).toMutableMap().apply {
    put("access_token", JsonPrimitive("a1." + letter.repeat(42) + "A")); put("refresh_token", JsonPrimitive("r1." + letter.repeat(42) + "A")); put("session_version", JsonPrimitive(version))
})
private class GoTransport(replies: List<WireResponse> = emptyList(), val beforeReply: suspend (PreparedRequest) -> Unit = {}) : HTTPTransport {
    val requests = mutableListOf<PreparedRequest>()
    private val replies = replies.toMutableList()
    override suspend fun send(request: PreparedRequest): AppResult<WireResponse> {
        requests += request; beforeReply(request)
        check(replies.isNotEmpty()) { "Unexpected network attempt" }
        return Outcome.Ok(replies.removeAt(0))
    }
}
private class GoVault(var credential: StoredCredential? = null) : CredentialVault {
    var saves = 0
    var failSave = false
    override suspend fun load(): AppResult<StoredCredential?> = Outcome.Ok(credential)
    override suspend fun save(credential: StoredCredential): AppResult<Unit> {
        saves++
        if (failSave) return Outcome.Err(SessionFailures.storage)
        this.credential = credential; return Outcome.Ok(Unit)
    }
    override suspend fun clear(): AppResult<Unit> { credential = null; return Outcome.Ok(Unit) }
}

class GoSessionTest {
    @Test fun sharedConfigurationAndResponseAdmission() = runTest {
        val f = goFixtures()
        val configurations = Json.parseToJsonElement(File(System.getProperty("go.session.fixtures"), "configuration.json").readText()) as JsonArray
        val transport = GoTransport()
        for (row in configurations.map { it as JsonObject }) {
            val result = GoSessionAuthority.create(row.string("input")!!, transport, allowLocalHTTP = row["local"]!!.jsonPrimitive.boolean)
            when (result) {
                is Outcome.Ok -> { assertTrue(row["valid"]!!.jsonPrimitive.boolean); assertEquals(row.string("canonical"), result.value.baseUrl); assertEquals(row.string("authority"), result.value.id) }
                is Outcome.Err -> assertFalse(row["valid"]!!.jsonPrimitive.boolean)
            }
        }
        assertTrue(transport.requests.isEmpty())
        for (row in f["cases"]!!.jsonArray.map { it.jsonObject }) {
            val body = row["body"]!!
            val valid = if (row.string("kind") == "grant") GoSessionWire.tokens(Entity(body, ResponseMetadata(row["status"]!!.jsonPrimitive.int, null, null, null)), f["nowMillis"]!!.jsonPrimitive.long) != null
                else GoSessionWire.account(body, f.string("ownerID")!!) != null
            assertEquals(row.string("name"), row["valid"]!!.jsonPrimitive.boolean, valid)
        }
    }
    @Test fun rotatedCheckpointSurvivesAccountFailureSelectionAndRestoration() = runTest {
        val f = goFixtures(); val vault = GoVault()
        val second = changed(f["account"]!!, "id", JsonPrimitive("00000000-0000-4000-8000-000000000102"))
        val r2 = rotation(f, "C", 2); val r3 = rotation(f, "D", 3); val r4 = rotation(f, "E", 4)
        val transport = GoTransport(listOf(wire(f["grant"]!!), wire(f["account"]!!, 201, "\"1\""), wire(r2),
            wire(buildJsonObject { put("kind", "unavailable"); put("detail", "Account temporarily unavailable") }, 503), wire(r3), wire(f["account"]!!, etag = "\"1\""),
            wire(second, etag = "\"1\""), wire(r4), wire(second, etag = "\"1\""), wire(status = 204))) { request ->
            if (request.method == HTTPMethod.GET) {
                val saved = vault.credential!!
                assertEquals("Bearer a1." + saved.refreshToken.drop(3), request.headers["authorization"])
            }
        }
        val now = { f["nowMillis"]!!.jsonPrimitive.long }
        val authority = (GoSessionAuthority.create("https://api.example.test/v1", transport, now = now) as Outcome.Ok).value
        val controller = SessionController(this, authority, vault, now) { fail("Unexpected error") }
        controller.signIn("alex", "a test passphrase")!!.join()
        val prior = controller.lease!!
        controller.renew()!!.join()
        assertNull(controller.lease); assertEquals(FailureKind.UNAVAILABLE, controller.state.value.failure?.kind)
        assertEquals(r2.string("refresh_token"), vault.credential!!.refreshToken)
        controller.renew()!!.join()
        assertEquals(SessionPhase.SIGNED_IN, controller.state.value.phase); assertFalse(controller.isCurrent(prior))
        assertEquals(r2["refresh_token"], Json.parseToJsonElement(transport.requests[4].body!!.toString(Charsets.UTF_8)).jsonObject["refresh_token"])
        val personal = controller.lease!!
        controller.selectAccount("00000000-0000-4000-8000-000000000102")!!.join()
        assertFalse(controller.isCurrent(personal)); assertTrue(controller.lease!!.scope.accountId.endsWith("102"))
        controller.shutdown()
        val restored = SessionController(this, authority, vault, now) { fail("Unexpected error") }
        restored.restore()!!.join()
        assertEquals(SessionPhase.SIGNED_IN, restored.state.value.phase); assertTrue(restored.lease!!.scope.accountId.endsWith("102"))
        assertEquals(5, vault.saves)
        restored.signOut()!!.join()
        assertNull(vault.credential); assertEquals(SessionPhase.SIGNED_OUT, restored.state.value.phase)
    }
    @Test fun failedCheckpointStopsIOAndRecoveryUsesKnownCurrentCredential() = runTest {
        val f = goFixtures(); val now = { f["nowMillis"]!!.jsonPrimitive.long }
        val transport = GoTransport(listOf(wire(rotation(f, "C", 2)), wire(rotation(f, "D", 3)), wire(f["account"]!!, etag = "\"1\"")))
        val authority = (GoSessionAuthority.create("https://api.example.test/v1", transport, now = now) as Outcome.Ok).value
        val scope = SessionScope.make(authority.id, f.string("ownerID")!!, f["account"]!!.jsonObject.string("id")!!)!!
        val vault = GoVault(StoredCredential.make(scope, f["grant"]!!.jsonObject.string("refresh_token")!!)!!).apply { failSave = true }
        val controller = SessionController(this, authority, vault, now) { fail("Unexpected error") }
        controller.restore()!!.join()
        assertNull(controller.lease); assertEquals(SessionFailures.storage, controller.state.value.failure); assertEquals(1, transport.requests.size)
        controller.restore()!!.join(); assertEquals(1, transport.requests.size)
        vault.failSave = false; controller.restore()!!.join()
        assertEquals(SessionPhase.SIGNED_IN, controller.state.value.phase)
        assertEquals(rotation(f, "C", 2)["refresh_token"], Json.parseToJsonElement(transport.requests[1].body!!.toString(Charsets.UTF_8)).jsonObject["refresh_token"])
    }
    @Test fun issuerAndPrincipalMismatchCannotRetargetSavedAccount() = runTest {
        val f = goFixtures(); val now = { f["nowMillis"]!!.jsonPrimitive.long }
        val bad = changed(f["grant"]!!, "principal", buildJsonObject { put("id", "00000000-0000-4000-8000-000000000002"); put("login", "sam"); put("display_name", "Sam") })
        val transport = GoTransport(listOf(wire(bad)))
        val authority = (GoSessionAuthority.create("https://api.example.test/v1", transport, now = now) as Outcome.Ok).value
        val other = (GoSessionAuthority.create("https://other.example.test/v1", transport, now = now) as Outcome.Ok).value
        val scope = SessionScope.make(other.id, f.string("ownerID")!!, f["account"]!!.jsonObject.string("id")!!)!!
        val vault = GoVault(StoredCredential.make(scope, f["grant"]!!.jsonObject.string("refresh_token")!!)!!)
        val controller = SessionController(this, authority, vault, now) { fail("Unexpected error") }
        controller.restore()!!.join(); assertTrue(transport.requests.isEmpty()); assertNull(vault.credential)
        vault.credential = StoredCredential.make(SessionScope.make(authority.id, scope.principalId, scope.accountId)!!, f["grant"]!!.jsonObject.string("refresh_token")!!)!!
        controller.restore()!!.join(); assertNull(controller.lease); assertNull(vault.credential); assertEquals(1, transport.requests.size)
    }
    @Test fun directoryRejectsForeignAndDuplicateRowsAndForwardsCursor() = runTest {
        val f = goFixtures()
        val pages = listOf(
            buildJsonObject { put("accounts", JsonArray(listOf(f["account"]!!))); put("next_cursor", "opaque") },
            buildJsonObject { put("accounts", JsonArray(listOf(changed(f["account"]!!, "owner_id", JsonPrimitive("00000000-0000-4000-8000-000000000002"))))); put("next_cursor", JsonNull) },
            buildJsonObject { put("accounts", JsonArray(listOf(f["account"]!!, f["account"]!!))); put("next_cursor", JsonNull) })
        val transport = GoTransport(pages.map { wire(it) })
        val client = (DefaultHTTPClient.create("https://api.example.test/v1", transport) as Outcome.Ok).value
        val directory = HTTPAccountDirectory(client, f.string("ownerID")!!)
        assertEquals("opaque", (directory.list(limit = 1) as Outcome.Ok).value.nextCursor)
        repeat(2) { assertTrue(directory.list("opaque", 2) is Outcome.Err) }
        assertEquals("opaque", transport.requests[1].url.queryParameter("cursor"))
    }
}
