package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** One attempt on an injected exact-lease client; no renewal/replay. */
class HTTPNotificationInboxRepository(private val client: HTTPClient, val accountId: String, val principalId: String) : NotificationInboxRepository {
    private val admitted get() = RemoteServiceAdmission.uuid(accountId) && RemoteServiceAdmission.uuid(principalId)
    private val path get() = "accounts/$accountId/notifications"
    override suspend fun page(cursor: String?, limit: Int, unreadOnly: Boolean): AppResult<NotificationInboxPage> {
        if (!admitted || limit !in 1..100 || cursor != null && (cursor.isEmpty() || cursor.toByteArray().size > 512)) return Outcome.Err(InboxAdmission.invalid)
        val query = listOf(QueryItem("limit",limit.toString()),QueryItem("unread",unreadOnly.toString())) + (cursor?.let { listOf(QueryItem("cursor",it)) } ?: emptyList())
        return request(HTTPMethod.GET,path,RequestOptions(query=query)).flatMap { e ->
            val b = e.body as? JsonObject; val rows = b?.get("items") as? JsonArray; val count = number(b?.get("unread_count"))
            if (e.metadata.status != 200 || b?.get("account_id").text() != accountId || rows == null || rows.size > limit || count == null) return@flatMap Outcome.Err(invalid(e))
            val items = ArrayList<InboxNotification>(); val ids = HashSet<String>()
            for (row in rows) { val item = decode(row) ?: return@flatMap Outcome.Err(invalid(e)); if (!ids.add(item.id) || unreadOnly && !item.isUnread) return@flatMap Outcome.Err(invalid(e)); items += item }
            if (count < items.count { it.isUnread }) return@flatMap Outcome.Err(invalid(e))
            val next = when (val v = b["next_cursor"]) {
                JsonNull -> null
                else -> v.text()?.takeIf { it.isNotEmpty() && it.toByteArray().size <= 512 && it != cursor && items.size == limit } ?: return@flatMap Outcome.Err(invalid(e))
            }
            Outcome.Ok(NotificationInboxPage(items.toList(),count,next))
        }
    }
    override suspend fun markRead(item: InboxNotification): AppResult<InboxNotification> {
        if (!admitted || item.accountId != accountId || item.principalId != principalId) return Outcome.Err(InboxAdmission.invalid)
        return request(HTTPMethod.POST,"$path/${item.id}/read").flatMap { e ->
            val result = decode(e.body)
            if (e.metadata.status != 200 || result == null || result.id != item.id || result.fileId != item.fileId || result.occurredAt != item.occurredAt || result.createdAt != item.createdAt || result.readAt == null || item.readAt != null && item.readAt != result.readAt) Outcome.Err(invalid(e)) else Outcome.Ok(result)
        }
    }
    override suspend fun mediaEnabled() = preference(null)
    override suspend fun setMediaEnabled(enabled: Boolean) = preference(enabled)
    private suspend fun preference(enabled: Boolean?): AppResult<Boolean> {
        if (!admitted) return Outcome.Err(InboxAdmission.invalid)
        return request(if (enabled == null) HTTPMethod.GET else HTTPMethod.PUT,"accounts/$accountId/notification-preferences",RequestOptions(body=enabled?.let { buildJsonObject { put("media_in_app",it) } })).flatMap { e ->
            val b = e.body as? JsonObject; val p = b?.get("media_in_app") as? JsonPrimitive; val value = p?.takeUnless { it.isString }?.booleanOrNull
            if (e.metadata.status != 200 || b?.get("account_id").text() != accountId || value == null || enabled != null && value != enabled) Outcome.Err(invalid(e)) else Outcome.Ok(value)
        }
    }
    private suspend fun request(method: HTTPMethod,path: String,options: RequestOptions = RequestOptions()): AppResult<Entity> {
        currentCoroutineContext().ensureActive(); val result = client.requestEntity(method,path,options); currentCoroutineContext().ensureActive(); return result
    }
    private fun decode(value: JsonElement): InboxNotification? {
        val b = value as? JsonObject ?: return null
        if (b["type"].text() != "files.upload_ready" || b["category"].text() != "media") return null
        val occurred = RemoteServiceAdmission.timestamp(b["occurred_at"].text()) ?: return null; val created = RemoteServiceAdmission.timestamp(b["created_at"].text()) ?: return null
        val read = when (val v = b["read_at"]) { JsonNull -> null; else -> RemoteServiceAdmission.timestamp(v.text()) ?: return null }
        return InboxNotification.make(b["id"].text() ?: return null,accountId,principalId,b["file_id"].text() ?: return null,occurred,created,read)
    }
    private fun number(value: JsonElement?): Long? {
        val p = value as? JsonPrimitive ?: return null; if (p.isString) return null
        val n = p.doubleOrNull ?: return null; return if (n.isFinite() && n >= 0 && n <= 9_007_199_254_740_991.0 && n == kotlin.math.floor(n)) n.toLong() else null
    }
    private fun JsonElement?.text() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun invalid(e: Entity) = Failure.Internal(FailureMeta("The remote inbox response was invalid.","notifications.invalid_remote_response",requestId=e.metadata.requestId,correlationId=e.metadata.correlationId))
}
