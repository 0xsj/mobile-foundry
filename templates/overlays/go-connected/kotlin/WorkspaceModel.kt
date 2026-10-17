package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkspaceState(val displayAccount: Account? = null, val current: ManagedAccount? = null, val created: ManagedAccount? = null, val busy: Boolean = false,
    val failure: Failure? = null, val message: String? = null, val createUncertain: Boolean = false, val reviewed: Boolean = false,
    val reviewAccounts: List<Account> = emptyList(), val nextCursor: String? = null)

/** Product owner of non-replayable Account commands; Notes intent stays independent. */
class WorkspaceModel(private val scope: CoroutineScope, private val controller: SessionController) {
    private val mutableState = MutableStateFlow(WorkspaceState())
    val state = mutableState.asStateFlow()
    private var service: AccountManagement? = null
    private var directory: AccountDirectory? = null
    private var lease: SessionLease? = null
    private var task: Job? = null
    private var generation = 0L
    private var creating = false
    private var uncertainOwner: String? = null
    private fun owner(lease: SessionLease) = "${lease.scope.authorityId}/${lease.scope.principalId}"
    val canRename get() = !state.value.busy && state.value.current != null && lease?.let(controller::isCurrent) == true
    val canCreate get() = canRename && !state.value.createUncertain && state.value.created == null
    fun bind(service: AccountManagement?, directory: AccountDirectory?, lease: SessionLease?) {
        cancel(); this.service = service; this.directory = directory; this.lease = lease
        val uncertain = lease?.let { owner(it) == uncertainOwner } == true
        mutableState.value = WorkspaceState(createUncertain = uncertain, message = if (uncertain) interruptedCreate else null)
    }
    fun cancel() {
        if (creating) lease?.let { uncertainOwner = owner(it); mutableState.value = state.value.copy(createUncertain = true, message = interruptedCreate) }
        generation++; task?.cancel(); task = null; creating = false
        mutableState.value = state.value.copy(busy = false, current = null, reviewed = false)
    }
    fun refresh() {
        val lease = lease ?: return; val service = service ?: return
        if (state.value.busy || !controller.isCurrent(lease)) return
        mutableState.value = state.value.copy(current = null)
        perform(operation = { service.read(lease.scope.accountId) }) { value ->
            if (value.account.id != lease.scope.accountId || value.ownerId != lease.scope.principalId) rejectWrite()
            else mutableState.value = state.value.copy(current = value, displayAccount = value.account, message = if (state.value.createUncertain) interruptedCreate else "Current workspace loaded. Review its name before renaming.")
        }
    }
    fun create(rawName: String) {
        val service = service ?: return; val lease = lease ?: return
        if (!canCreate) return
        val name = admit(rawName) ?: return
        mutableState.value = state.value.copy(reviewed = false, reviewAccounts = emptyList(), nextCursor = null)
        creating = true
        perform(write = true, operation = { service.create(name) }) { value ->
            if (value.ownerId != lease.scope.principalId || value.account.id == lease.scope.accountId || value.version != 1L || value.account.name != name.value) rejectWrite(true)
            else mutableState.value = state.value.copy(created = value, message = "Workspace created. Choose it explicitly from Workspaces.")
        }
    }
    fun rename(rawName: String) {
        val service = service ?: return; val before = state.value.current ?: return
        if (!canRename) return
        val name = admit(rawName) ?: return
        perform(write = true, operation = { service.rename(before, name) }) { value ->
            if (value.account.id != before.account.id || value.ownerId != before.ownerId || value.version != before.version + 1 || value.createdAtMicros != before.createdAtMicros || value.updatedAtMicros < before.updatedAtMicros || value.account.name != name.value) rejectWrite()
            else mutableState.value = state.value.copy(current = value, displayAccount = value.account, message = "Workspace renamed.")
        }
    }
    fun review(cursor: String? = null) {
        val lease = lease ?: return; val directory = directory ?: return
        if (state.value.busy || !controller.isCurrent(lease)) return
        val expected = generation
        mutableState.value = state.value.copy(busy = true, failure = null, reviewed = false)
        task = scope.launch {
            try {
                val answer = directory.list(cursor, 20); currentCoroutineContext().ensureActive()
                if (expected != generation || !controller.isCurrent(lease)) return@launch
                mutableState.value = when (answer) {
                    is Outcome.Ok -> state.value.copy(reviewAccounts = answer.value.accounts, nextCursor = answer.value.nextCursor, reviewed = true)
                    is Outcome.Err -> state.value.copy(failure = answer.error.publicInfo())
                }
            } catch (_: Exception) {
                if (expected == generation && controller.isCurrent(lease)) mutableState.value = state.value.copy(failure = Failure.Unavailable(FailureMeta("Workspaces could not be reviewed. Refresh again.")))
            } finally { if (expected == generation) { mutableState.value = state.value.copy(busy = false); task = null } }
        }
    }
    fun allowNewCreate() {
        if (!state.value.reviewed || !state.value.createUncertain || state.value.busy || lease?.let(controller::isCurrent) != true) return
        uncertainOwner = null
        mutableState.value = state.value.copy(createUncertain = false, created = null, message = "A new create may duplicate an earlier workspace. Enter a name deliberately.")
    }
    fun anotherCreate() { if (!state.value.busy && !state.value.createUncertain) mutableState.value = state.value.copy(created = null) }
    private fun admit(rawName: String): AccountName? = when (val answer = AccountName.make(rawName)) {
        is Outcome.Ok -> answer.value
        is Outcome.Err -> { mutableState.value = state.value.copy(failure = answer.error); null }
    }
    private fun rejectWrite(create: Boolean = false) {
        if (create) lease?.let { uncertainOwner = owner(it) }
        mutableState.value = state.value.copy(current = null, displayAccount = if (create) state.value.displayAccount else null, failure = Failure.Internal(FailureMeta("The result was not confirmed. Refresh before another write.")),
            createUncertain = state.value.createUncertain || create, message = if (create) interruptedCreate else state.value.message)
    }
    private fun perform(write: Boolean = false, operation: suspend () -> AppResult<ManagedAccount>, receive: (ManagedAccount) -> Unit) {
        val lease = lease ?: return
        if (!controller.isCurrent(lease)) return
        val expected = generation
        mutableState.value = state.value.copy(busy = true, failure = null, message = null)
        task = scope.launch {
            try {
                val answer = operation(); currentCoroutineContext().ensureActive()
                if (expected != generation || !controller.isCurrent(lease)) return@launch
                when (answer) {
                    is Outcome.Ok -> receive(answer.value)
                    is Outcome.Err -> {
                        val uncertain = creating && answer.error.kind in setOf(FailureKind.UNAVAILABLE, FailureKind.TIMEOUT, FailureKind.CANCELED, FailureKind.INTERNAL)
                        if (uncertain) uncertainOwner = owner(lease)
                        mutableState.value = state.value.copy(failure = answer.error.publicInfo(), current = if (write) null else state.value.current,
                            displayAccount = if (write && !creating) null else state.value.displayAccount,
                            createUncertain = state.value.createUncertain || uncertain,
                            message = if (uncertain) interruptedCreate else if (write) "Write not confirmed. Refresh the current workspace before another rename." else null)
                    }
                }
            } catch (_: Exception) {
                if (expected == generation && controller.isCurrent(lease)) {
                    if (creating) uncertainOwner = owner(lease)
                    mutableState.value = state.value.copy(current = null, displayAccount = if (write && !creating) null else state.value.displayAccount, failure = Failure.Unavailable(FailureMeta("The operation was interrupted. Refresh before another write.")),
                        createUncertain = state.value.createUncertain || creating, message = if (creating) interruptedCreate else null)
                }
            } finally {
                if (expected == generation) {
                    if (creating && !controller.isCurrent(lease)) uncertainOwner = owner(lease)
                    mutableState.value = state.value.copy(busy = false); creating = false; task = null
                }
            }
        }
    }
    companion object { const val interruptedCreate = "Creation was not confirmed. The workspace may already exist. Review workspaces before choosing to create again." }
}
