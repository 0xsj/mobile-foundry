package dev.mobilefoundry.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SQLitePendingNotesTest {
    private val scope = PendingNotesScope.make("go.test", "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000002")!!
    private fun command(title: String = "Cafe\u0301\u0000🧱") = (RemoteNoteWrite.create(UUID.randomUUID().toString(), title) as Outcome.Ok).value
    private fun receipt(c: RemoteNoteWrite) = NoteWriteReceipt.make(c.commandId, scope.accountId, scope.principalId, NoteWriteAction.CREATE, "00000000-0000-4000-8000-000000000004", c.title, 1)!!
    private suspend fun fixture(block: suspend (File) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.noBackupFilesDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try { block(File(directory, "journal.sqlite")) } finally { directory.deleteRecursively() }
    }
    private fun sql(file: File, vararg statements: String) { SQLiteDatabase.openOrCreateDatabase(file, null).use { db -> statements.forEach(db::execSQL) } }
    @Test fun reopenTerminalCASAndLateConfirmation() = runBlocking { fixture { file ->
        val c = command(); val first = SQLitePendingNotesStore(file)
        assertTrue(first.stage(c, scope, null) is Outcome.Ok)
        val reopened = SQLitePendingNotesStore(file)
        assertTrue((reopened.load(scope) as Outcome.Ok).value!!.matches(c))
        assertTrue(reopened.confirm(c, scope, receipt(c)) is Outcome.Ok)
        assertTrue((SQLitePendingNotesStore(file).load(scope) as Outcome.Ok).value!!.receipt!!.confirms(c))
        val next = command("Next")
        assertTrue(first.stage(next, scope, null) is Outcome.Err)
        assertTrue(first.stage(next, scope, c.commandId) is Outcome.Ok)
        assertTrue(reopened.confirm(c, scope, receipt(c)) is Outcome.Err)
        assertTrue((first.load(scope) as Outcome.Ok).value!!.matches(next))
    } }
    @Test fun partitionsScopeAndBoundsCapacity() = runBlocking { fixture { file ->
        val store = SQLitePendingNotesStore(file, 4); val c = command()
        val scopes = listOf(scope, PendingNotesScope.make("go.other",scope.principalId,scope.accountId)!!,
            PendingNotesScope.make("go.test","00000000-0000-4000-8000-000000000099",scope.accountId)!!,
            PendingNotesScope.make("go.test",scope.principalId,"00000000-0000-4000-8000-000000000099")!!)
        scopes.forEach { assertNull((store.load(it) as Outcome.Ok).value); assertTrue(store.stage(c,it,null) is Outcome.Ok) }
        assertTrue(store.stage(c,PendingNotesScope.make("go.excess",scope.principalId,scope.accountId)!!,null) is Outcome.Err)
        scopes.forEach { assertTrue((store.load(it) as Outcome.Ok).value!!.matches(c)) }
    } }
    @Test fun rollbackFutureSchemaAndCorruptionKeepData() = runBlocking { fixture { file ->
        val store = SQLitePendingNotesStore(file); val c = command()
        assertTrue(store.stage(c,scope,null) is Outcome.Ok)
        sql(file,"CREATE TRIGGER fail_settle BEFORE UPDATE ON pending_notes BEGIN SELECT RAISE(ABORT,'owned failure'); END")
        assertTrue(store.confirm(c,scope,receipt(c)) is Outcome.Err)
        assertEquals(PendingNoteStatus.PENDING,(store.load(scope) as Outcome.Ok).value!!.status)
        sql(file,"DROP TRIGGER fail_settle","PRAGMA user_version=99")
        assertTrue(store.load(scope) is Outcome.Err)
        sql(file,"PRAGMA user_version=1"); assertTrue((store.load(scope) as Outcome.Ok).value!!.matches(c))
        sql(file,"UPDATE pending_notes SET payload=x'ff'"); assertTrue(store.load(scope) is Outcome.Err)
        val corrupt = File(file.parentFile,"corrupt.sqlite"); val original = "not a database".toByteArray(); corrupt.writeBytes(original)
        assertTrue(SQLitePendingNotesStore(corrupt).load(scope) is Outcome.Err)
        assertArrayEquals(original,corrupt.readBytes())
    } }
    @Test fun independentInstancesSerializeStages() = runBlocking { fixture { file ->
        val a = SQLitePendingNotesStore(file); val b = SQLitePendingNotesStore(file)
        assertTrue(a.load(scope) is Outcome.Ok)
        val c1 = command("One"); val c2 = command("Two")
        val results = awaitAll(async(Dispatchers.IO) { a.stage(c1,scope,null) },async(Dispatchers.IO) { b.stage(c2,scope,null) })
        assertEquals(1,results.count { it is Outcome.Ok })
        val record = (a.load(scope) as Outcome.Ok).value!!;assertTrue(record.matches(c1) || record.matches(c2))
    } }
}
