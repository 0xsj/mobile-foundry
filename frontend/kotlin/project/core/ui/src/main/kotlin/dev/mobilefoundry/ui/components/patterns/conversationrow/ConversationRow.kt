package dev.mobilefoundry.ui.components.patterns.conversationrow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.display.badge.Badge
import dev.mobilefoundry.ui.components.display.listrow.ListRow
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.theme.FoundryTheme

/** One native action. Leading artwork is passive; routes and unread copy belong to the caller. */
@Composable
fun ConversationRow(title: String, preview: String, timestamp: String, onOpen: () -> Unit,
    modifier: Modifier = Modifier, unreadLabel: String? = null, enabled: Boolean = true,
    leading: @Composable () -> Unit = {}) {
    val t = FoundryTheme.tokens
    ListRow(title, preview, modifier.clickable(enabled = enabled, role = Role.Button, onClick = onOpen),
        leading = { Box(Modifier.clearAndSetSemantics {}) { leading() } }, trailing = {
            Column(verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
                Text(timestamp, style = t.typography.caption, color = t.colors.inkSecondary.color)
                if (unreadLabel != null) Badge(unreadLabel, tone = MessageTone.INFO)
            }
        })
}
