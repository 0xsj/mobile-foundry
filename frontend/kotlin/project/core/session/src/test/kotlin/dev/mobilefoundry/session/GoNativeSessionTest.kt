package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class GoNativeSessionTest {
    @Test fun nativeGoSignInScopeRotationReuseAndSignOut() = runBlocking {
        val configured = System.getProperty("go.session.test.base")
        assumeTrue("make go-native-test owns a disposable Go API", !configured.isNullOrBlank())
        val base = requireNotNull(configured)
        val transport = OkHttpTransport()
        val http = (DefaultHTTPClient.create(base, transport) as Outcome.Ok).value
        val login = "kotlin-${UUID.randomUUID()}"
        val samLogin = "kotlin-${UUID.randomUUID()}"
        val password = "native integration passphrase"
        for (user in listOf(login, samLogin)) {
            val entity = (http.requestEntity(HTTPMethod.POST, "identity/register", RequestOptions(body = buildJsonObject {
                put("login", user); put("display_name", if (user == login) "Kotlin Alex" else "Kotlin Sam"); put("password", password)
            })) as Outcome.Ok).value
            assertEquals(201, entity.metadata.status)
        }
        val authority = (GoSessionAuthority.create(base, transport, allowLocalHTTP = true) as Outcome.Ok).value
        val vault = MemoryCredentialVault()
        val controller = SessionController(this, authority, vault, System::currentTimeMillis) { fail("Unexpected integration failure") }
        controller.signIn(login, password)!!.join()
        assertEquals(SessionPhase.SIGNED_IN, controller.state.value.phase)
        val firstLease = controller.lease!!; val oldCredential = (vault.load() as Outcome.Ok).value!!
        val client = (AuthenticatedHTTPClient.create(base, transport, controller, firstLease, allowLocalHTTP = true) as Outcome.Ok).value
        val workspace = (client.requestEntity(HTTPMethod.POST, "accounts", RequestOptions(body = buildJsonObject { put("name", "Kotlin workspace") })) as Outcome.Ok).value
        val workspaceId = workspace.body.jsonObject["id"]!!.jsonPrimitive.content
        val directory = HTTPAccountDirectory(client, firstLease.scope.principalId)
        val page = (directory.list(limit = 1) as Outcome.Ok).value
        val next = (directory.list(page.nextCursor!!, 1) as Outcome.Ok).value
        assertEquals(setOf(firstLease.scope.accountId, workspaceId), (page.accounts + next.accounts).map { it.id }.toSet())
        controller.selectAccount(workspaceId)!!.join()
        assertFalse(controller.isCurrent(firstLease)); assertEquals(workspaceId, controller.lease!!.scope.accountId)
        controller.renew()!!.join()
        assertEquals(SessionPhase.SIGNED_IN, controller.state.value.phase); assertEquals(workspaceId, (vault.load() as Outcome.Ok).value!!.scope.accountId)
        controller.shutdown()
        val restored = SessionController(this, authority, vault, System::currentTimeMillis) { fail("Unexpected integration failure") }
        restored.restore()!!.join()
        assertEquals(SessionPhase.SIGNED_IN, restored.state.value.phase); assertEquals(workspaceId, restored.lease!!.scope.accountId)
        val sam = SessionController(this, authority, MemoryCredentialVault(), System::currentTimeMillis) { fail("Unexpected integration failure") }
        sam.signIn(samLogin, password)!!.join()
        val samClient = (AuthenticatedHTTPClient.create(base, transport, sam, sam.lease!!, allowLocalHTTP = true) as Outcome.Ok).value
        assertEquals(FailureKind.FORBIDDEN, (samClient.requestEntity(HTTPMethod.GET, "accounts/$workspaceId") as Outcome.Err).error.kind)
        assertEquals(FailureKind.UNAUTHENTICATED, (http.requestEntity(HTTPMethod.POST, "identity/renew", RequestOptions(body = buildJsonObject { put("refresh_token", oldCredential.refreshToken) })) as Outcome.Err).error.kind)
        val currentClient = (AuthenticatedHTTPClient.create(base, transport, restored, restored.lease!!, allowLocalHTTP = true) as Outcome.Ok).value
        assertEquals(FailureKind.UNAUTHENTICATED, (currentClient.requestEntity(HTTPMethod.GET, "accounts/$workspaceId") as Outcome.Err).error.kind)
        assertEquals(SessionPhase.SIGNED_IN, restored.state.value.phase)
        restored.renew()!!.join(); assertNull(restored.lease); assertNull((vault.load() as Outcome.Ok).value)
        restored.signIn(login, password)!!.join()
        val access = restored.accessToken(restored.lease!!)!!
        restored.signOut()!!.join(); assertNull((vault.load() as Outcome.Ok).value)
        assertEquals(FailureKind.UNAUTHENTICATED, (http.requestEntity(HTTPMethod.GET, "accounts/$workspaceId", RequestOptions(headers = mapOf("authorization" to "Bearer $access"))) as Outcome.Err).error.kind)
        sam.signOut()!!.join()
    }
}
