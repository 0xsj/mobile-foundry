package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PendingFilesTest {
    @Test fun sharedAdmissionAndExactRoundTrips() {
        val rows=Json.parseToJsonElement(File(System.getProperty("foundry.pending.files.fixtures"),"records.json").readText()).jsonArray
        assertEquals(14,rows.size)
        for (item in rows) {
            val row=item.jsonObject;val bytes=Base64.getDecoder().decode(row.getValue("content").jsonPrimitive.content)
            val result=PendingFileIntent.decode(row.getValue("metadata").toString().toByteArray(),bytes)
            if (row.getValue("valid").jsonPrimitive.boolean) {
                val original=(result as Outcome.Ok).value
                assertTrue((PendingFileIntent.decode(original.metadata(),original.bytes()) as Outcome.Ok).value.matches(original))
            } else assertTrue(row.getValue("name").toString(),result is Outcome.Err)
        }
        for (data in listOf(byteArrayOf(0xff.toByte()),ByteArray(16_385) { 32 })) assertTrue(PendingFileIntent.decode(data,byteArrayOf()) is Outcome.Err)
    }
    @Test fun exactGlobalSlotAndStaleRemove() = runBlocking {
        val scope=PendingFileScope.make("go.test","00000000-0000-4000-8000-000000000003","00000000-0000-4000-8000-000000000002")!!
        val key="00000000-0000-4000-8000-000000000001";val bytes=byteArrayOf(0,-1,1)
        val spec=(UploadSpec.prepare("Cafe\u0301.png","image/png",bytes) as Outcome.Ok).value
        val first=PendingFileIntent.upload(scope,key,spec,bytes)!!;bytes[0]=99
        assertEquals(0,first.bytes()[0].toInt());val copy=first.bytes();copy[0]=99;assertEquals(0,first.bytes()[0].toInt())
        val changed=PendingFileIntent.upload(scope,key,(UploadSpec.prepare("Café.png","image/png",first.bytes()) as Outcome.Ok).value,first.bytes())!!
        val store=MemoryPendingFilesStore();assertTrue(store.stage(first) is Outcome.Ok);assertTrue(store.stage(first) is Outcome.Ok);assertTrue(store.stage(changed) is Outcome.Err)
        val other=PendingFileIntent.upload(PendingFileScope.make("go.other",scope.principalId,scope.accountId)!!,key,spec,first.bytes())!!
        assertTrue(store.stage(other) is Outcome.Err);assertTrue(store.remove(first) is Outcome.Ok);assertTrue(store.remove(first) is Outcome.Ok)
        assertTrue(store.stage(other) is Outcome.Ok);assertTrue(store.remove(first) is Outcome.Err);assertTrue((store.load() as Outcome.Ok).value!!.matches(other))
    }
}
