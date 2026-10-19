package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Original deployment/actor/account, never credentials or a lease epoch. */
data class PendingFileScope private constructor(val authorityId: String, val principalId: String, val accountId: String) {
    companion object {
        fun make(authorityId: String, principalId: String, accountId: String): PendingFileScope? =
            if (authorityId.matches(Regex("[A-Za-z0-9._-]{1,200}")) && FilesAdmission.uuid(principalId) && FilesAdmission.uuid(accountId)) PendingFileScope(authorityId,principalId,accountId) else null
    }
}
class PendingFileIntent private constructor(val scope: PendingFileScope, val key: String, val spec: UploadSpec, val file: RemoteFile?, bytes: ByteArray) {
    private val retained = bytes.copyOf()
    val deleting get() = file != null
    fun bytes() = retained.copyOf()
    fun matches(other: PendingFileIntent) = metadata().contentEquals(other.metadata()) && retained.contentEquals(other.retained)
    fun metadata(): ByteArray = buildJsonObject {
        put("format","pending-files.v1");put("key",key);put("action",if (deleting) "delete" else "upload")
        put("scope",buildJsonObject { put("authority_id",scope.authorityId);put("principal_id",scope.principalId);put("account_id",scope.accountId) })
        put("spec",buildJsonObject { put("name",spec.name);put("content_type",spec.contentType);put("byte_size",spec.byteSize);put("sha256",spec.sha256) })
        put("file",file?.let { buildJsonObject { put("id",it.id);put("status",it.status.wire);put("version",it.version) } } ?: JsonNull)
    }.toString().toByteArray(Charsets.UTF_8)
    companion object {
        fun upload(scope: PendingFileScope,key: String,spec: UploadSpec,bytes: ByteArray): PendingFileIntent? =
            if (FilesAdmission.uuid(key) && spec.matches(bytes)) PendingFileIntent(scope,key,spec,null,bytes) else null
        fun delete(scope: PendingFileScope,key: String,file: RemoteFile): PendingFileIntent? =
            if (FilesAdmission.uuid(key) && file.accountId==scope.accountId && file.ownerId==scope.principalId && file.status!=FileStatus.DELETED) PendingFileIntent(scope,key,file.spec,file,byteArrayOf()) else null
        fun decode(metadata: ByteArray,bytes: ByteArray): AppResult<PendingFileIntent> {
            if (metadata.isEmpty() || metadata.size>16_384 || bytes.size>10*1024*1024) return Outcome.Err(PendingFilesFailure.invalid)
            val root = try {
                val text=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(metadata)).toString()
                Json.parseToJsonElement(text) as? JsonObject
            } catch (_: java.nio.charset.CharacterCodingException) { null } catch (_: IllegalArgumentException) { null }
            if (root==null || root.keys!=setOf("format","key","action","scope","spec","file") || root.text("format")!="pending-files.v1") return Outcome.Err(PendingFilesFailure.invalid)
            val s=root["scope"] as? JsonObject ?: return Outcome.Err(PendingFilesFailure.invalid)
            val p=root["spec"] as? JsonObject ?: return Outcome.Err(PendingFilesFailure.invalid)
            if (s.keys!=setOf("authority_id","principal_id","account_id") || p.keys!=setOf("name","content_type","byte_size","sha256")) return Outcome.Err(PendingFilesFailure.invalid)
            val scope=PendingFileScope.make(s.text("authority_id") ?: "",s.text("principal_id") ?: "",s.text("account_id") ?: "") ?: return Outcome.Err(PendingFilesFailure.invalid)
            val spec=UploadSpec.make(p.text("name") ?: "",p.text("content_type") ?: "",p.integer("byte_size",10*1024*1024)?.toInt() ?: 0,p.text("sha256") ?: "") ?: return Outcome.Err(PendingFilesFailure.invalid)
            val key=root.text("key") ?: ""
            val value=when (root.text("action")) {
                "upload" -> if (root["file"]==JsonNull) upload(scope,key,spec,bytes) else null
                "delete" -> {
                    val f=root["file"] as? JsonObject
                    if (bytes.isNotEmpty() || f==null || f.keys!=setOf("id","status","version")) null else {
                        val status=FileStatus.entries.firstOrNull { it.wire==f.text("status") }
                        val file=if (status==null) null else RemoteFile.make(f.text("id") ?: "",scope.accountId,scope.principalId,spec,status,f.integer("version",2) ?: 0)
                        file?.let { delete(scope,key,it) }
                    }
                }
                else -> null
            }
            return value?.let { Outcome.Ok(it) } ?: Outcome.Err(PendingFilesFailure.invalid)
        }
        private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.integer(key: String,maximum: Int): Long? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() && it>=1 && it<=maximum && it==kotlin.math.floor(it) }?.toLong()
    }
}
interface PendingFilesStore {
    suspend fun load(): AppResult<PendingFileIntent?>
    suspend fun stage(intent: PendingFileIntent): AppResult<Unit>
    suspend fun remove(intent: PendingFileIntent): AppResult<Unit>
}
class MemoryPendingFilesStore : PendingFilesStore {
    private val mutex=Mutex();private var intent: PendingFileIntent?=null
    override suspend fun load(): AppResult<PendingFileIntent?> = mutex.withLock { Outcome.Ok(intent) }
    override suspend fun stage(intent: PendingFileIntent): AppResult<Unit> = mutex.withLock {
        if (this.intent?.matches(intent)==false) Outcome.Err(PendingFilesFailure.conflict) else { this.intent=intent;Outcome.Ok(Unit) }
    }
    override suspend fun remove(intent: PendingFileIntent): AppResult<Unit> = mutex.withLock {
        if (this.intent?.matches(intent)==false) Outcome.Err(PendingFilesFailure.conflict) else { this.intent=null;Outcome.Ok(Unit) }
    }
}
object PendingFilesFailure {
    val invalid=Failure.Unavailable(FailureMeta("File recovery data is unreadable. It has been kept; new file changes are blocked.",code="pending_files.invalid_store"))
    val schema=Failure.Unavailable(FailureMeta("File recovery needs a supported store version. Existing data has been kept.",code="pending_files.schema"))
    val storage=Failure.Unavailable(FailureMeta("File recovery could not be saved or loaded. Retry without resetting its store.",code="pending_files.storage"))
    val conflict=Failure.Conflict(FailureMeta("Another original file operation is retained. Reload recovery before changing it.",code="pending_files.conflict"))
}
