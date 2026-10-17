package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

private const val liveBase = "https://example.invalid/v1"
private const val liveAccount = "00000000-0000-4000-8000-000000000002"
private fun hint(kind: String = "ready", account: String = liveAccount, extra: String = "") = """{"type":"$kind","resource":"notes","account_id":"$account","refetch":true$extra}"""
class AccountLiveHintsTest {
    @Test fun admitsScopedReadyThenChangedAndNeverRetargets() = runBlocking {
        val owner = SessionController(this, LiveAuthority(), MemoryCredentialVault(), { 1L }) { fail("Unexpected failure") }
        owner.signIn("alex", "fixture")!!.join(); val lease = owner.lease!!
        val vectors = kotlinx.serialization.json.Json.parseToJsonElement(java.io.File(System.getProperty("native.lifecycle.fixtures"), "hints.json").readText()) as kotlinx.serialization.json.JsonArray
        for (vector in vectors) {
            val objectValue = vector as kotlinx.serialization.json.JsonObject
            val frames = (objectValue["frames"] as kotlinx.serialization.json.JsonArray).map { it.toString() }
            val expected = objectValue["hints"].toString().toInt()
            val socket = object : LiveSocketTransport {
                override fun messages(url: String, bearer: String) = flow {
                    assertEquals("wss://example.invalid/v1/accounts/$liveAccount/realtime", url)
                    assertEquals("a1." + "A".repeat(43), bearer)
                    frames.forEach { emit(it) }
                }
            }
            val feed = (GoAccountLiveHints.create(liveBase, owner, lease, socket) as Outcome.Ok).value
            var count = 0; feed.watch { count++ }; assertEquals(expected, count)
        }
        assertTrue(GoAccountLiveHints.create("https://another.invalid/v1", owner, lease) is Outcome.Err)
        val socket = object : LiveSocketTransport { override fun messages(url: String, bearer: String) = flowOf(hint(), hint("changed")) }
        val feed = (GoAccountLiveHints.create(liveBase, owner, lease, socket) as Outcome.Ok).value
        var count = 0; feed.watch { count++; owner.signOut()!!.join() }; assertEquals(1, count)
        owner.shutdown()
    }
}
private class LiveAuthority : SessionAuthority {
    override val id = GoSessionAuthority.deploymentId(liveBase)
    override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = Outcome.Ok(SessionGrant.make(id,
        Principal.make("00000000-0000-4000-8000-000000000001", "Test")!!, Account.make(liveAccount, "Personal")!!,
        "a1." + "A".repeat(43), "r1." + "A".repeat(43), 10_000L)!!)
    override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = Outcome.Err(SessionFailures.rejected)
    override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = Outcome.Ok(Unit)
}
