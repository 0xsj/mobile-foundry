package dev.mobilefoundry.services
import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteNotesTest {
    private val commandId = "00000000-0000-4000-8000-000000000001"
    private val accountId = "00000000-0000-4000-8000-000000000002"
    private val principalId = "00000000-0000-4000-8000-000000000003"
    @Test fun sharedReceiptAdmissionAndSingleAttempt() = runBlocking {
        val command = (RemoteNoteWrite.create(commandId,"Café") as Outcome.Ok).value
        val rows = Json.parseToJsonElement(File(System.getProperty("foundry.remote.notes.fixtures"),"receipts.json").readText()).jsonArray
        for (row in rows) {
            val b = row.jsonObject; val requests = ArrayList<PreparedRequest>(); val headers = mutableMapOf("x-request-id" to "receipt-test")
            b["etag"].takeIf { it != JsonNull }?.jsonPrimitive?.content?.let { headers["etag"] = it }
            val transport = HTTPTransport { request -> requests += request; Outcome.Ok(WireResponse(b.getValue("status").jsonPrimitive.int,headers,b.getValue("body").toString().toByteArray())) }
            val client = (DefaultHTTPClient.create("https://example.invalid/v1",transport) as Outcome.Ok).value
            val answer = HTTPRemoteNotesRepository(client,accountId,principalId).execute(command)
            if (b.getValue("valid").jsonPrimitive.boolean) assertTrue((answer as Outcome.Ok).value.confirms(command))
            else { assertTrue(b.getValue("name").toString(),answer is Outcome.Err); val failure = (answer as Outcome.Err).error; assertEquals("notes.invalid_remote_response",failure.meta.code); assertEquals("receipt-test",failure.meta.requestId) }
            assertEquals(1,requests.size); val request = requests.single(); assertEquals(HTTPMethod.POST,request.method)
            assertEquals("/v1/accounts/$accountId/notes",request.url.encodedPath); assertEquals(commandId,request.headers["idempotency-key"]); assertNull(request.headers["if-match"])
        }
    }
    @Test fun commandAdmission() {
        assertTrue(RemoteNoteWrite.create(commandId,"🧱\u0000\n") is Outcome.Ok)
        for (id in listOf("AAAAAAAA-0000-4000-8000-000000000001","../path","00000000-0000-0000-0000-000000000000")) assertTrue(RemoteNoteWrite.create(id,"Idea") is Outcome.Err)
        for (version in listOf(0L,RemoteNotesAdmission.maxVersion)) assertTrue(RemoteNoteWrite.delete(commandId,accountId,version) is Outcome.Err)
        assertTrue(RemoteNoteWrite.create(commandId,"\uD800") is Outcome.Err)
    }
}
