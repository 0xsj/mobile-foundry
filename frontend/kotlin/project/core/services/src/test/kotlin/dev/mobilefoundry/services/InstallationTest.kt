package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InstallationTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private val proof = "i1." + "A".repeat(43)
    private val identity = InstallationIdentity.make(id,proof)!!
    private val route = PushRoute.make(PushProvider.APNS,"dev.example.app",PushEnvironment.SANDBOX)!!
    @Test fun sharedAdmissionAndSecrets() = runBlocking {
        val rows = Json.parseToJsonElement(File(System.getProperty("installation.fixtures"),"records.json").readText()).jsonArray
        for (row in rows) {
            val b = row.jsonObject;val requests = ArrayList<PreparedRequest>()
            val client = (DefaultHTTPClient.create("https://example.invalid/v1",HTTPTransport {
                requests += it; Outcome.Ok(WireResponse(200,mapOf("etag" to b.getValue("etag").jsonPrimitive.content),b.getValue("body").toString().toByteArray()))
            }) as Outcome.Ok).value
            val answer = HTTPInstallationRegistry(client).read(identity)
            if (b.getValue("valid").jsonPrimitive.boolean) assertEquals(id,(answer as Outcome.Ok).value?.id)
            else assertTrue(b.getValue("name").toString(),answer is Outcome.Err)
            val request = requests.single(); assertEquals("/v1/installations/$id",request.url.encodedPath);assertNull(request.url.query)
            assertEquals(proof,request.headers["x-installation-key"]);assertNull(request.headers["idempotency-key"])
        }
        assertNull(InstallationIdentity.make(id,"i1." + "A".repeat(42) + "B"));assertNull(InstallationIdentity.make(id,proof+"\n"))
        assertNull(DeliveryTarget.make(route,"bad token"));assertNull(DeliveryTarget.make(route,"x".repeat(4097)))
        assertNull(PushRoute.make(PushProvider.FCM,"dev.example.app",PushEnvironment.SANDBOX))
        assertFalse(identity.toString().contains(proof));assertFalse(DeliveryTarget.make(route,"secret-address")!!.toString().contains("secret-address"))
        assertEquals(proof,(InstallationIdentity.decode(identity.encoded()) as Outcome.Ok).value.key)
    }
    private inner class Probe(val pause: Boolean = false): InstallationRegistry {
        var record: InstallationRecord? = null;var registrations = 0;var reads = 0
        val targets = mutableListOf<String>()
        val waiting = CompletableDeferred<Unit>();val release = CompletableDeferred<Unit>()
        override suspend fun read(identity: InstallationIdentity): AppResult<InstallationRecord?> {
            reads++
            if (pause) withContext(NonCancellable) { waiting.complete(Unit);release.await() }
            return Outcome.Ok(record)
        }
        override suspend fun register(identity: InstallationIdentity,registrationId: String,target: DeliveryTarget,replacing: InstallationRecord?): AppResult<InstallationRecord> {
            registrations++
            targets.add(target.token)
            record = InstallationRecord.make(id,registrationId,(replacing?.version ?: 0)+1,target.route,true,true,Instant.ofEpochSecond(100),Instant.ofEpochSecond(101),Instant.ofEpochSecond(200))!!
            return Outcome.Err(Failure.Timeout(FailureMeta("Dropped acknowledgement")))
        }
        override suspend fun unregister(identity: InstallationIdentity,current: InstallationRecord): AppResult<InstallationRecord> = Outcome.Err(InstallationFailures.invalidInput)
    }
    @Test fun collectedTokenHandoffRetainsInputAndRotationNeedsANewIntent() = runTest {
        val tokens = PushTokenStore();val repo = Probe()
        val owner = InstallationController(this,InstallationVault { Outcome.Ok(identity) }) { throw AssertionError("Unexpected defect",it) }
        owner.bind(repo);advanceUntilIdle()
        tokens.receive(DeliveryTarget.make(route,"collected.first")!!);assertEquals(0,repo.registrations)
        owner.register(tokens.state.value.target!!)
        tokens.receive(DeliveryTarget.make(route,"collected.rotated")!!);advanceUntilIdle()
        assertEquals(listOf("collected.first"),repo.targets);assertTrue(owner.state.value.needsRefresh)
        owner.register(tokens.state.value.target!!);assertEquals(1,repo.registrations)
        owner.refresh();advanceUntilIdle();assertEquals(1,repo.registrations);assertEquals("collected.rotated",tokens.state.value.target?.token)
        owner.register(tokens.state.value.target!!);advanceUntilIdle()
        assertEquals(listOf("collected.first","collected.rotated"),repo.targets)
        owner.bind(null);assertEquals("collected.rotated",tokens.state.value.target?.token);assertFalse(owner.canRegister)
    }
    @Test fun uncertainWritesRequireReadbackAndNeverReplay() = runTest {
        val repo = Probe();val owner = InstallationController(this,InstallationVault { Outcome.Ok(identity) }) { throw AssertionError("Unexpected registry defect",it) }
        owner.bind(repo);advanceUntilIdle();assertTrue(owner.canRegister);assertNull(owner.state.value.record)
        owner.register(DeliveryTarget.make(route,"first-token")!!);advanceUntilIdle()
        assertTrue(owner.state.value.needsRefresh);assertFalse(owner.canRegister);assertNull(owner.state.value.record)
        owner.register(DeliveryTarget.make(route,"new-token")!!);assertEquals(1,repo.registrations)
        owner.refresh();advanceUntilIdle();assertEquals(1L,owner.state.value.record?.version);assertTrue(owner.canUnregister);assertEquals(1,repo.registrations)
        owner.bind(repo);advanceUntilIdle();assertEquals(id,owner.state.value.installationId);assertEquals(1L,owner.state.value.record?.version);assertEquals(1,repo.registrations)
        owner.bind(null);assertFalse(owner.canRegister);assertNull(owner.state.value.record)
    }
    @Test fun lateOldLeaseAndStorageFailuresCannotPublish() = runTest {
        val old = Probe(pause=true);val next = Probe();val owner = InstallationController(this,InstallationVault { Outcome.Ok(identity) }) { throw AssertionError("Unexpected registry defect",it) }
        old.record = InstallationRecord.make(id,"00000000-0000-4000-8000-000000000002",9,route,true,true,Instant.ofEpochSecond(100),Instant.ofEpochSecond(101),Instant.ofEpochSecond(200))!!
        owner.bind(old);runCurrent();assertTrue(old.waiting.isCompleted)
        owner.bind(next);runCurrent();old.release.complete(Unit);advanceUntilIdle()
        assertTrue(owner.canRegister);assertNull(owner.state.value.record);assertFalse(owner.state.value.busy)
        val blocked = InstallationController(this,InstallationVault { Outcome.Err(InstallationFailures.invalidStorage) }) { throw AssertionError("Unexpected vault defect",it) }
        blocked.bind(next);advanceUntilIdle();assertEquals(InstallationFailures.invalidStorage,blocked.state.value.failure);assertFalse(blocked.canRegister);assertEquals(1,next.reads)
    }
    @Test fun unexpectedDefectsReachReporterWithoutBecomingNetworkFailures() = runTest {
        val defect = IllegalStateException("private detail");var reported: Throwable? = null;val repo = Probe()
        val owner = InstallationController(this,InstallationVault { throw defect }) { reported = it }
        owner.bind(repo);advanceUntilIdle()
        assertSame(defect,reported);assertEquals(FailureKind.INTERNAL,owner.state.value.failure?.kind)
        assertFalse(owner.state.value.failure!!.meta.message.contains("private detail"));assertTrue(owner.state.value.needsRefresh);assertFalse(owner.canRegister);assertEquals(0,repo.reads)
    }
}
