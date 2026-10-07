package dev.mobilefoundry.catalog.ui.health

import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

private enum class Scenario(val label: String) {
    HEALTHY("Healthy"), UNAVAILABLE("Unavailable"), MALFORMED("Malformed"), VALIDATION("Validation"), RATE_LIMITED("Rate limited"), TIMEOUT("Timeout"),
}
private class CatalogTransport(private val scenario: Scenario) : HTTPTransport {
    override suspend fun send(request: PreparedRequest): AppResult<WireResponse> {
        delay(if (scenario == Scenario.TIMEOUT) 1_000 else 250)
        val headers = mutableMapOf("x-request-id" to "catalog-request", "etag" to "\"catalog-v1\"")
        val (status, body) = when (scenario) {
            Scenario.HEALTHY, Scenario.TIMEOUT -> 200 to "{\"status\":\"ok\"}"
            Scenario.UNAVAILABLE -> 503 to "{\"detail\":\"The service is temporarily unavailable.\"}"
            Scenario.MALFORMED -> 200 to "{"
            Scenario.VALIDATION -> 422 to "{\"detail\":\"Check the supplied input.\",\"fields\":{\"example\":\"Required.\"}}"
            Scenario.RATE_LIMITED -> { headers["retry-after"] = "2"; 429 to "{\"detail\":\"Too many requests.\"}" }
        }
        return Outcome.Ok(WireResponse(status, headers, body.toByteArray()))
    }
}
private sealed interface Phase {
    data object Loading : Phase
    data class Success(val health: Health) : Phase
    data class Failed(val failure: Failure) : Phase
}

@Composable
fun HealthCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var scenario by remember { mutableStateOf(Scenario.HEALTHY) }
    var generation by remember { mutableIntStateOf(0) }
    var phase: Phase by remember { mutableStateOf(Phase.Loading) }
    LaunchedEffect(scenario, generation) {
        phase = Phase.Loading
        try {
            val http = when (val created = DefaultHTTPClient.create("https://catalog.invalid/v1", CatalogTransport(scenario))) {
                is Outcome.Ok -> created.value
                is Outcome.Err -> { phase = Phase.Failed(created.error); return@LaunchedEffect }
            }
            val answer = HealthService(http).ready(RequestOptions(timeoutMilliseconds = if (scenario == Scenario.TIMEOUT) 100 else 15_000, correlationId = "catalog-correlation"))
            currentCoroutineContext().ensureActive()
            phase = when (answer) { is Outcome.Ok -> Phase.Success(answer.value); is Outcome.Err -> Phase.Failed(answer.error) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (defect: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e("FoundryHealth", "Unexpected health error", defect)
            phase = Phase.Failed(Failure.Internal(FailureMeta("An unexpected error occurred.")))
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("HTTP health", style = MaterialTheme.typography.headlineMedium)
        Text("Responses are injected locally. No backend is needed. Changing the example cancels the previous request.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Scenario.entries.forEach { example -> FilterChip(selected = scenario == example, onClick = { scenario = example }, label = { Text(example.label) }) }
        }
        OutlinedButton(onClick = { generation += 1 }) { Text("Run again") }
        Text("Readiness", style = MaterialTheme.typography.titleLarge)
        when (val state = phase) {
            Phase.Loading -> { CircularProgressIndicator(); Text("Checking health…") }
            is Phase.Success -> {
                Text("Healthy", style = MaterialTheme.typography.titleMedium)
                Text("Status: ok")
                state.health.requestId?.let { Text("Request: $it") }
                state.health.version?.let { Text("Version: $it") }
            }
            is Phase.Failed -> {
                val info = state.failure.publicInfo()
                Text(title(info.kind), style = MaterialTheme.typography.titleMedium)
                Text(info.meta.message)
                if (info is Failure.Invalid) info.fields.toSortedMap().forEach { (field, message) -> Text("$field: $message") }
                if (info is Failure.RateLimited) info.retryAfter?.let { Text("Retry timing: ${it.milliseconds} ms") }
                info.meta.requestId?.let { Text("Request: $it") }
            }
        }
    }
}
private fun title(kind: FailureKind): String = when (kind) {
    FailureKind.UNAUTHENTICATED -> "Sign in required"
    FailureKind.FORBIDDEN -> "Access denied"
    FailureKind.RATE_LIMITED -> "Please wait"
    FailureKind.UNAVAILABLE -> "Service unavailable"
    FailureKind.TIMEOUT -> "Request timed out"
    FailureKind.CANCELED -> "Request canceled"
    FailureKind.INTERNAL -> "Unexpected response"
    FailureKind.NOT_FOUND -> "Not found"
    FailureKind.INVALID -> "Check input"
    FailureKind.CONFLICT -> "Data changed"
}
