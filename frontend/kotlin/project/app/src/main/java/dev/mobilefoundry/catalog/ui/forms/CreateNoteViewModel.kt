package dev.mobilefoundry.catalog.ui.forms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CreateNoteFormState(
    val title: String = "",
    val touched: Boolean = false,
    val mutation: MutationState<Note> = MutationState.Idle,
) {
    val canEdit get() = !mutation.isSubmitting && mutation.value == null
    val titleError: String? get() {
        val server = (mutation.failure as? Failure.Invalid)?.fields?.get("title")
        val local = if (touched) ((CreateNote.make(title) as? Outcome.Err)?.error as? Failure.Invalid)?.fields?.get("title") else null
        return server ?: local
    }
}

/** Main-thread owner; generation guards admit results independently of cancellation. */
class CreateNoteViewModel(
    private var creator: NoteCreator,
    private val onUnexpectedError: (Exception) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateNoteFormState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var request: Job? = null

    fun editTitle(value: String) {
        if (!state.value.canEdit) return
        mutableState.value = state.value.copy(title = value, mutation = MutationState.Idle)
    }

    fun blurTitle() { mutableState.value = state.value.copy(touched = true) }

    /** Mark busy before launch so button and IME submissions share one synchronous guard. */
    fun submit(): Job? {
        if (!state.value.canEdit) return null
        mutableState.value = state.value.copy(touched = true)
        val command = when (val admitted = CreateNote.make(state.value.title)) {
            is Outcome.Err -> return null
            is Outcome.Ok -> admitted.value
        }
        val current = ++generation
        val service = creator
        mutableState.value = state.value.copy(mutation = state.value.mutation.starting())
        return viewModelScope.launch {
            try {
                val result = service.create(command)
                ensureActive()
                if (current == generation) mutableState.value = state.value.copy(mutation = state.value.mutation.settled(result))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (defect: Exception) {
                ensureActive()
                if (current == generation) {
                    onUnexpectedError(defect)
                    if (current == generation) mutableState.value = state.value.copy(
                        mutation = MutationState.Failed(Failure.Internal(FailureMeta("An unexpected error occurred."))),
                    )
                }
            } finally {
                if (current == generation) {
                    request = null
                    if (!isActive && state.value.mutation.isSubmitting) mutableState.value = state.value.copy(mutation = MutationState.Failed(canceled))
                }
            }
        }.also { request = it }
    }

    fun use(creator: NoteCreator): Boolean {
        if (state.value.mutation.isSubmitting) return false
        this.creator = creator
        mutableState.value = state.value.copy(mutation = MutationState.Idle)
        return true
    }

    fun reset() {
        if (!state.value.mutation.isSubmitting) mutableState.value = CreateNoteFormState()
    }

    fun stop() {
        ++generation
        request?.cancel(); request = null
        if (state.value.mutation.isSubmitting) mutableState.value = state.value.copy(mutation = MutationState.Failed(canceled))
    }

    private companion object {
        val canceled = Failure.Canceled(FailureMeta("Submission stopped before confirmation.", "notes.create_canceled"))
    }
}
