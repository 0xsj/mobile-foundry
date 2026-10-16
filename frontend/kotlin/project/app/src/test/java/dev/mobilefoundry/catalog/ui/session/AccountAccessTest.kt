package dev.mobilefoundry.catalog.ui.session

import dev.mobilefoundry.catalog.ui.notes.LocalNotesViewModel
import dev.mobilefoundry.data.SessionNotesRepository
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountAccessTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun <T> AppResult<T>.value() = (this as Outcome.Ok).value
    private suspend fun TestScope.session(): SessionController {
        val clock = ManualSessionClock(1_700_000_000_000)
        return SessionController(this, DemoSessionAuthority(clock::now), MemoryCredentialVault(), clock::now) { fail("Unexpected session error") }
            .also { it.signIn("alex@example.test", "catalog-demo")!!.join() }
    }
    @Test fun oldLeaseGatesEveryCRUDAndNewLeaseReadsOriginalRepository() = runTest(dispatcher) {
        val session = session(); val repo = InMemoryNotesRepository(); val old = SessionNotesRepository(repo, session, session.lease!!)
        val note = old.create(CreateNote.make("Retained").value()).value()
        session.signIn("sam@example.test", "catalog-demo")!!.join()
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), old.list())
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), old.create(CreateNote.make("Blocked").value()))
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), old.update(UpdateNote.make(note.id, "Blocked").value()))
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), old.delete(DeleteNote.make(note.id).value()))
        session.signIn("alex@example.test", "catalog-demo")!!.join()
        assertEquals(listOf(note), SessionNotesRepository(repo, session, session.lease!!).list().value())
        assertEquals(Outcome.Err(SessionFailures.scopeChanged), old.list())
    }
    private class GateNotes(private val mode: Int) : NotesRepository {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val target = InMemoryNotesRepository()
        override suspend fun list() = target.list()
        override suspend fun create(command: CreateNote): AppResult<Note> = withContext(NonCancellable) {
            entered.complete(Unit); release.await()
            when (mode) { 0 -> target.create(command); 1 -> Outcome.Err(Failure.Unavailable(FailureMeta("Old data failure"))); else -> error("Old account defect") }
        }
        override suspend fun update(command: UpdateNote) = target.update(command)
        override suspend fun delete(command: DeleteNote) = target.delete(command)
    }
    @Test fun acceptedOldWriteCommitsItsOriginalTargetAndLateErrorsCannotPublish() = runTest(dispatcher) {
        for (mode in 0..2) {
            val session = session(); val repo = GateNotes(mode); val bound = SessionNotesRepository(repo, session, session.lease!!)
            val task = async { bound.create(CreateNote.make("Accepted before switch").value()) }; repo.entered.await()
            session.signIn("sam@example.test", "catalog-demo")!!.join(); repo.release.complete(Unit)
            assertEquals(Outcome.Err(SessionFailures.scopeChanged), task.await()); assertEquals(if (mode == 0) 1 else 0, repo.list().value().size)
        }
    }
    @Test fun terminalEditorDropsNativeDraftSnapshotsAndIgnoredLateCommit() = runTest(dispatcher) {
        val repo = GateNotes(0); val model = LocalNotesViewModel(repo) { fail("Late defect") }; model.refresh()!!.join()
        model.editTitle("Private draft"); val task = model.save()!!; repo.entered.await()
        model.shutdown(); model.editTitle("Deferred edit"); model.blurTitle()
        assertEquals("", model.titleField.text.toString()); assertEquals("", model.state.value.title); assertNull(model.state.value.query.value)
        assertNull(model.state.value.editingId); assertFalse(model.state.value.touched); assertNull(model.refresh()); assertNull(model.save())
        repo.release.complete(Unit); task.join()
        assertNull(model.state.value.query.value); assertNull(model.state.value.mutation.value); assertEquals(1, repo.list().value().size)
    }
    @Test fun probe401DoesNotRenewAndShutdownDiscardsDelayedReport() = runTest(dispatcher) {
        val session = session(); val lease = session.lease!!; val probe = AuthenticatedProbeModel(this, session, lease)
        probe.run(ProbeResponse.UNAUTHORIZED)!!.join(); assertNotNull(probe.state.value.report); assertEquals(lease, session.lease)
        assertFalse(probe.state.value.report!!.contains("access."))
        val task = probe.run(ProbeResponse.SLOW)!!; runCurrent(); probe.shutdown(); session.signOut()!!.join(); task.join()
        assertNull(probe.state.value.report); assertFalse(probe.state.value.isBusy); assertNull(probe.run(ProbeResponse.SUCCESS))
    }
}
