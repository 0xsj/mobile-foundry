package dev.mobilefoundry.ui.components.display.trendbadge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.mobilefoundry.ui.components.feedback.alert.MessageTone
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class TrendDirection { UP, DOWN, STEADY }
/** Caller-formatted comparison. Direction never decides whether an increase is desirable. */
@Composable
fun TrendBadge(label: String, direction: TrendDirection, modifier: Modifier = Modifier, tone: MessageTone = MessageTone.NEUTRAL) {
    val t = FoundryTheme.tokens
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Text(when (direction) { TrendDirection.UP -> "↗"; TrendDirection.DOWN -> "↘"; TrendDirection.STEADY -> "→" },
            Modifier.clearAndSetSemantics {}, style = t.typography.caption, color = tone.color(t))
        Text(label, style = t.typography.caption, color = tone.color(t))
    }
}
