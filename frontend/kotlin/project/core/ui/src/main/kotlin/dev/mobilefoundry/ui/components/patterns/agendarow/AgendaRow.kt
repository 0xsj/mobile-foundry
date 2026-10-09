package dev.mobilefoundry.ui.components.patterns.agendarow

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Supplied schedule copy and independent status/action slots. No date arithmetic, route or event ownership. */
@Composable
fun AgendaRow(title: String, timeLabel: String, modifier: Modifier = Modifier, detail: String? = null,
    status: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(timeLabel, style = t.typography.caption, color = t.colors.inkSecondary.color)
            Text(title, style = t.typography.label)
            detail?.let { Text(it, style = t.typography.body, color = t.colors.inkSecondary.color) }
        }
        status(); actions()
    }
}
