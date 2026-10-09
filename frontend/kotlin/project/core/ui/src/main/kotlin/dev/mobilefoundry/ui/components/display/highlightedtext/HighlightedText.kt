package dev.mobilefoundry.ui.components.display.highlightedtext

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import dev.mobilefoundry.ui.theme.FoundryTheme

data class HighlightSegment(val text: String, val highlighted: Boolean = false)

/** One native text value from supplied literal runs. No query matching, ranges, parsing or interaction. */
@Composable
fun HighlightedText(segments: List<HighlightSegment>, modifier: Modifier = Modifier, style: TextStyle? = null,
    emphasisColor: Color = Color.Unspecified) {
    val t = FoundryTheme.tokens
    val value = buildAnnotatedString {
        segments.forEach { segment ->
            if (segment.highlighted) withStyle(SpanStyle(color = if (emphasisColor == Color.Unspecified) t.colors.accent.color else emphasisColor,
                fontWeight = FontWeight.SemiBold)) { append(segment.text) }
            else append(segment.text)
        }
    }
    Text(value, modifier, style = style ?: t.typography.body, color = t.colors.ink.color)
}
