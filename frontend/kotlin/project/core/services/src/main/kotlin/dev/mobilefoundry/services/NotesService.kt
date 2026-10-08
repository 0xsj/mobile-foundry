package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import java.util.Collections
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

data class Note(val id: String, val title: String) {
    init { require(valid(id, title)) { "Invalid note seed" } }

    companion object {
        fun valid(id: String, title: String): Boolean =
            id.length in 1..64 && id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' } &&
                title.any { it !in " \t\r\n" }
    }
}

/** Provider-independent port; structured cancellation and defects propagate. */
fun interface NotesService { suspend fun list(): AppResult<List<Note>> }

class InMemoryNotesService(notes: List<Note>) : NotesService {
    private val snapshot = Collections.unmodifiableList(ArrayList(notes))
    init { require(snapshot.map { it.id }.distinct().size == snapshot.size) { "Note IDs must be unique" } }

    override suspend fun list(): AppResult<List<Note>> {
        currentCoroutineContext().ensureActive()
        return Outcome.Ok(snapshot)
    }
}

class HTTPNotesService(private val http: HTTPClient) : NotesService {
    override suspend fun list(): AppResult<List<Note>> {
        currentCoroutineContext().ensureActive()
        return when (val answer = http.requestEntity(HTTPMethod.GET, "notes")) {
            is Outcome.Err -> answer
            is Outcome.Ok -> {
                val entity = answer.value
                val items = (entity.body as? JsonObject)?.get("notes") as? JsonArray ?: return invalid(entity)
                val notes = ArrayList<Note>()
                val ids = HashSet<String>()
                for (item in items) {
                    val objectValue = item as? JsonObject ?: return invalid(entity)
                    val id = (objectValue["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return invalid(entity)
                    val title = (objectValue["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return invalid(entity)
                    if (!Note.valid(id, title) || !ids.add(id)) return invalid(entity)
                    notes += Note(id, title)
                }
                currentCoroutineContext().ensureActive()
                Outcome.Ok(Collections.unmodifiableList(notes))
            }
        }
    }

    private fun invalid(entity: Entity): AppResult<List<Note>> = Outcome.Err(Failure.Internal(FailureMeta(
        "The notes response was invalid.", "notes.invalid_response", entity.metadata.requestId, entity.metadata.correlationId,
    )))
}
