package dev.mobilefoundry.ui.components.display.timelineitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Passive marker/connector. The host owns ordering, time formatting and independent actions in the content slot. */
@Composable
fun TimelineItem(title: String, timestamp: String, modifier: Modifier = Modifier, showsConnector: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    val t = FoundryTheme.tokens
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Column(Modifier.width(16.dp).fillMaxHeight().padding(top = 8.dp).clearAndSetSemantics {},
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(t.colors.accent.color, CircleShape))
            if (showsConnector) Box(Modifier.width(1.dp).weight(1f).background(t.colors.line.color))
        }
        Column(Modifier.weight(1f).padding(bottom = t.space.inline), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(title, style = t.typography.label)
            Text(timestamp, style = t.typography.caption, color = t.colors.inkSecondary.color)
            content()
        }
    }
}
