package dev.mobilefoundry.catalog.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.Note
import dev.mobilefoundry.services.NotesService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread state owner. The navigation entry owns its ViewModel scope. */
class NotesViewModel(
    private val service: NotesService,
    private val onUnexpectedError: (Exception) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow<QueryState<List<Note>>>(QueryState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var request: Job? = null

    fun refresh(): Job {
        val current = ++generation
        request?.cancel()
        mutableState.value = mutableState.value.starting()
        return viewModelScope.launch {
            try {
                val answer = service.list()
                ensureActive()
                if (current != generation) return@launch
                mutableState.value = mutableState.value.settled(answer)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (defect: Exception) {
                ensureActive()
                if (current == generation) {
                    onUnexpectedError(defect)
                    if (current == generation) mutableState.value = QueryState.Failed(
                        Failure.Internal(FailureMeta("An unexpected error occurred.")), mutableState.value.value,
                    )
                }
            } finally {
                if (current == generation) {
                    if (!isActive) restore()
                }
            }
        }.also { request = it }
    }

    fun cancel() {
        ++generation
        request?.cancel()
        request = null
        restore()
    }

    private fun restore() { mutableState.value = mutableState.value.restored() }
}
