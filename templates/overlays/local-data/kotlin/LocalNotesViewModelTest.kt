package dev.mobilefoundry.product.notes

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalNotesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun memoryCRUDAndSynchronousAdmissionShareTheRepositoryPort() = runTest(dispatcher) {
        val repo = InMemoryNotesRepository(); val model = LocalNotesViewModel(repo) { fail("Unexpected defect") }
        assertFalse(model.state.value.canAct); model.refresh()?.join(); assertTrue(model.state.value.canAct)
        assertNull(model.save()); assertEquals("Enter a title.", model.state.value.titleError)
        model.editTitle("  Idea 💡  "); val write = model.save()!!
        assertTrue(model.state.value.isBusy); assertNull(model.save()); assertNull(model.refresh())
        model.editTitle("Blocked"); model.newNote(); assertEquals("  Idea 💡  ", model.state.value.title)
        write.join(); val first = model.state.value.query.value!!.single()
        assertEquals("  Idea 💡  ", first.title); assertEquals("", model.state.value.title)
        model.edit(first); model.editTitle("Renamed"); model.save()?.join()
        assertEquals(listOf(Note(first.id, "Renamed")), model.state.value.query.value)
        assertEquals("Note updated.", model.state.value.mutation.value)
        val reopened = LocalNotesViewModel(repo) { fail("Unexpected defect") }; reopened.refresh()?.join()
        assertEquals(model.state.value.query.value, reopened.state.value.query.value)
        model.delete(model.state.value.query.value!!.single())?.join()
        assertEquals(emptyList<Note>(), model.state.value.query.value); assertEquals("Note deleted.", model.state.value.mutation.value)
        reopened.refresh()?.join(); assertEquals(emptyList<Note>(), reopened.state.value.query.value)
    }
    @Test fun saveReadsCurrentNativeTextBeforeAnyDraftObserverRuns() = runTest(dispatcher) {
        val repo = InMemoryNotesRepository(); val model = LocalNotesViewModel(repo) { fail("Unexpected defect") }
        model.refresh()?.join(); model.editTitle("Before")
        model.titleField.edit { append(" latest") }
        assertEquals("Before", model.state.value.title)
        model.save()?.join()
        assertEquals("Before latest", model.state.value.query.value!!.single().title)
        assertEquals("", model.titleField.text.toString())
        model.titleField.edit { append("One") }; model.titleField.edit { append(" two") }
        model.observeTitle()
        assertEquals("One two", model.state.value.title)
        assertEquals("One two", model.titleField.text.toString())
    }
    private class LateRepository : NotesRepository {
        val gate = CompletableDeferred<Unit>(); var notes = emptyList<Note>(); var started = false
        override suspend fun list(): AppResult<List<Note>> = Outcome.Ok(notes)
        override suspend fun create(command: CreateNote): AppResult<Note> = withContext(NonCancellable) {
            started = true; gate.await(); val note = Note("late-commit", command.title); notes = notes + note; Outcome.Ok(note)
        }
        override suspend fun update(command: UpdateNote): AppResult<Note> = Outcome.Err(NotesRepositoryFailure.missing)
        override suspend fun delete(command: DeleteNote): AppResult<Unit> = Outcome.Err(NotesRepositoryFailure.missing)
    }
    @Test fun abandonedWriteCannotPublishButFreshReadCanObserveItsCommit() = runTest(dispatcher) {
        val repo = LateRepository(); val model = LocalNotesViewModel(repo) { fail("Late defect reported") }
        model.refresh()?.join(); model.editTitle("Draft"); val write = model.save()!!; runCurrent(); assertTrue(repo.started)
        model.stop(); assertFalse(model.state.value.isBusy); assertEquals(FailureKind.CANCELED, model.state.value.mutation.failure?.kind)
        repo.gate.complete(Unit); write.join()
        assertEquals(emptyList<Note>(), model.state.value.query.value); assertNull(model.state.value.mutation.value); assertEquals("Draft", model.state.value.title)
        val reopened = LocalNotesViewModel(repo) { fail("Unexpected defect") }; reopened.refresh()?.join()
        assertEquals("late-commit", reopened.state.value.query.value!!.single().id)
    }
    private class ReadFailsAfterCommit : NotesRepository {
        var failRead = false; var notes = emptyList<Note>(); var creates = 0
        override suspend fun list(): AppResult<List<Note>> = if (failRead) Outcome.Err(Failure.Unavailable(FailureMeta("Read unavailable", "notes.storage_unavailable"))) else Outcome.Ok(notes)
        override suspend fun create(command: CreateNote): AppResult<Note> {
            creates++; val note = Note("saved", command.title); notes = notes + note; failRead = true; return Outcome.Ok(note)
        }
        override suspend fun update(command: UpdateNote): AppResult<Note> = Outcome.Err(NotesRepositoryFailure.missing)
        override suspend fun delete(command: DeleteNote): AppResult<Unit> = Outcome.Err(NotesRepositoryFailure.missing)
    }
    @Test fun receiptSurvivesFailedReloadWithoutReplayingTheCommittedWrite() = runTest(dispatcher) {
        val repo = ReadFailsAfterCommit(); val model = LocalNotesViewModel(repo) { fail("Expected failure reported as defect") }
        model.refresh()?.join(); model.editTitle("Saved"); model.save()?.join()
        assertEquals("Note created.", model.state.value.mutation.value); assertEquals("notes.storage_unavailable", model.state.value.query.failure?.meta?.code)
        assertEquals(emptyList<Note>(), model.state.value.query.value); assertEquals("", model.state.value.title); assertEquals(1, repo.creates)
        repo.failRead = false; model.refresh()?.join()
        assertEquals(listOf(Note("saved", "Saved")), model.state.value.query.value); assertEquals(1, repo.creates)
    }
}
