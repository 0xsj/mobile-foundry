package dev.mobilefoundry.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.data.SQLiteNotesRepository
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalNotesRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture() = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("repository.json").bufferedReader().use { it.readText() })
    private fun <T> AppResult<T>.value(): T = when (this) { is Outcome.Ok -> value; is Outcome.Err -> throw AssertionError(error) }
    private fun <T> AppResult<T>.failure(): Failure = when (this) { is Outcome.Err -> error; is Outcome.Ok -> throw AssertionError("Expected failure") }
    private fun name() = "test-local-notes-${UUID.randomUUID()}.sqlite"
    @Test fun sharedCommandAdmissionAndMemorySnapshotsMatchTheContract() = runBlocking {
        val f = fixture(); val invalid = f.getJSONArray("invalidUpdates")
        for (index in 0 until invalid.length()) {
            val row = invalid.getJSONObject(index)
            assertEquals(row.getString("code"), UpdateNote.make(row.getString("id"), row.getString("title")).failure().meta.code)
        }
        val ids = f.getJSONArray("invalidDeleteIDs")
        for (index in 0 until ids.length()) assertEquals("notes.invalid_id", DeleteNote.make(ids.getString(index)).failure().meta.code)
        val repo = InMemoryNotesRepository(); val titles = f.getJSONArray("createTitles")
        val created = (0 until titles.length()).map { repo.create(CreateNote.make(titles.getString(it)).value()).value() }
        val snapshot = repo.list().value()
        repo.update(UpdateNote.make(created[0].id, f.getString("updateTitle")).value()).value()
        repo.delete(DeleteNote.make(created[1].id).value()).value()
        assertEquals(created, snapshot)
        assertEquals(listOf(f.getString("updateTitle")) + created.drop(2).map { it.title }, repo.list().value().map { it.title })
        try { (snapshot as MutableList<Note>).clear(); fail("Snapshot mutable") } catch (_: UnsupportedOperationException) { }
    }
    @Test fun sqliteCRUDPreservesTitlesIDsAndOrderAcrossFreshHandles() = runBlocking {
        val name = name()
        try {
            val f = fixture(); val repo = SQLiteNotesRepository(context, name); val titles = f.getJSONArray("createTitles")
            assertTrue(repo.list().value().isEmpty())
            val created = (0 until titles.length()).map { repo.create(CreateNote.make(titles.getString(it)).value()).value() }
            assertEquals(created, SQLiteNotesRepository(context, name).list().value())
            val updated = repo.update(UpdateNote.make(created[0].id, f.getString("updateTitle")).value()).value()
            assertEquals(created[0].id, updated.id)
            repo.delete(DeleteNote.make(created[1].id).value()).value()
            val expected = listOf(updated) + created.drop(2)
            assertEquals(expected, SQLiteNotesRepository(context, name).list().value())
            assertEquals("notes.not_found", repo.delete(DeleteNote.make(created[1].id).value()).failure().meta.code)
            assertEquals(FailureKind.NOT_FOUND, repo.update(UpdateNote.make(f.getString("missingID"), "Missing").value()).failure().kind)
            assertEquals(expected, repo.list().value())
        } finally { context.deleteDatabase(name) }
    }
    @Test fun concurrentNativeHandlesDoNotLoseWritesOrDuplicateIDs() = runBlocking {
        val name = name()
        try {
            val first = SQLiteNotesRepository(context, name); val second = SQLiteNotesRepository(context, name)
            val notes = (0 until 12).map { index -> async {
                (if (index % 2 == 0) first else second).create(CreateNote.make("Idea $index").value()).value()
            } }.awaitAll()
            assertEquals(12, notes.map { it.id }.distinct().size)
            assertEquals(12, SQLiteNotesRepository(context, name).list().value().size)
        } finally { context.deleteDatabase(name) }
    }
    @Test fun newerCorruptAndInvalidStoredDataAreRefusedWithoutDestructiveRecovery() = runBlocking {
        val name = name()
        try {
            val repo = SQLiteNotesRepository(context, name); val note = repo.create(CreateNote.make("Keep me").value()).value()
            val file = context.getDatabasePath(name)
            SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = 2 }
            val before = file.readBytes()
            assertEquals("notes.storage_version", repo.list().failure().meta.code)
            assertArrayEquals(before, file.readBytes())
            SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = 1 }
            assertEquals(listOf(note), repo.list().value())
            SQLiteDatabase.openOrCreateDatabase(file, null).use { it.execSQL("UPDATE notes SET title = '   '") }
            assertEquals("notes.storage_corrupt", repo.list().failure().meta.code)
            val broken = "This is not a SQLite database".toByteArray(); file.writeBytes(broken)
            assertEquals("notes.storage_corrupt", SQLiteNotesRepository(context, name).list().failure().meta.code)
            assertArrayEquals(broken, file.readBytes())
        } finally { context.deleteDatabase(name) }
    }
    @Test fun canceledWritesBlockedPathsAndUnversionedStoresStayUntouched() = runBlocking {
        val name = name()
        try {
            val repo = SQLiteNotesRepository(context, name); val command = CreateNote.make("Canceled").value()
            val canceled = async { currentCoroutineContext().cancel(); repo.create(command) }
            try { canceled.await(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
            val file = context.getDatabasePath(name); assertFalse(file.exists())
            file.parentFile!!.mkdirs(); file.mkdir()
            assertEquals("notes.storage_unavailable", repo.create(command).failure().meta.code)
            assertTrue(file.isDirectory); file.delete()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { it.execSQL("CREATE TABLE foreign_data(value TEXT)"); it.execSQL("INSERT INTO foreign_data VALUES ('Keep me')") }
            assertEquals("notes.storage_corrupt", repo.list().failure().meta.code)
            SQLiteDatabase.openOrCreateDatabase(file, null).use {
                it.rawQuery("SELECT value FROM foreign_data", null).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("Keep me", cursor.getString(0)) }
                assertEquals(0, it.version)
            }
        } finally { context.deleteDatabase(name) }
    }
}
