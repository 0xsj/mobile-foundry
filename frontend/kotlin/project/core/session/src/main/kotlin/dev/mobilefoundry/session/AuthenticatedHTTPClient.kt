package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Call on the session's UI executor. One lease, one attempt; no renewal or replay. */
class AuthenticatedHTTPClient private constructor(private val client: HTTPClient, private val session: SessionController,
    private val lease: SessionLease) : HTTPClient {
    companion object {
        fun create(baseUrl: String, transport: HTTPTransport, session: SessionController, lease: SessionLease,
            timeoutMilliseconds: Long = 15_000, allowLocalHTTP: Boolean = false): AppResult<AuthenticatedHTTPClient> {
            val url = baseUrl.toHttpUrlOrNull()
            if (url == null || !(url.isHttps || allowLocalHTTP && url.scheme == "http" && url.host in listOf("localhost", "127.0.0.1", "::1")))
                return Outcome.Err(Failure.Internal(FailureMeta("Session requests require HTTPS or explicitly enabled local HTTP.", "session.invalid_configuration")))
            return DefaultHTTPClient.create(baseUrl, transport, timeoutMilliseconds).map { AuthenticatedHTTPClient(it, session, lease) }
        }
    }
    override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
        currentCoroutineContext().ensureActive()
        val token = session.accessToken(lease) ?: return Outcome.Err(SessionFailures.scopeChanged)
        if (options.headers.keys.any { it.equals("authorization", true) })
            return Outcome.Err(Failure.Invalid(FailureMeta("Authorization is owned by the session.", "session.authorization_override"), emptyMap()))
        if (!token.matches(Regex("[A-Za-z0-9._~+/-]+=*")))
            return Outcome.Err(Failure.Internal(FailureMeta("The access credential is not a Bearer token.", "session.invalid_access_token")))
        val headers = LinkedHashMap(options.headers).apply { put("authorization", "Bearer $token") }
        val forwarded = options.copy(headers = headers.toMap(), query = options.query.toList())
        try {
            val answer = client.requestEntity(method, path, forwarded)
            currentCoroutineContext().ensureActive()
            return if (session.isCurrent(lease)) answer else Outcome.Err(SessionFailures.scopeChanged)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!session.isCurrent(lease)) return Outcome.Err(SessionFailures.scopeChanged)
            throw error
        }
    }
}
