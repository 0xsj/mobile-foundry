package dev.mobilefoundry.catalog.ui.forms

import androidx.lifecycle.ViewModelStore
import dev.mobilefoundry.catalog.composition.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateNoteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun mainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    @Test fun invalidInputNeverCallsCreatorAndTouchedValidationUpdates() = runTest {
        var calls = 0
        val model = CreateNoteViewModel(NoteCreator { calls++; Outcome.Ok(Note("n", it.title)) }) { fail("Unexpected defect") }
        assertNull(model.state.value.titleError)
        model.blurTitle()
        assertEquals("Enter a title.", model.state.value.titleError)
        model.editTitle(" \t\r\n")
        assertNull(model.submit())
        assertEquals(0, calls)
        assertEquals(MutationState.Idle, model.state.value.mutation)
        model.editTitle("Idea")
        assertNull(model.state.value.titleError)
        model.editTitle("a".repeat(201))
        assertEquals("Use 200 characters or fewer.", model.state.value.titleError)
        model.reset()
        assertEquals(CreateNoteFormState(), model.state.value)
    }

    @Test fun bothProvidersRetainDraftMapFieldsAndResetAfterSuccess() = runTest {
        for (provider in NotesProvider.entries) {
            val model = CreateNoteViewModel(CreateNoteComposition.creator(provider, CreateNoteScenario.FIELD_ERROR)) { fail("Unexpected defect") }
            model.editTitle("  Idea  ")
            model.submit(); advanceUntilIdle()
            assertEquals("  Idea  ", model.state.value.title)
            assertEquals("This title is reserved.", model.state.value.titleError)
            assertEquals(FailureKind.INVALID, model.state.value.mutation.failure?.kind)
            model.editTitle("  Revised idea  ")
            assertNull(model.state.value.titleError)
            assertEquals(MutationState.Idle, model.state.value.mutation)
            assertTrue(model.use(CreateNoteComposition.creator(provider, CreateNoteScenario.SUCCESS)))
            assertEquals("  Revised idea  ", model.state.value.title)
            model.submit(); advanceUntilIdle()
            assertEquals("  Revised idea  ", model.state.value.mutation.value?.title)
            assertFalse(model.state.value.canEdit)
            assertNull(model.submit())
            model.reset()
            assertEquals(CreateNoteFormState(), model.state.value)
        }
    }

    @Test fun synchronousAdmissionBlocksDuplicatesEditsResetAndProviderSwitchWhileBusy() = runTest {
        val creator = ControlledCreator()
        val model = CreateNoteViewModel(creator) { fail("Unexpected defect") }
        model.editTitle("Original")
        model.submit()
        assertEquals(MutationState.Submitting, model.state.value.mutation)
        assertNull(model.submit())
        model.editTitle("Ignored"); model.reset()
        assertFalse(model.use(InMemoryNoteCreator()))
        runCurrent()
        val pending = creator.requests.receive()
        assertEquals(listOf("Original"), creator.titles)
        assertEquals("Original", model.state.value.title)
        val unknown = Failure.Invalid(FailureMeta("This note cannot be created."), mapOf("other" to "Not accepted."))
        pending.complete(Outcome.Err(unknown)); runCurrent()
        assertNull(model.state.value.titleError)
        assertEquals(unknown, model.state.value.mutation.failure)
        assertEquals("Original", model.state.value.title)
    }

    @Test fun stopRejectsLateSuccessFailureAndDefectAfterNewAttempt() = runTest {
        for (oldKind in 0..2) {
            val creator = ControlledCreator()
            val model = CreateNoteViewModel(creator) { fail("Obsolete defect reported") }
            model.editTitle("Old"); model.submit(); runCurrent()
            val old = creator.requests.receive()
            model.stop()
            assertEquals(FailureKind.CANCELED, model.state.value.mutation.failure?.kind)
            assertEquals("Old", model.state.value.title)
            model.editTitle("Next"); model.submit(); runCurrent()
            val next = creator.requests.receive()
            when (oldKind) {
                0 -> old.complete(Outcome.Ok(Note("old", "Old")))
                1 -> old.complete(Outcome.Err(Failure.Unavailable(FailureMeta("Old refusal"))))
                else -> old.completeExceptionally(IllegalStateException("Old defect"))
            }
            runCurrent()
            assertEquals(MutationState.Submitting, model.state.value.mutation)
            next.complete(Outcome.Ok(Note("next", "Next"))); runCurrent()
            assertEquals(MutationState.Succeeded(Note("next", "Next")), model.state.value.mutation)
        }
    }

    @Test fun currentDefectIsReportedAndPublicFeedbackIsGeneric() = runTest {
        val creator = ControlledCreator()
        val defect = IllegalStateException("Private detail")
        var observed: Exception? = null
        val model = CreateNoteViewModel(creator) { observed = it }
        model.editTitle("Keep me"); model.submit(); runCurrent()
        creator.requests.receive().completeExceptionally(defect); runCurrent()
        assertTrue(observed === defect || observed?.cause === defect)
        assertEquals("Keep me", model.state.value.title)
        assertEquals("An unexpected error occurred.", model.state.value.mutation.failure?.publicInfo()?.meta?.message)
    }

    @Test fun directJobCancellationRetainsDraftAndDoesNotReportLateDefect() = runTest {
        val creator = ControlledCreator()
        val model = CreateNoteViewModel(creator) { fail("Canceled defect reported") }
        model.editTitle("Keep me"); val job = checkNotNull(model.submit()); runCurrent()
        val pending = creator.requests.receive()
        job.cancel(); pending.completeExceptionally(IllegalStateException("Late defect")); runCurrent()
        assertEquals(FailureKind.CANCELED, model.state.value.mutation.failure?.kind)
        assertTrue(model.state.value.canEdit)
        assertEquals("Keep me", model.state.value.title)
    }

    @Test fun clearingNavigationOwnerCancelsOwnedWrite() = runTest {
        var canceled = false
        val model = CreateNoteViewModel(NoteCreator {
            try { awaitCancellation() } finally { canceled = true }
        }) { fail("Cancellation reported") }
        val owner = ViewModelStore(); owner.put("create-note", model)
        model.editTitle("Keep me"); model.submit(); runCurrent()
        owner.clear(); runCurrent()
        assertTrue(canceled)
        assertEquals(FailureKind.CANCELED, model.state.value.mutation.failure?.kind)
        assertEquals("Keep me", model.state.value.title)
    }
}

/** Deliberately ignores cancellation to exercise generation admission. */
private class ControlledCreator : NoteCreator {
    val requests = Channel<CompletableDeferred<AppResult<Note>>>(Channel.UNLIMITED)
    val titles = mutableListOf<String>()
    override suspend fun create(command: CreateNote): AppResult<Note> = withContext(NonCancellable) {
        titles.add(command.title)
        val gate = CompletableDeferred<AppResult<Note>>()
        requests.send(gate)
        gate.await()
    }
}
