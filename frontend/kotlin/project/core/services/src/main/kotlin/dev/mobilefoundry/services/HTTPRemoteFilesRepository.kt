package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** Account-bound, one attempt. Inject a client bound to the exact session lease. */
class HTTPRemoteFilesRepository(private val client: HTTPClient, val accountId: String, val principalId: String) : RemoteFilesRepository {
    private val admitted get() = FilesAdmission.uuid(accountId) && FilesAdmission.uuid(principalId)
    private val path get() = "accounts/$accountId/files"
    override suspend fun page(cursor: String?, limit: Int): AppResult<FilesPage> {
        if (!admitted || limit !in 1..100 || cursor != null && (cursor.isEmpty() || cursor.length > 512)) return Outcome.Err(FilesAdmission.invalid)
        val query = listOf(QueryItem("limit", limit.toString())) + (cursor?.let { listOf(QueryItem("cursor", it)) } ?: emptyList())
        return request(HTTPMethod.GET, path, RequestOptions(query = query)).flatMap { e ->
            val b = e.body as? JsonObject; val rows = b?.get("files") as? JsonArray
            if (e.metadata.status != 200 || b?.get("account_id").text() != accountId || rows == null || rows.size > limit) return@flatMap Outcome.Err(invalidResponse)
            val files = ArrayList<RemoteFile>(); var prior = ""
            for (row in rows) { val f = decode(row) ?: return@flatMap Outcome.Err(invalidResponse); if (f.status == FileStatus.DELETED || f.id <= prior) return@flatMap Outcome.Err(invalidResponse); files += f; prior = f.id }
            val next = when (val n = b["next_cursor"]) { JsonNull -> null; else -> n.text()?.takeIf { it.isNotEmpty() && it.length <= 512 && it != cursor && files.size == limit } ?: return@flatMap Outcome.Err(invalidResponse) }
            Outcome.Ok(FilesPage(files.toList(), next))
        }
    }
    override suspend fun requestUpload(spec: UploadSpec, key: String): AppResult<FileReceipt> {
        if (!admitted || !FilesAdmission.key(key)) return Outcome.Err(FilesAdmission.invalid)
        val body = buildJsonObject { put("name", spec.name); put("content_type", spec.contentType); put("byte_size", spec.byteSize); put("sha256", spec.sha256) }
        return request(HTTPMethod.POST, "$path/uploads", RequestOptions(body = body, idempotencyKey = key)).flatMap { e ->
            parseReceipt(e, key, "request_upload", 201).flatMap { r ->
                if (r.file.spec != spec || r.file.status != FileStatus.PENDING_UPLOAD || r.file.ownerId != principalId) Outcome.Err(invalidResponse) else Outcome.Ok(r)
            }
        }
    }
    override suspend fun uploadContent(file: RemoteFile, bytes: ByteArray): AppResult<Unit> {
        if (!admitted || file.accountId != accountId || file.status == FileStatus.DELETED || !file.spec.matches(bytes)) return Outcome.Err(FilesAdmission.invalid)
        return request(HTTPMethod.PUT, "$path/${file.id}/content", RequestOptions(binaryBody = BinaryBody(bytes, file.spec.contentType))).flatMap { e ->
            if (e.metadata.status != 200 || (e.body as? JsonObject)?.get("status").text() != "stored") Outcome.Err(invalidResponse) else Outcome.Ok(Unit)
        }
    }
    override suspend fun complete(file: RemoteFile, key: String) = mutate(file, key, false)
    override suspend fun delete(file: RemoteFile, key: String) = mutate(file, key, true)
    private suspend fun mutate(file: RemoteFile, key: String, remove: Boolean): AppResult<FileReceipt> {
        if (!admitted || !FilesAdmission.key(key) || file.accountId != accountId || file.status == FileStatus.DELETED || !remove && file.status != FileStatus.PENDING_UPLOAD) return Outcome.Err(FilesAdmission.invalid)
        return request(if (remove) HTTPMethod.DELETE else HTTPMethod.POST, "$path/${file.id}" + if (remove) "" else "/complete", RequestOptions(ifMatch = "\"${file.version}\"", idempotencyKey = key)).flatMap { e ->
            parseReceipt(e, key, if (remove) "delete" else "complete_upload", 200).flatMap { r ->
                if (r.file.id != file.id || r.file.ownerId != file.ownerId || r.file.spec != file.spec || r.file.version != file.version + 1 || r.file.status != if (remove) FileStatus.DELETED else FileStatus.AVAILABLE) Outcome.Err(invalidResponse) else Outcome.Ok(r)
            }
        }
    }
    override suspend fun receipt(key: String): AppResult<FileReceipt> {
        if (!admitted || !FilesAdmission.key(key)) return Outcome.Err(FilesAdmission.invalid)
        return request(HTTPMethod.GET, "receipts/files/$key", RequestOptions(query = listOf(QueryItem("account_id", accountId)))).flatMap { parseReceipt(it, key, null, 200) }
    }
    override suspend fun download(file: RemoteFile): AppResult<ByteArray> {
        if (!admitted || file.accountId != accountId || file.status != FileStatus.AVAILABLE) return Outcome.Err(FilesAdmission.invalid)
        currentCoroutineContext().ensureActive()
        val result = client.requestBytes(HTTPMethod.GET, "$path/${file.id}/content")
        currentCoroutineContext().ensureActive()
        return result.flatMap { e -> if (e.metadata.status != 200 || e.metadata.etag != "\"${file.version}\"" || e.contentType != file.spec.contentType || !file.spec.matches(e.bytes)) Outcome.Err(invalidResponse) else Outcome.Ok(e.bytes) }
    }
    private suspend fun request(method: HTTPMethod, path: String, options: RequestOptions = RequestOptions()): AppResult<Entity> {
        currentCoroutineContext().ensureActive(); val answer = client.requestEntity(method, path, options); currentCoroutineContext().ensureActive(); return answer
    }
    private fun parseReceipt(e: Entity, key: String, action: String?, status: Int): AppResult<FileReceipt> {
        val b = e.body as? JsonObject ?: return Outcome.Err(invalidResponse)
        val actual = b["action"].text(); val file = b["file"]?.let(::decode)
        if (e.metadata.status != status || b["key"].text() != key || b["principal_id"].text() != principalId || actual !in listOf("request_upload", "complete_upload", "delete") || action != null && action != actual || file == null || e.metadata.etag != "\"${file.version}\"") return Outcome.Err(invalidResponse)
        if (!(actual == "request_upload" && file.status == FileStatus.PENDING_UPLOAD || actual == "complete_upload" && file.status == FileStatus.AVAILABLE || actual == "delete" && file.status == FileStatus.DELETED)) return Outcome.Err(invalidResponse)
        return Outcome.Ok(FileReceipt(key, actual!!, principalId, file))
    }
    private fun decode(value: JsonElement): RemoteFile? {
        val b = value as? JsonObject ?: return null
        if (b["account_id"].text() != accountId) return null
        val spec = UploadSpec.make(b["name"].text() ?: return null, b["content_type"].text() ?: return null, integer(b["byte_size"])?.toInt() ?: return null, b["sha256"].text() ?: return null) ?: return null
        return RemoteFile.make(b["id"].text() ?: return null, accountId, b["owner_id"].text() ?: return null, spec, FileStatus.entries.firstOrNull { it.wire == b["status"].text() } ?: return null, integer(b["version"]) ?: return null)
    }
    private fun integer(value: JsonElement?): Long? { val p = value as? JsonPrimitive ?: return null; if (p.isString) return null; val n = p.doubleOrNull ?: return null; return if (n.isFinite() && n >= 1 && n <= 10 * 1024 * 1024 && n == kotlin.math.floor(n)) n.toLong() else null }
    private fun JsonElement?.text() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private val invalidResponse get() = Failure.Internal(FailureMeta("The remote Files response was invalid.", "files.invalid_remote_response"))
}
