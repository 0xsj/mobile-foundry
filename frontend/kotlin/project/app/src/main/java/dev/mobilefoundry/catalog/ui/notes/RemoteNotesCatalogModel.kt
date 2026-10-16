package dev.mobilefoundry.catalog.ui.notes
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class RemoteNotesState(val title: String = "",val editing: VersionedNote? = null,val notes: List<VersionedNote> = emptyList(),val hasMore: Boolean = false,
    val isBusy: Boolean = false,val loaded: Boolean = false,val failure: Failure? = null,val pending: RemoteNoteWrite? = null,val receipt: NoteWriteReceipt? = null) {
    val canWrite get() = loaded && !isBusy && pending == null
}
/** Pending commands are scoped and retained through renewal for this catalog owner. */
class RemoteNotesCatalogModel(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(RemoteNotesState());val state = mutable.asStateFlow()
    private var accountScope: SessionScope? = null;private var lastScope: SessionScope? = null
    private var repository: RemoteNotesRepository? = null
    private val retained = mutableMapOf<SessionScope,RemoteNoteWrite>()
    private var task: Job? = null;private var generation = 0L
    fun bind(repository: RemoteNotesRepository?,account: SessionScope?) {
        generation++;task?.cancel();task = null;this.repository = repository;accountScope = account
        val previous = mutable.value
        mutable.value = RemoteNotesState(title = if (account == null || account == lastScope) previous.title else "",editing = if (account == null || account == lastScope) previous.editing else null,pending = retained[account])
        if (account != null) lastScope = account
        if (repository != null) refresh()
    }
    fun setTitle(value: String) { if (mutable.value.canWrite) mutable.value = mutable.value.copy(title = value) }
    fun edit(note: VersionedNote) { if (mutable.value.canWrite) mutable.value = mutable.value.copy(editing = note,title = note.title) }
    fun newDraft() { if (mutable.value.canWrite) mutable.value = mutable.value.copy(editing = null,title = "") }
    fun save() {
        val state = mutable.value;if (!state.canWrite) return
        val id = UUID.randomUUID().toString()
        val answer = state.editing?.let { RemoteNoteWrite.update(id,it.id,state.title,it.version) } ?: RemoteNoteWrite.create(id,state.title)
        when (answer) { is Outcome.Err -> mutable.value = state.copy(failure = answer.error.publicInfo());is Outcome.Ok -> dispatch(answer.value) }
    }
    fun delete(note: VersionedNote) { if (!mutable.value.canWrite) return;when (val answer = RemoteNoteWrite.delete(UUID.randomUUID().toString(),note.id,note.version)) { is Outcome.Ok -> dispatch(answer.value);is Outcome.Err -> mutable.value = mutable.value.copy(failure = answer.error.publicInfo()) } }
    private fun dispatch(command: RemoteNoteWrite) { val account = accountScope ?: return;retained[account] = command;mutable.value = mutable.value.copy(pending = command);recover(false) }
    fun refresh() { run { read(it) } }
    fun recover(checkOnly: Boolean) {
        val command = mutable.value.pending ?: return
        run { repo ->
            val answer = if (checkOnly) repo.receipt(command) else repo.execute(command)
            currentCoroutineContext().ensureActive()
            when (answer) {
                is Outcome.Ok -> {
                    val account = accountScope
                    if (account == null || answer.value.accountId != account.accountId || answer.value.principalId != account.principalId || !answer.value.confirms(command)) {
                        mutable.value = mutable.value.copy(failure = Failure.Internal(FailureMeta("The write receipt did not match this command.")));return@run
                    }
                    accountScope?.let { retained.remove(it) }
                    val previous = mutable.value;val clearsDraft = command.action != NoteWriteAction.DELETE || previous.editing?.id == command.noteId
                    mutable.value = previous.copy(pending = null,receipt = answer.value,title = if (clearsDraft) "" else previous.title,editing = if (clearsDraft) null else previous.editing)
                    read(repo)
                }
                is Outcome.Err -> {
                    val rejected = !checkOnly && answer.error.meta.code in listOf("notes.version_conflict","notes.not_found")
                    if (rejected) accountScope?.let { retained.remove(it) }
                    mutable.value = mutable.value.copy(failure = answer.error.publicInfo(),pending = if (rejected) null else command,loaded = if (rejected) false else mutable.value.loaded)
                }
            }
        }
    }
    private suspend fun read(repo: RemoteNotesRepository) {
        val answer = repo.page(null,20);currentCoroutineContext().ensureActive()
        mutable.value = when (answer) { is Outcome.Ok -> mutable.value.copy(notes = answer.value.notes,hasMore = answer.value.nextAfter != null,loaded = true);is Outcome.Err -> mutable.value.copy(failure = answer.error.publicInfo(),loaded = false) }
    }
    private fun run(action: suspend (RemoteNotesRepository) -> Unit) {
        if (mutable.value.isBusy) return;val repo = repository ?: return;val current = generation
        mutable.value = mutable.value.copy(isBusy = true,failure = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { action(repo) } catch (_: CancellationException) {} catch (_: Exception) {
                if (current == generation) mutable.value = mutable.value.copy(failure = Failure.Internal(FailureMeta("Remote Notes could not complete. Keep any unconfirmed command.")),loaded = false)
            } finally { if (current == generation) { mutable.value = mutable.value.copy(isBusy = false);task = null } }
        };task = job;job.start()
    }
}
