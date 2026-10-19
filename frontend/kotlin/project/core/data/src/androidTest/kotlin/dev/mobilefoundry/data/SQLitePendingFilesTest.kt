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

class SQLitePendingFilesTest {
    private val scope=PendingFileScope.make("go.test","00000000-0000-4000-8000-000000000003","00000000-0000-4000-8000-000000000002")!!
    private fun intent(bytes: ByteArray=byteArrayOf(0,-1,1))=PendingFileIntent.upload(scope,UUID.randomUUID().toString(),(UploadSpec.prepare("Photo.png","image/png",bytes) as Outcome.Ok).value,bytes)!!
    private suspend fun fixture(block: suspend (File)->Unit) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(context.noBackupFilesDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try { block(File(directory,"journal.sqlite")) } finally { directory.deleteRecursively() }
    }
    private fun sql(file: File,vararg statements: String) { SQLiteDatabase.openOrCreateDatabase(file,null).use { db -> statements.forEach(db::execSQL) } }
    @Test fun tenMiBReopensAndExactRemovalCannotEraseNewerIntent()=runBlocking { fixture { file ->
        val first=intent(ByteArray(10*1024*1024) { (it%251).toByte() });val store=SQLitePendingFilesStore(file)
        assertTrue(store.stage(first) is Outcome.Ok);assertTrue(store.stage(first) is Outcome.Ok)
        val reopened=SQLitePendingFilesStore(file);assertTrue((reopened.load() as Outcome.Ok).value!!.matches(first))
        val other=intent();assertTrue(reopened.stage(other) is Outcome.Err)
        assertTrue(reopened.remove(first) is Outcome.Ok);assertTrue(reopened.remove(first) is Outcome.Ok)
        val remote=RemoteFile.make("00000000-0000-4000-8000-000000000004",scope.accountId,scope.principalId,other.spec,FileStatus.AVAILABLE,2)!!
        val deletion=PendingFileIntent.delete(scope,other.key,remote)!!;assertTrue(store.stage(deletion) is Outcome.Ok)
        assertTrue(reopened.remove(first) is Outcome.Err);assertTrue((reopened.load() as Outcome.Ok).value!!.matches(deletion))
    } }
    @Test fun rollbackAndCorruptionPreserveRecovery()=runBlocking { fixture { file ->
        val store=SQLitePendingFilesStore(file);val first=intent();assertTrue(store.stage(first) is Outcome.Ok)
        sql(file,"CREATE TRIGGER refuse_remove BEFORE DELETE ON pending_files BEGIN SELECT RAISE(ABORT,'owned failure'); END")
        assertTrue(store.remove(first) is Outcome.Err);assertTrue((store.load() as Outcome.Ok).value!!.matches(first))
        sql(file,"DROP TRIGGER refuse_remove","UPDATE pending_files SET content=x'00'")
        assertTrue(store.load() is Outcome.Err);assertTrue(file.exists())
        val corrupt=File(file.parentFile,"corrupt.sqlite");val original="not a database".toByteArray();corrupt.writeBytes(original)
        assertTrue(SQLitePendingFilesStore(corrupt).load() is Outcome.Err);assertArrayEquals(original,corrupt.readBytes())
    } }
    @Test fun futureForeignAndMalformedStoresAreRefused()=runBlocking { fixture { file ->
        val store=SQLitePendingFilesStore(file);assertTrue(store.load() is Outcome.Ok);sql(file,"PRAGMA user_version=99");assertTrue(store.load() is Outcome.Err)
        sql(file,"PRAGMA user_version=1","UPDATE pending_store SET kind='pending-notes.v1'");assertTrue(store.load() is Outcome.Err)
        val foreign=File(file.parentFile,"foreign.sqlite");sql(foreign,"CREATE TABLE foreign_data(value TEXT)","INSERT INTO foreign_data VALUES('keep')")
        assertTrue(SQLitePendingFilesStore(foreign).load() is Outcome.Err)
    } }
    @Test fun independentInstancesSerializeCompetingStages()=runBlocking { fixture { file ->
        val a=SQLitePendingFilesStore(file);val b=SQLitePendingFilesStore(file);val one=intent();val two=intent();assertTrue(a.load() is Outcome.Ok)
        val results=awaitAll(async(Dispatchers.IO) { a.stage(one) },async(Dispatchers.IO) { b.stage(two) })
        assertEquals(1,results.count { it is Outcome.Ok });val winner=(a.load() as Outcome.Ok).value!!;assertTrue(winner.matches(one) || winner.matches(two))
    } }
}
