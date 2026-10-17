package dev.mobilefoundry.services

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RemoteNotificationTest {
    @Test fun sharedRemoteAdmission() {
        val rows = Json.parseToJsonElement(File(System.getProperty("system.notification.fixtures"), "remote-payloads.json").readText()).jsonArray
        for (row in rows) {
            val value = row.jsonObject
            val payload = value.getValue("payload").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val valid = value.getValue("valid").jsonPrimitive.boolean
            assertEquals(value.getValue("name").toString(), valid, RemoteNotificationPreview.decode(payload) != null)
            if (valid) assertNull(LocalNotificationPreview.decode(payload))
        }
    }
    @Test fun receiptDoesNotNavigateAndConsumptionIncludesKind() {
        val router = NotificationOpenRouter()
        val remote = RemoteNotificationPreview.make(UUID.randomUUID().toString())!!
        val local = LocalNotificationPreview.make(remote.id)!!
        router.recordReceipt(remote)
        assertNull(router.state.value.pending); assertNull(router.state.value.lastOpened)
        router.receive(local); router.receive(remote); router.consume(NotificationOpen.Local(local))
        assertEquals(NotificationOpen.Remote(remote), router.state.value.pending)
        router.consume(NotificationOpen.Remote(remote)); router.receive(remote); router.receive(local)
        assertNull(router.state.value.pending); assertEquals(NotificationOpen.Remote(remote), router.state.value.lastOpened)
        assertEquals(remote, router.state.value.lastReceived)
        repeat(32) { router.receive(RemoteNotificationPreview.make(UUID.randomUUID().toString())!!) }
        router.receive(remote); assertEquals(NotificationOpen.Remote(remote), router.state.value.pending)
    }
}
