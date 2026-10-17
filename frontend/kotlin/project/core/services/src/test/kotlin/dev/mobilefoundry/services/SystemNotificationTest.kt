package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SystemNotificationTest {
    @Test fun sharedPayloadAdmissionAndOpenOwnership() {
        val rows = Json.parseToJsonElement(File(System.getProperty("system.notification.fixtures"),"payloads.json").readText()).jsonArray
        for (row in rows) {
            val b = row.jsonObject; val payload = b.getValue("payload").jsonObject.mapValues { it.value.jsonPrimitive.content }
            assertEquals(b.getValue("name").toString(),b.getValue("valid").jsonPrimitive.boolean,LocalNotificationPreview.decode(payload) != null)
        }
        val router = NotificationOpenRouter();val first = LocalNotificationPreview.make(UUID.randomUUID().toString())!!;val next = LocalNotificationPreview.make(UUID.randomUUID().toString())!!
        router.receive(first);router.receive(next);router.consume(NotificationOpen.Local(first));assertEquals(NotificationOpen.Local(next),router.state.value.pending)
        router.consume(NotificationOpen.Local(next));router.receive(first);router.receive(next);assertNull(router.state.value.pending);assertEquals(next.id,router.state.value.lastOpenedId)
    }
    private class Probe : SystemNotificationPort {
        var permissionRequests = 0;var posted = 0;var allowed = false;var blockedOnPost = false;var holdRead = false
        val held = CompletableDeferred<AppResult<NotificationAccess>>()
        override suspend fun access(): AppResult<NotificationAccess> = if (holdRead) withContext(NonCancellable) { held.await() } else Outcome.Ok(NotificationAccess(if (allowed) NotificationAuthorization.ALLOWED else NotificationAuthorization.NOT_GRANTED,allowed))
        override suspend fun requestPermission(): AppResult<NotificationAccess> { permissionRequests++;allowed=true;return access() }
        override suspend fun postPreview(preview: LocalNotificationPreview): AppResult<Unit> { posted++;return if (blockedOnPost) Outcome.Err(SystemNotificationFailures.blocked) else Outcome.Ok(Unit) }
        override suspend fun clearPreview(): AppResult<Unit> = Outcome.Ok(Unit)
        override suspend fun openSettings(): AppResult<Unit> = Outcome.Ok(Unit)
    }
    @Test fun permissionIsExplicitAndPostingNeverRetries() = runTest {
        val port = Probe();val owner = SystemNotificationController(this,port) { throw AssertionError("Unexpected defect",it) }
        owner.refresh();advanceUntilIdle();assertFalse(owner.canPost);assertEquals(0,port.permissionRequests)
        owner.postPreview();assertEquals(0,port.posted)
        owner.requestPermission();advanceUntilIdle();assertTrue(owner.canPost);assertEquals(1,port.permissionRequests)
        port.blockedOnPost=true;owner.postPreview();advanceUntilIdle();assertEquals(SystemNotificationFailures.blocked,owner.state.value.failure);assertFalse(owner.canPost);assertNull(owner.state.value.lastAcceptedId)
        owner.postPreview();assertEquals(1,port.posted)
        owner.refresh();advanceUntilIdle();port.blockedOnPost=false;owner.postPreview();advanceUntilIdle();assertNotNull(owner.state.value.lastAcceptedId);assertEquals(2,port.posted)
        owner.clearPreview();advanceUntilIdle();assertNull(owner.state.value.lastAcceptedId)
    }
    @Test fun closedOwnerIgnoresLateNativeResponse() = runTest {
        val port = Probe().apply { holdRead=true };val owner = SystemNotificationController(this,port) { throw AssertionError("Unexpected defect",it) }
        owner.refresh();runCurrent();owner.close();port.held.complete(Outcome.Ok(NotificationAccess(NotificationAuthorization.ALLOWED,true)));advanceUntilIdle()
        assertNull(owner.state.value.access);assertFalse(owner.state.value.busy);assertFalse(owner.canPost)
    }
}
