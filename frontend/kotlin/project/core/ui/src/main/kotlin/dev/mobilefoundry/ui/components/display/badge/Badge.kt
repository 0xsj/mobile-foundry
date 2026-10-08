package dev.mobilefoundry.ui.components.display.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, tone: MessageTone = MessageTone.NEUTRAL) {
    val t = FoundryTheme.tokens
    val shape = RoundedCornerShape(t.shape.pill)
    Text(text, modifier.background(t.colors.surfacePanel.color, shape).border(1.dp, tone.color(t), shape)
        .padding(horizontal = t.space.inline, vertical = t.space.steps[1]),
        style = t.typography.caption, color = tone.color(t))
}
