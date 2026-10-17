package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-dispatcher-only process store. No auth, persistence or backend writes. */
class PushTokenStore {
    data class Snapshot(val target: DeliveryTarget? = null, val revision: Long = 0)
    private val mutable = MutableStateFlow(Snapshot())
    val state = mutable.asStateFlow()

    fun receive(target: DeliveryTarget?) {
        val previous = mutable.value
        if (previous.target?.route == target?.route && previous.target?.token == target?.token) return
        mutable.value = Snapshot(target, previous.revision + 1)
    }

    /** An explicit invalidation orders callbacks even while already empty. */
    fun invalidate() { mutable.value = Snapshot(revision = mutable.value.revision + 1) }
}

/** Publishes to its process store before success. Never prompts or sends registry HTTP. */
interface PushTokenPort { suspend fun collect(): AppResult<Unit> }

object PushTokenFailures {
    val unconfigured = Failure.Unavailable(FailureMeta("Push provider setup is missing. Follow the push token setup guide.", code = "push.unconfigured"))
    val unavailable = Failure.Unavailable(FailureMeta("The push provider is unavailable. Collect again when the device and provider are ready.", code = "push.unavailable"))
}

data class PushTokenState(val busy: Boolean = false, val failure: Failure? = null)

/** UI-dispatcher screen owner. Closing does not undo a provider registration. */
class PushTokenController(
    private val owner: CoroutineScope,
    val tokens: PushTokenStore,
    private val port: PushTokenPort,
    private val onUnexpectedError: (Throwable) -> Unit,
) {
    private val mutable = MutableStateFlow(PushTokenState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var closed = false
    val target get() = if (closed || mutable.value.busy) null else tokens.state.value.target

    fun collect() {
        if (closed || mutable.value.busy) return
        mutable.value = PushTokenState(busy = true)
        tokens.receive(null)
        val job = owner.launch(start = CoroutineStart.LAZY) {
            try {
                val answer = port.collect()
                currentCoroutineContext().ensureActive()
                if (!closed && answer is Outcome.Err) mutable.value = mutable.value.copy(failure = answer.error.publicInfo())
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!closed && currentCoroutineContext().isActive) {
                    onUnexpectedError(error)
                    if (!closed) mutable.value = mutable.value.copy(failure = Failure.Internal(FailureMeta("An unexpected error occurred.")))
                }
            } finally {
                if (!closed) { task = null; mutable.value = mutable.value.copy(busy = false) }
            }
        }
        task = job
        job.start()
    }

    fun close() {
        closed = true
        task?.cancel()
        task = null
        mutable.value = mutable.value.copy(busy = false)
    }
}
