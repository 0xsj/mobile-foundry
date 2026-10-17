package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenTest {
    private class Probe : PushTokenPort {
        var calls = 0
        var held = CompletableDeferred<AppResult<Unit>>()
        override suspend fun collect(): AppResult<Unit> { calls++; return withContext(NonCancellable) { held.await() } }
    }
    private fun target(token: String) = DeliveryTarget.make(PushRoute.make(PushProvider.FCM,"dev.example.app",PushEnvironment.PRODUCTION)!!,token)!!

    @Test fun explicitCollectionAndRotationOnlyUpdatesLocalCandidate() = runTest {
        val tokens = PushTokenStore(); val port = Probe()
        val owner = PushTokenController(this,tokens,port) { throw AssertionError("Unexpected defect",it) }
        assertEquals(0,port.calls);assertNull(owner.target)
        owner.collect();owner.collect();runCurrent();assertEquals(1,port.calls);assertTrue(owner.state.value.busy)
        tokens.receive(target("opaque.first"));port.held.complete(Outcome.Ok(Unit));advanceUntilIdle()
        assertEquals("opaque.first",owner.target?.token);assertEquals(1L,tokens.state.value.revision)
        tokens.receive(target("opaque.first"));assertEquals(1L,tokens.state.value.revision)
        tokens.receive(target("opaque.rotated"));assertEquals("opaque.rotated",owner.target?.token);assertEquals(2L,tokens.state.value.revision)
        assertEquals(1,port.calls);assertTrue(owner.target.toString().contains("<redacted>"))
        port.held = CompletableDeferred();owner.collect();runCurrent();assertNull(tokens.state.value.target);assertNull(owner.target)
        port.held.complete(Outcome.Err(PushTokenFailures.unavailable));advanceUntilIdle()
        assertEquals(PushTokenFailures.unavailable,owner.state.value.failure);assertEquals(2,port.calls);assertNull(owner.target)
    }

    @Test fun closedScreenDropsLateRequestStateButProcessStoreCanRotate() = runTest {
        val tokens = PushTokenStore();val port = Probe()
        val owner = PushTokenController(this,tokens,port) { throw AssertionError("Unexpected defect",it) }
        owner.collect();runCurrent();owner.close();tokens.receive(target("opaque.late"))
        port.held.complete(Outcome.Err(PushTokenFailures.unavailable));advanceUntilIdle()
        assertNull(owner.target);assertFalse(owner.state.value.busy);assertNull(owner.state.value.failure)
        assertEquals("opaque.late",tokens.state.value.target?.token)
        owner.collect();assertEquals(1,port.calls)
    }

    @Test fun defectHookGetsOriginalFailureWhilePublicStateIsGeneric() = runTest {
        val tokens = PushTokenStore();val defect = IllegalStateException("private provider context")
        val port = object : PushTokenPort { override suspend fun collect(): AppResult<Unit> = throw defect }
        var reported: Throwable? = null
        val owner = PushTokenController(this,tokens,port) { reported = it }
        owner.collect();advanceUntilIdle()
        assertSame(defect,reported);assertEquals("An unexpected error occurred.",owner.state.value.failure?.meta?.message);assertNull(owner.target)
    }
}
