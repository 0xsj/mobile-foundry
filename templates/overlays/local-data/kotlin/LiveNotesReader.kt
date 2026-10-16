package dev.mobilefoundry.product.notes

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NotesReaderState(val query: QueryState<List<Note>> = QueryState.Idle, val isWatching: Boolean = false)
/** Call on the UI executor; each reader owns its collector, generation and optional scope admission. */
class LiveNotesReader(private val scope: CoroutineScope, private val source: NotesObserver,
    private val admitted: () -> Boolean = { true }, private val onUnexpectedError: (Exception) -> Unit) {
    private val mutable = MutableStateFlow(NotesReaderState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var generation = 0
    private var closed = false
    fun start(): Job? {
        if (closed || state.value.isWatching) return null
        if (!admitted()) { shutdown(); return null }
        val current = ++generation; mutable.value = state.value.copy(query = state.value.query.starting(), isWatching = true)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                source.observe().collect { answer ->
                    ensureActive()
                    if (current != generation) return@collect
                    if (!admitted()) { shutdown(); return@collect }
                    mutable.value = state.value.copy(query = state.value.query.settled(answer))
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (defect: Exception) {
                ensureActive()
                if (current == generation) {
                    if (!admitted()) { shutdown(); return@launch }
                    onUnexpectedError(defect)
                    if (current == generation) {
                        if (!admitted()) { shutdown(); return@launch }
                        mutable.value = state.value.copy(query = QueryState.Failed(
                            Failure.Internal(FailureMeta("Live notes could not be observed.", "notes.observation_failed")), state.value.query.value))
                    }
                }
            } finally {
                if (current == generation) { task = null; mutable.value = state.value.copy(query = state.value.query.restored(), isWatching = false) }
            }
        }
        task = job; job.start(); return job
    }
    fun restart(): Job? { pause(); return start() }
    fun pause() { ++generation; task?.cancel(); task = null; mutable.value = state.value.copy(query = state.value.query.restored(), isWatching = false) }
    fun shutdown() { closed = true; pause(); mutable.value = NotesReaderState() }
}
