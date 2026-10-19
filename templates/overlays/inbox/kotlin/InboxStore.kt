package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InboxState(val items: List<InboxNotification> = emptyList(), val unreadCount: Long? = null,
    val mediaEnabled: Boolean? = null, val unreadOnly: Boolean = false, val busy: Boolean = false,
    val needsRefresh: Boolean = false, val failure: Failure? = null, val nextCursor: String? = null)
/** Owner lifetime only. Unconfirmed settings are refreshed, not automatically replayed. */
class InboxStore(private val owner: CoroutineScope) {
    private val mutable = MutableStateFlow(InboxState()); val state = mutable.asStateFlow()
    private var repository: NotificationInboxRepository? = null
    private var scope: SessionScope? = null
    private var generation = 0L
    private var task: Job? = null
    val canEdit get() = repository != null && mutable.value.mediaEnabled != null && !mutable.value.busy && !mutable.value.needsRefresh
    val canLoadMore get() = canEdit && mutable.value.nextCursor != null && mutable.value.items.size < 200
    fun bind(repository: NotificationInboxRepository?, scope: SessionScope?) {
        generation++; task?.cancel(); task = null; this.repository = repository; this.scope = scope
        mutable.value = InboxState()
        if (repository != null && scope != null) refresh()
    }
    fun chooseUnreadOnly(value: Boolean) {
        if (mutable.value.busy || value == mutable.value.unreadOnly) return
        mutable.value = mutable.value.copy(unreadOnly=value,items=emptyList(),nextCursor=null,needsRefresh=true); refresh()
    }
    fun refresh() {
        val filter = mutable.value.unreadOnly
        run({ repo -> when (val page = repo.page(null,20,filter)) {
            is Outcome.Err -> page
            is Outcome.Ok -> repo.mediaEnabled().map { page.value to it }
        } }) { (page,enabled) -> mutable.value = mutable.value.copy(items=page.items,unreadCount=page.unreadCount,nextCursor=page.nextCursor,mediaEnabled=enabled,needsRefresh=false) }
    }
    fun loadMore() {
        if (!canLoadMore) return; val cursor = mutable.value.nextCursor ?: return; val filter = mutable.value.unreadOnly
        run({ it.page(cursor,20,filter) }) { page ->
            if (page.items.any { item -> mutable.value.items.any { it.id == item.id } }) {
                mutable.value = mutable.value.copy(needsRefresh=true,failure=Failure.Internal(FailureMeta("The inbox changed while paging. Refresh to continue.")))
            } else mutable.value = mutable.value.copy(items=mutable.value.items+page.items,unreadCount=page.unreadCount,nextCursor=page.nextCursor)
        }
    }
    fun markRead(item: InboxNotification) {
        if (!canEdit || !item.isUnread || item.accountId != scope?.accountId || item.principalId != scope?.principalId || mutable.value.items.none { it.id == item.id }) return
        mutable.value = mutable.value.copy(needsRefresh=true)
        run({ it.markRead(item) }) { refresh() }
    }
    fun setMediaEnabled(enabled: Boolean) {
        if (!canEdit || mutable.value.mediaEnabled == enabled) return
        mutable.value = mutable.value.copy(needsRefresh=true)
        run({ it.setMediaEnabled(enabled) }) { refresh() }
    }
    private fun <T> run(operation: suspend (NotificationInboxRepository) -> AppResult<T>, receive: (T) -> Unit) {
        if (mutable.value.busy || scope == null) return; val repo = repository ?: return; val current = generation
        mutable.value = mutable.value.copy(busy=true,failure=null)
        val job = owner.launch(start=CoroutineStart.LAZY) {
            try {
                val result = operation(repo); currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                mutable.value = mutable.value.copy(busy=false); task = null
                when (result) { is Outcome.Ok -> receive(result.value); is Outcome.Err -> mutable.value = mutable.value.copy(failure=result.error.publicInfo()) }
            } catch (error: CancellationException) {
                if (current == generation) { task = null; mutable.value = mutable.value.copy(busy=false) }
                throw error
            } catch (_: Exception) {
                if (current == generation) { task = null; mutable.value = mutable.value.copy(busy=false,failure=Failure.Internal(FailureMeta("The inbox could not complete. Refresh to confirm server state."))) }
            }
        }; task = job; job.start()
    }
}
