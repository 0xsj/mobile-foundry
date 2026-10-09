package dev.mobilefoundry.ui.components.patterns.notificationrow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One native open action, supplied unread emphasis/copy and independent sibling actions. Leading artwork is passive. */
@Composable
fun NotificationRow(title: String, message: String, timeLabel: String, stateLabel: String, isUnread: Boolean,
    accessibilityLabel: String, onOpen: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    leading: @Composable () -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Row(Modifier.fillMaxWidth().heightIn(min = t.shape.minimumInteractive)
            .clickable(enabled = enabled, role = Role.Button) { if (enabled) onOpen() }
            .semantics { contentDescription = accessibilityLabel }, horizontalArrangement = Arrangement.spacedBy(t.space.stack)) {
            Box(Modifier.clearAndSetSemantics {}) { leading() }
            Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                Text(title, style = t.typography.body, fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal)
                Text(message, style = t.typography.body, color = t.colors.inkSecondary.color)
                Text(timeLabel, style = t.typography.caption, color = t.colors.inkSecondary.color)
                Text(stateLabel, style = t.typography.caption, color = if (isUnread) t.colors.accent.color else t.colors.inkSecondary.color)
            }
        }
        actions()
    }
}
