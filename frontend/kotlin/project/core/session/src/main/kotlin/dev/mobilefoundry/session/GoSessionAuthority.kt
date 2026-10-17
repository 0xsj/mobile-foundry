package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.GoSessionWire.string
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

sealed interface GoAccountSelection {
    data class Personal(val name: String = "Personal") : GoAccountSelection
    data class Existing(val accountId: String) : GoAccountSelection
}

/** Owned Identity + Account contracts. One attempt per request, no redirect or replay. */
class GoSessionAuthority private constructor(override val id: String, val baseUrl: String, private val client: HTTPClient,
    private val selection: GoAccountSelection, private val now: () -> Long) : AccountSelectingAuthority {
    companion object {
        private val configuration = Failure.Internal(FailureMeta("Use a valid HTTPS API base, or explicitly enabled loopback HTTP, and an admitted account selection.", "session.go_configuration"))
        fun create(baseUrl: String, transport: HTTPTransport, selection: GoAccountSelection = GoAccountSelection.Personal(),
            timeoutMilliseconds: Long = 15_000, allowLocalHTTP: Boolean = false, now: () -> Long = System::currentTimeMillis): AppResult<GoSessionAuthority> {
            val canonical = canonicalBase(baseUrl, allowLocalHTTP) ?: return Outcome.Err(configuration)
            if (!(when (selection) { is GoAccountSelection.Personal -> GoSessionWire.name(selection.name); is GoAccountSelection.Existing -> GoSessionWire.uuid(selection.accountId) })) return Outcome.Err(configuration)
            val authorityId = deploymentId(canonical)
            return DefaultHTTPClient.create(canonical, transport, timeoutMilliseconds).map { GoSessionAuthority(authorityId, canonical, it, selection, now) }
        }
        internal fun deploymentId(canonical: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("go.identity.v1\n$canonical".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
            return "go.identity.v1.$digest"
        }
        internal fun canonicalBase(value: String, allowLocalHTTP: Boolean): String? {
            if (value != value.trim()) return null
            val parts = try { URI(value) } catch (_: java.net.URISyntaxException) { return null }
            val scheme = parts.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = parts.host?.lowercase(Locale.ROOT)?.removePrefix("[")?.removeSuffix("]") ?: return null
            if (parts.rawUserInfo != null || parts.rawQuery != null || parts.rawFragment != null || parts.port != -1 && parts.port !in 1..65535) return null
            if (!(host == "::1" || host.matches(Regex("[a-z0-9.-]+"))) || !(scheme == "https" || allowLocalHTTP && scheme == "http" && host in listOf("localhost", "127.0.0.1", "::1"))) return null
            val path = (parts.rawPath ?: "").trimEnd('/')
            if (path.isNotEmpty() && path.split('/').drop(1).any { it in listOf("", ".", "..") || !it.matches(Regex("[A-Za-z0-9._~-]+")) }) return null
            val port = parts.port.takeUnless { it == -1 || scheme == "https" && it == 443 || scheme == "http" && it == 80 }
            return "$scheme://${if (host == "::1") "[::1]" else host}${port?.let { ":$it" } ?: ""}$path"
        }
    }
    override suspend fun signIn(command: SignIn): AppResult<SessionGrant> {
        currentCoroutineContext().ensureActive()
        val entity = when (val answer = client.requestEntity(HTTPMethod.POST, "identity/sign-in", RequestOptions(body = buildJsonObject { put("login", command.login); put("password", command.password) }))) {
            is Outcome.Err -> return answer
            is Outcome.Ok -> answer.value
        }
        val tokens = GoSessionWire.tokens(entity, now()) ?: return Outcome.Err(GoSessionWire.failure(entity))
        if ((entity.body as? JsonObject)?.get("principal")?.let { it as? JsonObject }?.string("login") != command.login) return Outcome.Err(GoSessionWire.failure(entity))
        val account = when (val chosen = selection) {
            is GoAccountSelection.Personal -> read(tokens, "accounts/bootstrap", chosen.name)
            is GoAccountSelection.Existing -> read(tokens, "accounts/${chosen.accountId}")
        }
        return when (account) {
            is Outcome.Ok -> grant(tokens, account.value)
            is Outcome.Err -> { revokeToken(tokens.refresh, 2000); account }
        }
    }
    /** Direct callers own persistence; controllers use the checkpoint overload. */
    override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = renew(credential) { Outcome.Ok(Unit) }
    override suspend fun renew(credential: StoredCredential, checkpoint: CredentialCheckpoint): AppResult<SessionGrant> {
        currentCoroutineContext().ensureActive()
        if (credential.scope.authorityId != id || !GoSessionWire.uuid(credential.scope.accountId) || !GoSessionWire.uuid(credential.scope.principalId)) return Outcome.Err(SessionFailures.scopeMismatch)
        if (!GoSessionWire.token(credential.refreshToken, "r1")) return Outcome.Err(SessionFailures.rejected)
        val entity = when (val answer = client.requestEntity(HTTPMethod.POST, "identity/renew", RequestOptions(body = buildJsonObject { put("refresh_token", credential.refreshToken) }))) {
            is Outcome.Err -> return answer
            is Outcome.Ok -> answer.value
        }
        val tokens = GoSessionWire.tokens(entity, now()) ?: return Outcome.Err(GoSessionWire.failure(entity))
        if (tokens.principal.id != credential.scope.principalId) return Outcome.Err(SessionFailures.scopeMismatch)
        val rotated = StoredCredential.make(credential.scope, tokens.refresh) ?: return Outcome.Err(SessionFailures.scopeMismatch)
        val saved = checkpoint(rotated)
        if (saved is Outcome.Err) return saved
        currentCoroutineContext().ensureActive()
        return read(tokens, "accounts/${credential.scope.accountId}").flatMap { grant(tokens, it) }
    }
    override suspend fun selectAccount(accountId: String, grant: SessionGrant): AppResult<SessionGrant> {
        currentCoroutineContext().ensureActive()
        if (grant.scope.authorityId != id || !GoSessionWire.uuid(accountId) || !GoSessionWire.uuid(grant.principal.id) || grant.expiresAtMillis <= now()) return Outcome.Err(SessionFailures.scopeMismatch)
        val tokens = GoSessionWire.Tokens(grant.principal, grant.accessToken, grant.refreshToken, grant.expiresAtMillis)
        return read(tokens, "accounts/$accountId").flatMap { grant(tokens, it) }
    }
    override suspend fun revoke(credential: StoredCredential): AppResult<Unit> {
        currentCoroutineContext().ensureActive()
        if (credential.scope.authorityId != id) return Outcome.Err(SessionFailures.scopeMismatch)
        if (!GoSessionWire.token(credential.refreshToken, "r1")) return Outcome.Err(SessionFailures.rejected)
        return revokeToken(credential.refreshToken)
    }
    private suspend fun revokeToken(token: String, timeout: Long? = null): AppResult<Unit> = client.requestEntity(HTTPMethod.POST, "identity/sign-out",
        RequestOptions(timeoutMilliseconds = timeout, body = buildJsonObject { put("refresh_token", token) })).flatMap {
        if (it.metadata.status == 204) Outcome.Ok(Unit) else Outcome.Err(GoSessionWire.failure(it))
    }
    private suspend fun read(tokens: GoSessionWire.Tokens, path: String, name: String? = null): AppResult<Account> = client.requestEntity(
        if (name == null) HTTPMethod.GET else HTTPMethod.POST, path, RequestOptions(headers = mapOf("authorization" to "Bearer ${tokens.access}"),
            body = name?.let { buildJsonObject { put("name", it) } })).flatMap { entity ->
        val account = GoSessionWire.account(entity.body, tokens.principal.id)
        val version = GoSessionWire.version((entity.body as? JsonObject)?.get("version"))
        if (entity.metadata.status !in (if (name == null) listOf(200) else listOf(200, 201)) || account == null || version == null || entity.metadata.etag != "\"$version\"" || name == null && path != "accounts/${account.id}") Outcome.Err(GoSessionWire.failure(entity)) else Outcome.Ok(account)
    }
    private fun grant(tokens: GoSessionWire.Tokens, account: Account): AppResult<SessionGrant> {
        if (tokens.expiry <= now()) return Outcome.Err(SessionFailures.expired)
        return SessionGrant.make(id, tokens.principal, account, tokens.access, tokens.refresh, tokens.expiry)?.let { Outcome.Ok(it) } ?: Outcome.Err(SessionFailures.expired)
    }
}
