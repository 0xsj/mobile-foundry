package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One attempt. Inject a client bound to the exact account/session lease. */
class HTTPRemoteNotesRepository(private val client: HTTPClient,val accountId: String,val principalId: String) : RemoteNotesRepository {
    private val admitted get() = RemoteNotesAdmission.uuid(accountId) && RemoteNotesAdmission.uuid(principalId)
    private val path get() = "accounts/$accountId/notes"
    override suspend fun page(after: String?,limit: Int): AppResult<RemoteNotesPage> {
        if (!admitted || limit !in 1..100 || after != null && !RemoteNotesAdmission.uuid(after)) return Outcome.Err(RemoteNotesAdmission.invalid)
        val query = listOf(QueryItem("limit",limit.toString())) + (after?.let { listOf(QueryItem("after",it)) } ?: emptyList())
        return request(HTTPMethod.GET,path,RequestOptions(query = query)).flatMap { entity ->
            val b = entity.body as? JsonObject; val rows = b?.get("notes") as? JsonArray
            if (entity.metadata.status != 200 || b?.get("account_id").text() != accountId || rows == null || rows.size > limit) return@flatMap Outcome.Err(failure(entity))
            val notes = ArrayList<VersionedNote>(); var prior = after ?: ""
            for (row in rows) { val n = note(row) ?: return@flatMap Outcome.Err(failure(entity)); if (n.id <= prior) return@flatMap Outcome.Err(failure(entity)); notes += n; prior = n.id }
            val next = when (val value = b["next_after"]) { JsonNull -> null; else -> value.text()?.takeIf { notes.size == limit && it == prior } ?: return@flatMap Outcome.Err(failure(entity)) }
            Outcome.Ok(RemoteNotesPage(notes,next))
        }
    }
    override suspend fun get(noteId: String): AppResult<VersionedNote> {
        if (!admitted || !RemoteNotesAdmission.uuid(noteId)) return Outcome.Err(RemoteNotesAdmission.invalid)
        return request(HTTPMethod.GET,"$path/$noteId").flatMap { entity ->
            val n = note(entity.body)
            if (entity.metadata.status != 200 || n == null || n.id != noteId || entity.metadata.etag != "\"${n.version}\"") Outcome.Err(failure(entity)) else Outcome.Ok(n)
        }
    }
    override suspend fun execute(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> {
        if (!admitted) return Outcome.Err(RemoteNotesAdmission.invalid)
        val method = when (command.action) { NoteWriteAction.CREATE -> HTTPMethod.POST; NoteWriteAction.UPDATE -> HTTPMethod.PATCH; NoteWriteAction.DELETE -> HTTPMethod.DELETE }
        val target = path + (command.noteId?.let { "/$it" } ?: "")
        val body = if (command.action == NoteWriteAction.DELETE) null else buildJsonObject { put("title",command.title) }
        return request(method,target,RequestOptions(body = body,ifMatch = command.expectedVersion?.let { "\"$it\"" },idempotencyKey = command.commandId)).flatMap { decodeReceipt(it,command,if (command.action == NoteWriteAction.CREATE) 201 else 200) }
    }
    override suspend fun receipt(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> {
        if (!admitted) return Outcome.Err(RemoteNotesAdmission.invalid)
        return request(HTTPMethod.GET,"accounts/$accountId/note-receipts/${command.commandId}").flatMap { decodeReceipt(it,command,200) }
    }
    private suspend fun request(method: HTTPMethod,path: String,options: RequestOptions = RequestOptions()): AppResult<Entity> {
        currentCoroutineContext().ensureActive()
        val result = client.requestEntity(method,path,options)
        currentCoroutineContext().ensureActive()
        return result
    }
    private fun decodeReceipt(entity: Entity,command: RemoteNoteWrite,status: Int): AppResult<NoteWriteReceipt> {
        val b = entity.body as? JsonObject ?: return Outcome.Err(failure(entity))
        val commandId = b["command_id"].text(); val account = b["account_id"].text(); val principal = b["principal_id"].text()
        val action = NoteWriteAction.entries.firstOrNull { it.wire == b["action"].text() }; val id = b["note_id"].text(); val title = b["title"].text(); val version = number(b["version"])
        if (entity.metadata.status != status || commandId == null || account == null || principal == null || action == null || id == null || title == null || version == null) return Outcome.Err(failure(entity))
        val r = NoteWriteReceipt.make(commandId,account,principal,action,id,title,version)
        if (r == null || account != accountId || principal != principalId || !r.confirms(command) || entity.metadata.etag != "\"$version\"") return Outcome.Err(failure(entity))
        return Outcome.Ok(r)
    }
    private fun note(value: JsonElement): VersionedNote? { val b = value as? JsonObject ?: return null; return VersionedNote.make(b["id"].text() ?: return null,b["title"].text() ?: return null,number(b["version"]) ?: return null) }
    private fun number(value: JsonElement?): Long? { val p = value as? JsonPrimitive ?: return null; if (p.isString) return null; val n = p.doubleOrNull ?: return null; return if (n.isFinite() && n >= 1 && n <= RemoteNotesAdmission.maxVersion.toDouble() && n == kotlin.math.floor(n)) n.toLong() else null }
    private fun JsonElement?.text() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun failure(entity: Entity) = Failure.Internal(FailureMeta("The remote Notes response was invalid.","notes.invalid_remote_response",requestId = entity.metadata.requestId,correlationId = entity.metadata.correlationId))
}
