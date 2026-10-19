package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FileRecoveryReview internal constructor(internal val generation: Long, internal val key: String, val name: String, val deleting: Boolean) {
    val consequence get() = if (deleting)
        "This only forgets this app's retry information. The server may already have deleted the file, and byte cleanup may still be unfinished. Forgetting does not finish cleanup or undo deletion."
        else "This discards the retained photo and retry information from this app. The upload may already exist on the server. Forgetting does not cancel or delete it."
}
data class FilesState(val files: List<RemoteFile> = emptyList(), val busy: Boolean = false, val pending: Boolean = false,
    val blockedByOtherScope: Boolean = false, val ready: Boolean = false, val failure: Failure? = null,
    val nextCursor: String? = null, val preview: FilePreview? = null)
class FilePreview(val name: String, bytes: ByteArray) {
    private val retained = bytes.copyOf()
    fun bytes() = retained.copyOf()
}
/** One original intent in the injected journal. Binding never replays it. */
class FilesStore(private val owner: CoroutineScope, private val journal: PendingFilesStore = MemoryPendingFilesStore()) {
    private val mutable = MutableStateFlow(FilesState()); val state = mutable.asStateFlow()
    private var repository: RemoteFilesRepository? = null
    private var scope: SessionScope? = null
    private var intent: PendingFileIntent? = null
    private var journalReady = false
    private val recoveryScope get() = scope?.let { PendingFileScope.make(it.authorityId,it.principalId,it.accountId) }
    private var generation = 0L
    private var task: Job? = null
    val selectionTicket get() = generation
    val canEdit get() = repository != null && recoveryScope != null && journalReady && mutable.value.ready && !mutable.value.busy && intent == null
    val canLoadMore get() = mutable.value.ready && !mutable.value.busy && mutable.value.nextCursor != null && mutable.value.files.size < 200
    fun bind(repository: RemoteFilesRepository?, scope: SessionScope?) {
        generation++; task?.cancel(); task = null; this.repository = repository; this.scope = scope; journalReady=false
        val pending = scope != null && intent?.scope == recoveryScope
        mutable.value = FilesState(pending=pending,blockedByOtherScope=scope != null && intent != null && !pending)
        if (repository != null && scope != null) refresh()
    }
    fun refresh() {
        if (mutable.value.busy) return
        val expected=intent;journalReady=false;mutable.value=mutable.value.copy(ready=false)
        run({ repo -> when (val loaded=journal.load()) {
            is Outcome.Err -> loaded
            is Outcome.Ok -> {
                val retained=loaded.value
                if (expected!=null && retained!=null && !expected.matches(retained)) Outcome.Err(PendingFilesFailure.conflict)
                else { currentCoroutineContext().ensureActive();Outcome.Ok(retained to repo.page(null,20)) }
            }
        } }) { (retained,page) ->
            intent=intent ?: retained;journalReady=true
            val pending=intent!=null && intent?.scope==recoveryScope
            mutable.value=mutable.value.copy(pending=pending,blockedByOtherScope=intent!=null && !pending)
            when (page) {
                is Outcome.Ok -> mutable.value=mutable.value.copy(files=page.value.files,nextCursor=page.value.nextCursor,ready=true)
                is Outcome.Err -> mutable.value=mutable.value.copy(failure=page.error.publicInfo())
            }
        }
    }
    fun loadMore() {
        if (!canLoadMore) return; val cursor = mutable.value.nextCursor ?: return
        run({ it.page(cursor,20) }) { page ->
            if (page.files.any { file -> mutable.value.files.any { it.id == file.id } }) {
                mutable.value = mutable.value.copy(ready=false,failure=Failure.Internal(FailureMeta("Files changed while paging. Refresh to continue.")))
            } else mutable.value = mutable.value.copy(files=mutable.value.files+page.files,nextCursor=page.nextCursor)
        }
    }
    fun upload(spec: UploadSpec, bytes: ByteArray, ticket: Long) {
        val currentScope = recoveryScope ?: return
        if (ticket != generation || !canEdit || !spec.matches(bytes)) return
        intent = PendingFileIntent.upload(currentScope,UUID.randomUUID().toString(),spec,bytes) ?: return
        mutable.value = mutable.value.copy(pending=true); retry()
    }
    fun delete(file: RemoteFile) {
        val currentScope = recoveryScope ?: return
        if (!canEdit || file.accountId != currentScope.accountId || file !in mutable.value.files) return
        intent = PendingFileIntent.delete(currentScope,UUID.randomUUID().toString(),file) ?: return
        mutable.value = mutable.value.copy(pending=true); retry()
    }
    fun retry() {
        val pending=intent ?: return
        if (mutable.value.busy || pending.scope!=recoveryScope) return
        run({ repo ->
            when (val staged=journal.stage(pending)) {
                is Outcome.Err -> staged
                is Outcome.Ok -> {
                    currentCoroutineContext().ensureActive()
                    val result=pending.file?.let { repo.delete(it,pending.key).map { Unit } } ?: resumeUpload(repo,pending)
                    when (result) {
                        is Outcome.Err -> result
                        is Outcome.Ok -> { currentCoroutineContext().ensureActive();journal.remove(pending) }
                    }
                }
            }
        }) { intent=null;mutable.value=mutable.value.copy(pending=false,preview=null);refresh() }
    }
    fun reviewPending(): FileRecoveryReview? {
        val pending=intent ?: return null
        if (mutable.value.busy || pending.scope!=recoveryScope) return null
        return FileRecoveryReview(generation,pending.key,pending.spec.name,pending.deleting)
    }
    fun forgetLocalRecovery(review: FileRecoveryReview) {
        val pending=intent ?: return
        if (mutable.value.busy || review.generation!=generation || pending.scope!=recoveryScope || review.key!=pending.key) return
        run({ journal.remove(pending) }) {
            generation++;intent=null;mutable.value=mutable.value.copy(pending=false,ready=false,failure=null,preview=null);refresh()
        }
    }
    private suspend fun resumeUpload(repo: RemoteFilesRepository, pending: PendingFileIntent): AppResult<Unit> {
        when (val known = repo.receipt(pending.key+".complete")) {
            is Outcome.Ok -> {
                val receipt = known.value
                if (receipt.key != pending.key+".complete" || receipt.action != "complete_upload" || receipt.principalId != pending.scope.principalId ||
                    receipt.file.accountId != pending.scope.accountId || receipt.file.ownerId != pending.scope.principalId ||
                    receipt.file.spec != pending.spec || receipt.file.status != FileStatus.AVAILABLE)
                    return Outcome.Err(Failure.Internal(FailureMeta("The upload receipt did not match the retained photo.")))
                return Outcome.Ok(Unit)
            }
            is Outcome.Err -> if (known.error !is Failure.NotFound) return known
        }
        currentCoroutineContext().ensureActive()
        return when (val requested = repo.requestUpload(pending.spec,pending.key+".request")) {
            is Outcome.Err -> requested
            is Outcome.Ok -> {
                currentCoroutineContext().ensureActive()
                when (val stored = repo.uploadContent(requested.value.file,pending.bytes())) {
                is Outcome.Err -> stored
                is Outcome.Ok -> { currentCoroutineContext().ensureActive();repo.complete(requested.value.file,pending.key+".complete").map { Unit } }
                }
            }
        }
    }
    fun open(file: RemoteFile) {
        if (!mutable.value.ready || mutable.value.busy || file.status != FileStatus.AVAILABLE || file.accountId != scope?.accountId || file !in mutable.value.files) return
        closePreview(); run({ it.download(file) }) { mutable.value = mutable.value.copy(preview=FilePreview(file.spec.name,it)) }
    }
    fun closePreview() { mutable.value = mutable.value.copy(preview=null) }
    fun importFailed(ticket: Long) {
        if (ticket == generation) mutable.value = mutable.value.copy(failure=Failure.Invalid(FailureMeta("Choose a supported photo up to 10 MiB."),emptyMap()))
    }
    private fun <T> run(operation: suspend (RemoteFilesRepository) -> AppResult<T>, receive: (T) -> Unit) {
        if (mutable.value.busy || scope == null) return; val repo = repository ?: return; val current = generation
        mutable.value = mutable.value.copy(busy=true,failure=null)
        val job = owner.launch(start=CoroutineStart.LAZY) {
            try {
                val result = operation(repo); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                mutable.value = mutable.value.copy(busy=false); task = null
                when (result) { is Outcome.Ok -> receive(result.value); is Outcome.Err -> mutable.value = mutable.value.copy(failure=result.error.publicInfo()) }
            } catch (error: CancellationException) {
                if (current == generation) { task = null; mutable.value = mutable.value.copy(busy=false) }; throw error
            } catch (_: Exception) {
                if (current == generation) { task = null; mutable.value = mutable.value.copy(busy=false,failure=Failure.Internal(FailureMeta("Files could not complete. Keep the pending operation and retry explicitly."))) }
            }
        }; task = job; job.start()
    }
}
