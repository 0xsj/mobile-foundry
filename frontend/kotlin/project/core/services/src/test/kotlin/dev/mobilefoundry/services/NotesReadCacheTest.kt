package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NotesReadCacheTest {
    private val scope = PendingNotesScope.make("go.test", "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000002")!!
    @Test fun sharedAdmissionAndJournalCheckpoint() {
        val rows = Json.parseToJsonElement(File(System.getProperty("notes.read.cache.fixtures"), "snapshots.json").readText()).jsonArray
        for (row in rows) {
            val r = row.jsonObject; val answer = NotesReadSnapshot.decode(r.getValue("snapshot").toString().toByteArray(), scope)
            if (r.getValue("valid").jsonPrimitive.boolean) {
                val value = (answer as Outcome.Ok).value
                val restored = (NotesReadSnapshot.decode(value.encoded(), scope) as Outcome.Ok).value
                assertEquals(value.savedAtMilliseconds, restored.savedAtMilliseconds)
                assertEquals(value.notes.map { it.title }, restored.notes.map { it.title })
            } else assertTrue(r.getValue("name").toString(), answer is Outcome.Err)
        }
        val command = (RemoteNoteWrite.create("00000000-0000-4000-8000-000000000001", "Original") as Outcome.Ok).value
        val pending = PendingNoteRecord(scope, command)
        val snapshot = NotesReadSnapshot.make(scope, emptyList(), 1_800_000_000_000, pending)!!
        val restored = (NotesReadSnapshot.decode(snapshot.encoded(), scope) as Outcome.Ok).value
        assertTrue(restored.matches(pending)); assertFalse(restored.matches(null))
        assertFalse(restored.matches((pending.rejecting(PendingNoteRejection.NOT_FOUND) as Outcome.Ok).value))
        for (bytes in listOf(byteArrayOf(-1), ByteArray(65_537) { 32 }, snapshot.encoded().toString(Charsets.UTF_8).toByteArray(Charsets.UTF_16LE))) assertTrue(NotesReadSnapshot.decode(bytes, scope) is Outcome.Err)
    }
    @Test fun expiryScopeEvictionAndNoTimestampRegression() = runTest {
        val cache = MemoryNotesReadCache(1); val at = 1_800_000_000_000L
        fun snapshot(s: PendingNotesScope, t: Long) = NotesReadSnapshot.make(s, emptyList(), t, null)!!
        assertTrue(cache.save(snapshot(scope, at)) is Outcome.Ok); cache.save(snapshot(scope, at - 1))
        assertEquals(at, (cache.load(scope, at) as Outcome.Ok).value!!.savedAtMilliseconds)
        val other = PendingNotesScope.make("go.other", scope.principalId, scope.accountId)!!
        assertNull((cache.load(other, at) as Outcome.Ok).value)
        cache.save(snapshot(other, at + 1)); assertNull((cache.load(scope, at + 1) as Outcome.Ok).value)
        assertNull((cache.load(other, at + 1 + NotesReadSnapshot.lifetimeMilliseconds) as Outcome.Ok).value)
        cache.save(snapshot(scope, at)); assertNull((cache.load(scope, at - 1) as Outcome.Ok).value)
        cache.save(snapshot(scope, at)); cache.remove(scope); assertNull((cache.load(scope, at) as Outcome.Ok).value)
    }
}
