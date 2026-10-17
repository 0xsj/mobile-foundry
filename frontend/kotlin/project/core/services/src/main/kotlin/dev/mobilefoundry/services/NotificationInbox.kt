package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.time.Instant

/** Identifiers and timestamps only; display copy belongs to the app. */
@ConsistentCopyVisibility
data class InboxNotification private constructor(val id: String, val accountId: String, val principalId: String, val fileId: String,
    val occurredAt: Instant, val createdAt: Instant, val readAt: Instant?) {
    val isUnread get() = readAt == null
    companion object {
        fun make(id: String, accountId: String, principalId: String, fileId: String, occurredAt: Instant, createdAt: Instant, readAt: Instant?): InboxNotification? {
            if (!listOf(id,accountId,principalId,fileId).all(RemoteServiceAdmission::uuid) || !listOfNotNull(occurredAt,createdAt,readAt).all(RemoteServiceAdmission::date)) return null
            return InboxNotification(id,accountId,principalId,fileId,occurredAt,createdAt,readAt)
        }
    }
}
data class NotificationInboxPage(val items: List<InboxNotification>, val unreadCount: Long, val nextCursor: String?)
interface NotificationInboxReader {
    suspend fun page(cursor: String? = null, limit: Int = 20, unreadOnly: Boolean = false): AppResult<NotificationInboxPage>
    suspend fun mediaEnabled(): AppResult<Boolean>
}
interface NotificationInboxWriter {
    suspend fun markRead(item: InboxNotification): AppResult<InboxNotification>
    suspend fun setMediaEnabled(enabled: Boolean): AppResult<Boolean>
}
interface NotificationInboxRepository : NotificationInboxReader, NotificationInboxWriter
internal object InboxAdmission {
    val invalid = Failure.Invalid(FailureMeta("Check the inbox account, item and page request.","notifications.invalid_input"),emptyMap())
}
