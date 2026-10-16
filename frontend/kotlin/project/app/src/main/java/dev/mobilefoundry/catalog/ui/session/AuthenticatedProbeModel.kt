package dev.mobilefoundry.catalog.ui.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProbeResponse(val label: String) { SUCCESS("Success"), UNAUTHORIZED("Unauthorized"), SLOW("Slow response") }
data class ProbeState(val report: String? = null, val isBusy: Boolean = false)
/** Local wire fixture; request preparation and failure decoding use the real HTTP boundary. */
private class ProbeTransport(private val response: ProbeResponse) : HTTPTransport {
    override suspend fun send(request: PreparedRequest): AppResult<WireResponse> {
        if (response == ProbeResponse.SLOW) delay(3_000)
        return if (response == ProbeResponse.UNAUTHORIZED) Outcome.Ok(WireResponse(401,
            mapOf("content-type" to "application/problem+json", "x-request-id" to "catalog-probe"),
            """{"type":"about:blank","title":"Unauthorized","status":401,"detail":"This local fixture rejected the request."}""".toByteArray()))
        else Outcome.Ok(WireResponse(200, mapOf("content-type" to "application/json", "x-request-id" to "catalog-probe"), """{"status":"ok"}""".toByteArray()))
    }
}
class AuthenticatedProbeModel(private val scope: CoroutineScope, private val session: SessionController, private val lease: SessionLease) {
    private val mutable = MutableStateFlow(ProbeState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var generation = 0
    private var closed = false
    fun run(response: ProbeResponse): Job? {
        if (closed || state.value.isBusy || !session.isCurrent(lease)) return null
        val current = ++generation; mutable.value = ProbeState(isBusy = true)
        return scope.launch {
            try {
                val configured = AuthenticatedHTTPClient.create("https://catalog.example.test/api", ProbeTransport(response), session, lease)
                val answer = when (configured) { is Outcome.Ok -> configured.value.requestEntity(HTTPMethod.GET, "probe", RequestOptions(correlationId = "catalog-probe")); is Outcome.Err -> configured }
                ensureActive(); if (current != generation || !session.isCurrent(lease)) return@launch
                val report = when (answer) {
                    is Outcome.Ok -> "GET /api/probe · ${answer.value.metadata.status} · ${lease.scope.accountId} · request ${answer.value.metadata.requestId ?: "none"}"
                    is Outcome.Err -> answer.error.meta.message
                }
                mutable.value = ProbeState(report)
            } catch (cancel: CancellationException) { throw cancel
            } catch (defect: Exception) {
                ensureActive()
                if (current == generation && session.isCurrent(lease)) mutable.value = ProbeState("The local probe could not complete.")
            } finally {
                if (current == generation) { task = null; mutable.value = state.value.copy(isBusy = false) }
            }
        }.also { task = it }
    }
    fun shutdown() { closed = true; ++generation; task?.cancel(); task = null; mutable.value = ProbeState() }
}
