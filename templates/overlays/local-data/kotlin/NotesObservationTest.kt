package dev.mobilefoundry.data

import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.data.SQLiteNotesRepository
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class NotesObservationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun <T> AppResult<T>.get(): T = when (this) { is Outcome.Ok -> value; is Outcome.Err -> throw AssertionError(error) }
    private suspend fun StateFlow<AppResult<List<Note>>?>.titles(expected: List<String>) {
        withTimeout(8_000) { first { it is Outcome.Ok && it.value.map(Note::title) == expected } }
    }
    @Test fun independentNativeSQLiteReadersObserveCRUDAndResumeLatestAcrossInstances() = runBlocking {
        val name = "observation-${UUID.randomUUID()}.sqlite"
        val writer = SQLiteNotesRepository(context, name)
        val a = MutableStateFlow<AppResult<List<Note>>?>(null); val b = MutableStateFlow<AppResult<List<Note>>?>(null)
        var first = launch(Dispatchers.Default) { SQLiteNotesRepository(context, name).observe().collect { a.value = it } }
        val second = launch(Dispatchers.Default) { SQLiteNotesRepository(context, name).observe().collect { b.value = it } }
        try {
            a.titles(emptyList()); b.titles(emptyList())
            val one = writer.create(CreateNote.make("First 💡").get()).get(); a.titles(listOf(one.title)); b.titles(listOf(one.title))
            first.cancelAndJoin()
            writer.update(UpdateNote.make(one.id, "Renamed").get()).get(); b.titles(listOf("Renamed"))
            assertEquals(listOf(one), (a.value as Outcome.Ok).value)
            val two = writer.create(CreateNote.make("  Second  ").get()).get(); b.titles(listOf("Renamed", two.title))
            assertEquals(FailureKind.NOT_FOUND, (writer.delete(DeleteNote.make("missing").get()) as Outcome.Err).error.kind)
            first = launch(Dispatchers.Default) { SQLiteNotesRepository(context, name).observe().collect { a.value = it } }
            a.titles(listOf("Renamed", two.title))
            writer.delete(DeleteNote.make(one.id).get()).get(); a.titles(listOf(two.title)); b.titles(listOf(two.title))
            writer.delete(DeleteNote.make(two.id).get()).get(); a.titles(emptyList()); b.titles(emptyList())
        } finally { first.cancelAndJoin(); second.cancelAndJoin(); context.deleteDatabase(name) }
    }
    @Test fun registrationAndBurstWritesConvergeAndUnusedFlowDoesNoIO() = runBlocking {
        val name = "observation-${UUID.randomUUID()}.sqlite"
        val observer = SQLiteNotesRepository(context, name); observer.observe()
        assertFalse(context.getDatabasePath(name).exists())
        val latest = MutableStateFlow<AppResult<List<Note>>?>(null)
        val job = launch(Dispatchers.Default) { observer.observe().collect { latest.value = it } }
        try {
            val writer = SQLiteNotesRepository(context, name)
            repeat(30) { writer.create(CreateNote.make("Note $it").get()).get() }
            latest.titles((0..<30).map { "Note $it" })
            assertEquals(1, android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { it.version })
        } finally { job.cancelAndJoin(); context.deleteDatabase(name) }
    }
    @Test fun corruptReadIsAValueKeepsBytesAndRestartReadsExplicitlyRepairedTestStore() = runBlocking {
        val name = "observation-${UUID.randomUUID()}.sqlite"; val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs(); val bytes = "Corrupt test store".toByteArray(); file.writeBytes(bytes)
        val observer = SQLiteNotesRepository(context, name)
        try {
            val result = withTimeout(8_000) { observer.observe().first() }
            assertEquals("notes.storage_corrupt", (result as Outcome.Err).error.meta.code); assertArrayEquals(bytes, file.readBytes())
            // Explicit disposal of this test-owned file, never implicit production recovery.
            assertTrue(context.deleteDatabase(name))
            val note = SQLiteNotesRepository(context, name).create(CreateNote.make("Recovered test file").get()).get()
            assertEquals(listOf(note), withTimeout(8_000) { observer.observe().first() }.get())
        } finally { context.deleteDatabase(name) }
    }
}
