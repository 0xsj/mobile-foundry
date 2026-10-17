package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.Collections

/** Advisory first-page content; never authorization or a write base. */
class NotesReadSnapshot private constructor(val scope: PendingNotesScope, notes: List<VersionedNote>, val savedAtMilliseconds: Long, private val checkpoint: String?) {
    val notes: List<VersionedNote> = Collections.unmodifiableList(ArrayList(notes))
    fun matches(journal: PendingNoteRecord?) = (journal == null || journal.scope == scope) && checkpoint == checkpoint(journal)
    fun isUsable(now: Long) = now >= savedAtMilliseconds && now - savedAtMilliseconds < lifetimeMilliseconds
    fun encoded(): ByteArray = buildJsonObject {
        put("format", "notes-read-cache.v1")
        put("scope", buildJsonObject { put("authority_id", scope.authorityId); put("principal_id", scope.principalId); put("account_id", scope.accountId) })
        put("saved_at_ms", savedAtMilliseconds); put("checkpoint", checkpoint?.let(::JsonPrimitive) ?: JsonNull)
        put("notes", buildJsonArray { notes.forEach { n -> add(buildJsonObject { put("id", n.id); put("title", n.title); put("version", n.version) }) } })
    }.toString().toByteArray(Charsets.UTF_8)
    companion object {
        const val lifetimeMilliseconds = 86_400_000L
        const val maxBytes = 65_536
        fun make(scope: PendingNotesScope, notes: List<VersionedNote>, savedAtMilliseconds: Long, journal: PendingNoteRecord?): NotesReadSnapshot? =
            if (notes.size <= 20 && notes.map { it.id }.toSet().size == notes.size && savedAtMilliseconds in 1..RemoteNotesAdmission.maxVersion && (journal == null || journal.scope == scope))
                NotesReadSnapshot(scope, notes, savedAtMilliseconds, checkpoint(journal)) else null
        private fun checkpoint(record: PendingNoteRecord?) = record?.let { "${it.command.commandId}:${it.status.name.lowercase()}" }
        fun decode(bytes: ByteArray, expected: PendingNotesScope): AppResult<NotesReadSnapshot> {
            if (bytes.size > maxBytes || bytes.contains(0)) return Outcome.Err(NotesCacheFailure.invalid)
            val root = try {
                Json.parseToJsonElement(Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()) as? JsonObject
            } catch (_: java.nio.charset.CharacterCodingException) { null } catch (_: IllegalArgumentException) { null }
            if (root == null || root.keys != setOf("format", "scope", "saved_at_ms", "checkpoint", "notes") || root.string("format") != "notes-read-cache.v1") return Outcome.Err(NotesCacheFailure.invalid)
            val s = root["scope"] as? JsonObject ?: return Outcome.Err(NotesCacheFailure.invalid)
            if (s.keys != setOf("authority_id", "principal_id", "account_id") || s.string("authority_id") != expected.authorityId || s.string("principal_id") != expected.principalId || s.string("account_id") != expected.accountId) return Outcome.Err(NotesCacheFailure.invalid)
            val saved = integer(root["saved_at_ms"]) ?: return Outcome.Err(NotesCacheFailure.invalid)
            val rows = root["notes"] as? JsonArray ?: return Outcome.Err(NotesCacheFailure.invalid)
            if (rows.size > 20) return Outcome.Err(NotesCacheFailure.invalid)
            val notes = mutableListOf<VersionedNote>()
            for (row in rows) {
                val n = row as? JsonObject ?: return Outcome.Err(NotesCacheFailure.invalid)
                if (n.keys != setOf("id", "title", "version")) return Outcome.Err(NotesCacheFailure.invalid)
                val id = n.string("id") ?: return Outcome.Err(NotesCacheFailure.invalid)
                val title = n.string("title") ?: return Outcome.Err(NotesCacheFailure.invalid)
                val version = integer(n["version"]) ?: return Outcome.Err(NotesCacheFailure.invalid)
                notes += VersionedNote.make(id, title, version) ?: return Outcome.Err(NotesCacheFailure.invalid)
            }
            val admitted = make(expected, notes, saved, null) ?: return Outcome.Err(NotesCacheFailure.invalid)
            if (root["checkpoint"] == JsonNull) return Outcome.Ok(admitted)
            val checkpoint = root.string("checkpoint") ?: return Outcome.Err(NotesCacheFailure.invalid)
            val parts = checkpoint.split(':')
            if (parts.size != 2 || !RemoteNotesAdmission.uuid(parts[0]) || parts[1] !in setOf("pending", "confirmed", "rejected")) return Outcome.Err(NotesCacheFailure.invalid)
            return Outcome.Ok(NotesReadSnapshot(expected, notes, saved, checkpoint))
        }
        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun integer(value: JsonElement?): Long? {
            val n = (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
            return if (n.isFinite() && n >= 1 && n <= RemoteNotesAdmission.maxVersion.toDouble() && n % 1.0 == 0.0) n.toLong() else null
        }
    }
}

/** Owners gate reads with the current lease and restored journal checkpoint. */
interface NotesReadCache {
    suspend fun load(scope: PendingNotesScope, nowMilliseconds: Long): AppResult<NotesReadSnapshot?>
    suspend fun save(snapshot: NotesReadSnapshot): AppResult<Unit>
    suspend fun remove(scope: PendingNotesScope): AppResult<Unit>
}
object NotesCacheFailure {
    val storage get() = Failure.Unavailable(FailureMeta("Saved read cache is unavailable. Server data and pending changes are separate.", "notes.cache_storage"))
    val invalid get() = Failure.Unavailable(FailureMeta("Saved read cache could not be read. Stored data has been kept.", "notes.cache_invalid"))
    val schema get() = Failure.Unavailable(FailureMeta("Saved read cache needs a compatible app version. Stored data has been kept.", "notes.cache_schema"))
}
class MemoryNotesReadCache(private val maxScopes: Int = 32) : NotesReadCache {
    init { require(maxScopes in 1..32) }
    private val mutex = Mutex()
    private val snapshots = mutableMapOf<PendingNotesScope, NotesReadSnapshot>()
    override suspend fun load(scope: PendingNotesScope, nowMilliseconds: Long): AppResult<NotesReadSnapshot?> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val value = snapshots[scope]
        if (value != null && !value.isUsable(nowMilliseconds)) { snapshots.remove(scope); Outcome.Ok(null) } else Outcome.Ok(value)
    }
    override suspend fun save(snapshot: NotesReadSnapshot): AppResult<Unit> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (snapshot.encoded().size > NotesReadSnapshot.maxBytes) return@withLock Outcome.Err(NotesCacheFailure.invalid)
        if ((snapshots[snapshot.scope]?.savedAtMilliseconds ?: 0) <= snapshot.savedAtMilliseconds) snapshots[snapshot.scope] = snapshot
        if (snapshots.size > maxScopes) snapshots.minBy { it.value.savedAtMilliseconds }.key.let(snapshots::remove)
        Outcome.Ok(Unit)
    }
    override suspend fun remove(scope: PendingNotesScope): AppResult<Unit> = mutex.withLock {
        currentCoroutineContext().ensureActive(); snapshots.remove(scope); Outcome.Ok(Unit)
    }
}
