package dev.mobilefoundry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.kernel.publicInfo
import dev.mobilefoundry.query.QueryState

/** Caller-selected copy; the owning application can inject localized strings. */
data class QueryCopy(
    val idle: String,
    val loading: String,
    val refreshing: String,
    val empty: String,
    val refresh: String = "Refresh",
    val retry: String = "Retry",
    val cancel: String = "Cancel loading",
)

/** Pure rendering. The host owns scrolling, theme, state observation and effects. */
@Composable
fun <Value : Any> QueryContent(
    state: QueryState<Value>,
    copy: QueryCopy,
    refresh: () -> Unit,
    cancel: () -> Unit,
    modifier: Modifier = Modifier,
    isEmpty: (Value) -> Boolean = { false },
    content: @Composable (Value) -> Unit,
) {
    @Composable fun Snapshot(value: Value) {
        if (isEmpty(value)) Text(copy.empty) else content(value)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            QueryState.Idle -> Text(copy.idle)
            is QueryState.Loading -> {
                val label = if (state.previous == null) copy.loading else copy.refreshing
                CircularProgressIndicator(Modifier.semantics { contentDescription = label })
                Text(label)
                state.previous?.let { Snapshot(it) }
                TextButton(onClick = cancel) { Text(copy.cancel) }
            }
            is QueryState.Loaded -> Snapshot(state.value)
            is QueryState.Failed -> {
                Text(state.failure.publicInfo().meta.message)
                state.previous?.let { Snapshot(it) }
                Button(onClick = refresh) { Text(copy.retry) }
            }
        }
        OutlinedButton(onClick = refresh) { Text(copy.refresh) }
    }
}
