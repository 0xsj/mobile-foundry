package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationAuthorization { NOT_GRANTED, ALLOWED, QUIET }
data class NotificationAccess(val authorization: NotificationAuthorization, val deliveryEnabled: Boolean) {
    val canPost get() = authorization != NotificationAuthorization.NOT_GRANTED && deliveryEnabled
}
@ConsistentCopyVisibility
data class LocalNotificationPreview private constructor(val id: String) {
    val payload get() = mapOf("version" to "1", "kind" to "local_preview", "preview_id" to id)
    companion object {
        fun make(id: String): LocalNotificationPreview? = if (RemoteServiceAdmission.uuid(id)) LocalNotificationPreview(id) else null
        fun decode(payload: Map<String,String>): LocalNotificationPreview? =
            if (payload.keys == setOf("version","kind","preview_id") && payload["version"] == "1" && payload["kind"] == "local_preview") payload["preview_id"]?.let(::make) else null
    }
}
/** UI-dispatcher port. Permission prompts happen only on explicit request. */
interface SystemNotificationPort {
    suspend fun access(): AppResult<NotificationAccess>
    suspend fun requestPermission(): AppResult<NotificationAccess>
    suspend fun postPreview(preview: LocalNotificationPreview): AppResult<Unit>
    suspend fun clearPreview(): AppResult<Unit>
    suspend fun openSettings(): AppResult<Unit>
}
object SystemNotificationFailures {
    val blocked = Failure.Forbidden(FailureMeta("Notifications are disabled. Check notification settings, then refresh.","notifications.device_blocked"))
    val unavailable = Failure.Unavailable(FailureMeta("The notification service is unavailable. Refresh before trying again.","notifications.device_unavailable"))
}
data class NotificationOpenState(val pending: NotificationOpen? = null, val lastOpened: NotificationOpen? = null, val lastReceived: RemoteNotificationPreview? = null) {
    val lastOpenedId get() = lastOpened?.previewId
}
/** Confined to UI dispatcher; bounded process-only opens, never an auth/deep-link authority. */
class NotificationOpenRouter {
    private val mutable = MutableStateFlow(NotificationOpenState()); val state = mutable.asStateFlow()
    private val recent = ArrayDeque<NotificationOpen>()
    fun recordReceipt(preview: RemoteNotificationPreview) { mutable.value = mutable.value.copy(lastReceived=preview) }
    fun receive(preview: LocalNotificationPreview) = receive(NotificationOpen.Local(preview))
    fun receive(preview: RemoteNotificationPreview) = receive(NotificationOpen.Remote(preview))
    fun receive(preview: NotificationOpen) {
        if (preview in recent) return
        recent.addLast(preview); if (recent.size > 32) recent.removeFirst()
        mutable.value = mutable.value.copy(pending=preview)
    }
    fun consume(preview: NotificationOpen) { if (mutable.value.pending == preview) mutable.value = mutable.value.copy(pending=null,lastOpened=preview) }
}
