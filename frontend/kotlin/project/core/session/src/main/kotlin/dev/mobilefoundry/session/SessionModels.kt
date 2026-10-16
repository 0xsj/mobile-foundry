package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException

private fun validId(value: String) = value.length in 1..200 && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._" }
private fun validName(value: String) = value.isNotBlank() && value.codePointCount(0, value.length) <= 200
private fun validSecret(value: String) = value.length in 1..8192 && value.all { it.code in 33..126 }

@ConsistentCopyVisibility
data class Principal private constructor(val id: String, val displayName: String) {
    companion object { fun make(id: String, displayName: String) = if (validId(id) && validName(displayName)) Principal(id, displayName) else null }
}
@ConsistentCopyVisibility
data class Account private constructor(val id: String, val name: String) {
    companion object { fun make(id: String, name: String) = if (validId(id) && validName(name)) Account(id, name) else null }
}
@ConsistentCopyVisibility
data class SessionScope private constructor(val authorityId: String, val principalId: String, val accountId: String) {
    companion object { fun make(authorityId: String, principalId: String, accountId: String) =
        if (validId(authorityId) && validId(principalId) && validId(accountId)) SessionScope(authorityId, principalId, accountId) else null }
}
class SignIn private constructor(val login: String, val password: String) {
    override fun toString() = "SignIn(<redacted>)"
    companion object {
        fun make(login: String, password: String): AppResult<SignIn> {
            val normalized = login.trim().lowercase(Locale.ROOT)
            val fields = linkedMapOf<String, String>()
            if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length) > 320) fields["login"] = "Enter a login of up to 320 characters."
            if (password.isEmpty() || password.codePointCount(0, password.length) > 1024) fields["password"] = "Enter a password of up to 1024 characters."
            return if (fields.isEmpty()) Outcome.Ok(SignIn(normalized, password))
                else Outcome.Err(Failure.Invalid(FailureMeta("Check your sign-in details.", "session.invalid_sign_in"), fields))
        }
    }
}
class SessionGrant private constructor(val scope: SessionScope, val principal: Principal, val account: Account,
    val accessToken: String, val refreshToken: String, val expiresAtMillis: Long) {
    override fun toString() = "SessionGrant(<redacted>)"
    companion object {
        fun make(authorityId: String, principal: Principal, account: Account, accessToken: String, refreshToken: String, expiresAtMillis: Long): SessionGrant? {
            val scope = SessionScope.make(authorityId, principal.id, account.id) ?: return null
            return if (validSecret(accessToken) && validSecret(refreshToken) && expiresAtMillis > 0)
                SessionGrant(scope, principal, account, accessToken, refreshToken, expiresAtMillis) else null
        }
    }
}
class StoredCredential private constructor(val scope: SessionScope, val refreshToken: String) {
    override fun equals(other: Any?) = other is StoredCredential && scope == other.scope && refreshToken == other.refreshToken
    override fun hashCode() = 31 * scope.hashCode() + refreshToken.hashCode()
    override fun toString() = "StoredCredential(<redacted>)"
    fun encoded(): ByteArray = buildJsonObject {
        put("version", 1); put("authority", scope.authorityId); put("principal", scope.principalId)
        put("account", scope.accountId); put("refreshToken", refreshToken)
    }.toString().toByteArray(Charsets.UTF_8)
    companion object {
        fun make(scope: SessionScope, refreshToken: String) = if (validSecret(refreshToken)) StoredCredential(scope, refreshToken) else null
        fun from(grant: SessionGrant) = StoredCredential(grant.scope, grant.refreshToken)
        fun decode(bytes: ByteArray): AppResult<StoredCredential> {
            if (bytes.size > 65_536) return Outcome.Err(SessionFailures.invalidCredential)
            val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                catch (_: CharacterCodingException) { return Outcome.Err(SessionFailures.invalidCredential) }
            val record = try { Json.parseToJsonElement(text) as? JsonObject } catch (_: IllegalArgumentException) { null }
            fun string(key: String): String? = (record?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
            val version = (record?.get("version") as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            val scope = SessionScope.make(string("authority") ?: "", string("principal") ?: "", string("account") ?: "")
            val value = scope?.let { make(it, string("refreshToken") ?: "") }
            return if (version == 1 && value != null) Outcome.Ok(value) else Outcome.Err(SessionFailures.invalidCredential)
        }
    }
}
enum class SessionPhase { SIGNED_OUT, RESTORING, SIGNING_IN, SIGNED_IN, RENEWING, SELECTING_ACCOUNT, SIGNING_OUT, BLOCKED;
    val isBusy get() = this in listOf(RESTORING, SIGNING_IN, RENEWING, SELECTING_ACCOUNT, SIGNING_OUT)
}
data class SessionSnapshot(val phase: SessionPhase = SessionPhase.SIGNED_OUT, val principal: Principal? = null,
    val account: Account? = null, val failure: Failure? = null, val epoch: Long = 0)
@ConsistentCopyVisibility
data class SessionLease internal constructor(val scope: SessionScope, val epoch: Long, internal val ownerId: UUID)
object SessionFailures {
    val scopeChanged = Failure.Canceled(FailureMeta("Your session changed. Reopen or sign in before trying again.", "session.scope_changed"))
    val invalidCredential = Failure.Unavailable(FailureMeta("Saved session data could not be read. It has been kept.", "session.credential_invalid"))
    val storage = Failure.Unavailable(FailureMeta("Session storage is unavailable. Try again.", "session.storage_unavailable"))
    val expired = Failure.Unauthenticated(FailureMeta("Your access has expired. Renew or sign in again.", "session.expired"))
    val rejected = Failure.Unauthenticated(FailureMeta("Sign in again to continue.", "session.rejected"))
    val scopeMismatch = Failure.Unauthenticated(FailureMeta("The saved session no longer matches this account. Sign in again.", "session.scope_mismatch"))
}
