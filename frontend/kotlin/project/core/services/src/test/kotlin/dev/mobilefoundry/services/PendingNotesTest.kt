package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PendingNotesTest {
    private val scope = PendingNotesScope.make("go.test", "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000002")!!
    private val id = "00000000-0000-4000-8000-000000000001"
    @Test fun sharedAdmissionAndExactRoundTrips() {
        val fixture = File(System.getProperty("foundry.pending.notes.fixtures"), "records.json")
        for (row in Json.parseToJsonElement(fixture.readText()).jsonArray) {
            val record = row.jsonObject
            val answer = PendingNoteRecord.decode(record.getValue("record").toString().toByteArray(Charsets.UTF_8), scope)
            if (record.getValue("valid").jsonPrimitive.boolean) {
                val admitted = (answer as Outcome.Ok).value
                val restored = (PendingNoteRecord.decode(admitted.encoded(), scope) as Outcome.Ok).value
                assertTrue(restored.matches(admitted.command)); assertEquals(admitted.status, restored.status)
            } else assertTrue(record.getValue("name").toString(), answer is Outcome.Err)
        }
        for (bytes in listOf(byteArrayOf(0xff.toByte()), ByteArray(16_385) { 32 })) assertTrue(PendingNoteRecord.decode(bytes, scope) is Outcome.Err)
        // Valid JSON structure with malformed UTF-8 inside an otherwise admitted title.
        val valid = PendingNoteRecord(scope, (RemoteNoteWrite.create(id, "replace") as Outcome.Ok).value).encoded()
        val at = valid.toString(Charsets.UTF_8).indexOf("replace")
        valid[at] = 0xff.toByte(); assertTrue(PendingNoteRecord.decode(valid, scope) is Outcome.Err)
    }
    @Test fun exactInputCASCapacityAndLateSettlement() = runBlocking {
        val store = MemoryPendingNotesStore(1)
        val command = (RemoteNoteWrite.create(id, "Cafe\u0301\u0000") as Outcome.Ok).value
        val changed = (RemoteNoteWrite.create(id, "Café\u0000") as Outcome.Ok).value
        val staged = (store.stage(command, scope, null) as Outcome.Ok).value
        assertFalse(staged.matches(changed)); assertTrue(store.stage(changed, scope, id) is Outcome.Err)
        assertTrue((store.stage(command, scope, "stale") as Outcome.Ok).value.matches(command))
        val other = PendingNotesScope.make("go.other", scope.principalId, scope.accountId)!!
        assertNull((store.load(other) as Outcome.Ok).value); assertTrue(store.stage(command, other, null) is Outcome.Err)
        val next = (RemoteNoteWrite.create("00000000-0000-4000-8000-000000000009", "Next") as Outcome.Ok).value
        assertTrue(store.stage(next, scope, id) is Outcome.Err)
        assertTrue(store.reject(command, scope, PendingNoteRejection.VERSION_CONFLICT) is Outcome.Ok)
        assertTrue(store.stage(next, scope, id) is Outcome.Ok)
        assertTrue(store.reject(command, scope, PendingNoteRejection.VERSION_CONFLICT) is Outcome.Err)
        assertTrue((store.load(scope) as Outcome.Ok).value!!.matches(next))
    }
    @Test fun terminalReceiptCannotChangeCreatedIdentityOrContradictRefusal() {
        val command = (RemoteNoteWrite.create(id,"Original") as Outcome.Ok).value
        val pending = PendingNoteRecord(scope,command)
        fun receipt(note: String) = NoteWriteReceipt.make(id,scope.accountId,scope.principalId,NoteWriteAction.CREATE,note,command.title,1)!!
        val first = receipt("00000000-0000-4000-8000-000000000004")
        val terminal = (pending.confirming(first) as Outcome.Ok).value
        assertTrue(terminal.confirming(first) is Outcome.Ok)
        assertTrue(terminal.confirming(receipt("00000000-0000-4000-8000-000000000005")) is Outcome.Err)
        assertTrue(terminal.rejecting(PendingNoteRejection.NOT_FOUND) is Outcome.Err)
        val rejected = (pending.rejecting(PendingNoteRejection.NOT_FOUND) as Outcome.Ok).value
        assertTrue(rejected.confirming(first) is Outcome.Err)
        assertTrue(rejected.rejecting(PendingNoteRejection.VERSION_CONFLICT) is Outcome.Err)
    }

}
