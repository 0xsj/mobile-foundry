package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class IdentityRecoveryTest {
    @Test fun routesSeparatePublicAndAuthenticatedClientsAndRefuseWrongPurpose() = runBlocking {
        val public = RecoveryClient(); val auth = RecoveryClient()
        val service = HTTPIdentityRecovery(public, auth)
        val verify = "e1." + "A".repeat(43); val reset = "p1." + "A".repeat(43)
        assertTrue(service.requestVerification("person@example.com", "a private passphrase") is Outcome.Ok)
        assertTrue(service.verify(verify) is Outcome.Ok)
        assertTrue(service.requestReset("person@example.com") is Outcome.Ok)
        assertTrue(service.reset(reset, "a private passphrase") is Outcome.Ok)
        assertTrue(service.change("a private passphrase", "another private password") is Outcome.Ok)
        assertEquals(listOf("identity/email/request", "identity/password/change"), auth.paths)
        assertEquals(listOf("identity/email/verify", "identity/password/request-reset", "identity/password/reset"), public.paths)
        assertTrue(service.reset(verify, "a private passphrase") is Outcome.Err)
        assertTrue(service.verify(reset) is Outcome.Err)
        assertTrue(service.change("a private passphrase", "short") is Outcome.Err)
        assertEquals(3, public.paths.size); assertEquals(2, auth.paths.size)
        assertTrue(HTTPIdentityRecovery(public).requestVerification("person@example.com", "a private passphrase") is Outcome.Err)
        assertEquals("RecoveryContact(<redacted>)", RecoveryContact("person@example.com", null).toString())
    }
    @Test fun contactProjectionRejectsWrongTypesAndUnexpectedFields() = runBlocking {
        for (body in listOf(buildJsonObject { put("verified", "true") }, buildJsonObject { put("verified", false); put("email", "private@example.com") })) {
            val client = RecoveryClient(body)
            assertTrue(HTTPIdentityRecovery(client, client).contact() is Outcome.Err)
        }
        val client = RecoveryClient(buildJsonObject { put("verified", false) })
        assertNull((HTTPIdentityRecovery(client, client).contact() as Outcome.Ok).value.email)
    }
}
private class RecoveryClient(private val contactBody: JsonElement = JsonNull) : HTTPClient {
    val paths = mutableListOf<String>()
    override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
        paths += path
        return Outcome.Ok(Entity(contactBody, ResponseMetadata(if (method == HTTPMethod.GET) 200 else if (path.endsWith("request-reset")) 202 else 204, null, null, null)))
    }
}
