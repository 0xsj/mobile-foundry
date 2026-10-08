package dev.mobilefoundry.ui.components.feedback.query

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
import dev.mobilefoundry.ui.theme.FoundryTheme
import dev.mobilefoundry.kernel.publicInfo
import dev.mobilefoundry.query.QueryState

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
    val tokens = FoundryTheme.tokens
    @Composable fun Snapshot(value: Value) {
        if (isEmpty(value)) Text(copy.empty, color = tokens.colors.inkMuted.color) else content(value)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
        when (state) {
            QueryState.Idle -> Text(copy.idle, color = tokens.colors.inkSecondary.color)
            is QueryState.Loading -> {
                val label = if (state.previous == null) copy.loading else copy.refreshing
                CircularProgressIndicator(Modifier.semantics { contentDescription = label }, color = tokens.colors.accent.color)
                Text(label)
                state.previous?.let { Snapshot(it) }
                TextButton(onClick = cancel) { Text(copy.cancel) }
            }
            is QueryState.Loaded -> Snapshot(state.value)
            is QueryState.Failed -> {
                Text(state.failure.publicInfo().meta.message, color = tokens.colors.crit.color)
                state.previous?.let { Snapshot(it) }
                Button(onClick = refresh) { Text(copy.retry) }
            }
        }
        OutlinedButton(onClick = refresh) { Text(copy.refresh) }
    }
}
