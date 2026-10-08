package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NotesServiceTest {
    @Test fun sharedAdmissionAndMemoryParity() = runTest {
        val fixtures = Json.parseToJsonElement(File(System.getProperty("foundry.notes.fixtures"), "responses.json").readText()).jsonArray
        for (value in fixtures) {
            val fixture = value.jsonObject
            var path: String? = null
            val http = (DefaultHTTPClient.create("https://example.invalid/v1", HTTPTransport {
                path = it.url.encodedPath
                Outcome.Ok(WireResponse(fixture.getValue("status").jsonPrimitive.int,
                    mapOf("x-request-id" to "notes-req", "x-correlation-id" to "notes-corr"),
                    fixture.getValue("body").jsonPrimitive.content.toByteArray()))
            }) as Outcome.Ok).value
            val result = HTTPNotesService(http).list()
            if ("expected" in fixture) {
                val notes = fixture.getValue("expected").jsonArray.map { Note(it.jsonObject.getValue("id").jsonPrimitive.content, it.jsonObject.getValue("title").jsonPrimitive.content) }
                assertEquals(notes, (result as Outcome.Ok).value)
                assertEquals(notes, (InMemoryNotesService(notes).list() as Outcome.Ok).value)
            } else {
                val failure = (result as Outcome.Err).error
                assertEquals(fixture.getValue("kind").jsonPrimitive.content, failure.kind.value)
                assertEquals(if (fixture.getValue("status").jsonPrimitive.int == 503) "notes.unavailable" else "notes.invalid_response", failure.meta.code)
                assertEquals("notes-req", failure.meta.requestId)
                assertEquals("notes-corr", failure.meta.correlationId)
            }
            assertEquals("/v1/notes", path)
        }
    }

    @Test fun domainBoundsAndOwnedSnapshots() = runTest {
        assertTrue(Note.valid("a".repeat(64), "Idea"))
        assertFalse(Note.valid("a".repeat(65), "Idea"))
        assertFalse(Note.valid("", "Idea"))
        assertFalse(Note.valid("é", "Idea"))
        assertFalse(Note.valid("n1", " \t\r\n"))
        val note = Note("n1", "Idea")
        val seeds = mutableListOf(note)
        val memory = InMemoryNotesService(seeds)
        seeds.clear()
        val returned = (memory.list() as Outcome.Ok).value
        try { (returned as MutableList<Note>).clear(); fail("Mutable result") } catch (_: UnsupportedOperationException) {}
        assertEquals(listOf(note), (memory.list() as Outcome.Ok).value)
        assertThrows(IllegalArgumentException::class.java) { InMemoryNotesService(listOf(note, note)) }
    }

    @Test fun adaptersPreserveDefectsAndCancellation() = runTest {
        val defect = IllegalStateException("Original")
        val client = object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> = throw defect
        }
        try { HTTPNotesService(client).list(); fail("Defect swallowed") } catch (error: IllegalStateException) { assertSame(defect, error) }
        for (service in listOf<NotesService>(InMemoryNotesService(emptyList()), HTTPNotesService(client))) {
            var canceled = false
            launch {
                cancel()
                try { service.list(); fail("Cancellation admitted") } catch (_: CancellationException) { canceled = true }
            }.join()
            assertTrue(canceled)
        }
    }
}
