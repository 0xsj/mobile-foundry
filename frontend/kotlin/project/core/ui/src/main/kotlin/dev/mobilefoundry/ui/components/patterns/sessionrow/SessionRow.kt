package dev.mobilefoundry.ui.components.patterns.sessionrow

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive session copy; caller owns current-session and revocation policy. Actions remain independent. */
@Composable
fun SessionRow(title: String, activityLabel: String, modifier: Modifier = Modifier, detail: String? = null,
    icon: @Composable () -> Unit = {}, status: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        ListRow(title, subtitle = detail, leading = { Box(Modifier.clearAndSetSemantics {}) { icon() } })
        Text(activityLabel, style = t.typography.caption, color = t.colors.inkSecondary.color)
        status(); actions()
    }
}
