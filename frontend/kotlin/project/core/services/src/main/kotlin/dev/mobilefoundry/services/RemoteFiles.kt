package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.security.MessageDigest

enum class FileStatus(val wire: String) { PENDING_UPLOAD("pending_upload"), AVAILABLE("available"), DELETED("deleted") }
@ConsistentCopyVisibility
data class UploadSpec private constructor(val name: String, val contentType: String, val byteSize: Int, val sha256: String) {
    companion object {
        fun make(name: String, contentType: String, byteSize: Int, sha256: String): UploadSpec? {
            if (name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) != name || name != name.trim() || name.codePointCount(0, name.length) !in 1..255 || name.any { it == '/' || it == '\\' || Character.isISOControl(it) } ||
                contentType !in listOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf", "application/octet-stream") || byteSize !in 1..10 * 1024 * 1024 || !Regex("[a-f0-9]{64}").matches(sha256)) return null
            return UploadSpec(name, contentType, byteSize, sha256)
        }
        fun prepare(name: String, contentType: String, bytes: ByteArray): AppResult<UploadSpec> {
            if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) return Outcome.Err(FilesAdmission.invalid)
            return make(name.trim(), contentType, bytes.size, digest(bytes))?.let { Outcome.Ok(it) } ?: Outcome.Err(FilesAdmission.invalid)
        }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun matches(bytes: ByteArray) = bytes.size == byteSize && digest(bytes) == sha256
}
@ConsistentCopyVisibility
data class RemoteFile private constructor(val id: String, val accountId: String, val ownerId: String, val spec: UploadSpec, val status: FileStatus, val version: Long) {
    companion object {
        fun make(id: String, accountId: String, ownerId: String, spec: UploadSpec, status: FileStatus, version: Long): RemoteFile? {
            if (!FilesAdmission.uuid(id) || !FilesAdmission.uuid(accountId) || !FilesAdmission.uuid(ownerId) ||
                !(status == FileStatus.PENDING_UPLOAD && version == 1L || status == FileStatus.AVAILABLE && version == 2L || status == FileStatus.DELETED && version in 2L..3L)) return null
            return RemoteFile(id, accountId, ownerId, spec, status, version)
        }
    }
}
data class FilesPage(val files: List<RemoteFile>, val nextCursor: String?)
data class FileReceipt(val key: String, val action: String, val principalId: String, val file: RemoteFile)
interface RemoteFilesReader {
    suspend fun page(cursor: String? = null, limit: Int = 20): AppResult<FilesPage>
    suspend fun download(file: RemoteFile): AppResult<ByteArray>
    suspend fun receipt(key: String): AppResult<FileReceipt>
}
interface RemoteFilesWriter {
    suspend fun requestUpload(spec: UploadSpec, key: String): AppResult<FileReceipt>
    suspend fun uploadContent(file: RemoteFile, bytes: ByteArray): AppResult<Unit>
    suspend fun complete(file: RemoteFile, key: String): AppResult<FileReceipt>
    suspend fun delete(file: RemoteFile, key: String): AppResult<FileReceipt>
}
interface RemoteFilesRepository : RemoteFilesReader, RemoteFilesWriter
internal object FilesAdmission {
    fun uuid(value: String) = value != "00000000-0000-0000-0000-000000000000" && Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(value)
    val invalid = Failure.Invalid(FailureMeta("Check the file specification, command key and account.", "files.invalid_input"), emptyMap())
    fun key(value: String) = Regex("[A-Za-z0-9._:-]{1,128}").matches(value)
}
