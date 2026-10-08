package dev.mobilefoundry.catalog.composition

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

enum class CreateNoteScenario(val label: String) {
    SUCCESS("Success"), FIELD_ERROR("Field error"), UNAVAILABLE("Unavailable"), SLOW("Slow"), TIMEOUT("Timeout"), MALFORMED("Malformed");

    val failure: Failure? get() = when (this) {
        SUCCESS, SLOW -> null
        FIELD_ERROR -> Failure.Invalid(FailureMeta("Check the highlighted field.", "notes.title_reserved"), mapOf("title" to "This title is reserved."))
        UNAVAILABLE -> Failure.Unavailable(FailureMeta("Notes are temporarily unavailable.", "notes.unavailable"))
        TIMEOUT -> Failure.Timeout(FailureMeta("No confirmation was received.", "notes.create_timeout"))
        MALFORMED -> Failure.Internal(FailureMeta("Private malformed create detail.", "notes.invalid_create_response"))
    }
}

object CreateNoteComposition {
    fun creator(provider: NotesProvider, scenario: CreateNoteScenario): NoteCreator {
        val creator: NoteCreator = when (provider) {
            NotesProvider.MEMORY -> {
                val memory = InMemoryNoteCreator()
                NoteCreator { command ->
                    currentCoroutineContext().ensureActive()
                    scenario.failure?.let { Outcome.Err(it) } ?: memory.create(command)
                }
            }
            NotesProvider.HTTP -> when (val client = DefaultHTTPClient.create("https://catalog.invalid/v1", HTTPTransport { request ->
                currentCoroutineContext().ensureActive()
                val title = request.body?.let { Json.parseToJsonElement(it.toString(Charsets.UTF_8)).jsonObject["title"]?.jsonPrimitive?.content }.orEmpty()
                val status = when (scenario) {
                    CreateNoteScenario.FIELD_ERROR -> 422
                    CreateNoteScenario.UNAVAILABLE -> 503
                    CreateNoteScenario.TIMEOUT -> 504
                    else -> 201
                }
                val body = buildJsonObject {
                    if (status == 201) put("note", buildJsonObject {
                        put("id", if (scenario == CreateNoteScenario.MALFORMED) "" else UUID.randomUUID().toString())
                        put("title", title)
                    }) else {
                        val failure = checkNotNull(scenario.failure)
                        put("detail", failure.meta.message); put("code", failure.meta.code)
                        if (failure is Failure.Invalid) put("fields", buildJsonObject { failure.fields.forEach { (key, value) -> put(key, value) } })
                    }
                }
                Outcome.Ok(WireResponse(status, mapOf("x-request-id" to "catalog-create"), body.toString().toByteArray()))
            })) {
                is Outcome.Ok -> HTTPNoteCreator(client.value)
                is Outcome.Err -> NoteCreator { client }
            }
        }
        return NoteCreator { command ->
            delay(if (scenario == CreateNoteScenario.SLOW) 1_500 else 250)
            creator.create(command)
        }
    }
}
