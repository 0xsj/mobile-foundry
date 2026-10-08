package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CreateNoteTest {
    private fun fixture(name: String) = Json.parseToJsonElement(File(System.getProperty("foundry.notes.fixtures"), "$name.json").readText()).jsonArray

    @Test fun sharedCodePointAdmissionPreservesInput() {
        for (entry in fixture("create-titles")) {
            val row = entry.jsonObject
            val title = row.getValue("title").jsonPrimitive.content
            val error = row.getValue("error").jsonPrimitive.contentOrNull
            val result = CreateNote.make(title)
            if (error == null) assertEquals(title, (result as Outcome.Ok).value.title)
            else {
                val failure = (result as Outcome.Err).error as Failure.Invalid
                assertEquals("notes.invalid_title", failure.meta.code)
                assertEquals("Check the highlighted field.", failure.meta.message)
                assertEquals(mapOf("title" to error), failure.fields)
            }
        }
    }

    @Test fun postBodyAndResponseAdmissionUseSharedCases() = runTest {
        val command = (CreateNote.make("  Idea  ") as Outcome.Ok).value
        for (entry in fixture("create-responses")) {
            val row = entry.jsonObject
            val requests = mutableListOf<PreparedRequest>()
            val http = (DefaultHTTPClient.create("https://example.invalid/v1", HTTPTransport { request ->
                requests.add(request)
                Outcome.Ok(WireResponse(row.getValue("status").jsonPrimitive.int,
                    mapOf("x-request-id" to "create-req", "x-correlation-id" to "create-corr"),
                    row.getValue("body").jsonPrimitive.content.toByteArray()))
            }) as Outcome.Ok).value
            val result = HTTPNoteCreator(http).create(command)
            if ("expected" in row) {
                val expected = row.getValue("expected").jsonObject
                assertEquals(Note(expected.getValue("id").jsonPrimitive.content, expected.getValue("title").jsonPrimitive.content), (result as Outcome.Ok).value)
            } else {
                val failure = (result as Outcome.Err).error
                assertEquals(row.getValue("kind").jsonPrimitive.content, failure.kind.value)
                assertEquals(row["code"]?.jsonPrimitive?.content, failure.meta.code)
                assertEquals("create-req", failure.meta.requestId)
                assertEquals("create-corr", failure.meta.correlationId)
                row["fields"]?.let { fields ->
                    assertEquals(fields.jsonObject.mapValues { it.value.jsonPrimitive.content }, (failure as Failure.Invalid).fields)
                }
            }
            val request = requests.single()
            assertEquals(HTTPMethod.POST, request.method)
            assertEquals("/v1/notes", request.url.encodedPath)
            assertNull(request.headers["idempotency-key"])
            assertEquals(buildJsonObject { put("title", command.title) }, Json.parseToJsonElement(checkNotNull(request.body).toString(Charsets.UTF_8)))
        }
    }

    @Test fun memoryCreatesUniqueNotesAndOwnsSnapshots() = runTest {
        val memory = InMemoryNoteCreator()
        val command = (CreateNote.make("Idea") as Outcome.Ok).value
        val before = (memory.list() as Outcome.Ok).value
        val pair = List(10) { async(Dispatchers.Default) { (memory.create(command) as Outcome.Ok).value } }.awaitAll()
        assertEquals(10, pair.map { it.id }.distinct().size)
        val snapshot = (memory.list() as Outcome.Ok).value
        assertEquals(10, snapshot.size)
        assertTrue(before.isEmpty())
        try { (snapshot as MutableList<Note>).clear(); fail("Mutable snapshot") } catch (_: UnsupportedOperationException) {}
        assertEquals(10, (memory.list() as Outcome.Ok).value.size)
    }

    @Test fun adaptersPreserveDefectsAndAlreadyCanceledCallsDoNotCommit() = runTest {
        val command = (CreateNote.make("Idea") as Outcome.Ok).value
        val defect = IllegalStateException("Private detail")
        val client = object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> = throw defect
        }
        try { HTTPNoteCreator(client).create(command); fail("Defect swallowed") } catch (error: IllegalStateException) { assertSame(defect, error) }
        val memory = InMemoryNoteCreator()
        for (creator in listOf<NoteCreator>(memory, HTTPNoteCreator(client))) {
            var canceled = false
            launch {
                cancel()
                try { creator.create(command); fail("Cancellation admitted") } catch (_: CancellationException) { canceled = true }
            }.join()
            assertTrue(canceled)
        }
        assertTrue((memory.list() as Outcome.Ok).value.isEmpty())
    }
}
