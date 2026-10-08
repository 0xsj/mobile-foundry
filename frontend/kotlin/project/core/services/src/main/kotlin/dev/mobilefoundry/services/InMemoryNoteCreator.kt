package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Instance-local memory. The mutex protects both append and snapshot reads. */
class InMemoryNoteCreator(notes: List<Note> = emptyList()) : NoteCreator, NotesService {
    private val notes = ArrayList(notes)
    private val mutex = Mutex()
    init { require(notes.map { it.id }.distinct().size == notes.size) { "Note IDs must be unique" } }

    override suspend fun create(command: CreateNote): AppResult<Note> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        var id = UUID.randomUUID().toString()
        while (notes.any { it.id == id }) id = UUID.randomUUID().toString()
        val note = Note(id, command.title)
        notes.add(note)
        Outcome.Ok(note)
    }

    override suspend fun list(): AppResult<List<Note>> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        Outcome.Ok(Collections.unmodifiableList(ArrayList(notes)))
    }
}
