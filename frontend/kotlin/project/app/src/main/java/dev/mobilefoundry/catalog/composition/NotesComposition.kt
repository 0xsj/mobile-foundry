package dev.mobilefoundry.catalog.composition

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.delay

enum class NotesProvider(val label: String) { MEMORY("Memory"), HTTP("HTTP") }
enum class NotesScenario(val label: String) { CONTENT("Content"), EMPTY("Empty"), UNAVAILABLE("Unavailable"), SLOW("Slow") }

object NotesComposition {
    private val seeds = listOf(Note("n1", "Sketch a native idea"), Note("n2", "Explore a GPU interaction"))

    fun service(provider: NotesProvider, scenario: NotesScenario): NotesService {
        val service: NotesService = when (provider) {
            NotesProvider.MEMORY -> if (scenario == NotesScenario.UNAVAILABLE) NotesService {
                Outcome.Err(Failure.Unavailable(FailureMeta("Notes are temporarily unavailable.", "notes.unavailable")))
            } else InMemoryNotesService(if (scenario == NotesScenario.EMPTY) emptyList() else seeds)
            NotesProvider.HTTP -> when (val client = DefaultHTTPClient.create("https://catalog.invalid/v1", HTTPTransport {
                val status = if (scenario == NotesScenario.UNAVAILABLE) 503 else 200
                val body = when (scenario) {
                    NotesScenario.UNAVAILABLE -> """{"detail":"Notes are temporarily unavailable.","code":"notes.unavailable"}"""
                    NotesScenario.EMPTY -> """{"notes":[]}"""
                    else -> """{"notes":[{"id":"n1","title":"Sketch a native idea"},{"id":"n2","title":"Explore a GPU interaction"}]}"""
                }
                Outcome.Ok(WireResponse(status, mapOf("x-request-id" to "catalog-notes"), body.toByteArray()))
            })) {
                is Outcome.Ok -> HTTPNotesService(client.value)
                is Outcome.Err -> NotesService { client }
            }
        }
        return NotesService {
            delay(if (scenario == NotesScenario.SLOW) 1_500 else 250)
            service.list()
        }
    }
}
