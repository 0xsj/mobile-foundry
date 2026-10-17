package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NotificationInboxTest {
    private val account = "00000000-0000-4000-8000-000000000002"
    private val principal = "00000000-0000-4000-8000-000000000003"
    @Test fun sharedPageAdmissionAndSingleAttempt() = runBlocking {
        val rows = Json.parseToJsonElement(File(System.getProperty("foundry.inbox.fixtures"),"pages.json").readText()).jsonArray
        for (row in rows) {
            val b = row.jsonObject; val requests = ArrayList<PreparedRequest>()
            val transport = HTTPTransport { request -> requests += request; Outcome.Ok(WireResponse(200,mapOf("x-request-id" to "inbox-test"),b.getValue("body").toString().toByteArray())) }
            val client = (DefaultHTTPClient.create("https://example.invalid/v1",transport) as Outcome.Ok).value
            val answer = HTTPNotificationInboxRepository(client,account,principal).page(limit=2,unreadOnly=b.getValue("unread").jsonPrimitive.boolean)
            if (b.getValue("valid").jsonPrimitive.boolean) {
                val page = (answer as Outcome.Ok).value; assertEquals(1,page.items.size); assertEquals(123456000,page.items[0].occurredAt.nano)
            } else {
                assertTrue(b.getValue("name").toString(),answer is Outcome.Err)
                assertEquals("notifications.invalid_remote_response",(answer as Outcome.Err).error.meta.code); assertEquals("inbox-test",answer.error.meta.requestId)
            }
            assertEquals(1,requests.size); val request = requests.single()
            assertEquals(HTTPMethod.GET,request.method); assertEquals("/v1/accounts/$account/notifications",request.url.encodedPath)
            assertNull(request.headers["idempotency-key"]); assertNull(request.headers["if-match"])
        }
    }
    @Test fun mutationIntentAndScopeAdmission() = runBlocking {
        val requests = ArrayList<PreparedRequest>()
        val body = buildJsonObject { put("account_id",account);put("media_in_app",false) }
        val client = (DefaultHTTPClient.create("https://example.invalid/v1",HTTPTransport { requests += it; Outcome.Ok(WireResponse(200,emptyMap(),body.toString().toByteArray())) }) as Outcome.Ok).value
        val repo = HTTPNotificationInboxRepository(client,account,principal)
        assertFalse((repo.setMediaEnabled(false) as Outcome.Ok).value)
        assertTrue(repo.setMediaEnabled(true) is Outcome.Err)
        assertTrue(HTTPNotificationInboxRepository(client,"../escape",principal).page() is Outcome.Err)
        assertTrue(repo.page(cursor="") is Outcome.Err)
        assertEquals(2,requests.size)
        assertTrue(requests.all { it.method == HTTPMethod.PUT && it.headers["idempotency-key"] == null && it.headers["if-match"] == null })
    }
}
