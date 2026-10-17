package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.GoSessionWire.string
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

class RecoveryContact(val email: String?, val verifiedAt: String?) {
    override fun toString() = "RecoveryContact(<redacted>)"
}

/** One attempt per explicit action; authenticated clients bind an exact lease. */
interface IdentityRecovery {
    suspend fun contact(): AppResult<RecoveryContact>
    suspend fun requestVerification(email: String, password: String): AppResult<Unit>
    suspend fun verify(token: String): AppResult<Unit>
    suspend fun requestReset(email: String): AppResult<Unit>
    suspend fun reset(token: String, password: String): AppResult<Unit>
    suspend fun change(currentPassword: String, newPassword: String): AppResult<Unit>
}

class HTTPIdentityRecovery(private val publicClient: HTTPClient, private val authenticatedClient: HTTPClient? = null) : IdentityRecovery {
    companion object {
        fun create(baseUrl: String, transport: HTTPTransport, authenticatedClient: HTTPClient? = null, allowLocalHTTP: Boolean = false): AppResult<HTTPIdentityRecovery> {
            val base = GoSessionAuthority.canonicalBase(baseUrl, allowLocalHTTP) ?: return Outcome.Err(SessionFailures.scopeMismatch)
            return DefaultHTTPClient.create(base, transport).map { HTTPIdentityRecovery(it, authenticatedClient) }
        }
        private fun mail(s: String) = s.toByteArray().size in 3..254 && s.all { it.code in 33..126 } && '@' in s
        private fun password(s: String) = s.codePointCount(0, s.length) >= 12 && s.toByteArray().size <= 256 && s.codePoints().noneMatch { it in 0xD800..0xDFFF }
        private val input = Failure.Invalid(FailureMeta("Check the address, code and password (at least 12 characters).", "identity.invalid_input"), emptyMap())
        private val response = Failure.Internal(FailureMeta("The API returned an unexpected identity response.", "identity.invalid_response"))
    }
    override suspend fun contact(): AppResult<RecoveryContact> {
        val client = authenticatedClient ?: return Outcome.Err(SessionFailures.scopeChanged)
        return client.requestEntity(HTTPMethod.GET, "identity/email").flatMap { entity ->
            val b = entity.body as? JsonObject
            if (entity.metadata.status != 200 || b == null) Outcome.Err(response)
            else if (b == buildJsonObject { put("verified", false) }) Outcome.Ok(RecoveryContact(null, null))
            else {
                val email = b.string("email"); val at = b.string("verified_at")
                if (b.keys != setOf("verified", "email", "verified_at") || b["verified"] != JsonPrimitive(true) || email == null || !mail(email) || at == null || GoSessionWire.micros(at) == null) Outcome.Err(response)
                else Outcome.Ok(RecoveryContact(email, at))
            }
        }
    }
    override suspend fun requestVerification(email: String, password: String): AppResult<Unit> {
        if (!mail(email) || !password(password)) return Outcome.Err(input)
        val client = authenticatedClient ?: return Outcome.Err(SessionFailures.scopeChanged)
        return post(client, "email/request", mapOf("email" to email, "password" to password))
    }
    override suspend fun verify(token: String): AppResult<Unit> {
        if (!GoSessionWire.token(token, "e1")) return Outcome.Err(input)
        return post(publicClient, "email/verify", mapOf("token" to token))
    }
    override suspend fun requestReset(email: String): AppResult<Unit> {
        if (!mail(email)) return Outcome.Err(input)
        return post(publicClient, "password/request-reset", mapOf("email" to email), 202)
    }
    override suspend fun reset(token: String, password: String): AppResult<Unit> {
        if (!GoSessionWire.token(token, "p1") || !password(password)) return Outcome.Err(input)
        return post(publicClient, "password/reset", mapOf("token" to token, "password" to password))
    }
    override suspend fun change(currentPassword: String, newPassword: String): AppResult<Unit> {
        if (!password(currentPassword) || !password(newPassword)) return Outcome.Err(input)
        val client = authenticatedClient ?: return Outcome.Err(SessionFailures.scopeChanged)
        return post(client, "password/change", mapOf("current_password" to currentPassword, "new_password" to newPassword))
    }
    private suspend fun post(client: HTTPClient, path: String, body: Map<String, String>, status: Int = 204): AppResult<Unit> {
        currentCoroutineContext().ensureActive()
        return client.requestEntity(HTTPMethod.POST, "identity/$path", RequestOptions(body = JsonObject(body.mapValues { JsonPrimitive(it.value) }))).flatMap {
            if (it.metadata.status == status) Outcome.Ok(Unit) else Outcome.Err(response)
        }
    }
}
