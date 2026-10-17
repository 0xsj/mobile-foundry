package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class InstallationState(val record: InstallationRecord? = null, val installationId: String? = null, val busy: Boolean = false, val needsRefresh: Boolean = true, val failure: Failure? = null)
/** Confined to the UI dispatcher. Current-state reconciliation, never a durable command queue. */
class InstallationController(private val owner: CoroutineScope, private val vault: InstallationVault, private val onUnexpectedError: (Throwable) -> Unit) {
    private val mutable = MutableStateFlow(InstallationState()); val state = mutable.asStateFlow()
    private var registry: InstallationRegistry? = null
    private var identity: InstallationIdentity? = null
    private var generation = 0L
    private var task: Job? = null
    val canRegister get() = registry != null && identity != null && !mutable.value.busy && !mutable.value.needsRefresh
    val canUnregister get() = canRegister && mutable.value.record?.registered == true && mutable.value.record?.currentBinding == true
    fun bind(registry: InstallationRegistry?) {
        generation++; task?.cancel(); task = null; this.registry = registry
        mutable.value = InstallationState(installationId=identity?.id)
        if (registry != null) refresh()
    }
    fun refresh() {
        if (mutable.value.busy || registry == null) return
        mutable.value = mutable.value.copy(needsRefresh=true)
        run({ repo -> when (val loaded = vault.loadOrCreate()) {
            is Outcome.Err -> loaded
            is Outcome.Ok -> repo.read(loaded.value).map { loaded.value to it }
        } }) { (identity,record) -> this.identity = identity; mutable.value = mutable.value.copy(installationId=identity.id,record=record,needsRefresh=false) }
    }
    fun register(target: DeliveryTarget) {
        if (!canRegister) return; val identity = identity ?: return; val record = mutable.value.record; val registration = UUID.randomUUID().toString()
        mutable.value = mutable.value.copy(needsRefresh=true)
        run({ it.register(identity,registration,target,record) }) { mutable.value = mutable.value.copy(record=it,needsRefresh=false) }
    }
    fun unregister() {
        if (!canUnregister) return; val identity = identity ?: return; val record = mutable.value.record ?: return
        mutable.value = mutable.value.copy(needsRefresh=true)
        run({ it.unregister(identity,record) }) { mutable.value = mutable.value.copy(record=it,needsRefresh=false) }
    }
    private fun <T> run(operation: suspend (InstallationRegistry) -> AppResult<T>, receive: (T) -> Unit) {
        if (mutable.value.busy) return; val repo = registry ?: return; val current = generation
        mutable.value = mutable.value.copy(busy=true,failure=null)
        val job = owner.launch(start=CoroutineStart.LAZY) {
            try {
                val answer = operation(repo); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                when (answer) { is Outcome.Ok -> receive(answer.value); is Outcome.Err -> mutable.value = mutable.value.copy(failure=answer.error.publicInfo()) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current == generation && currentCoroutineContext().isActive) { onUnexpectedError(error);if (current == generation) mutable.value = mutable.value.copy(failure=Failure.Internal(FailureMeta("An unexpected error occurred."))) } }
            finally { if (current == generation) { task = null; mutable.value = mutable.value.copy(busy=false) } }
        }; task = job; job.start()
    }
}
