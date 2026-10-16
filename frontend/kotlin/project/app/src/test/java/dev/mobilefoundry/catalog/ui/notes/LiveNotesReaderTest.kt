package dev.mobilefoundry.catalog.ui.notes

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.query.*
import dev.mobilefoundry.services.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveNotesReaderTest {
    private fun <T> AppResult<T>.get(): T = when (this) { is Outcome.Ok -> value; is Outcome.Err -> throw AssertionError(error) }
    @Test fun sharedScenarioCoversCRUDPauseAndFreshResume() = runTest {
        val steps = Json.parseToJsonElement(File(System.getProperty("notes.observation.fixtures"), "scenario.json").readText()).jsonArray
        val repo = InMemoryNotesRepository()
        val first = LiveNotesReader(backgroundScope, repo) { fail("Unexpected defect") }
        val second = LiveNotesReader(backgroundScope, repo) { fail("Unexpected defect") }
        first.start(); second.start(); val ids = mutableMapOf<String, String>()
        for (element in steps) {
            val step = element.jsonObject; val reference = step["reference"]?.jsonPrimitive?.content; val title = step["title"]?.jsonPrimitive?.content
            when (step.getValue("action").jsonPrimitive.content) {
                "create" -> ids[reference!!] = repo.create(CreateNote.make(title!!).get()).get().id
                "update" -> repo.update(UpdateNote.make(ids.getValue(reference!!), title!!).get()).get()
                "delete" -> repo.delete(DeleteNote.make(ids.getValue(reference!!)).get()).get()
                "missing" -> assertEquals(FailureKind.NOT_FOUND, (repo.delete(DeleteNote.make("missing").get()) as Outcome.Err).error.kind)
                "pause" -> second.pause()
                "resume" -> second.start()
            }
            runCurrent()
            fun titles(key: String) = step.getValue(key).jsonArray.map { it.jsonPrimitive.content }
            assertEquals(titles("first"), first.state.value.query.value?.map { it.title })
            assertEquals(titles("second"), second.state.value.query.value?.map { it.title })
        }
        first.shutdown(); second.shutdown(); runCurrent()
    }
    private class ScriptedObserver : NotesObserver {
        val values = MutableSharedFlow<AppResult<List<Note>>>(replay = 1).also { it.tryEmit(Outcome.Ok(emptyList())) }
        var calls = 0; var active = 0; var defect = false
        override fun observe(): Flow<AppResult<List<Note>>> = flow {
            calls++; active++
            try { values.collect { if (defect) error("Unexpected defect"); emit(it) } } finally { active-- }
        }
    }
    @Test fun expectedFailureRetainsContentAndRecoversWithoutRestartOrDiagnostics() = runTest {
        val source = ScriptedObserver(); var defects = 0
        val reader = LiveNotesReader(backgroundScope, source) { defects++ }; reader.start(); runCurrent()
        val note = Note("one", "Owned content"); source.values.emit(Outcome.Ok(listOf(note))); runCurrent()
        source.values.emit(Outcome.Err(Failure.Unavailable(FailureMeta("Read unavailable", "notes.storage_unavailable")))); runCurrent()
        assertTrue(reader.state.value.isWatching); assertEquals(listOf(note), reader.state.value.query.value)
        assertEquals("notes.storage_unavailable", reader.state.value.query.failure?.meta?.code); assertEquals(0, defects)
        source.values.emit(Outcome.Ok(emptyList())); runCurrent()
        assertEquals(QueryState.Loaded(emptyList<Note>()), reader.state.value.query)
        reader.pause(); runCurrent(); assertEquals(0, source.active)
        reader.start(); runCurrent(); assertEquals(1, source.active); assertEquals(2, source.calls)
        reader.shutdown(); runCurrent(); assertEquals(0, source.active); assertNull(reader.start())
    }
    @Test fun terminalReaderIgnoresLateDefectFromSourceIgnoringCancellation() = runTest {
        val gate = CompletableDeferred<Unit>(); var defects = 0; var entered = false
        val source = object : NotesObserver {
            override fun observe(): Flow<AppResult<List<Note>>> = flow {
                withContext(NonCancellable) { entered = true; gate.await(); error("Late defect") }
            }
        }
        val reader = LiveNotesReader(backgroundScope, source) { defects++ }; val old = reader.start()!!
        runCurrent(); assertTrue(entered); reader.shutdown(); gate.complete(Unit); old.join(); runCurrent()
        assertEquals(0, defects); assertEquals(NotesReaderState(), reader.state.value); assertNull(reader.start())
    }
    @Test fun admissionClosesBeforePublishingAndCanCloseReentrantlyInDiagnostics() = runTest {
        for (closeInDiagnostic in listOf(false, true)) {
            val source = ScriptedObserver(); var admitted = true; var defects = 0
            val reader = LiveNotesReader(backgroundScope, source, { admitted }) { defects++; admitted = false }
            reader.start(); runCurrent(); source.values.emit(Outcome.Ok(listOf(Note("private", "Private content")))); runCurrent()
            if (closeInDiagnostic) source.defect = true else admitted = false
            source.values.emit(Outcome.Ok(emptyList())); runCurrent()
            assertEquals(NotesReaderState(), reader.state.value); assertNull(reader.start())
            assertEquals(if (closeInDiagnostic) 1 else 0, defects); assertEquals(0, source.active)
        }
        val source = ScriptedObserver(); val reader = LiveNotesReader(backgroundScope, source, { false }) { fail("Unexpected defect") }
        assertNull(reader.start()); runCurrent(); assertEquals(0, source.calls)
    }
}
