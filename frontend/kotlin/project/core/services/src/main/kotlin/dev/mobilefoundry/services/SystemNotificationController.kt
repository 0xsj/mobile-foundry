package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SystemNotificationState(val access: NotificationAccess? = null, val busy: Boolean = false, val lastAcceptedId: String? = null, val failure: Failure? = null)
/** UI-dispatcher owner. OS acceptance is not proof of display or a durable delivery receipt. */
class SystemNotificationController(private val owner: CoroutineScope, private val port: SystemNotificationPort, private val onUnexpectedError: (Throwable) -> Unit) {
    private val mutable = MutableStateFlow(SystemNotificationState()); val state = mutable.asStateFlow()
    private var task: Job? = null; private var generation = 0L; private var closed = false
    val canPost get() = !closed && !mutable.value.busy && mutable.value.access?.canPost == true
    fun refresh() {
        if (closed || mutable.value.busy) return
        mutable.value = mutable.value.copy(access=null);run({ port.access() }) { mutable.value = mutable.value.copy(access=it) }
    }
    fun requestPermission() {
        if (closed || mutable.value.busy) return
        mutable.value = mutable.value.copy(access=null);run({ port.requestPermission() }) { mutable.value = mutable.value.copy(access=it) }
    }
    fun postPreview() {
        if (!canPost) return; val preview = checkNotNull(LocalNotificationPreview.make(UUID.randomUUID().toString()))
        run({ port.postPreview(preview) }) { mutable.value = mutable.value.copy(lastAcceptedId=preview.id) }
    }
    fun clearPreview() { run({ port.clearPreview() }) { mutable.value = mutable.value.copy(lastAcceptedId=null) } }
    fun openSettings() { run({ port.openSettings() }) {} }
    fun close() { closed = true;generation++;task?.cancel();task=null;mutable.value = mutable.value.copy(access=null,busy=false) }
    private fun <T> run(operation: suspend () -> AppResult<T>, receive: (T) -> Unit) {
        if (closed || mutable.value.busy) return; val current = generation
        mutable.value = mutable.value.copy(busy=true,failure=null)
        val job = owner.launch(start=CoroutineStart.LAZY) {
            try {
                val answer = operation();currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                when (answer) { is Outcome.Ok -> receive(answer.value);is Outcome.Err -> mutable.value = mutable.value.copy(access=null,failure=answer.error.publicInfo()) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current == generation && currentCoroutineContext().isActive) {
                    onUnexpectedError(error)
                    if (current == generation) mutable.value = mutable.value.copy(access=null,failure=Failure.Internal(FailureMeta("An unexpected error occurred.")))
                }
            } finally { if (current == generation) { task=null;mutable.value = mutable.value.copy(busy=false) } }
        };task=job;job.start()
    }
}
