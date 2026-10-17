package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Original deployment/principal/account. Credentials and lease epochs never enter storage. */
data class PendingNotesScope private constructor(val authorityId: String, val principalId: String, val accountId: String) {
    companion object {
        fun make(authorityId: String, principalId: String, accountId: String): PendingNotesScope? =
            if (authorityId.matches(Regex("[A-Za-z0-9._-]{1,200}")) && RemoteNotesAdmission.uuid(principalId) && RemoteNotesAdmission.uuid(accountId))
                PendingNotesScope(authorityId, principalId, accountId) else null
    }
}
enum class PendingNoteRejection(val code: String) { VERSION_CONFLICT("notes.version_conflict"), NOT_FOUND("notes.not_found") }
enum class PendingNoteStatus { PENDING, CONFIRMED, REJECTED }

/** One bounded slot per scope. A terminal record remains until explicitly replaced. */
class PendingNoteRecord private constructor(val scope: PendingNotesScope, val command: RemoteNoteWrite, val status: PendingNoteStatus,
    val receipt: NoteWriteReceipt?, val rejection: PendingNoteRejection?) {
    constructor(scope: PendingNotesScope, command: RemoteNoteWrite) : this(scope, command, PendingNoteStatus.PENDING, null, null)
    fun matches(other: RemoteNoteWrite) = command.commandId == other.commandId && command.action == other.action && command.noteId == other.noteId &&
        command.expectedVersion == other.expectedVersion && command.title == other.title
    fun confirming(value: NoteWriteReceipt): AppResult<PendingNoteRecord> {
        if (value.accountId != scope.accountId || value.principalId != scope.principalId || !value.confirms(command) || status == PendingNoteStatus.REJECTED) return Outcome.Err(PendingNotesFailure.conflict)
        if (receipt != null && receipt.noteId != value.noteId) return Outcome.Err(PendingNotesFailure.conflict)
        return Outcome.Ok(PendingNoteRecord(scope, command, PendingNoteStatus.CONFIRMED, value, null))
    }
    fun rejecting(value: PendingNoteRejection): AppResult<PendingNoteRecord> {
        if (status == PendingNoteStatus.CONFIRMED || rejection != null && rejection != value) return Outcome.Err(PendingNotesFailure.conflict)
        return Outcome.Ok(PendingNoteRecord(scope, command, PendingNoteStatus.REJECTED, null, value))
    }
    fun encoded(): ByteArray = buildJsonObject {
        put("format", "pending-notes.v1")
        put("scope", buildJsonObject { put("authority_id", scope.authorityId); put("principal_id", scope.principalId); put("account_id", scope.accountId) })
        put("command", buildJsonObject {
            put("command_id", command.commandId); put("action", command.action.name.lowercase()); put("note_id", command.noteId?.let(::JsonPrimitive) ?: JsonNull)
            put("title", command.title); put("expected_version", command.expectedVersion?.let(::JsonPrimitive) ?: JsonNull)
        })
        put("status", status.name.lowercase())
        put("receipt", receipt?.let { r -> buildJsonObject {
            put("command_id", r.commandId); put("account_id", r.accountId); put("principal_id", r.principalId); put("action", r.action.name.lowercase())
            put("note_id", r.noteId); put("title", r.title); put("version", r.version)
        } } ?: JsonNull)
        put("rejection", rejection?.code?.let(::JsonPrimitive) ?: JsonNull)
    }.toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray, expected: PendingNotesScope): AppResult<PendingNoteRecord> {
            if (bytes.size > 16_384) return Outcome.Err(PendingNotesFailure.invalid)
            val root = try {
                Json.parseToJsonElement(Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()) as? JsonObject
            } catch (_: java.nio.charset.CharacterCodingException) { null
            } catch (_: IllegalArgumentException) { null }
            if (root == null || root.keys != setOf("format", "scope", "command", "status", "receipt", "rejection") || root.string("format") != "pending-notes.v1") return Outcome.Err(PendingNotesFailure.invalid)
            val s = root["scope"] as? JsonObject ?: return Outcome.Err(PendingNotesFailure.invalid)
            if (s.keys != setOf("authority_id", "principal_id", "account_id") || s.string("authority_id") != expected.authorityId || s.string("principal_id") != expected.principalId || s.string("account_id") != expected.accountId) return Outcome.Err(PendingNotesFailure.invalid)
            val c = root["command"] as? JsonObject ?: return Outcome.Err(PendingNotesFailure.invalid)
            if (c.keys != setOf("command_id", "action", "note_id", "title", "expected_version")) return Outcome.Err(PendingNotesFailure.invalid)
            val id = c.string("command_id") ?: return Outcome.Err(PendingNotesFailure.invalid)
            val title = c.string("title") ?: return Outcome.Err(PendingNotesFailure.invalid)
            val answer = when (c.string("action")) {
                "create" -> if (c["note_id"] == JsonNull && c["expected_version"] == JsonNull) RemoteNoteWrite.create(id, title) else return Outcome.Err(PendingNotesFailure.invalid)
                "update", "delete" -> {
                    val note = c.string("note_id") ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val version = integer(c["expected_version"]) ?: return Outcome.Err(PendingNotesFailure.invalid)
                    if (c.string("action") == "update") RemoteNoteWrite.update(id, note, title, version)
                    else { if (title.isNotEmpty()) return Outcome.Err(PendingNotesFailure.invalid); RemoteNoteWrite.delete(id, note, version) }
                }
                else -> return Outcome.Err(PendingNotesFailure.invalid)
            }
            if (answer !is Outcome.Ok) return Outcome.Err(PendingNotesFailure.invalid)
            val pending = PendingNoteRecord(expected, answer.value)
            return when (root.string("status")) {
                "pending" -> if (root["receipt"] == JsonNull && root["rejection"] == JsonNull) Outcome.Ok(pending) else Outcome.Err(PendingNotesFailure.invalid)
                "rejected" -> {
                    val reason = PendingNoteRejection.entries.find { it.code == root.string("rejection") }
                    if (reason != null && root["receipt"] == JsonNull) pending.rejecting(reason) else Outcome.Err(PendingNotesFailure.invalid)
                }
                "confirmed" -> {
                    val r = root["receipt"] as? JsonObject ?: return Outcome.Err(PendingNotesFailure.invalid)
                    if (root["rejection"] != JsonNull || r.keys != setOf("command_id", "account_id", "principal_id", "action", "note_id", "title", "version")) return Outcome.Err(PendingNotesFailure.invalid)
                    val commandId = r.string("command_id") ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val account = r.string("account_id") ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val principal = r.string("principal_id") ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val action = NoteWriteAction.entries.find { it.name.lowercase() == r.string("action") } ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val note = r.string("note_id") ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val text = r.string("title") ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val version = integer(r["version"]) ?: return Outcome.Err(PendingNotesFailure.invalid)
                    val receipt = NoteWriteReceipt.make(commandId, account, principal, action, note, text, version) ?: return Outcome.Err(PendingNotesFailure.invalid)
                    when (val result = pending.confirming(receipt)) { is Outcome.Ok -> result; is Outcome.Err -> Outcome.Err(PendingNotesFailure.invalid) }
                }
                else -> Outcome.Err(PendingNotesFailure.invalid)
            }
        }
        private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun integer(value: JsonElement?): Long? {
            val primitive = value as? JsonPrimitive ?: return null
            if (primitive.isString) return null
            val number = primitive.doubleOrNull ?: return null
            return if (number.isFinite() && number >= 1 && number <= RemoteNotesAdmission.maxVersion.toDouble() && number % 1 == 0.0) number.toLong() else null
        }
    }
}

interface PendingNotesStore {
    suspend fun load(scope: PendingNotesScope): AppResult<PendingNoteRecord?>
    suspend fun stage(command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?): AppResult<PendingNoteRecord>
    suspend fun confirm(command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt): AppResult<PendingNoteRecord>
    suspend fun reject(command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection): AppResult<PendingNoteRecord>
}
object PendingNotesFailure {
    val storage get() = Failure.Unavailable(FailureMeta("Saved changes are unavailable. Reload recovery before trying again.", "notes.pending_storage"))
    val invalid get() = Failure.Unavailable(FailureMeta("Saved changes could not be read. Stored data has been kept.", "notes.pending_invalid"))
    val schema get() = Failure.Unavailable(FailureMeta("Saved changes need a compatible app version. Stored data has been kept.", "notes.pending_schema"))
    val conflict get() = Failure.Conflict(FailureMeta("The saved change has changed. Reload recovery before trying again.", "notes.pending_conflict"))
    val capacity get() = Failure.Unavailable(FailureMeta("Saved change storage is full. Existing changes have been kept.", "notes.pending_capacity"))
}
class MemoryPendingNotesStore(private val maxScopes: Int = 256) : PendingNotesStore {
    init { require(maxScopes > 0) }
    private val mutex = Mutex()
    private val records = mutableMapOf<PendingNotesScope, PendingNoteRecord>()
    override suspend fun load(scope: PendingNotesScope): AppResult<PendingNoteRecord?> = mutex.withLock { currentCoroutineContext().ensureActive(); Outcome.Ok(records[scope]) }
    override suspend fun stage(command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?): AppResult<PendingNoteRecord> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val current = records[scope]
        if (current?.command?.commandId == command.commandId) return@withLock if (current.matches(command)) Outcome.Ok(current) else Outcome.Err(PendingNotesFailure.conflict)
        if (current?.command?.commandId != replacing || current?.status == PendingNoteStatus.PENDING) return@withLock Outcome.Err(PendingNotesFailure.conflict)
        if (current == null && records.size >= maxScopes) return@withLock Outcome.Err(PendingNotesFailure.capacity)
        PendingNoteRecord(scope, command).let { records[scope] = it; Outcome.Ok(it) }
    }
    override suspend fun confirm(command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt): AppResult<PendingNoteRecord> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val current = records[scope]?.takeIf { it.matches(command) } ?: return@withLock Outcome.Err(PendingNotesFailure.conflict)
        current.confirming(receipt).map { records[scope] = it; it }
    }
    override suspend fun reject(command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection): AppResult<PendingNoteRecord> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val current = records[scope]?.takeIf { it.matches(command) } ?: return@withLock Outcome.Err(PendingNotesFailure.conflict)
        current.rejecting(reason).map { records[scope] = it; it }
    }
}
