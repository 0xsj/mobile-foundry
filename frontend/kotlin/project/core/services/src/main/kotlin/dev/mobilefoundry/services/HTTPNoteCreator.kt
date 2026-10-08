package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

class HTTPNoteCreator(private val http: HTTPClient) : NoteCreator {
    override suspend fun create(command: CreateNote): AppResult<Note> {
        currentCoroutineContext().ensureActive()
        return when (val answer = http.requestEntity(HTTPMethod.POST, "notes", RequestOptions(
            body = buildJsonObject { put("title", command.title) },
        ))) {
            is Outcome.Err -> answer
            is Outcome.Ok -> {
                val entity = answer.value
                val body = (entity.body as? JsonObject)?.get("note") as? JsonObject
                val id = (body?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
                val title = (body?.get("title") as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (id == null || title == null || !Note.valid(id, title)) {
                    Outcome.Err(Failure.Internal(FailureMeta("The created note response was invalid.", "notes.invalid_create_response",
                        entity.metadata.requestId, entity.metadata.correlationId)))
                } else {
                    currentCoroutineContext().ensureActive()
                    Outcome.Ok(Note(id, title))
                }
            }
        }
    }
}
