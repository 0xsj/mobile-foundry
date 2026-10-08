package dev.mobilefoundry.catalog.ui.notes

import androidx.lifecycle.ViewModelStore
import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.QueryState
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val notes = listOf(Note("n1", "Idea"))
    private val unavailable = Failure.Unavailable(FailureMeta("Unavailable", "notes.unavailable"))

    @Before fun mainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    @Test fun bothProvidersShareRefreshFailureAndRecoveryBehavior() = runTest {
        for (provider in providers()) {
            val model = NotesViewModel(provider) { fail("Unexpected defect") }
            assertEquals(QueryState.Idle, model.state.value)
            model.refresh()
            assertEquals(QueryState.Loading<List<Note>>(null), model.state.value)
            runCurrent()
            assertEquals(QueryState.Loaded(notes), model.state.value)
            model.refresh()
            assertEquals(QueryState.Loading(notes), model.state.value)
            runCurrent()
            assertEquals(QueryState.Failed(unavailable, notes), model.state.value)
            model.refresh()
            runCurrent()
            assertEquals(QueryState.Loaded(emptyList<Note>()), model.state.value)
            model.refresh(); runCurrent()
            assertEquals(QueryState.Failed(unavailable, emptyList<Note>()), model.state.value)
            model.cancel()
        }
    }

    @Test fun refreshRetainsDataAndCancelRejectsLateResult() = runTest {
        val service = ControlledNotes()
        val model = NotesViewModel(service) { fail("Unexpected defect") }
        model.refresh(); runCurrent()
        service.requests.receive().complete(Outcome.Ok(notes)); runCurrent()
        model.refresh(); runCurrent()
        val old = service.requests.receive()
        assertEquals(QueryState.Loading(notes), model.state.value)
        model.cancel()
        assertEquals(QueryState.Loaded(notes), model.state.value)
        old.complete(Outcome.Ok(emptyList())); runCurrent()
        assertEquals(QueryState.Loaded(notes), model.state.value)
    }

    @Test fun obsoleteFailureCannotReplaceNewerSuccess() = runTest {
        val service = ControlledNotes()
        val model = NotesViewModel(service) { fail("Obsolete defect reported") }
        model.refresh(); runCurrent()
        val old = service.requests.receive()
        model.refresh(); runCurrent()
        service.requests.receive().complete(Outcome.Ok(notes)); runCurrent()
        old.complete(Outcome.Err(unavailable)); runCurrent()
        assertEquals(QueryState.Loaded(notes), model.state.value)
    }

    @Test fun cancellationBeforeContentRestoresIdle() = runTest {
        val service = ControlledNotes()
        val model = NotesViewModel(service) { fail("Cancellation reported") }
        val job = model.refresh(); runCurrent()
        val pending = service.requests.receive()
        job.cancel()
        pending.complete(Outcome.Ok(emptyList())); runCurrent()
        assertEquals(QueryState.Idle, model.state.value)
    }

    @Test fun obsoleteSuccessCannotReplaceNewerFailure() = runTest {
        val service = ControlledNotes()
        val model = NotesViewModel(service) { fail("Unexpected defect") }
        model.refresh(); runCurrent()
        val old = service.requests.receive()
        model.refresh(); runCurrent()
        service.requests.receive().complete(Outcome.Err(unavailable)); runCurrent()
        old.complete(Outcome.Ok(notes)); runCurrent()
        assertEquals(QueryState.Failed<List<Note>>(unavailable, null), model.state.value)
    }

    @Test fun defectIdentityIsReportedAndPublicStateIsGeneric() = runTest {
        val defect = IllegalStateException("Private detail")
        var observed: Exception? = null
        val service = ControlledNotes()
        val model = NotesViewModel(service) { observed = it }
        model.refresh(); runCurrent()
        service.requests.receive().completeExceptionally(defect); runCurrent()
        // Coroutine recovery can copy exceptions while retaining their cause.
        assertTrue(observed === defect || observed?.cause === defect)
        assertEquals(QueryState.Failed<List<Note>>(Failure.Internal(FailureMeta("An unexpected error occurred.")), null), model.state.value)
    }

    @Test fun obsoleteDefectCannotReportOrReplaceCurrentState() = runTest {
        val service = ControlledNotes()
        val model = NotesViewModel(service) { fail("Obsolete defect reported") }
        model.refresh(); runCurrent()
        val old = service.requests.receive()
        model.refresh(); runCurrent()
        service.requests.receive().complete(Outcome.Ok(emptyList())); runCurrent()
        old.completeExceptionally(IllegalStateException("Old")); runCurrent()
        assertEquals(QueryState.Loaded(emptyList<Note>()), model.state.value)
    }

    @Test fun clearingViewModelStoreCancelsOwnedRead() = runTest {
        var canceled = false
        val service = NotesService {
            try { awaitCancellation() } finally { canceled = true }
        }
        val model = NotesViewModel(service) { fail("Cancellation reported") }
        val owner = ViewModelStore()
        owner.put("notes", model)
        model.refresh(); runCurrent()
        owner.clear(); runCurrent()
        assertTrue(canceled)
        assertEquals(QueryState.Idle, model.state.value)
    }

    private fun providers(): List<NotesService> {
        val answers: List<AppResult<List<Note>>> = listOf(Outcome.Ok(notes), Outcome.Err(unavailable), Outcome.Ok(emptyList()), Outcome.Err(unavailable))
        val memoryAnswers = answers.iterator()
        val memory = NotesService {
            when (val answer = memoryAnswers.next()) {
                is Outcome.Ok -> InMemoryNotesService(answer.value).list()
                is Outcome.Err -> answer
            }
        }
        val wireAnswers = answers.iterator()
        val http = (DefaultHTTPClient.create("https://example.invalid/v1", HTTPTransport {
            when (val answer = wireAnswers.next()) {
                is Outcome.Ok -> {
                    val body = buildJsonObject { put("notes", JsonArray(answer.value.map { note ->
                        buildJsonObject { put("id", note.id); put("title", note.title) }
                    })) }
                    Outcome.Ok(WireResponse(200, emptyMap(), body.toString().toByteArray()))
                }
                is Outcome.Err -> Outcome.Ok(WireResponse(503, emptyMap(), """{"detail":"Unavailable","code":"notes.unavailable"}""".toByteArray()))
            }
        }) as Outcome.Ok).value
        return listOf(memory, HTTPNotesService(http))
    }
}

/** Intentionally ignores cancellation to exercise generation admission. */
private class ControlledNotes : NotesService {
    val requests = Channel<CompletableDeferred<AppResult<List<Note>>>>(Channel.UNLIMITED)
    override suspend fun list(): AppResult<List<Note>> = withContext(NonCancellable) {
        val gate = CompletableDeferred<AppResult<List<Note>>>()
        requests.send(gate)
        gate.await()
    }
}
