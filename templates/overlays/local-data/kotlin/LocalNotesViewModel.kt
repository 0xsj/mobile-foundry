package dev.mobilefoundry.product.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalNotesState(val query: QueryState<List<Note>> = QueryState.Idle,
    val mutation: MutationState<String> = MutationState.Idle, val title: String = "", val editingId: String? = null,
    val touched: Boolean = false, val isBusy: Boolean = false) {
    val canAct get() = !isBusy && query.value != null
    val titleError get() = (mutation.failure as? Failure.Invalid)?.fields?.get("title") ?:
        if (touched) ((CreateNote.make(title) as? Outcome.Err)?.error as? Failure.Invalid)?.fields?.get("title") else null
}
/** Main-thread feature owner; repository values stay independent of native storage and UI. */
class LocalNotesViewModel(private val repository: NotesRepository, private val onUnexpectedError: (Exception) -> Unit) : ViewModel() {
    // Transient native editing state; saved records remain independent domain values.
    val titleField = TextFieldState()
    private val mutable = MutableStateFlow(LocalNotesState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var request: Job? = null
    private var closed = false
    private fun set(transform: (LocalNotesState) -> LocalNotesState) { mutable.value = transform(state.value) }
    private fun fieldTitle(value: String) { if (titleField.text.toString() != value) titleField.setTextAndPlaceCursorAtEnd(value) }
    fun observeTitle() {
        val value = titleField.text.toString()
        if (state.value.canAct && state.value.title != value) set { it.copy(title = value, mutation = MutationState.Idle) }
    }
    fun editTitle(value: String) {
        if (state.value.canAct) { fieldTitle(value); observeTitle() }
    }
    fun blurTitle() { if (!closed) set { it.copy(touched = true) } }
    fun edit(note: Note) {
        if (state.value.canAct && state.value.query.value?.any { it.id == note.id } == true) {
            fieldTitle(note.title)
            set { it.copy(title = note.title, editingId = note.id, touched = false, mutation = MutationState.Idle) }
        }
    }
    fun newNote() { if (state.value.canAct) { fieldTitle(""); set { it.copy(title = "", editingId = null, touched = false, mutation = MutationState.Idle) } } }
    fun refresh(): Job? {
        if (closed || state.value.isBusy) return null
        val current = ++generation
        set { it.copy(query = it.query.starting(), isBusy = true) }
        return viewModelScope.launch {
            try {
                val answer = repository.list(); ensureActive()
                if (current == generation) set { it.copy(query = it.query.settled(answer)) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (defect: Exception) {
                ensureActive()
                if (current == generation) {
                    onUnexpectedError(defect)
                    if (current == generation) set { it.copy(query = QueryState.Failed(unexpected, it.query.value)) }
                }
            } finally {
                if (current == generation) { request = null; set { it.copy(isBusy = false, query = if (isActive) it.query else it.query.restored()) } }
            }
        }.also { request = it }
    }
    fun save(): Job? {
        if (!state.value.canAct) return null
        // Flush the latest native edit synchronously; Flow collection need not precede button/IME admission.
        observeTitle()
        set { it.copy(touched = true) }
        val id = state.value.editingId
        return if (id == null) when (val command = CreateNote.make(state.value.title)) {
            is Outcome.Err -> { set { it.copy(mutation = MutationState.Failed(command.error)) }; null }
            is Outcome.Ok -> perform(Action.Create(command.value))
        } else when (val command = UpdateNote.make(id, state.value.title)) {
            is Outcome.Err -> { set { it.copy(mutation = MutationState.Failed(command.error)) }; null }
            is Outcome.Ok -> perform(Action.Update(command.value))
        }
    }
    fun delete(note: Note): Job? {
        if (!state.value.canAct || state.value.query.value?.any { it.id == note.id } != true) return null
        val command = (DeleteNote.make(note.id) as? Outcome.Ok)?.value ?: return null
        return perform(Action.Delete(command))
    }
    private sealed interface Action {
        data class Create(val command: CreateNote) : Action
        data class Update(val command: UpdateNote) : Action
        data class Delete(val command: DeleteNote) : Action
    }
    private fun perform(action: Action): Job {
        val current = ++generation
        set { it.copy(isBusy = true, mutation = MutationState.Submitting) }
        return viewModelScope.launch {
            try {
                val result: AppResult<String> = when (action) {
                    is Action.Create -> when (val answer = repository.create(action.command)) { is Outcome.Err -> answer; is Outcome.Ok -> Outcome.Ok("Note created.") }
                    is Action.Update -> when (val answer = repository.update(action.command)) { is Outcome.Err -> answer; is Outcome.Ok -> Outcome.Ok("Note updated.") }
                    is Action.Delete -> when (val answer = repository.delete(action.command)) { is Outcome.Err -> answer; is Outcome.Ok -> Outcome.Ok("Note deleted.") }
                }
                ensureActive(); if (current != generation) return@launch
                set { it.copy(mutation = it.mutation.settled(result)) }
                if (result is Outcome.Err) return@launch
                val clear = action !is Action.Delete || state.value.editingId == action.command.id
                if (clear) fieldTitle("")
                set { it.copy(title = if (clear) "" else it.title, editingId = if (clear) null else it.editingId,
                    touched = if (clear) false else it.touched, query = it.query.starting()) }
                val snapshot = repository.list(); ensureActive()
                if (current == generation) set { it.copy(query = it.query.settled(snapshot)) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (defect: Exception) {
                ensureActive()
                if (current == generation) {
                    onUnexpectedError(defect)
                    if (current == generation) set {
                        if (it.mutation.value != null) it.copy(query = QueryState.Failed(unexpected, it.query.value))
                        else it.copy(mutation = MutationState.Failed(unexpected))
                    }
                }
            } finally {
                if (current == generation) {
                    request = null
                    set { it.copy(isBusy = false, query = if (isActive) it.query else it.query.restored(),
                        mutation = if (!isActive && it.mutation.isSubmitting) MutationState.Failed(stopped) else it.mutation) }
                }
            }
        }.also { request = it }
    }
    fun stop() {
        ++generation; request?.cancel(); request = null
        set { it.copy(isBusy = false, query = it.query.restored(), mutation = if (it.mutation.isSubmitting) MutationState.Failed(stopped) else it.mutation) }
    }
    /** Terminal account owner: drop native editing state, snapshots and pending publication. */
    fun shutdown() {
        closed = true; stop(); viewModelScope.cancel(); fieldTitle(""); mutable.value = LocalNotesState()
    }
    private companion object {
        val stopped = Failure.Canceled(FailureMeta("Action stopped before confirmation. Refresh notes before trying again.", "notes.local_canceled"))
        val unexpected = Failure.Internal(FailureMeta("An unexpected error occurred."))
    }
}
