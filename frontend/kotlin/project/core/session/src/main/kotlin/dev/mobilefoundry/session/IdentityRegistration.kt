package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.GoSessionWire.string
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.util.Locale

/** Transient admitted input, never a saved-state/journal/vault record. */
class RegisterIdentity private constructor(val login: String, val displayName: String, val password: String) {
    override fun toString() = "RegisterIdentity(<redacted>)"
    companion object {
        fun make(login: String, displayName: String, password: String): AppResult<RegisterIdentity> {
            val rawLogin = RegistrationAdmission.trim(login); val canonical = rawLogin.lowercase(Locale.ROOT)
            val name = RegistrationAdmission.trim(displayName)
            val fields = linkedMapOf<String, String>()
            if (!rawLogin.all { it.code < 128 } || !RegistrationAdmission.login(canonical)) fields["login"] = "Use 3–128 characters: a–z, 0–9, . _ @ + -. Start with a letter or number."
            if (!RegistrationAdmission.name(name)) fields["display_name"] = "Enter a display name of 1–80 characters without control characters."
            if (!RegistrationAdmission.unicode(password) || password.codePointCount(0, password.length) < 12 || password.toByteArray(Charsets.UTF_8).size > 256) fields["password"] = "Use at least 12 characters and at most 256 UTF-8 bytes."
            return if (fields.isEmpty()) Outcome.Ok(RegisterIdentity(canonical, name, password))
            else Outcome.Err(Failure.Invalid(FailureMeta("Check your account details.", "identity.invalid_registration"), fields))
        }
    }
}
class RegisteredIdentity private constructor(val id: String, val login: String, val displayName: String) {
    fun confirms(command: RegisterIdentity) = login == command.login && displayName == command.displayName
    override fun toString() = "RegisteredIdentity(<redacted>)"
    companion object {
        fun make(id: String, login: String, displayName: String): RegisteredIdentity? =
            if (Principal.make(id, displayName) != null && RegistrationAdmission.login(login) && RegistrationAdmission.name(displayName)) RegisteredIdentity(id, login, displayName) else null
    }
}

/** One public attempt; a profile is not a session or account grant. */
interface IdentityRegistration { suspend fun register(command: RegisterIdentity): AppResult<RegisteredIdentity> }

class HTTPIdentityRegistration(private val client: HTTPClient) : IdentityRegistration {
    companion object {
        fun create(baseUrl: String, transport: HTTPTransport, allowLocalHTTP: Boolean = false): AppResult<HTTPIdentityRegistration> {
            val base = GoSessionAuthority.canonicalBase(baseUrl, allowLocalHTTP) ?: return Outcome.Err(Failure.Internal(FailureMeta("Use an admitted API base for registration.", "identity.registration_configuration")))
            return DefaultHTTPClient.create(base, transport).map(::HTTPIdentityRegistration)
        }
    }
    override suspend fun register(command: RegisterIdentity): AppResult<RegisteredIdentity> {
        currentCoroutineContext().ensureActive()
        val answer = client.requestEntity(HTTPMethod.POST, "identity/register", RequestOptions(body = buildJsonObject {
            put("login", command.login); put("display_name", command.displayName); put("password", command.password)
        }))
        currentCoroutineContext().ensureActive()
        return answer.flatMap { entity ->
            val body = entity.body as? JsonObject
            val id = body?.string("id"); val login = body?.string("login"); val name = body?.string("display_name")
            val value = if (id != null && GoSessionWire.uuid(id) && login != null && name != null) RegisteredIdentity.make(id, login, name) else null
            if (entity.metadata.status == 201 && body?.keys == setOf("id", "login", "display_name") && value != null && value.confirms(command)) Outcome.Ok(value)
            else Outcome.Err(Failure.Internal(FailureMeta("Registration returned an unexpected response.", "identity.registration_response", entity.metadata.requestId, entity.metadata.correlationId)))
        }
    }
}
private object RegistrationAdmission {
    private val whitespace = "\u0009\u000A\u000B\u000C\u000D \u0085\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".toSet()
    fun trim(value: String) = value.trim { it in whitespace }
    fun login(value: String) = value.matches(Regex("[a-z0-9][a-z0-9._@+-]{2,127}"))
    fun unicode(value: String) = value.codePoints().noneMatch { it in 0xD800..0xDFFF }
    fun name(value: String) = value == trim(value) && unicode(value) && value.codePointCount(0, value.length) in 1..80 && value.codePoints().noneMatch { Character.getType(it) == Character.CONTROL.toInt() }
}
