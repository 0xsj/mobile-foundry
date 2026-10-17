package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Recoverable invalidations only. Consumers own HTTP reads/reconciliation. */
interface AccountLiveHints { suspend fun watch(onHint: suspend () -> Unit): AppResult<Unit> }
interface LiveSocketTransport { fun messages(url: String, bearer: String): Flow<String> }

class GoAccountLiveHints private constructor(private val url: String, private val session: SessionController,
    private val lease: SessionLease, private val transport: LiveSocketTransport) : AccountLiveHints {
    companion object {
        fun create(baseUrl: String, session: SessionController, lease: SessionLease,
            transport: LiveSocketTransport = OkHttpLiveSocketTransport(), allowLocalHTTP: Boolean = false): AppResult<GoAccountLiveHints> {
            val base = GoSessionAuthority.canonicalBase(baseUrl, allowLocalHTTP) ?: return Outcome.Err(SessionFailures.scopeMismatch)
            if (lease.scope.authorityId != GoSessionAuthority.deploymentId(base) || !GoSessionWire.uuid(lease.scope.accountId)) return Outcome.Err(SessionFailures.scopeMismatch)
            val url = base.replaceFirst("https:", "wss:").replaceFirst("http:", "ws:") + "/accounts/${lease.scope.accountId}/realtime"
            return Outcome.Ok(GoAccountLiveHints(url, session, lease, transport))
        }
        private val invalid = Failure.Internal(FailureMeta("Live updates returned an unexpected message.", "realtime.invalid_message"))
    }
    override suspend fun watch(onHint: suspend () -> Unit): AppResult<Unit> {
        val bearer = session.accessToken(lease)?.takeIf { GoSessionWire.token(it, "a1") } ?: return Outcome.Err(SessionFailures.scopeChanged)
        var ready = false
        return try {
            transport.messages(url, bearer).collect { text ->
                currentCoroutineContext().ensureActive()
                if (!session.isCurrent(lease)) throw HintFailure(SessionFailures.scopeChanged)
                val b = if (text.toByteArray().size <= 1024) try { Json.parseToJsonElement(text) as? JsonObject } catch (_: Exception) { null } else null
                if (b == null || b.keys != setOf("type", "resource", "account_id", "refetch") || b["type"] != JsonPrimitive(if (ready) "changed" else "ready") ||
                    b["resource"] != JsonPrimitive("notes") || b["account_id"] != JsonPrimitive(lease.scope.accountId) || b["refetch"] != JsonPrimitive(true)) throw HintFailure(invalid)
                ready = true; onHint()
            }
            Outcome.Err(Failure.Unavailable(FailureMeta("Live updates disconnected. Refresh or reconnect.", "realtime.disconnected")))
        } catch (canceled: CancellationException) { throw canceled }
        catch (failure: HintFailure) { Outcome.Err(failure.failure) }
        catch (_: IOException) { Outcome.Err(Failure.Unavailable(FailureMeta("Live updates unavailable. You can still refresh.", "realtime.unavailable"))) }
    }
}
private class HintFailure(val failure: Failure) : Exception("Live hint refused")

/** Dedicated client: no cookies, credentials, redirects, retries, or shared requests. */
class OkHttpLiveSocketTransport : LiveSocketTransport {
    override fun messages(url: String, bearer: String): Flow<String> = callbackFlow {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(10, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
        val socket = client.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $bearer").build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.toByteArray().size > 1024 || !trySend(text).isSuccess) { close(IOException("Live message refused")); webSocket.cancel() }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { close(IOException("Live text required")); webSocket.cancel() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { close(); webSocket.cancel() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(IOException("Live connection unavailable")) }
        })
        awaitClose { socket.cancel(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }.buffer(16)
}
