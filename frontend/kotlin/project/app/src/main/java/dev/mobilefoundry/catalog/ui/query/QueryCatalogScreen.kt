package dev.mobilefoundry.catalog.ui.query

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.kernel.Failure
import dev.mobilefoundry.kernel.FailureMeta
import dev.mobilefoundry.query.*
import dev.mobilefoundry.ui.components.feedback.query.QueryContent
import dev.mobilefoundry.ui.components.feedback.query.QueryCopy

private enum class QueryScenario(val label: String) {
    IDLE("Idle"), LOADING("Loading"), CONTENT("Content"), EMPTY("Empty"),
    REFRESHING("Refreshing"), REFRESHING_EMPTY("Refreshing empty"),
    FAILURE("Failure"), FAILURE_CONTENT("Failure with content"), FAILURE_EMPTY("Failure with empty"),
    INTERNAL_FAILURE("Internal failure");

    val state: QueryState<String> get() {
        val content = "Workspace is ready."
        val failure = Failure.Unavailable(FailureMeta("Workspace is temporarily unavailable."))
        return when (this) {
            IDLE -> QueryState.Idle
            LOADING -> QueryState.Loading(null)
            CONTENT -> QueryState.Loaded(content)
            EMPTY -> QueryState.Loaded("")
            REFRESHING -> QueryState.Loading(content)
            REFRESHING_EMPTY -> QueryState.Loading("")
            FAILURE -> QueryState.Failed(failure, null)
            FAILURE_CONTENT -> QueryState.Failed(failure, content)
            FAILURE_EMPTY -> QueryState.Failed(failure, "")
            INTERNAL_FAILURE -> QueryState.Failed(Failure.Internal(FailureMeta("Private diagnostic detail")), null)
        }
    }
    val starting: QueryScenario get() = when (state.value) {
        null -> LOADING
        "" -> REFRESHING_EMPTY
        else -> REFRESHING
    }
    val restored: QueryScenario get() = when (state.value) {
        null -> IDLE
        "" -> EMPTY
        else -> CONTENT
    }
}

/** A scalar consumer with manually selected states and no service or request owner. */
@Composable
fun QueryCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var scenario by rememberSaveable { mutableStateOf(QueryScenario.IDLE) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Async UI patterns", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QueryScenario.entries.forEach { value ->
                FilterChip(selected = scenario == value, onClick = { scenario = value }, label = { Text(value.label) })
            }
        }
        Text("Choose a state. Refresh or Retry starts loading; Cancel restores the last content.")
        Text("Workspace", style = MaterialTheme.typography.titleLarge)
        QueryContent(scenario.state, QueryCopy(
            idle = "Ready to load workspace.", loading = "Loading workspace…",
            refreshing = "Refreshing workspace…", empty = "No workspace content.",
        ), refresh = { scenario = scenario.starting }, cancel = { scenario = scenario.restored },
            isEmpty = { it.isEmpty() }) { Text(it) }
    }
}
