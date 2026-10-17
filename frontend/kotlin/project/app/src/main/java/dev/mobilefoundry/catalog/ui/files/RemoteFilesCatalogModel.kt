package dev.mobilefoundry.catalog.ui.files

import dev.mobilefoundry.catalog.ui.camera.PhotoDecoder
import dev.mobilefoundry.graphics.RasterImage
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private sealed interface PendingFile {
    class Upload(val key: String, val spec: UploadSpec, bytes: ByteArray) : PendingFile { private val data = bytes.copyOf(); val bytes get() = data.copyOf() }
    data class Delete(val key: String, val file: RemoteFile) : PendingFile
}
data class RemoteFilesState(val files: List<RemoteFile> = emptyList(), val busy: Boolean = false, val pending: Boolean = false, val hasMore: Boolean = false, val failure: Failure? = null, val preview: RasterImage? = null)
/** Pending work is account-bound for this owner only; this is not a durable queue. */
class RemoteFilesCatalogModel(private val owner: CoroutineScope) {
    private val mutable = MutableStateFlow(RemoteFilesState()); val state = mutable.asStateFlow()
    private var repository: RemoteFilesRepository? = null
    private var account: SessionScope? = null
    private val retained = mutableMapOf<SessionScope, PendingFile>()
    private var task: Job? = null
    private var generation = 0L
    val selectionTicket get() = generation
    val canUpload get() = repository != null && !mutable.value.busy && !mutable.value.pending
    fun bind(repository: RemoteFilesRepository?, account: SessionScope?) {
        generation++; task?.cancel(); task = null; this.repository = repository; this.account = account
        mutable.value = RemoteFilesState(pending = retained[account] != null)
        if (repository != null) refresh()
    }
    fun refresh() { run({ it.page(null, 20) }) { page -> mutable.value = mutable.value.copy(files = page.files, hasMore = page.nextCursor != null) } }
    fun upload(spec: UploadSpec, bytes: ByteArray, ticket: Long) {
        if (!canUpload || ticket != generation) return
        val account = account ?: return
        retained[account] = PendingFile.Upload(UUID.randomUUID().toString(), spec, bytes)
        mutable.value = mutable.value.copy(pending = true); retry()
    }
    fun delete(file: RemoteFile) {
        if (!canUpload) return; val account = account ?: return
        retained[account] = PendingFile.Delete(UUID.randomUUID().toString(), file)
        mutable.value = mutable.value.copy(pending = true); retry()
    }
    fun retry() {
        val account = account ?: return; val pending = retained[account] ?: return
        run({ repo ->
            when (pending) {
                is PendingFile.Upload -> {
                    when (val created = repo.requestUpload(pending.spec, pending.key + ".request")) {
                        is Outcome.Err -> created
                        is Outcome.Ok -> when (val uploaded = repo.uploadContent(created.value.file, pending.bytes)) {
                            is Outcome.Err -> uploaded
                            is Outcome.Ok -> repo.complete(created.value.file, pending.key + ".complete")
                        }
                    }
                }
                is PendingFile.Delete -> repo.delete(pending.file, pending.key)
            }
        }) { _ -> retained.remove(account); mutable.value = mutable.value.copy(pending = false); refresh() }
    }
    fun open(file: RemoteFile) {
        run({ repo -> when (val result = repo.download(file)) {
            is Outcome.Err -> result
            is Outcome.Ok -> withContext(Dispatchers.Default) { PhotoDecoder.decodeLibrary(result.value) }
        } }) { image -> mutable.value = mutable.value.copy(preview = image) }
    }
    fun closePreview() { mutable.value = mutable.value.copy(preview = null) }
    fun importFailed(failure: Failure, ticket: Long) { if (ticket == generation) mutable.value = mutable.value.copy(failure = failure.publicInfo()) }
    private fun <T> run(operation: suspend (RemoteFilesRepository) -> AppResult<T>, receive: (T) -> Unit) {
        if (mutable.value.busy) return; val repo = repository ?: return; val current = generation
        mutable.value = mutable.value.copy(busy = true, failure = null)
        val job = owner.launch(start = CoroutineStart.LAZY) {
            try {
                val answer = operation(repo); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                mutable.value = mutable.value.copy(busy = false); task = null
                when (answer) { is Outcome.Ok -> receive(answer.value); is Outcome.Err -> mutable.value = mutable.value.copy(failure = answer.error.publicInfo()) }
            } catch (_: CancellationException) {} catch (_: Exception) {
                if (current == generation) { task = null; mutable.value = mutable.value.copy(busy = false, failure = Failure.Internal(FailureMeta("Files could not complete. Keep the pending operation for retry."))) }
            }
        }; task = job; job.start()
    }
}
