package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

class GoNativeLifecycleTest {
    @Test fun mailPasswordRevocationAndRealSocket() = runBlocking {
        val mailbox = System.getProperty("go.lifecycle.mail.base")
        assumeTrue("Connected acceptance owns SMTP and API", !mailbox.isNullOrEmpty())
        val base = System.getProperty("go.session.test.base")
        val transport = OkHttpTransport()
        val http = (DefaultHTTPClient.create(base, transport) as Outcome.Ok).value
        val login = "kotlin-life-${UUID.randomUUID()}"; val email = "$login@example.com"
        val password = "native lifecycle first password"; val next = "native lifecycle second password"; val final = "native lifecycle third password"
        assertTrue(http.requestEntity(HTTPMethod.POST, "identity/register", RequestOptions(body = buildJsonObject { put("login", login); put("display_name", "Kotlin lifecycle"); put("password", password) })) is Outcome.Ok)
        val authority = (GoSessionAuthority.create(base, transport, allowLocalHTTP = true) as Outcome.Ok).value
        val owner = SessionController(this, authority, MemoryCredentialVault(), System::currentTimeMillis) { fail("Unexpected lifecycle error") }
        try {
            owner.signIn(login, password)!!.join(); val lease = owner.lease!!
            val auth = (AuthenticatedHTTPClient.create(base, transport, owner, lease, allowLocalHTTP = true) as Outcome.Ok).value
            val recovery = (HTTPIdentityRecovery.create(base, transport, auth, true) as Outcome.Ok).value
            val feed = (GoAccountLiveHints.create(base, owner, lease, allowLocalHTTP = true) as Outcome.Ok).value
            var hints = 0
            val watch = launch { feed.watch { hints++ } }
            try {
                withTimeout(10_000) { while (hints < 1) delay(20) }
                assertTrue(auth.requestEntity(HTTPMethod.POST, "accounts/${lease.scope.accountId}/notes", RequestOptions(body = buildJsonObject { put("title", "Native socket change") }, idempotencyKey = UUID.randomUUID().toString())) is Outcome.Ok)
                withTimeout(10_000) { while (hints < 2) delay(20) }
            } finally { watch.cancelAndJoin() }
            assertTrue(recovery.requestVerification(email, password) is Outcome.Ok)
            val verify = capturedCode(mailbox, email, "verify_email")
            assertTrue(recovery.verify(verify) is Outcome.Ok)
            assertEquals(email, (recovery.contact() as Outcome.Ok).value.email)
            assertTrue(recovery.requestReset(email) is Outcome.Ok)
            val reset = capturedCode(mailbox, email, "reset_password")
            assertTrue(recovery.reset(reset, next) is Outcome.Ok)
            assertEquals(FailureKind.UNAUTHENTICATED, (auth.requestEntity(HTTPMethod.GET, "identity/me") as Outcome.Err).error.kind)
            owner.signOut()!!.join(); owner.signIn(login, next)!!.join()
            val changedClient = (AuthenticatedHTTPClient.create(base, transport, owner, owner.lease!!, allowLocalHTTP = true) as Outcome.Ok).value
            val changedRecovery = (HTTPIdentityRecovery.create(base, transport, changedClient, true) as Outcome.Ok).value
            assertTrue(changedRecovery.change(next, final) is Outcome.Ok)
            owner.signOut()!!.join(); owner.signIn(login, final)!!.join(); assertNotNull(owner.lease)
            assertTrue(changedRecovery.reset(reset, password) is Outcome.Err)
            owner.signOut()!!.join()
        } finally { owner.shutdown() }
    }
    private suspend fun capturedCode(base: String, email: String, purpose: String): String {
        repeat(100) {
            val connection = URI("$base/code?email=${URLEncoder.encode(email, "UTF-8")}&purpose=$purpose").toURL().openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 2000; connection.readTimeout = 2000
            try {
                if (connection.responseCode == 200) return Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject["token"]!!.jsonPrimitive.content
            } finally { connection.disconnect() }
            delay(50)
        }
        error("Owned SMTP fixture did not capture the code")
    }
}
