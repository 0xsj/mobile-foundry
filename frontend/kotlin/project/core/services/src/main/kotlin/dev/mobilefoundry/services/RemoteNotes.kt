package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.util.Collections

class VersionedNote private constructor(val id: String, val title: String, val version: Long) {
    companion object { fun make(id: String, title: String, version: Long): VersionedNote? = if (RemoteNotesAdmission.uuid(id) && RemoteNotesAdmission.title(title) && version in 1..RemoteNotesAdmission.maxVersion) VersionedNote(id,title,version) else null }
}
enum class NoteWriteAction(val wire: String) { CREATE("create"), UPDATE("update"), DELETE("delete") }
class RemoteNoteWrite private constructor(val commandId: String, val action: NoteWriteAction, val noteId: String?, val title: String, val expectedVersion: Long?) {
    companion object {
        fun create(commandId: String,title: String) = make(commandId,NoteWriteAction.CREATE,null,title,null)
        fun update(commandId: String,noteId: String,title: String,expectedVersion: Long) = make(commandId,NoteWriteAction.UPDATE,noteId,title,expectedVersion)
        fun delete(commandId: String,noteId: String,expectedVersion: Long) = make(commandId,NoteWriteAction.DELETE,noteId,"",expectedVersion)
        private fun make(commandId: String,action: NoteWriteAction,noteId: String?,title: String,expectedVersion: Long?): AppResult<RemoteNoteWrite> {
            if (!RemoteNotesAdmission.uuid(commandId) || action != NoteWriteAction.CREATE && (!RemoteNotesAdmission.uuid(noteId ?: "") || (expectedVersion ?: 0) !in 1 until RemoteNotesAdmission.maxVersion)) return Outcome.Err(RemoteNotesAdmission.invalid)
            if (action != NoteWriteAction.DELETE) {
                val admitted = CreateNote.make(title); if (admitted is Outcome.Err) return admitted
                if (!RemoteNotesAdmission.title(title)) return Outcome.Err(RemoteNotesAdmission.invalid)
            }
            return Outcome.Ok(RemoteNoteWrite(commandId,action,noteId,title,expectedVersion))
        }
    }
}
class NoteWriteReceipt private constructor(val commandId: String,val accountId: String,val principalId: String,val action: NoteWriteAction,val noteId: String,val title: String,val version: Long) {
    fun confirms(command: RemoteNoteWrite) = commandId == command.commandId && action == command.action && title == command.title && (action == NoteWriteAction.CREATE || noteId == command.noteId && version == command.expectedVersion!! + 1)
    companion object {
        fun make(commandId: String,accountId: String,principalId: String,action: NoteWriteAction,noteId: String,title: String,version: Long): NoteWriteReceipt? {
            if (!listOf(commandId,accountId,principalId,noteId).all(RemoteNotesAdmission::uuid) || version !in 1..RemoteNotesAdmission.maxVersion) return null
            if (action == NoteWriteAction.CREATE) { if (version != 1L || !RemoteNotesAdmission.title(title)) return null }
            else if (version < 2 || if (action == NoteWriteAction.DELETE) title.isNotEmpty() else !RemoteNotesAdmission.title(title)) return null
            return NoteWriteReceipt(commandId,accountId,principalId,action,noteId,title,version)
        }
    }
}
class RemoteNotesPage(notes: List<VersionedNote>,val nextAfter: String?) { val notes: List<VersionedNote> = Collections.unmodifiableList(ArrayList(notes)) }
interface RemoteNotesReader {
    suspend fun page(after: String? = null,limit: Int = 20): AppResult<RemoteNotesPage>
    suspend fun get(noteId: String): AppResult<VersionedNote>
}
interface RemoteNotesWriter {
    suspend fun execute(command: RemoteNoteWrite): AppResult<NoteWriteReceipt>
    /** Not-found is an observation; a still-running write may commit later. */
    suspend fun receipt(command: RemoteNoteWrite): AppResult<NoteWriteReceipt>
}
interface RemoteNotesRepository : RemoteNotesReader,RemoteNotesWriter
internal object RemoteNotesAdmission {
    const val maxVersion = 9_007_199_254_740_991L
    private val grammar = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun uuid(value: String) = grammar.matches(value) && value != "00000000-0000-0000-0000-000000000000"
    fun title(value: String): Boolean {
        var index = 0
        while (index < value.length) { val c = value[index]; if (Character.isHighSurrogate(c)) { if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false; index += 2 } else { if (Character.isLowSurrogate(c)) return false; index++ } }
        return CreateNote.make(value) is Outcome.Ok
    }
    val invalid get() = Failure.Invalid(FailureMeta("Use an admitted command, identifier and version.","notes.invalid_write"),emptyMap())
}
