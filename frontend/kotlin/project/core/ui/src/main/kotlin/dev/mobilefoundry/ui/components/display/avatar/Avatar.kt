package dev.mobilefoundry.ui.components.display.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.theme.FoundryTheme

enum class AvatarShape { CIRCLE, ROUNDED }

/** Passive admitted artwork or caller-supplied fallback; no fetching or image decoding. */
@Composable
fun Avatar(label: String, fallback: String, modifier: Modifier = Modifier, size: Dp = 48.dp,
    shape: AvatarShape = AvatarShape.CIRCLE, content: (@Composable BoxScope.() -> Unit)? = null) {
    require(size.value.isFinite() && size > 0.dp)
    val t = FoundryTheme.tokens
    val outline = RoundedCornerShape(if (shape == AvatarShape.CIRCLE) size / 2 else minOf(t.shape.panel, size / 4))
    Box(modifier.size(size).clip(outline).background(t.colors.accentTint.color)
        .clearAndSetSemantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (content != null) content()
        else Text(fallback, style = t.typography.label, color = t.colors.accent.color, maxLines = 1,
            modifier = Modifier.padding(4.dp), autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased())
    }
}
