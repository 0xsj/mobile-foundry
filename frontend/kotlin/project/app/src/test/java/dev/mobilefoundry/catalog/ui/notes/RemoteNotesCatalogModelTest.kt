package dev.mobilefoundry.catalog.ui.notes
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoteNotesCatalogModelTest {
    private val account = SessionScope.make("go.test","00000000-0000-4000-8000-000000000003","00000000-0000-4000-8000-000000000002")!!
    private class Repository(val account: SessionScope) : RemoteNotesRepository {
        var executions = 0;var loseAck = true;var failAfter = false;var failRead = false
        val notes = mutableListOf<VersionedNote>();val receipts = mutableMapOf<String,NoteWriteReceipt>()
        override suspend fun page(after: String?,limit: Int): AppResult<RemoteNotesPage> = if (failRead) Outcome.Err(Failure.Unavailable(FailureMeta("Read failed"))) else Outcome.Ok(RemoteNotesPage(notes,null))
        override suspend fun get(noteId: String): AppResult<VersionedNote> = Outcome.Err(NotesRepositoryFailure.missing)
        override suspend fun execute(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> {
            executions++;receipts[command.commandId]?.let { return Outcome.Ok(it) }
            val id = UUID.randomUUID().toString();val r = NoteWriteReceipt.make(command.commandId,account.accountId,account.principalId,NoteWriteAction.CREATE,id,command.title,1)!!
            notes += VersionedNote.make(id,command.title,1)!!;receipts[command.commandId] = r;failRead = failAfter
            return if (loseAck) Outcome.Err(Failure.Unavailable(FailureMeta("Lost acknowledgement"))) else Outcome.Ok(r)
        }
        override suspend fun receipt(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> = receipts[command.commandId]?.let { Outcome.Ok(it) } ?: Outcome.Err(NotesRepositoryFailure.missing)
    }
    @Test fun pendingCommandSurvivesRenewalAndReceiptDoesNotReplay() = runTest {
        val repo = Repository(account);val model = RemoteNotesCatalogModel(this);model.bind(repo,account);advanceUntilIdle()
        model.setTitle("Retained");model.save();model.save();advanceUntilIdle()
        val command = model.state.value.pending!!;assertEquals(1,repo.executions);assertFalse(model.state.value.canWrite)
        model.bind(null,null);model.bind(repo,account);advanceUntilIdle();assertEquals(command.commandId,model.state.value.pending!!.commandId)
        model.recover(true);advanceUntilIdle();assertNull(model.state.value.pending);assertTrue(model.state.value.canWrite);assertEquals(1,model.state.value.notes.size);assertEquals("",model.state.value.title);assertEquals(1,repo.executions)
    }
    @Test fun confirmedWriteSurvivesReadFailureWithoutReplay() = runTest {
        val repo = Repository(account);repo.loseAck = false;repo.failAfter = true
        val model = RemoteNotesCatalogModel(this);model.bind(repo,account);advanceUntilIdle();model.setTitle("Committed");model.save();advanceUntilIdle()
        assertNull(model.state.value.pending);assertNotNull(model.state.value.receipt);assertFalse(model.state.value.canWrite)
        repo.failRead = false;model.refresh();advanceUntilIdle();assertTrue(model.state.value.canWrite);assertEquals(1,repo.executions);assertNotNull(model.state.value.receipt)
    }
    @Test fun lateAcknowledgementCannotPopulateReplacementAccount() = runTest {
        var continuation: Continuation<AppResult<NoteWriteReceipt>>? = null;var command: RemoteNoteWrite? = null
        val old = object : RemoteNotesRepository {
            override suspend fun page(after: String?,limit: Int): AppResult<RemoteNotesPage> = Outcome.Ok(RemoteNotesPage(emptyList(),null))
            override suspend fun get(noteId: String): AppResult<VersionedNote> = Outcome.Err(NotesRepositoryFailure.missing)
            override suspend fun receipt(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> = Outcome.Err(NotesRepositoryFailure.missing)
            override suspend fun execute(input: RemoteNoteWrite): AppResult<NoteWriteReceipt> { command = input;return suspendCoroutine { continuation = it } }
        }
        val model = RemoteNotesCatalogModel(this);model.bind(old,account);advanceUntilIdle();model.setTitle("Old target");model.save();runCurrent();assertNotNull(continuation)
        val next = SessionScope.make("go.test",account.principalId,"00000000-0000-4000-8000-000000000099")!!;model.bind(Repository(next),next);runCurrent()
        continuation!!.resume(Outcome.Ok(NoteWriteReceipt.make(command!!.commandId,account.accountId,account.principalId,NoteWriteAction.CREATE,UUID.randomUUID().toString(),command!!.title,1)!!));advanceUntilIdle()
        assertTrue(model.state.value.notes.isEmpty());assertNull(model.state.value.receipt);assertNull(model.state.value.pending)
        model.bind(old,account);advanceUntilIdle();assertNotNull(model.state.value.pending)
    }
}
