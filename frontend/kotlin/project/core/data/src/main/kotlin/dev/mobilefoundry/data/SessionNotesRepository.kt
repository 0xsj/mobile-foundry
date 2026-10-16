package dev.mobilefoundry.data

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

/** Call on the session's UI executor. The admitted write target never changes. */
class SessionNotesRepository(private val repository: NotesRepository, private val session: SessionController,
    private val lease: SessionLease) : NotesRepository {
    override suspend fun list() = access { repository.list() }
    override suspend fun create(command: CreateNote) = access { repository.create(command) }
    override suspend fun update(command: UpdateNote) = access { repository.update(command) }
    override suspend fun delete(command: DeleteNote) = access { repository.delete(command) }
    private suspend fun <T> access(operation: suspend () -> AppResult<T>): AppResult<T> {
        currentCoroutineContext().ensureActive()
        if (!session.isCurrent(lease)) return Outcome.Err(SessionFailures.scopeChanged)
        try {
            val answer = operation(); currentCoroutineContext().ensureActive()
            return if (session.isCurrent(lease)) answer else Outcome.Err(SessionFailures.scopeChanged)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (error is CancellationException) throw error
            if (!session.isCurrent(lease)) return Outcome.Err(SessionFailures.scopeChanged)
            throw error
        }
    }
}
