package dev.mobilefoundry.ui.components.feedback.typing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

/** Caller decides whether typing is present. Dots are decorative; the supplied label is always visible. */
@Composable
fun TypingIndicator(label: String, modifier: Modifier = Modifier, animated: Boolean = true) {
    val t = FoundryTheme.tokens
    val opacity = if (animated && !t.motion.reduced) {
        val transition = rememberInfiniteTransition(label = "Typing pulse")
        val pulse by transition.animateFloat(0.4f, 1f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "Typing opacity")
        pulse
    } else 1f
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(t.space.inline)) {
        Row(Modifier.alpha(opacity).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.size(6.dp).background(t.colors.accent.color, CircleShape)) }
        }
        Text(label, style = t.typography.caption, color = t.colors.inkSecondary.color,
            modifier = Modifier.clearAndSetSemantics {})
    }
}
