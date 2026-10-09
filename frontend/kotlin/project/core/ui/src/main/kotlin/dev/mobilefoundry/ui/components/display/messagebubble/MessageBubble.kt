package dev.mobilefoundry.ui.components.display.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class MessageDirection { INCOMING, OUTGOING }

/** Logical start/end placement. Delivery metadata and interactive accessories are caller supplied. */
@Composable
fun MessageBubble(author: String, text: String, metadata: String, modifier: Modifier = Modifier,
    direction: MessageDirection = MessageDirection.INCOMING, accessories: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    Row(modifier.fillMaxWidth()) {
        if (direction == MessageDirection.OUTGOING) Spacer(Modifier.width(t.space.section))
        Column(Modifier.weight(1f).background(
            if (direction == MessageDirection.OUTGOING) t.colors.accentTint.color else t.colors.surfacePanel.color,
            RoundedCornerShape(t.shape.panel)).border(1.dp, t.colors.line.color, RoundedCornerShape(t.shape.panel)).padding(t.space.stack),
            verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
            Text(author, style = t.typography.label, color = t.colors.ink.color)
            if (text.isNotEmpty()) SelectionContainer { Text(text, style = t.typography.body, color = t.colors.ink.color) }
            accessories()
            Text(metadata, style = t.typography.caption, color = t.colors.inkSecondary.color)
        }
        if (direction == MessageDirection.INCOMING) Spacer(Modifier.width(t.space.section))
    }
}
