package dev.mobilefoundry.ui.components.feedback.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.styles.tokens.FoundryTokens
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class MessageTone { NEUTRAL, INFO, WARNING, CRITICAL;
    fun color(tokens: FoundryTokens): Color = when (this) {
        NEUTRAL -> tokens.colors.inkSecondary.color
        INFO -> tokens.colors.info.color
        WARNING -> tokens.colors.warn.color
        CRITICAL -> tokens.colors.crit.color
    }
}

/** Persistent feedback. Actions and dismissal belong to the caller. */
@Composable
fun InlineAlert(title: String, message: String, modifier: Modifier = Modifier,
                tone: MessageTone = MessageTone.INFO, actions: @Composable ColumnScope.() -> Unit = {}) {
    val t = FoundryTheme.tokens
    val shape = RoundedCornerShape(t.shape.radii[2])
    Column(modifier.fillMaxWidth().background(t.colors.surfacePanel.color, shape)
        .border(1.dp, tone.color(t), shape).padding(t.space.stack),
        verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(title, style = t.typography.label, color = tone.color(t))
        Text(message, color = t.colors.ink.color)
        actions()
    }
}
