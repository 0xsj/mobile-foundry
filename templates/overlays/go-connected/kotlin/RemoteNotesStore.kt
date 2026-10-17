package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class RemoteNotesState(val title: String = "", val editing: VersionedNote? = null, val notes: List<VersionedNote> = emptyList(), val nextAfter: String? = null,
    val isBusy: Boolean = false, val loaded: Boolean = false, val recoveryLoaded: Boolean = false, val failure: Failure? = null,
    val cachedAtMilliseconds: Long? = null, val cacheFailure: Failure? = null,
    val pending: RemoteNoteWrite? = null, val receipt: NoteWriteReceipt? = null) {
    val canWrite get() = loaded && recoveryLoaded && !isBusy && pending == null
}

/** Persist before HTTP. Restore/read never automatically resends a saved command. */
class RemoteNotesStore(private val scope: CoroutineScope, private val journal: PendingNotesStore = MemoryPendingNotesStore(),
    private val cache: NotesReadCache = MemoryNotesReadCache(), private val now: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow(RemoteNotesState())
    val state = mutable.asStateFlow()
    private var accountScope: PendingNotesScope? = null
    private var lastScope: PendingNotesScope? = null
    private var repository: RemoteNotesRepository? = null
    private var record: PendingNoteRecord? = null
    private var task: Job? = null
    private var generation = 0L
    private var dirty = false
    private var cacheAccessRefused = false
    fun bind(repository: RemoteNotesRepository?, account: SessionScope?) {
        generation++; task?.cancel(); task = null; this.repository = repository
        dirty = false; cacheAccessRefused = false
        accountScope = account?.let { PendingNotesScope.make(it.authorityId, it.principalId, it.accountId) }
        val previous = mutable.value
        mutable.value = RemoteNotesState(title = if (accountScope == null || accountScope == lastScope) previous.title else "",
            editing = if (accountScope == null || accountScope == lastScope) previous.editing else null)
        record = null
        if (accountScope != null) lastScope = accountScope
        if (repository != null) refresh()
    }
    fun setTitle(value: String) { if (state.value.canWrite) mutable.value = state.value.copy(title = value) }
    fun edit(note: VersionedNote) { if (state.value.canWrite) mutable.value = state.value.copy(editing = note, title = note.title) }
    fun newDraft() { if (state.value.canWrite) mutable.value = state.value.copy(editing = null, title = "") }
    fun save() {
        val current = state.value; if (!current.canWrite) return
        val id = UUID.randomUUID().toString()
        when (val answer = current.editing?.let { RemoteNoteWrite.update(id, it.id, current.title, it.version) } ?: RemoteNoteWrite.create(id, current.title)) {
            is Outcome.Err -> mutable.value = current.copy(failure = answer.error.publicInfo())
            is Outcome.Ok -> dispatch(answer.value)
        }
    }
    fun delete(note: VersionedNote) {
        if (!state.value.canWrite) return
        when (val answer = RemoteNoteWrite.delete(UUID.randomUUID().toString(), note.id, note.version)) {
            is Outcome.Ok -> dispatch(answer.value)
            is Outcome.Err -> mutable.value = state.value.copy(failure = answer.error.publicInfo())
        }
    }
    private fun dispatch(command: RemoteNoteWrite) {
        val replacing = record?.command?.commandId
        run { repo, account ->
            val answer = journal.stage(command, account, replacing); currentCoroutineContext().ensureActive()
            val staged = accept(answer) ?: return@run
            apply(staged)
            if (staged.status == PendingNoteStatus.PENDING) send(command, repo, account, false)
        }
    }
    fun refresh(after: String? = null) = run { repo, account ->
        if (restore(account)) {
            if (!state.value.loaded && state.value.notes.isEmpty()) showSavedRead(account)
            read(repo, account, after)
        }
    }
    /** Hints coalesce behind active work; never cancel/replay an accepted command. */
    fun invalidate() { dirty = true; if (!state.value.isBusy) { dirty = false; refresh() } }
    fun recover(checkOnly: Boolean) {
        val command = state.value.pending ?: return
        run { repo, account ->
            if (!restore(account)) return@run
            val saved = record
            if (saved == null || !saved.matches(command)) {
                mutable.value = state.value.copy(recoveryLoaded = false, failure = PendingNotesFailure.conflict); return@run
            }
            if (saved.status == PendingNoteStatus.PENDING) send(command, repo, account, checkOnly) else read(repo, account)
        }
    }
    private suspend fun restore(account: PendingNotesScope): Boolean {
        val answer = journal.load(account); currentCoroutineContext().ensureActive()
        return when (answer) {
            is Outcome.Err -> { mutable.value = state.value.copy(recoveryLoaded = false, loaded = false, notes = emptyList(), nextAfter = null, cachedAtMilliseconds = null, failure = answer.error.publicInfo()); false }
            is Outcome.Ok -> {
                mutable.value = state.value.copy(recoveryLoaded = true); apply(answer.value)
                state.value.pending?.takeIf { it.action != NoteWriteAction.DELETE }?.let { mutable.value = state.value.copy(title = it.title) }
                true
            }
        }
    }
    private suspend fun send(command: RemoteNoteWrite, repo: RemoteNotesRepository, account: PendingNotesScope, checkOnly: Boolean) {
        val answer = if (checkOnly) repo.receipt(command) else repo.execute(command)
        currentCoroutineContext().ensureActive()
        when (answer) {
            is Outcome.Ok -> {
                val receipt = answer.value
                if (receipt.accountId != account.accountId || receipt.principalId != account.principalId || !receipt.confirms(command)) {
                    mutable.value = state.value.copy(failure = Failure.Internal(FailureMeta("The write receipt did not match this command."))); return
                }
                mutable.value = state.value.copy(receipt = receipt)
                val persisted = journal.confirm(command, account, receipt); currentCoroutineContext().ensureActive()
                val confirmed = accept(persisted) ?: return
                apply(confirmed)
                if (command.action != NoteWriteAction.DELETE || state.value.editing?.id == command.noteId) mutable.value = state.value.copy(title = "", editing = null)
                read(repo, account)
            }
            is Outcome.Err -> {
                mutable.value = state.value.copy(failure = answer.error.publicInfo())
                val reason = PendingNoteRejection.entries.find { it.code == answer.error.meta.code }
                if (!checkOnly && reason != null) {
                    val persisted = journal.reject(command, account, reason); currentCoroutineContext().ensureActive()
                    val refused = accept(persisted) ?: return
                    apply(refused); mutable.value = state.value.copy(loaded = false)
                }
            }
        }
    }
    private fun accept(answer: AppResult<PendingNoteRecord>): PendingNoteRecord? = when (answer) {
        is Outcome.Ok -> { mutable.value = state.value.copy(recoveryLoaded = true); answer.value }
        is Outcome.Err -> { mutable.value = state.value.copy(recoveryLoaded = false, failure = answer.error.publicInfo()); null }
    }
    private fun apply(saved: PendingNoteRecord?) {
        record = saved
        val pending = saved?.takeIf { it.status == PendingNoteStatus.PENDING }?.command
        val receipt = saved?.receipt ?: state.value.receipt?.takeIf { pending != null && it.confirms(pending) }
        val failure = saved?.rejection?.let { reason ->
            if (reason == PendingNoteRejection.VERSION_CONFLICT) Failure.Conflict(FailureMeta("The saved change was refused because this note changed. Refresh and review your edit.", reason.code))
            else Failure.NotFound(FailureMeta("The saved change was refused because this note is missing. Refresh your notes.", reason.code))
        } ?: state.value.failure
        mutable.value = state.value.copy(pending = pending, receipt = receipt, failure = failure)
    }
    fun forgetSavedRead() = run { _, account ->
        try {
            val answer = cache.remove(account); currentCoroutineContext().ensureActive()
            mutable.value = when (answer) {
                is Outcome.Ok -> if (state.value.cachedAtMilliseconds != null) state.value.copy(notes = emptyList(), nextAfter = null, cachedAtMilliseconds = null, cacheFailure = null) else state.value.copy(cacheFailure = null)
                is Outcome.Err -> state.value.copy(cacheFailure = answer.error.publicInfo())
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); mutable.value = state.value.copy(cacheFailure = NotesCacheFailure.storage) }
    }
    private suspend fun showSavedRead(account: PendingNotesScope) {
        if (cacheAccessRefused) return
        try {
            val answer = cache.load(account, now()); currentCoroutineContext().ensureActive()
            when (answer) {
                is Outcome.Ok -> answer.value?.takeIf { it.scope == account && it.isUsable(now()) && it.matches(record) }?.let {
                    mutable.value = state.value.copy(notes = it.notes, nextAfter = null, loaded = false, cachedAtMilliseconds = it.savedAtMilliseconds)
                }
                is Outcome.Err -> mutable.value = state.value.copy(cacheFailure = answer.error.publicInfo())
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); mutable.value = state.value.copy(cacheFailure = NotesCacheFailure.storage) }
    }
    private suspend fun read(repo: RemoteNotesRepository, account: PendingNotesScope, after: String? = null) {
        val answer = repo.page(after, 20); currentCoroutineContext().ensureActive()
        when (answer) {
            is Outcome.Ok -> {
                cacheAccessRefused = false
                mutable.value = state.value.copy(notes = answer.value.notes, nextAfter = answer.value.nextAfter, loaded = true, cachedAtMilliseconds = null)
                if (after == null) NotesReadSnapshot.make(account, answer.value.notes, now(), record)?.let { snapshot ->
                    try {
                        val saved = cache.save(snapshot); currentCoroutineContext().ensureActive()
                        mutable.value = state.value.copy(cacheFailure = (saved as? Outcome.Err)?.error?.publicInfo())
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { currentCoroutineContext().ensureActive(); mutable.value = state.value.copy(cacheFailure = NotesCacheFailure.storage) }
                }
            }
            is Outcome.Err -> {
                mutable.value = state.value.copy(failure = answer.error.publicInfo(), loaded = false, notes = emptyList(), nextAfter = null, cachedAtMilliseconds = null)
                when (answer.error.kind) {
                    FailureKind.UNAVAILABLE, FailureKind.TIMEOUT -> showSavedRead(account)
                    FailureKind.UNAUTHENTICATED, FailureKind.FORBIDDEN -> try {
                        cacheAccessRefused = true
                        val removed = cache.remove(account); currentCoroutineContext().ensureActive()
                        if (removed is Outcome.Err) mutable.value = state.value.copy(cacheFailure = removed.error.publicInfo())
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { currentCoroutineContext().ensureActive(); mutable.value = state.value.copy(cacheFailure = NotesCacheFailure.storage) }
                    else -> Unit
                }
            }
        }
    }
    private fun run(action: suspend (RemoteNotesRepository, PendingNotesScope) -> Unit) {
        if (state.value.isBusy) return
        val repo = repository ?: return; val account = accountScope ?: return; val current = generation
        mutable.value = state.value.copy(isBusy = true, failure = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { action(repo, account) }
            catch (_: CancellationException) {}
            catch (_: Exception) {
                if (current == generation && currentCoroutineContext().isActive) mutable.value = state.value.copy(
                    recoveryLoaded = false, loaded = false, notes = emptyList(), nextAfter = null, cachedAtMilliseconds = null, failure = Failure.Internal(FailureMeta("Saved changes could not complete. Reload recovery before trying again.")))
            } finally {
                if (current == generation) {
                    mutable.value = state.value.copy(isBusy = false); task = null
                    if (dirty) { dirty = false; refresh() }
                }
            }
        }
        task = job; job.start()
    }
}
