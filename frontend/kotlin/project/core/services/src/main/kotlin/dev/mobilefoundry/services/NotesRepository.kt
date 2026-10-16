package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Composed local-data capability. Narrow read/create consumers still depend on their existing ports. */
interface NotesRepository : NotesService, NoteCreator {
    suspend fun update(command: UpdateNote): AppResult<Note>
    suspend fun delete(command: DeleteNote): AppResult<Unit>
}
class UpdateNote private constructor(val id: String, val title: String) {
    companion object {
        fun make(id: String, title: String): AppResult<UpdateNote> {
            if (!Note.valid(id, "ID admission")) return Outcome.Err(invalidNoteID)
            return when (val command = CreateNote.make(title)) {
                is Outcome.Err -> command
                is Outcome.Ok -> Outcome.Ok(UpdateNote(id, command.value.title))
            }
        }
    }
}
class DeleteNote private constructor(val id: String) {
    companion object {
        fun make(id: String): AppResult<DeleteNote> = if (Note.valid(id, "ID admission")) Outcome.Ok(DeleteNote(id)) else Outcome.Err(invalidNoteID)
    }
}
private val invalidNoteID = Failure.Invalid(FailureMeta("The note identifier is invalid.", "notes.invalid_id"), mapOf("id" to "Use a valid note identifier."))
object NotesRepositoryFailure {
    val missing get() = Failure.NotFound(FailureMeta("This note no longer exists. Refresh your notes.", "notes.not_found"))
}

/** Mutable memory adapter; owned snapshots and insertion order match local storage. */
class InMemoryNotesRepository(notes: List<Note> = emptyList()) : ObservableNotesRepository {
    private val notes = ArrayList(notes)
    private val mutex = Mutex()
    private val snapshots = MutableStateFlow(ownedSnapshot())
    private fun ownedSnapshot(): List<Note> = Collections.unmodifiableList(ArrayList(notes))
    private fun publish() { snapshots.value = ownedSnapshot() }
    init { require(notes.map { it.id }.distinct().size == notes.size) }
    override fun observe(): Flow<AppResult<List<Note>>> = snapshots.map { Outcome.Ok(it) }
    override suspend fun list(): AppResult<List<Note>> = mutex.withLock {
        currentCoroutineContext().ensureActive(); Outcome.Ok(Collections.unmodifiableList(ArrayList(notes)))
    }
    override suspend fun create(command: CreateNote): AppResult<Note> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        var id = UUID.randomUUID().toString()
        while (notes.any { it.id == id }) id = UUID.randomUUID().toString()
        val note = Note(id, command.title); notes.add(note); publish(); Outcome.Ok(note)
    }
    override suspend fun update(command: UpdateNote): AppResult<Note> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val index = notes.indexOfFirst { it.id == command.id }
        if (index < 0) Outcome.Err(NotesRepositoryFailure.missing)
        else { val note = Note(command.id, command.title); notes[index] = note; publish(); Outcome.Ok(note) }
    }
    override suspend fun delete(command: DeleteNote): AppResult<Unit> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val index = notes.indexOfFirst { it.id == command.id }
        if (index < 0) Outcome.Err(NotesRepositoryFailure.missing) else { notes.removeAt(index); publish(); Outcome.Ok(Unit) }
    }
}
