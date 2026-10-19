package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FileDestinationState(val presented: Boolean = false, val file: RemoteFile? = null,
    val bytes: ByteArray? = null, val busy: Boolean = false, val failure: Failure? = null)
/** A notice is a hint; current exact-lease reads supply the destination. */
class FileDestinationStore(private val owner: CoroutineScope) {
    private val mutable = MutableStateFlow(FileDestinationState()); val state = mutable.asStateFlow()
    private var reader: RemoteFileDetailReader? = null
    private var scope: SessionScope? = null
    private var target: String? = null
    private var generation = 0L
    private var task: Job? = null
    fun bind(reader: RemoteFileDetailReader?, scope: SessionScope?) { close(); this.reader = reader; this.scope = scope }
    fun open(notice: InboxNotification) {
        val scope = scope ?: return
        if (reader == null || notice.accountId != scope.accountId || notice.principalId != scope.principalId) return
        close(); target = notice.fileId; mutable.value = FileDestinationState(presented = true); refresh()
    }
    fun close() {
        generation++; task?.cancel(); task = null; target = null; mutable.value = FileDestinationState()
    }
    fun refresh() {
        val target = target ?: return
        if (state.value.busy || !state.value.presented) return
        mutable.value = state.value.copy(file = null, bytes = null)
        run({ it.get(target) }) { file ->
            mutable.value = if (file.id != target || file.accountId != scope?.accountId || file.status == FileStatus.DELETED)
                state.value.copy(failure = invalidResponse) else state.value.copy(file = file)
        }
    }
    fun download() {
        val file = state.value.file ?: return
        if (state.value.busy || file.status != FileStatus.AVAILABLE) return
        mutable.value = state.value.copy(bytes = null)
        run({ it.download(file) }) { bytes -> mutable.value = if (file.spec.matches(bytes)) state.value.copy(bytes = bytes.copyOf()) else state.value.copy(failure = invalidResponse) }
    }
    private val invalidResponse get() = Failure.Internal(FailureMeta("The file response could not be verified.", "files.invalid_remote_response"))
    private fun <T> run(operation: suspend (RemoteFileDetailReader) -> AppResult<T>, receive: (T) -> Unit) {
        val reader = reader ?: return; if (scope == null) return; val current = generation
        mutable.value = state.value.copy(busy = true, failure = null)
        val job = owner.launch(start = CoroutineStart.LAZY) {
            try {
                val answer = operation(reader); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                task = null; mutable.value = state.value.copy(busy = false)
                when (answer) { is Outcome.Ok -> receive(answer.value); is Outcome.Err -> mutable.value = state.value.copy(failure = answer.error.publicInfo()) }
            } catch (error: CancellationException) {
                if (current == generation) { task = null; mutable.value = state.value.copy(busy = false) }; throw error
            } catch (_: Exception) {
                if (current == generation) { task = null; mutable.value = state.value.copy(busy = false, failure = Failure.Internal(FailureMeta("The file could not be opened. Try refreshing."))) }
            }
        }; task = job; job.start()
    }
}
