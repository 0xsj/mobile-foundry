package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesObservationTest {
    private fun <T> AppResult<T>.value() = (this as Outcome.Ok).value
    @Test fun multipleMemoryReadersReceiveOwnedCurrentSnapshotsAndCancelIndependently() = runTest {
        val repository = InMemoryNotesRepository(); val a = mutableListOf<List<Note>>(); val b = mutableListOf<List<Note>>()
        val first = launch { repository.observe().collect { a += it.value() } }; val second = launch { repository.observe().collect { b += it.value() } }; runCurrent()
        assertEquals(emptyList<Note>(), a.last()); assertEquals(emptyList<Note>(), b.last())
        val initial = a.first(); val note = repository.create(CreateNote.make("First").value()).value(); runCurrent()
        assertEquals(listOf(note), a.last()); assertEquals(listOf(note), b.last()); assertTrue(initial.isEmpty())
        val updated = repository.update(UpdateNote.make(note.id, "Updated").value()).value(); runCurrent()
        assertEquals(listOf(updated), a.last()); assertEquals(listOf(updated), b.last())
        repository.delete(DeleteNote.make(note.id).value()).value(); runCurrent(); assertTrue(a.last().isEmpty()); assertTrue(b.last().isEmpty())
        first.cancelAndJoin(); val count = a.size
        repository.create(CreateNote.make("Second reader remains").value()).value(); runCurrent()
        assertEquals(count, a.size); assertEquals("Second reader remains", b.last().single().title); second.cancelAndJoin()
    }
    @Test fun coldMemoryFlowHasNoReplayLogAndSlowCollectorsConverge() = runTest {
        val repository = InMemoryNotesRepository(); val states = mutableListOf<List<Note>>()
        val task = launch { repository.observe().collect { states += it.value() } }; runCurrent()
        repeat(80) { repository.create(CreateNote.make("Note $it").value()).value() }; runCurrent()
        assertEquals(80, states.last().size); assertEquals(2, states.size); task.cancelAndJoin()
        repository.create(CreateNote.make("While paused").value()).value()
        assertEquals(81, repository.observe().first().value().size)
    }
}
