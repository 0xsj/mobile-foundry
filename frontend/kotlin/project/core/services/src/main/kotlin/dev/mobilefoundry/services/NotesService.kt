package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.util.Collections
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
