package dev.mobilefoundry.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SQLiteNotesReadCacheTest {
    private val scope = PendingNotesScope.make("go.test", "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000002")!!
    private val at = 1_800_000_000_000L
    private fun snapshot(s: PendingNotesScope = scope, time: Long = at) = NotesReadSnapshot.make(s, listOf(VersionedNote.make("00000000-0000-4000-8000-000000000004", "Cafe\u0301\u0000🧱", 1)!!), time, null)!!
    private suspend fun fixture(block: suspend (File) -> Unit) {
        val dir = File(ApplicationProvider.getApplicationContext<Context>().noBackupFilesDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try { block(File(dir, "cache.sqlite")) } finally { dir.deleteRecursively() }
    }
    private fun sql(file: File, vararg statements: String) { SQLiteDatabase.openOrCreateDatabase(file, null).use { db -> statements.forEach(db::execSQL) } }
    @Test fun reopenPartitionsExpiryAndForget() = runBlocking { fixture { file ->
        val cache = SQLiteNotesReadCache(file); assertTrue(cache.save(snapshot()) is Outcome.Ok)
        val reopened = SQLiteNotesReadCache(file)
        assertEquals(snapshot().notes.single().title, (reopened.load(scope, at) as Outcome.Ok).value!!.notes.single().title)
        val variants = listOf(PendingNotesScope.make("go.other", scope.principalId, scope.accountId)!!,
            PendingNotesScope.make("go.test", "00000000-0000-4000-8000-000000000099", scope.accountId)!!,
            PendingNotesScope.make("go.test", scope.principalId, "00000000-0000-4000-8000-000000000099")!!)
        variants.forEach { assertNull((reopened.load(it, at) as Outcome.Ok).value) }
        assertNull((reopened.load(scope, at + NotesReadSnapshot.lifetimeMilliseconds) as Outcome.Ok).value)
        cache.save(snapshot()); assertNull((reopened.load(scope, at - 1) as Outcome.Ok).value)
        cache.save(snapshot()); reopened.remove(scope); assertNull((cache.load(scope, at) as Outcome.Ok).value)
    } }
    @Test fun boundedEvictionAndNoTimestampRegression() = runBlocking { fixture { file ->
        val cache = SQLiteNotesReadCache(file, 1); cache.save(snapshot()); cache.save(snapshot(time = at - 1))
        assertEquals(at, (cache.load(scope, at) as Outcome.Ok).value!!.savedAtMilliseconds)
        val other = PendingNotesScope.make("go.other", scope.principalId, scope.accountId)!!
        cache.save(snapshot(other, at + 1)); assertNull((cache.load(scope, at + 1) as Outcome.Ok).value)
        assertNotNull((cache.load(other, at + 1) as Outcome.Ok).value)
    } }
    @Test fun futureCorruptAndJournalFilesArePreserved() = runBlocking { fixture { file ->
        val cache = SQLiteNotesReadCache(file); cache.save(snapshot())
        sql(file, "PRAGMA user_version=99"); assertTrue(cache.load(scope, at) is Outcome.Err)
        sql(file, "PRAGMA user_version=1"); assertNotNull((cache.load(scope, at) as Outcome.Ok).value)
        sql(file, "UPDATE notes_cache SET saved_at=saved_at+1"); assertTrue(cache.load(scope, at) is Outcome.Err)
        sql(file, "UPDATE notes_cache SET payload=x'ff'"); assertTrue(cache.load(scope, at) is Outcome.Err)
        assertTrue(cache.save(snapshot()) is Outcome.Err); assertTrue(cache.load(scope, at) is Outcome.Err)
        val corrupt = File(file.parentFile, "corrupt.sqlite"); val original = "not a database".toByteArray(); corrupt.writeBytes(original)
        assertTrue(SQLiteNotesReadCache(corrupt).load(scope, at) is Outcome.Err); assertArrayEquals(original, corrupt.readBytes())
        val journalFile = File(file.parentFile, "journal.sqlite"); val journal = SQLitePendingNotesStore(journalFile)
        val command = (RemoteNoteWrite.create(UUID.randomUUID().toString(), "Keep me") as Outcome.Ok).value
        journal.stage(command, scope, null)
        assertTrue(SQLiteNotesReadCache(journalFile).remove(scope) is Outcome.Err)
        assertTrue((journal.load(scope) as Outcome.Ok).value!!.matches(command))
    } }
}
